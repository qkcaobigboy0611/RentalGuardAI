/**
 * @author qkcao
 * @date 2026/2/4 18:30
 */
package com.rental.guard.ai.domain.service.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.guard.ai.domain.dto.v1.AgentResponse;
import com.rental.guard.ai.domain.dto.v1.SessionManager;
import com.rental.guard.ai.domain.service.LLMService;
import com.rental.guard.ai.domain.service.v1.tool.AgentTool;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.micrometer.common.util.StringUtils.truncate;

/**
 * ReAct循环引擎 - 实现思考-行动循环
 * * 优化说明：
 * 1. 【逻辑优化】evaluateShouldContinue: 移除了基于场景的硬编码终止条件（如强制RAG、强制轮数）。
 * 现在循环仅在发生系统错误或达到最大迭代次数时强制中断，其余情况由 LLM 自主决定是否给出 final_answer。
 * 2. 【提示词优化】buildThinkingPrompt: 增加了明确的决策策略指令，指导 LLM 在信息充足时停止循环。
 * 3. 【稳定性】parseDecision: 增强了 JSON 解析的鲁棒性，防止因格式问题导致循环异常。
 */
@Slf4j
public class ReActEngine {
    private final LLMService llmService;
    private final ObjectMapper objectMapper;
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

    public ReActEngine(LLMService llmService, ObjectMapper objectMapper,
                       Map<String, AgentTool> availableTools,
                       LongTermMemoryService memoryService,
                       SimpleKnowledgeGraphService kgService) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
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
                // 等待预加载完成
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

                // 初始思考
                String initialThought = "开始分析用户问题：" + userInput;
                if (riskAssessment != null && !riskAssessment.getMatches().isEmpty()) {
                    initialThought += "（检测到" + riskAssessment.getMatches().size() + "个风险实体）";
                }

                ReActStep currentStep = new ReActStep(0, initialThought);
                steps.add(currentStep);

                // ReAct循环
                for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
                    log.info("ReAct循环第 {} 次迭代，会话: {}", iteration, sessionId);

                    // 1. 思考阶段 - LLM决定下一步行动
                    String decision = thinkAndDecide(
                            enhancedInput,
                            scenario,
                            steps,
                            collectedData,
                            session
                    );

                    Map<String, Object> decisionMap = parseDecision(decision);
                    String actionType = (String) decisionMap.get("action");

                    if ("final_answer".equals(actionType)) {
                        // 【优化点】LLM 决定停止循环
                        String answer = (String) decisionMap.get("answer");
                        // 容错：如果 LLM 忘了写 answer 但写了 reasoning，用 reasoning 兜底
                        if (answer == null || answer.trim().isEmpty()) {
                            answer = (String) decisionMap.get("reasoning");
                        }
                        finalAnswer.append(answer);

                        currentStep = new ReActStep(iteration,
                                "决定给出最终答案: " + decisionMap.get("reasoning"));
                        currentStep.setAction("final_answer");
                        steps.add(currentStep);
                        break; // 退出循环

                    } else if ("tool_call".equals(actionType)) {
                        // 执行工具调用
                        List<Map<String, Object>> toolCalls =
                                (List<Map<String, Object>>) decisionMap.get("tool_calls");

                        // 容错校验
                        if (toolCalls == null || toolCalls.isEmpty()) {
                            log.warn("决策为 tool_call 但未提供工具列表，跳过本次执行");
                            continue;
                        }

                        currentStep = new ReActStep(iteration,
                                (String) decisionMap.get("reasoning"));
                        currentStep.setAction("tool_call");
                        currentStep.setActionInput(decisionMap);
                        steps.add(currentStep);

                        // 并行执行工具调用（限制数量）
                        List<CompletableFuture<Map<String, Object>>> toolFutures =
                                new ArrayList<>();

                        int toolCount = Math.min(toolCalls.size(), MAX_TOOL_CALLS_PER_STEP);
                        for (int i = 0; i < toolCount; i++) {
                            Map<String, Object> toolCall = toolCalls.get(i);
                            String toolName = (String) toolCall.get("tool");
                            Map<String, Object> toolParams =
                                    (Map<String, Object>) toolCall.get("parameters");

                            AgentTool tool = availableTools.get(toolName);
                            if (tool != null) {
                                toolFutures.add(
                                        tool.execute(toolParams, session)
                                                .thenApply(result -> {
                                                    Map<String, Object> resultMap = new HashMap<>();
                                                    resultMap.put("tool", toolName);
                                                    resultMap.put("result", result);
                                                    return resultMap;
                                                })
                                );
                            } else {
                                log.warn("未找到工具: {}", toolName);
                            }
                        }

                        // 等待所有工具完成
                        if (!toolFutures.isEmpty()) {
                            CompletableFuture.allOf(
                                    toolFutures.toArray(new CompletableFuture[0])
                            ).join();
                        }

                        // 收集工具结果
                        List<Map<String, Object>> observations = new ArrayList<>();
                        for (CompletableFuture<Map<String, Object>> future : toolFutures) {
                            observations.add(future.join());
                        }

                        currentStep.setObservation(observations);

                        // 处理工具结果（存入 collectedData 和 allDocuments）
                        processToolResults(observations, collectedData, allDocuments);

                        // 【优化点】动态评估：不再检查场景硬性条件，仅检查系统错误
                        boolean shouldContinue = evaluateShouldContinue(observations, iteration);

                        if (!shouldContinue) {
                            log.info("ReAct循环因工具执行错误提前结束，迭代次数: {}", iteration);
                            break;
                        }

                    } else {
                        log.warn("未知的动作类型: {}", actionType);
                        break;
                    }

