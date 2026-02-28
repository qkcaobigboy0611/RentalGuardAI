/**
 * @author qkcao
 * @date 2026/2/4 18:30
 */
package com.rental.guard.ai.domain.service.v1;

import com.rental.guard.ai.domain.dto.AgentDecision;
import com.rental.guard.ai.domain.dto.v1.AgentResponse;
import com.rental.guard.ai.domain.dto.v1.SessionManager;
import com.rental.guard.ai.domain.service.ReActAgent;
import com.rental.guard.ai.domain.service.v1.tool.AgentTool;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static io.micrometer.common.util.StringUtils.truncate;

/**
 * ReAct循环引擎 - 实现思考-行动循环
 * * 优化说明 (2026/02 重构)：
 * 1. 【架构升级】引入 LangChain4j 的 @AiService，替代手动 JSON 拼接和解析。
 * 2. 【类型安全】使用 AgentDecision 强类型对象，彻底解决 "Fragile Parsing" 问题。
 * 3. 【逻辑简化】核心循环逻辑更清晰，专注于业务流转而非字符串处理。
 * <p>
 * 解决幻觉方案：强类型 POJO + 负反馈 Observation + 动态工具描述
 */
@Slf4j
public class ReActEngine {
    // 替换原有的 LLMService，使用强类型的 ReActAgent
    private final ReActAgent reActAgent;
    private final Map<String, AgentTool> availableTools;
    private final LongTermMemoryService memoryService;
    private final SimpleKnowledgeGraphService kgService;

    // ReAct循环配置
    private static final int MAX_ITERATIONS = 5;
    private static final int MAX_TOOL_CALLS_PER_STEP = 3;

    @Data
    public static class ReActStep {
        private int stepNumber;
        private String thought;
        private String action;
        private Map<String, Object> actionInput;
        private Object observation;
        private LocalDateTime timestamp;

        public ReActStep(int stepNumber, String thought) {
            this.stepNumber = stepNumber;
            this.thought = thought;
            this.timestamp = LocalDateTime.now();
        }
    }

    // 构造函数注入 ReActAgent
    public ReActEngine(ReActAgent reActAgent,
                       Map<String, AgentTool> availableTools,
                       LongTermMemoryService memoryService,
                       SimpleKnowledgeGraphService kgService) {
        this.reActAgent = reActAgent;
        this.availableTools = availableTools;
        this.memoryService = memoryService;
        this.kgService = kgService;
    }

    /**
     * 执行ReAct循环
     */
    public CompletableFuture<AgentResponse> executeReActLoop(
            String sessionId,
            String userInput,
            SessionManager session,
            String scenario,
            String userId) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 0. 预加载上下文：获取记忆和风险检测（并行执行）
                CompletableFuture<String> memoryFuture = CompletableFuture.supplyAsync(() ->
                        memoryService.getUserMemoryContext(sessionId, userId)
                );
                CompletableFuture<RiskAssessment> riskFuture = CompletableFuture.supplyAsync(() ->
                        kgService.assessRisk(userInput)
                );

                CompletableFuture.allOf(memoryFuture, riskFuture).join();
                String memoryContext = memoryFuture.get();
                RiskAssessment riskAssessment = riskFuture.get();

                // 1. 构建增强的用户输入
                String enhancedInput = buildEnhancedInput(userInput, memoryContext, riskAssessment);

                // 2. 执行ReAct思考循环
                List<ReActStep> steps = new ArrayList<>();
                StringBuilder finalAnswer = new StringBuilder();
                List<AgentResponse.RetrievedDocument> allDocuments = new ArrayList<>();
                Map<String, Object> collectedData = new ConcurrentHashMap<>();

                // 将风险检测结果存入收集数据
                if (riskAssessment != null && !riskAssessment.getMatches().isEmpty()) {
                    collectedData.put("risk_assessment", riskAssessment);
                }

                // 初始思考记录
                String initialThought = "开始分析用户问题：" + userInput;
                if (riskAssessment != null && !riskAssessment.getMatches().isEmpty()) {
                    initialThought += "（检测到" + riskAssessment.getMatches().size() + "个风险实体）";
                }

                ReActStep currentStep = new ReActStep(0, initialThought);
                steps.add(currentStep);

                // ReAct循环主体
                for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
                    log.info("ReAct循环第 {} 次迭代，会话: {}", iteration, sessionId);

                    // --- 1. 思考阶段 (通过 LangChain4j 获取结构化决策) ---
                    // 这里利用 LangChain4j 的结构化输出防止格式幻觉
                    AgentDecision decision = thinkAndDecide(
                            enhancedInput,
                            scenario,
                            steps,
                            collectedData
                    );

                    String actionType = decision.getAction();
                    String reasoning = decision.getReasoning();

                    // 记录思考步骤
                    currentStep = new ReActStep(iteration, reasoning);
                    currentStep.setAction(actionType);
                    steps.add(currentStep);

                    // --- 2. 行动阶段 ---
                    // 执行与抗幻觉校验
                    if ("final_answer".equals(actionType)) {
                        // case: 给出最终答案
                        String answer = decision.getAnswer();
                        if (StringUtils.isBlank(answer)) {
                            // 容错：如果模型未填 answer 但填了 reasoning
                            answer = reasoning;
                        }
                        finalAnswer.append(answer);
                        log.info("ReAct循环结束，生成最终答案");
                        break;

                    } else if ("tool_call".equals(actionType)) {
                        // case: 调用工具
                        List<AgentDecision.ToolCallRequest> toolCalls = decision.getToolCalls();
                        //处理"空调用"幻觉
                        if (toolCalls == null || toolCalls.isEmpty()) {
                            log.warn("决策为 tool_call 但未提供工具列表，跳过本次执行");
                            continue;
                        }

                        // 记录 Input 用于调试
                        Map<String, Object> inputLog = new HashMap<>();
                        inputLog.put("calls", toolCalls);
                        currentStep.setActionInput(inputLog);

                        // 并行执行工具调用，并捕获"工具名"幻觉
                        List<CompletableFuture<Map<String, Object>>> toolFutures = new ArrayList<>();
                        for (int i = 0; i < Math.min(toolCalls.size(), MAX_TOOL_CALLS_PER_STEP); i++) {
                            toolFutures.add(executeSingleToolWithValidation(toolCalls.get(i), session));
                        }

                        // 等待执行完成
                        if (!toolFutures.isEmpty()) {
                            CompletableFuture.allOf(toolFutures.toArray(new CompletableFuture[0])).join();
                        }

                        // 收集结果
                        List<Map<String, Object>> observations = new ArrayList<>();
                        for (CompletableFuture<Map<String, Object>> future : toolFutures) {
                            observations.add(future.join());
                        }

                        currentStep.setObservation(observations);

                        // 处理并归档工具结果
                        processToolResults(observations, collectedData, allDocuments);

                        // 错误检测
                        boolean shouldContinue = evaluateShouldContinue(observations);
                        if (!shouldContinue) {
                            log.info("ReAct循环因工具执行错误提前结束，迭代次数: {}", iteration);
                            break;
                        }

                    } else {
                        log.warn("模型返回了未知的动作类型: {}", actionType);
                        // 尝试纠正或中断，这里选择中断防止死循环
                        // 处理"未知 Action"幻觉
                        currentStep.setObservation("Error: Unknown action '" + actionType + "'. Valid actions are: [tool_call, final_answer].");
                    }