                    // 检查是否达到最大迭代次数
                    if (iteration >= MAX_ITERATIONS) {
                        log.info("达到最大迭代次数: {}", MAX_ITERATIONS);
                        if (finalAnswer.length() == 0) {
                            finalAnswer.append("经过多轮分析，基于当前已收集的信息，结论如下：\n");
                            finalAnswer.append(generateSummaryFromSteps(steps, collectedData));
                        }
                        break;
                    }
                }

                // 3. 构建最终响应（集成风险检测结果）
                AgentResponse response = buildFinalResponse(
                        sessionId, enhancedInput, scenario, steps,
                        finalAnswer.toString(), allDocuments, collectedData,
                        memoryContext, riskAssessment, userId
                );

                // 4. 异步更新记忆（不影响主流程）
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
     * 思考并决定下一步行动
     */
    private String thinkAndDecide(
            String userInput,
            String scenario,
            List<ReActStep> steps,
            Map<String, Object> collectedData,
            SessionManager session) {

        // 构建思考提示
        String prompt = buildThinkingPrompt(
                userInput, scenario, steps, collectedData, session
        );

        // 调用LLM进行思考
        return llmService.generate(prompt);
    }

    /**
     * 【优化核心】构建思考提示
     * 明确告知 LLM 何时停止，替代原本的代码硬编码逻辑。
     */
    private String buildThinkingPrompt(
            String userInput,
            String scenario,
            List<ReActStep> steps,
            Map<String, Object> collectedData,
            SessionManager session) {

        StringBuilder prompt = new StringBuilder();

        // 系统指令
        prompt.append("你是一个租房风险分析智能体。使用 ReAct（思考-行动）模式处理问题。\n\n");
        prompt.append("请结合【用户画像】、【对话历史】和【已收集信息】，利用 ReAct 模式回答用户。\n\n");

        // --- Context Block 1: 短期状态 (Session Scope) ---
        prompt.append("### 1. 当前会话状态\n");
        prompt.append("- 用户问题: ").append(userInput).append("\n");
        prompt.append("- 识别场景: ").append(scenario).append("\n");
        prompt.append("- 当前轮次: ").append(steps.size()).append(" / ").append(MAX_ITERATIONS).append("\n\n");


        // --- Context Block 2: 知识库/工具数据 ---
        if (!collectedData.isEmpty()) {
            prompt.append("### 2. 已获取的外部信息\n");
            collectedData.forEach((k, v) ->
                    prompt.append("- ").append(k).append(": ").append(truncate(v.toString(), 300)).append("\n")
            );
            prompt.append("\n");
        }


        // --- Context Block 3: 思考历史 ---
        if (!steps.isEmpty()) {
            prompt.append("### 3. 思考与行动历史\n");
            for (ReActStep step : steps) {
                prompt.append("Step ").append(step.getStepNumber()).append(":\n");
                prompt.append("  Thought: ").append(step.getThought()).append("\n");
                if (step.getAction() != null) {
                    prompt.append("  Action: ").append(step.getAction()).append("\n");
                    prompt.append("  Observation: ").append(truncate(String.valueOf(step.getObservation()), 200)).append("\n");
                }
            }
            prompt.append("\n");
        }

        // --- Context Block 4: 可用工具 ---
        prompt.append("## 可用的工具\n");
        for (Map.Entry<String, AgentTool> entry : availableTools.entrySet()) {
            prompt.append("- ").append(entry.getKey()).append(": ")
                    .append(entry.getValue().getDescription()).append("\n");
        }
        prompt.append("\n");


        // 【优化核心】决策指令
        prompt.append("## 决策策略（重要）\n");
        prompt.append("请基于以上信息决定下一步。请遵循以下逻辑：\n");
        prompt.append("1. **如果信息不足**：选择 `tool_call` 调用相关工具获取信息（例如查询合同条款、搜索房源地段）。\n");
        prompt.append("2. **如果信息已足够**：或者用户问题很简单（如问候），或者已尝试查询但无果，请**立即**选择 `final_answer`。\n");
        prompt.append("   - 不要重复调用相同的工具。\n");
        prompt.append("   - 不要为了调用工具而调用工具。\n\n");

        prompt.append("## 输出格式\n");
        prompt.append("请返回严格的 JSON 格式：\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"action\": \"tool_call\" 或 \"final_answer\",\n");
        prompt.append("  \"reasoning\": \"简要说明你的判断依据\",\n");
        prompt.append("  \"tool_calls\": [ { \"tool\": \"工具名\", \"parameters\": {...} } ], // 仅当 action=tool_call 时\n");
        prompt.append("  \"answer\": \"最终回复内容\", // 仅当 action=final_answer 时\n");
        prompt.append("  \"confidence\": 0.95\n");
        prompt.append("}\n");
        prompt.append("```\n");

        return prompt.toString();
    }

    /**
     * 解析决策JSON (增强鲁棒性)
     */
    private Map<String, Object> parseDecision(String decisionJson) {
        try {
            String jsonStr = extractJsonFromResponse(decisionJson);
            return objectMapper.readValue(jsonStr, Map.class);
        } catch (Exception e) {
            log.error("解析决策JSON失败: {}", decisionJson, e);
            // 降级策略：如果解析失败，强制结束以避免死循环
            return Map.of(
                    "action", "final_answer",
                    "answer", "系统正在维护中，暂时无法分析具体细节。(JSON Parsing Error)",
                    "reasoning", "解析失败，自动降级",
                    "confidence", 0.0
            );
        }
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
     * 【优化核心】评估是否应该继续循环
     * 移除了原来的 switch-case 业务逻辑判断。
     * 现在只负责检测“系统级错误”。逻辑层面的“是否继续”完全交给 LLM 决定。
     */
    private boolean evaluateShouldContinue(List<Map<String, Object>> observations, int iteration) {
        // 检查是否有工具执行失败（Technical Failures）
        boolean hasError = observations.stream()
                .anyMatch(obs -> {
                    Object result = obs.get("result");
                    if (result instanceof Map) {
                        return ((Map<?, ?>) result).containsKey("error");
                    }
                    return false;
                });

        if (hasError) {
            log.warn("检测到工具执行内部错误，为防止错误扩散，停止循环");
            return false;
        }

        // 只要没有底层报错，就允许 LLM 继续思考。
        // LLM 会根据 prompt 中的 "如果信息足够...请选择 final_answer" 来决定是否停止。
        return true;
    }

    /**
     * 从步骤中生成摘要
     */
    private String generateSummaryFromSteps(List<ReActStep> steps, Map<String, Object> collectedData) {
        StringBuilder summary = new StringBuilder();
        summary.append("基于分析过程的总结：\n");
        for (ReActStep step : steps) {
            if (step.getStepNumber() > 0 && step.getThought() != null) {
                summary.append("- ").append(step.getThought()).append("\n");
            }
        }

        // 如果有风险检测结果，添加到摘要
        if (collectedData.containsKey("risk_assessment")) {
            RiskAssessment riskAssessment = (RiskAssessment) collectedData.get("risk_assessment");
            if (riskAssessment != null && !riskAssessment.getMatches().isEmpty()) {
                summary.append("\n风险检测发现：\n");
                riskAssessment.getMatches().forEach(match -> {
                    summary.append("- ").append(match.getExtractedEntity().getText())
                            .append(" (").append(match.getRiskLevel()).append(")\n");
                });
            }
        }

        return summary.toString();
    }

    /**
     * 构建最终响应（集成记忆和风险检测）
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

        // 1. 计算置信度
        double confidence = calculateConfidence(documents, collectedData, riskAssessment);

        // 2. 评估风险等级（考虑知识图谱风险）
        String riskLevel = evaluateRiskLevel(scenario, documents, collectedData, riskAssessment);

        // 3. 构建元数据（包含记忆和风险信息）
        Map<String, Object> metadata = buildMetadata(steps, collectedData, riskAssessment, memoryContext);

        // 4. 构建详细分析（集成风险警告）
        String detailedAnalysis = buildDetailedAnalysis(finalAnswer, riskAssessment);

        // 5. 创建响应
        return AgentResponse.builder()
                .responseId("resp_react_" + UUID.randomUUID().toString())
                .sessionId(sessionId)
                .scenario(scenario)
                .responseType(AgentResponse.ResponseType.ANALYSIS)
                .coreLogic("ReAct动态编排 + 记忆增强 + 风险检测")
                .detailedAnalysis(detailedAnalysis)
                .riskLevel(riskLevel)
                .confidence(Math.min(confidence, 0.95))
                .supportingDocuments(documents)
                .metadata(metadata)
                .generatedAt(LocalDateTime.now())
                .build();
    }


    /**
     * 评估风险等级（集成知识图谱风险）
     */
    private String evaluateRiskLevel(String scenario,
                                     List<AgentResponse.RetrievedDocument> documents,
                                     Map<String, Object> collectedData,
                                     RiskAssessment riskAssessment) {

        String baseRiskLevel = "MEDIUM";

        // 1. 检查知识图谱风险
        if (riskAssessment != null && !riskAssessment.getMatches().isEmpty()) {
            String highestRisk = riskAssessment.getHighestRiskLevel();
            if ("CRITICAL".equals(highestRisk) || "HIGH".equals(highestRisk)) {
                return highestRisk;
            }

            double riskScore = riskAssessment.getRiskScore();
            if (riskScore > 0.8) {
                return "HIGH";
            } else if (riskScore > 0.5) {
                return "MEDIUM";
            }
        }
        // 2. 根据场景调整
        switch (scenario) {
            case "霸王条款":
            case "距离欺诈":
                baseRiskLevel = "MEDIUM";
                break;
            case "租金欺诈":
                baseRiskLevel = "HIGH";
                break;
            case "合同审核":
                baseRiskLevel = "MEDIUM";
                break;
        }

        return baseRiskLevel;
    }


    /**
     * 创建错误响应
     */
    private AgentResponse createErrorResponse(String sessionId, String userInput, String error) {
        return AgentResponse.builder()
                .responseId("resp_err_" + UUID.randomUUID().toString())
                .sessionId(sessionId)
                .scenario("系统错误")
                .responseType(AgentResponse.ResponseType.ERROR)
                .coreLogic("系统处理请求时发生错误")
                .detailedAnalysis("错误信息：" + error + "\n建议稍后重试或联系技术支持")
                .riskLevel("未知")
                .confidence(0.0)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private String truncateString(String str, int maxLength) {
        if (str == null) return "";
        return str.length() <= maxLength ? str : str.substring(0, maxLength) + "...";
    }

    private String extractJsonFromResponse(String response) {
        if (response == null) return "{}";
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response.replaceAll("```json", "").replaceAll("```", "").trim();
    }

    /**
     * 构建增强的用户输入（集成记忆和风险检测）
     */
    private String buildEnhancedInput(String originalInput,
                                      String memoryContext,
                                      RiskAssessment riskAssessment) {

        StringBuilder enhanced = new StringBuilder();

        // 1. 系统角色
        enhanced.append("你是一个具备记忆能力和风险检测能力的租房风险分析智能体。\n\n");

        // 2. 添加记忆上下文
        if (StringUtils.isNotBlank(memoryContext)) {
            enhanced.append("【用户历史与偏好】\n")
                    .append(memoryContext)
                    .append("\n\n");
        } else {
            enhanced.append("【用户历史与偏好】\n（新用户，暂无历史记录）\n\n");
        }

        // 3. 添加风险警告（如果检测到风险实体）
        if (riskAssessment != null && !riskAssessment.getMatches().isEmpty()) {
            enhanced.append("【风险预警】\n");
            enhanced.append("系统检测到以下风险实体（请在你的分析中考虑这些风险）：\n");

            riskAssessment.getMatches().forEach(match -> {
                enhanced.append("- ").append(match.getExtractedEntity().getText())
                        .append(" (").append(match.getKgEntity().getEntityType()).append(")")
                        .append(" - 风险等级：").append(match.getRiskLevel())
                        .append("，相似度：").append(String.format("%.1f", match.getSimilarity()))
                        .append("\n");

                if (StringUtils.isNotBlank(match.getKgEntity().getDescription())) {
                    enhanced.append("  描述：").append(match.getKgEntity().getDescription()).append("\n");
                }
            });
            enhanced.append("\n");
        }

        // 4. 添加原始输入
        enhanced.append("【当前问题】\n")
                .append(originalInput);

        return enhanced.toString();
    }

    /**
     * 计算置信度（考虑记忆和风险因素）
     */
    private double calculateConfidence(List<AgentResponse.RetrievedDocument> documents,
                                       Map<String, Object> collectedData,
                                       RiskAssessment riskAssessment) {
        double baseConfidence = 0.7;

        if (documents != null && !documents.isEmpty()) {
            baseConfidence += 0.1;
        }

        if (collectedData.containsKey("web_search_results")) {
            baseConfidence += 0.1;
        }

        if (riskAssessment != null && !riskAssessment.getMatches().isEmpty()) {
            // 有风险检测结果会增加置信度
            baseConfidence += 0.05;
        }

        // 如果有记忆上下文，增加个性化置信度
        if (collectedData.containsKey("memory_context_used")) {
            baseConfidence += 0.05;
        }

        return Math.min(baseConfidence, 0.95);
    }

    /**
     * 构建元数据
     */
    private Map<String, Object> buildMetadata(List<ReActStep> steps,
                                              Map<String, Object> collectedData,
                                              RiskAssessment riskAssessment,
                                              String memoryContext) {

        Map<String, Object> metadata = new HashMap<>();

        // ReAct过程信息
        metadata.put("react_steps", steps.size());
        metadata.put("tool_calls", collectedData.keySet().stream()
                .filter(k -> k.startsWith("last_tool_"))
                .count());

        // 记忆信息
        metadata.put("memory_context_used", StringUtils.isNotBlank(memoryContext));
        if (StringUtils.isNotBlank(memoryContext)) {
            metadata.put("memory_context_length", memoryContext.length());
        }

        // 风险检测信息
        if (riskAssessment != null) {
            metadata.put("risk_assessment", Map.of(
                    "has_risks", !riskAssessment.getMatches().isEmpty(),
                    "highest_risk_level", riskAssessment.getHighestRiskLevel(),
                    "risk_score", riskAssessment.getRiskScore(),
                    "matched_entities_count", riskAssessment.getMatches().size(),
                    "risk_entities", riskAssessment.getMatches().stream()
                            .map(match -> Map.of(
                                    "entity", match.getExtractedEntity().getText(),
                                    "type", match.getKgEntity().getEntityType(),
                                    "risk_level", match.getRiskLevel()
                            ))
                            .collect(Collectors.toList())
            ));
        }

        // 处理时间
        if (!steps.isEmpty()) {
            long processingTime = steps.stream()
                    .mapToLong(step -> step.getTimestamp().toEpochSecond(java.time.ZoneOffset.UTC))
                    .max()
                    .orElse(0) -
                    steps.get(0).getTimestamp().toEpochSecond(java.time.ZoneOffset.UTC);
            metadata.put("processing_time_seconds", processingTime);
        }

        return metadata;
    }

    /**
     * 构建详细分析（集成风险警告）
     */
    private String buildDetailedAnalysis(String baseAnalysis, RiskAssessment riskAssessment) {
        if (riskAssessment == null || riskAssessment.getMatches().isEmpty()) {
            return baseAnalysis;
        }

        StringBuilder enhanced = new StringBuilder(baseAnalysis);
        enhanced.append("\n\n--- 风险检测报告 ---\n");
        enhanced.append("⚠️ 智能风险检测系统发现以下风险实体：\n\n");

        riskAssessment.getMatches().forEach(match -> {
            enhanced.append("### ").append(match.getExtractedEntity().getText()).append("\n");
            enhanced.append("- **类型**：").append(match.getKgEntity().getEntityType()).append("\n");
            enhanced.append("- **风险等级**：").append(match.getRiskLevel()).append("\n");

            if (StringUtils.isNotBlank(match.getKgEntity().getDescription())) {
                enhanced.append("- **风险描述**：").append(match.getKgEntity().getDescription()).append("\n");
            }

            if (match.getKgEntity().getReportCount() > 0) {
                enhanced.append("- **举报次数**：").append(match.getKgEntity().getReportCount()).append("次\n");
            }
            enhanced.append("- **置信度**：").append(String.format("%.1f", match.getSimilarity() * 100)).append("%\n");
            enhanced.append("\n");
        });

        enhanced.append("💡 **建议**：在租房过程中，请特别注意以上提到的风险实体，建议：\n");
        enhanced.append("1. 要求中介或房东提供更多验证信息\n");
        enhanced.append("2. 在签约前实地考察房源\n");
        enhanced.append("3. 仔细核对合同条款\n");
        enhanced.append("4. 如有疑问，建议咨询专业律师\n");

        return enhanced.toString();
    }
}