                    // 检查最大迭代
                    if (iteration >= MAX_ITERATIONS) {
                        if (iteration == MAX_ITERATIONS) {
                            finalAnswer.append("已达到最大分析深度。结论：\n").append(generateSummaryFromSteps(steps, collectedData));
                        }

                        log.info("达到最大迭代次数: {}", MAX_ITERATIONS);
                        if (finalAnswer.length() == 0) {
                            finalAnswer.append("基于已有信息总结：\n")
                                    .append(generateSummaryFromSteps(steps, collectedData));
                        }
                        break;
                    }
                }

                // 3. 构建最终响应
                AgentResponse response = buildFinalResponse(
                        sessionId, enhancedInput, scenario, steps,
                        finalAnswer.toString(), allDocuments, collectedData,
                        memoryContext, riskAssessment, userId
                );

                // 4. 异步更新记忆
                if (session != null && session.getMessageHistory() != null) {
                    CompletableFuture.runAsync(() ->
                            memoryService.updateMemoryAsync(userId, session.getMessageHistory())
                    ).exceptionally(e -> {
                        log.warn("异步更新记忆失败", e);
                        return null;
                    });
                }
                return response;

            } catch (Exception e) {
                log.error("ReAct循环执行失败", e);
                return createErrorResponse(sessionId, userInput, e.getMessage());
            }
        });
    }

    /**
     * 带验证的工具执行 - 专门解决名称和参数幻觉
     */
    private CompletableFuture<Map<String, Object>> executeSingleToolWithValidation(
            AgentDecision.ToolCallRequest toolCall,
            SessionManager session) {

        String toolName = toolCall.getTool();
        Map<String, Object> params = toolCall.getParameters();
        AgentTool tool = availableTools.get(toolName);

        // 【抗幻觉校验 1】：检查工具是否存在
        if (tool == null) {
            log.warn("检测到工具名幻觉: {}", toolName);
            String errorMsg = String.format("Error: Tool '%s' not found. Available tools: %s",
                    toolName, availableTools.keySet());
            return CompletableFuture.completedFuture(Map.of("tool", toolName, "result", errorMsg));
        }

        // 【抗幻觉校验 2】：执行并捕获参数错误
        return tool.execute(params, session)
                .thenApply(result -> Map.of("tool", toolName, "result", result))
                .exceptionally(ex -> {
                    log.error("工具调用幻觉（参数或执行异常）: {}", toolName);
                    // 反馈具体的错误，让模型下一次修正参数
                    return Map.of("tool", toolName, "result", "Execution Error: " + ex.getMessage());
                });
    }


    /**
     * 执行增强版的 ReAct 循环 - 支持预取数据的注入
     * * @param preFetchedDocs  预取的 RAG 文档
     *
     * @param searchContent 预取的联网搜索结果
     * @param mcpResponse   预取的 MCP 服务器响应
     */
    public CompletableFuture<AgentResponse> executeReActLoop(
            String sessionId,
            String userInput,
            SessionManager session,
            String scenario,
            String userId,
            List<AgentResponse.RetrievedDocument> preFetchedDocs,
            String searchContent,
            Map<String, Object> mcpResponse) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 预加载核心上下文：记忆和实时风险实体检测 (并行执行)
                CompletableFuture<String> memoryFuture = CompletableFuture.supplyAsync(() ->
                        memoryService.getUserMemoryContext(sessionId, userId)
                );
                CompletableFuture<RiskAssessment> riskFuture = CompletableFuture.supplyAsync(() ->
                        kgService.assessRisk(userInput)
                );

                CompletableFuture.allOf(memoryFuture, riskFuture).join();
                String memoryContext = memoryFuture.get();
                RiskAssessment riskAssessment = riskFuture.get();

                // 2. 初始化 ReAct 状态机容器
                List<ReActStep> steps = new ArrayList<>();
                StringBuilder finalAnswer = new StringBuilder();
                List<AgentResponse.RetrievedDocument> allDocuments = new ArrayList<>(preFetchedDocs != null ? preFetchedDocs : Collections.emptyList());
                Map<String, Object> collectedData = new ConcurrentHashMap<>();

                // --- 注入预取数据到 Knowledge Base ---
                if (preFetchedDocs != null && !preFetchedDocs.isEmpty()) {
                    collectedData.put("rag_documents", preFetchedDocs);
                    collectedData.put("last_tool_rag_retrieval", System.currentTimeMillis());
                }
                if (StringUtils.isNotBlank(searchContent)) {
                    collectedData.put("web_search_results", searchContent);
                    collectedData.put("last_tool_web_search", System.currentTimeMillis());
                }
                if (mcpResponse != null && !mcpResponse.isEmpty()) {
                    collectedData.put("mcp_context", mcpResponse);
                    collectedData.put("last_tool_mcp_call", System.currentTimeMillis());
                }
                if (riskAssessment != null && !riskAssessment.getMatches().isEmpty()) {
                    collectedData.put("risk_assessment", riskAssessment);
                }

                // 3. 构建增强的用户输入 (注入用户偏好、长期记忆和初步风险提示)
                String enhancedInput = buildEnhancedInput(userInput, memoryContext, riskAssessment);

                // 4. 初始思考步骤记录
                String initialThought = "系统已预加载上下文信息（包含记忆、风险检测及相关文档）。开始针对场景 [" + scenario + "] 进行深度分析。";
                steps.add(new ReActStep(0, initialThought));

                // 5. ReAct 循环主体
                for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
                    log.info("ReAct 迭代 [{}/{}] 会话: {}", iteration, MAX_ITERATIONS, sessionId);

                    // --- 阶段 A: 思考 (Think) ---
                    // 调用 ReActAgent (LangChain4j Service) 获取结构化决策
                    AgentDecision decision = thinkAndDecide(
                            enhancedInput,
                            scenario,
                            steps,
                            collectedData
                    );

                    String actionType = decision.getAction();
                    String reasoning = decision.getReasoning();

                    // 记录当前步骤的思考
                    ReActStep currentStep = new ReActStep(iteration, reasoning);
                    currentStep.setAction(actionType);
                    steps.add(currentStep);

                    // --- 阶段 B: 行动 (Act) ---
                    if ("final_answer".equals(actionType)) {
                        // 结束条件：模型认为信息已足够
                        String answer = decision.getAnswer();
                        finalAnswer.append(StringUtils.defaultIfBlank(answer, reasoning));
                        log.info("智能体达成共识，生成最终答案。");
                        break;

                    } else if ("tool_call".equals(actionType)) {
                        // 行动：调用外部工具补充缺失信息
                        List<AgentDecision.ToolCallRequest> toolCalls = decision.getToolCalls();
                        if (toolCalls == null || toolCalls.isEmpty()) {
                            log.warn("检测到空工具调用指令，强制中断以防止死循环");
                            break;
                        }

                        // 并行执行本轮次的工具调用
                        List<CompletableFuture<Map<String, Object>>> toolFutures = new ArrayList<>();
                        int callsInThisStep = Math.min(toolCalls.size(), MAX_TOOL_CALLS_PER_STEP);

                        for (int i = 0; i < callsInThisStep; i++) {
                            AgentDecision.ToolCallRequest toolCall = toolCalls.get(i);
                            toolFutures.add(executeSingleTool(toolCall, session));
                        }

                        CompletableFuture.allOf(toolFutures.toArray(new CompletableFuture[0])).join();

                        // --- 阶段 C: 观察 (Observe) ---
                        List<Map<String, Object>> observations = new ArrayList<>();
                        for (CompletableFuture<Map<String, Object>> future : toolFutures) {
                            observations.add(future.join());
                        }

                        currentStep.setObservation(observations);

                        // 更新全局知识库和支持文档
                        processToolResults(observations, collectedData, allDocuments);

                        // 错误检查与熔断
                        if (!evaluateShouldContinue(observations)) {
                            finalAnswer.append("分析过程中遇到工具执行错误，已基于当前已知信息提供部分分析：\n");
                            finalAnswer.append(generateSummaryFromSteps(steps, collectedData));
                            break;
                        }

                    } else {
                        log.error("收到未知 Action 类型: {}", actionType);
                        break;
                    }

                    // 达到最大尝试次数后的降级处理
                    if (iteration == MAX_ITERATIONS) {
                        log.warn("达到最大迭代次数 {}，强制生成总结响应", MAX_ITERATIONS);
                        finalAnswer.append(generateSummaryFromSteps(steps, collectedData));
                    }
                }

                // 6. 整合结果并构建最终 AgentResponse
                AgentResponse response = buildFinalResponse(
                        sessionId, enhancedInput, scenario, steps,
                        finalAnswer.toString(), allDocuments, collectedData,
                        memoryContext, riskAssessment, userId
                );

                // 7. 异步更新用户长期记忆
                asyncUpdateUserMemory(userId, session, response);

                return response;

            } catch (Exception e) {
                log.error("ReAct 核心循环异常", e);
                return createErrorResponse(sessionId, userInput, e.getMessage());
            }
        });
    }

    /**
     * 执行单个工具调用的辅助方法
     */
    private CompletableFuture<Map<String, Object>> executeSingleTool(
            AgentDecision.ToolCallRequest toolCall,
            SessionManager session) {

        String toolName = toolCall.getTool();
        Map<String, Object> params = toolCall.getParameters();
        AgentTool tool = availableTools.get(toolName);

        if (tool == null) {
            log.warn("工具库中不存在: {}", toolName);
            return CompletableFuture.completedFuture(Map.of("tool", toolName, "result", "Error: Tool not found"));
        }

        return tool.execute(params, session)
                .thenApply(result -> {
                    Map<String, Object> resultMap = new HashMap<>();
                    resultMap.put("tool", toolName);
                    resultMap.put("result", result);
                    return resultMap;
                })
                .exceptionally(ex -> {
                    log.error("工具 {} 执行异常", toolName, ex);
                    return Map.of("tool", toolName, "result", "Error: " + ex.getMessage());
                });
    }

    /**
     * 异步更新用户长期记忆 (Profile Update)
     */
    private void asyncUpdateUserMemory(String userId, SessionManager session, AgentResponse response) {
        if (StringUtils.isNotBlank(userId) && session != null) {
            CompletableFuture.runAsync(() -> {
                log.debug("正在为用户 {} 更新风险偏好和历史记录", userId);
                memoryService.updateMemoryAsync(userId, session.getMessageHistory());
            }, CompletableFuture.delayedExecutor(500, java.util.concurrent.TimeUnit.MILLISECONDS));
        }
    }

    /**
     * 核心思考方法：调用 LangChain4j Agent
     * 不再需要手动解析 JSON 字符串
     */
    private AgentDecision thinkAndDecide(
            String userInput,
            String scenario,
            List<ReActStep> steps,
            Map<String, Object> collectedData) {

        // 1. 准备上下文文本 (Prompt Context)
        // 我们只需要把信息组织好喂给 LLM，结构化约束由框架负责
        String context = buildContextString(userInput, scenario, steps, collectedData);

        // 2. 调用 AI Service
        try {
            return reActAgent.think(context);
        } catch (Exception e) {
            log.error("LLM 思考过程发生异常", e);
            // 降级策略
            AgentDecision fallback = new AgentDecision();
            fallback.setAction("final_answer");
            fallback.setReasoning("AI 服务暂时不可用，启用降级处理。");
            fallback.setAnswer("抱歉，系统繁忙，无法进行深入分析。");
            return fallback;
        }
    }

    /**
     * 构建上下文信息字符串
     * 注意：不再需要包含 JSON 格式指令，只包含业务数据
     */
    private String buildContextString(
            String userInput,
            String scenario,
            List<ReActStep> steps,
            Map<String, Object> collectedData) {

        StringBuilder sb = new StringBuilder();

        // 1. 基础信息
        sb.append("### 当前任务状态\n");
        sb.append("- 用户输入: ").append(userInput).append("\n");
        sb.append("- 场景: ").append(scenario).append("\n");
        sb.append("- 当前轮次: ").append(steps.size()).append(" / ").append(MAX_ITERATIONS).append("\n\n");

        // 2. 已收集的数据
        if (!collectedData.isEmpty()) {
            sb.append("### 已知信息 (Knowledge)\n");
            collectedData.forEach((k, v) ->
                    sb.append("- ").append(k).append(": ").append(truncate(v.toString(), 300)).append("\n")
            );
            sb.append("\n");
        }

        // 3. 历史思考路径
        if (!steps.isEmpty()) {
            sb.append("\n### 执行历史与观察 (如果 Observation 包含 Error，请修正后重试)\n");
            for (ReActStep step : steps) {
                sb.append("Step ").append(step.getStepNumber()).append(":\n");
                sb.append("  Thought: ").append(step.getThought()).append("\n");
                if (step.getAction() != null) {
                    sb.append("  Action: ").append(step.getAction()).append("\n");
                    sb.append("  Observation: ").append(truncate(String.valueOf(step.getObservation()), 200)).append("\n");
                }
            }
            sb.append("\n");
        }

        // 4. 工具描述
        sb.append("### 可用工具 (Tools)\n");
        for (Map.Entry<String, AgentTool> entry : availableTools.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ")
                    .append(entry.getValue().getDescription()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 处理工具结果
     */
    private void processToolResults(
            List<Map<String, Object>> observations,
            Map<String, Object> collectedData,
            List<AgentResponse.RetrievedDocument> allDocuments) {

        for (Map<String, Object> obs : observations) {
            String toolName = (String) obs.get("tool");
            Object result = obs.get("result");

            if (result instanceof Map) {
                Map<String, Object> resultMap = (Map<String, Object>) result;
                // 根据工具类型归档数据
                switch (toolName) {
                    case "rag_retrieval":
                        List<AgentResponse.RetrievedDocument> docs =
                                (List<AgentResponse.RetrievedDocument>) resultMap.get("documents");
                        if (docs != null) {
                            allDocuments.addAll(docs);
                            collectedData.put("rag_documents", docs);
                        }
                        break;
                    case "web_search":
                        collectedData.put("web_search_results", resultMap.get("results"));
                        break;
                    case "conversation_analysis":
                        collectedData.put("conversation_analysis", resultMap.get("analysis_result"));
                        break;
                    case "risk_assessment":
                        collectedData.put("detailed_risk_assessment", resultMap.get("assessment_result"));
                        break;
                }
            }
            // 记录该工具已调用，避免 LLM 重复调用
            collectedData.put("last_tool_" + toolName, System.currentTimeMillis());
        }
    }

    /**
     * 评估是否应该继续循环 (仅检查系统错误)
     */
    private boolean evaluateShouldContinue(List<Map<String, Object>> observations) {
        boolean hasError = observations.stream()
                .anyMatch(obs -> {
                    Object result = obs.get("result");
                    if (result instanceof Map) {
                        return ((Map<?, ?>) result).containsKey("error");
                    }
                    return false;
                });

        if (hasError) {
            log.warn("检测到工具执行内部错误，停止循环");
            return false;
        }
        return true;
    }

    /**
     * 从步骤中生成摘要
     */
    private String generateSummaryFromSteps(List<ReActStep> steps, Map<String, Object> collectedData) {
        StringBuilder summary = new StringBuilder();
        summary.append("分析过程总结：\n");
        for (ReActStep step : steps) {
            if (step.getStepNumber() > 0 && step.getThought() != null) {
                summary.append("- ").append(step.getThought()).append("\n");
            }
        }
        // 补充风险信息
        if (collectedData.containsKey("risk_assessment")) {
            RiskAssessment riskAssessment = (RiskAssessment) collectedData.get("risk_assessment");
            if (riskAssessment != null && !riskAssessment.getMatches().isEmpty()) {
                summary.append("\n风险提示：\n");
                riskAssessment.getMatches().forEach(match -> {
                    summary.append("- ").append(match.getExtractedEntity().getText())
                            .append(" (等级:").append(match.getRiskLevel()).append(")\n");
                });
            }
        }
        return summary.toString();
    }

    /**
     * 构建增强的用户输入
     */
    private String buildEnhancedInput(String originalInput,
                                      String memoryContext,
                                      RiskAssessment riskAssessment) {
        StringBuilder enhanced = new StringBuilder();
        enhanced.append("你是一个具备记忆能力和风险检测能力的租房风险分析智能体。\n\n");

        if (StringUtils.isNotBlank(memoryContext)) {
            enhanced.append("【用户偏好】\n").append(memoryContext).append("\n\n");
        }

        if (riskAssessment != null && !riskAssessment.getMatches().isEmpty()) {
            enhanced.append("【高风险预警】\n检测到以下风险实体：\n");
            riskAssessment.getMatches().forEach(match ->
                    enhanced.append("- ").append(match.getExtractedEntity().getText())
                            .append(" (").append(match.getRiskLevel()).append(")\n")
            );
            enhanced.append("\n");
        }

        enhanced.append("【用户问题】\n").append(originalInput);
        return enhanced.toString();
    }

    /**
     * 构建最终响应
     */
    private AgentResponse buildFinalResponse(
            String sessionId,
            String enhancedInput,
            String scenario,
            List<ReActStep> steps,
            String finalAnswer,
            List<AgentResponse.RetrievedDocument> documents,
            Map<String, Object> collectedData,
            String memoryContext,
            RiskAssessment riskAssessment,
            String userId) {

        double confidence = calculateConfidence(documents, collectedData, riskAssessment);
        String riskLevel = evaluateRiskLevel(scenario, documents, collectedData, riskAssessment);
        Map<String, Object> metadata = buildMetadata(steps, collectedData, riskAssessment, memoryContext);
        String detailedAnalysis = buildDetailedAnalysis(finalAnswer, riskAssessment);

        return AgentResponse.builder()
                .responseId("resp_react_" + UUID.randomUUID().toString())
                .sessionId(sessionId)
                .scenario(scenario)
                .responseType(AgentResponse.ResponseType.ANALYSIS)
                .coreLogic("ReAct V2 (LangChain4j Structured)")
                .detailedAnalysis(detailedAnalysis)
                .riskLevel(riskLevel)
                .confidence(Math.min(confidence, 0.95))
                .supportingDocuments(documents)
                .metadata(metadata)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // --- 辅助评估方法 (保持原业务逻辑不变) ---

    private double calculateConfidence(List<AgentResponse.RetrievedDocument> documents,
                                       Map<String, Object> collectedData,
                                       RiskAssessment riskAssessment) {
        double baseConfidence = 0.7;
        if (documents != null && !documents.isEmpty()) baseConfidence += 0.1;
        if (collectedData.containsKey("web_search_results")) baseConfidence += 0.1;
        if (riskAssessment != null && !riskAssessment.getMatches().isEmpty()) baseConfidence += 0.05;
        if (collectedData.containsKey("memory_context_used")) baseConfidence += 0.05;
        return Math.min(baseConfidence, 0.95);
    }

    private String evaluateRiskLevel(String scenario,
                                     List<AgentResponse.RetrievedDocument> documents,
                                     Map<String, Object> collectedData,
                                     RiskAssessment riskAssessment) {
        if (riskAssessment != null && !riskAssessment.getMatches().isEmpty()) {
            String highest = riskAssessment.getHighestRiskLevel();
            if ("CRITICAL".equals(highest) || "HIGH".equals(highest)) return highest;
            if (riskAssessment.getRiskScore() > 0.8) return "HIGH";
        }
        if ("租金欺诈".equals(scenario)) return "HIGH";
        return "MEDIUM";
    }

    private Map<String, Object> buildMetadata(List<ReActStep> steps,
                                              Map<String, Object> collectedData,
                                              RiskAssessment riskAssessment,
                                              String memoryContext) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("react_steps", steps.size());
        metadata.put("memory_used", StringUtils.isNotBlank(memoryContext));

        if (riskAssessment != null) {
            metadata.put("risk_score", riskAssessment.getRiskScore());
            metadata.put("risk_entity_count", riskAssessment.getMatches().size());
        }
        return metadata;
    }

    private String buildDetailedAnalysis(String baseAnalysis, RiskAssessment riskAssessment) {
        if (riskAssessment == null || riskAssessment.getMatches().isEmpty()) return baseAnalysis;

        StringBuilder sb = new StringBuilder(baseAnalysis);
        sb.append("\n\n--- 风险实体检测详情 ---\n");
        riskAssessment.getMatches().forEach(match -> {
            sb.append("⚠️ **").append(match.getExtractedEntity().getText()).append("**")
                    .append(" (").append(match.getRiskLevel()).append(")\n");
            if (StringUtils.isNotBlank(match.getKgEntity().getDescription())) {
                sb.append("   说明: ").append(match.getKgEntity().getDescription()).append("\n");
            }
        });
        return sb.toString();
    }

    private AgentResponse createErrorResponse(String sessionId, String userInput, String error) {
        return AgentResponse.builder()
                .responseId("err_" + UUID.randomUUID())
                .sessionId(sessionId)
                .scenario("System Error")
                .responseType(AgentResponse.ResponseType.ERROR)
                .detailedAnalysis("系统处理时发生错误: " + error)
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
