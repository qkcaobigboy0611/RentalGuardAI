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

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ReAct循环引擎 - 实现思考-行动循环
 * <p>
 * 优化点：
 * 1. 动态终止策略：移除硬编码的场景判断，完全由 LLM 根据信息充足度决定是否结束。
 * 2. 增强提示词：明确告知 LLM 何时停止。
 * 3. 鲁棒性提升：增强 JSON 解析能力。
 */
@Slf4j
public class ReActEngine {

    private final LLMService llmService;
    private final ObjectMapper objectMapper;
    private final Map<String, AgentTool> availableTools;

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
                       Map<String, AgentTool> availableTools) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.availableTools = availableTools;
    }

    /**
     * 执行ReAct循环
     */
    public CompletableFuture<AgentResponse> executeReActLoop(
            String sessionId,
            String userInput,
            SessionManager session,
            String scenario) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                List<ReActStep> steps = new ArrayList<>();
                StringBuilder finalAnswer = new StringBuilder();
                List<AgentResponse.RetrievedDocument> allDocuments = new ArrayList<>();
                Map<String, Object> collectedData = new ConcurrentHashMap<>();

                // 初始思考
                String initialThought = "开始分析用户问题：" + userInput;
                ReActStep currentStep = new ReActStep(0, initialThought);
                steps.add(currentStep);

                // ReAct循环
                for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
                    log.info("ReAct循环第 {} 次迭代，会话: {}", iteration, sessionId);

                    // 1. 思考阶段 - LLM决定下一步行动
                    String decision = thinkAndDecide(
                            userInput,
                            scenario,
                            steps,
                            collectedData,
                            session
                    );

                    Map<String, Object> decisionMap = parseDecision(decision);
                    String actionType = (String) decisionMap.get("action");

                    if ("final_answer".equals(actionType)) {
                        // 生成最终答案
                        // 优化：优先使用 decision 中的 answer，如果为空则使用 reasoning
                        String answer = (String) decisionMap.get("answer");
                        if (answer == null || answer.trim().isEmpty()) {
                            answer = (String) decisionMap.get("reasoning");
                        }
                        finalAnswer.append(answer);

                        currentStep = new ReActStep(iteration,
                                "决定给出最终答案: " + decisionMap.get("reasoning"));
                        currentStep.setAction("final_answer");
                        steps.add(currentStep);
                        break;
                    } else if ("tool_call".equals(actionType)) {
                        // 执行工具调用
                        List<Map<String, Object>> toolCalls =
                                (List<Map<String, Object>>) decisionMap.get("tool_calls");

                        if (toolCalls == null || toolCalls.isEmpty()) {
                            log.warn("决策为 tool_call 但未提供工具列表，跳过");
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

                        // 处理工具结果
                        processToolResults(observations, collectedData, allDocuments);

                        // 评估是否应该继续
                        // 优化：逻辑简化，只关注是否有错误。是否继续由下一轮 LLM 决定。
                        boolean shouldContinue = evaluateShouldContinue(
                                observations, iteration
                        );

                        if (!shouldContinue) {
                            log.info("ReAct循环因错误提前结束，迭代次数: {}", iteration);
                            break;
                        }

                    } else {
                        log.warn("未知的动作类型: {}", actionType);
                        // 遇到未知动作，保守起见可以再试一次或者直接结束
                        break;
                    }

                    // 检查是否达到最大迭代次数
                    if (iteration >= MAX_ITERATIONS) {
                        log.info("达到最大迭代次数: {}", MAX_ITERATIONS);
                        // 如果最后一次还是工具调用，尝试用已有信息生成总结
                        if (finalAnswer.length() == 0) {
                            finalAnswer.append("经过多轮分析，基于当前已收集的信息，我的结论如下：\n");
                            finalAnswer.append(generateSummaryFromSteps(steps, collectedData));
                        }
                        break;
                    }
                }

                // 构建最终响应
                return buildFinalResponse(
                        sessionId, userInput, scenario, steps,
                        finalAnswer.toString(), allDocuments, collectedData
                );

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
     * 构建思考提示 - 优化版
     */
    private String buildThinkingPrompt(
            String userInput,
            String scenario,
            List<ReActStep> steps,
            Map<String, Object> collectedData,
            SessionManager session) {

        StringBuilder prompt = new StringBuilder();

        // 系统指令
        prompt.append("你是一个租房风险分析智能体。请使用 ReAct（思考-行动）模式来解决用户的问题。\n");
        prompt.append("你的目标是尽可能准确、全面地回答用户，同时避免不必要的步骤。\n\n");

        // 当前状态
        prompt.append("## 当前状态\n");
        prompt.append("- 用户问题: <user_query>").append(userInput).append("</user_query>\n");
        prompt.append("- 场景: ").append(scenario).append("\n");
        prompt.append("- 当前步骤: ").append(steps.size()).append(" / ").append(MAX_ITERATIONS).append("\n\n");

        // 已收集的信息（带截断，防止 Context 过长）
        if (!collectedData.isEmpty()) {
            prompt.append("## 已收集的信息\n");
            for (Map.Entry<String, Object> entry : collectedData.entrySet()) {
                String valueStr = entry.getValue().toString();
                prompt.append("- ").append(entry.getKey()).append(": ")
                        .append(truncateString(valueStr, 300))
                        .append("\n");
            }
            prompt.append("\n");
        }

        // 可用的工具
        prompt.append("## 可用的工具\n");
        for (Map.Entry<String, AgentTool> entry : availableTools.entrySet()) {
            prompt.append("- ").append(entry.getKey()).append(": ")
                    .append(entry.getValue().getDescription()).append("\n");
        }
        prompt.append("\n");

        // 历史步骤
        if (!steps.isEmpty()) {
            prompt.append("## 历史步骤\n");
            for (ReActStep step : steps) {
                prompt.append("步骤 ").append(step.getStepNumber()).append(":\n");
                prompt.append("- 思考: ").append(step.getThought()).append("\n");
                if (step.getAction() != null) {
                    prompt.append("- 行动: ").append(step.getAction()).append("\n");
                    if (step.getObservation() != null) {
                        prompt.append("- 观察: ").append(truncateString(
                                step.getObservation().toString(), 200)).append("\n");
                    }
                }
                prompt.append("\n");
            }
        }

        // 决策指令 - 重点优化部分
        prompt.append("## 决策策略\n");
        prompt.append("请基于以上信息，仔细分析并决定下一步行动：\n");
        prompt.append("1. **tool_call**: 如果你需要更多外部信息（如法律条款、市场价格、地理位置）来回答问题，请调用工具。\n");
        prompt.append("2. **final_answer**: 如果你**已经收集了足够的信息**，或者**已经尝试查询但无法获取更多信息**，请务必选择此项直接回答。\n");
        prompt.append("   - **注意**: 不要重复调用已经失败或返回空结果的工具。\n\n");

        prompt.append("## 输出格式\n");
        prompt.append("请仅返回一个合法的 JSON 对象，不要包含 Markdown 标记（如 ```json）。格式如下：\n");
        prompt.append("{\n");
        prompt.append("  \"action\": \"tool_call\" 或 \"final_answer\",\n");
        prompt.append("  \"reasoning\": \"简要说明你的思考过程...\",\n");

        prompt.append("  // 仅当 action 为 tool_call 时需要\n");
        prompt.append("  \"tool_calls\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"tool\": \"工具名称\",\n");
        prompt.append("      \"parameters\": {\"key\": \"value\"}\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");

        prompt.append("  // 仅当 action 为 final_answer 时需要\n");
        prompt.append("  \"answer\": \"对用户问题的完整回答\",\n");
        prompt.append("  \"confidence\": 0.95\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    /**
     * 解析决策JSON - 增强鲁棒性
     */
    private Map<String, Object> parseDecision(String decisionJson) {
        try {
            // 1. 提取 JSON 字符串（去除可能存在的 Markdown 标记或解释性文字）
            String jsonStr = extractJsonFromResponse(decisionJson);
            // 2. 解析
            return objectMapper.readValue(jsonStr, Map.class);
        } catch (Exception e) {
            log.warn("解析决策JSON失败，原始输入: {}", decisionJson, e);
            // 降级策略：如果解析失败，尝试直接结束，避免死循环或崩溃
            return Map.of(
                    "action", "final_answer",
                    "answer", "系统处理您的请求时遇到格式解析问题，请稍后重试。（原始回复: " + truncateString(decisionJson, 50) + "）",
                    "reasoning", "JSON解析异常，强制终止",
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

                // 根据工具类型处理结果
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
                }
            }

            // 记录工具调用
            collectedData.put("last_tool_" + toolName, System.currentTimeMillis());
        }
    }

    /**
     * 评估是否应该继续循环 - 优化版
     * * 策略：
     * 不再根据场景硬性规定循环次数或必须调用的工具。
     * 只要工具执行没有严重错误，就返回 true，
     * 将“是否继续”的决策权完全交给 LLM 在下一轮的 thinkAndDecide 中处理。
     */
    private boolean evaluateShouldContinue(
            List<Map<String, Object>> observations,
            int iteration) {

        // 检查是否有工具执行失败 (系统级错误)
        boolean hasError = observations.stream()
                .anyMatch(obs -> {
                    Object result = obs.get("result");
                    if (result instanceof Map) {
                        return ((Map<?, ?>) result).containsKey("error");
                    }
                    return false;
                });

        if (hasError) {
            log.warn("检测到工具执行发生系统错误，为防止错误扩散，提前停止 ReAct 循环");
            return false;
        }

        // 只要没有底层错误，就继续循环，让 LLM 看通过 observation 决定下一步
        // (最大循环次数由外层 MAX_ITERATIONS 控制)
        return true;
    }

    /**
     * 从步骤中生成摘要
     */
    private String generateSummaryFromSteps(
            List<ReActStep> steps,
            Map<String, Object> collectedData) {

        StringBuilder summary = new StringBuilder();

        for (ReActStep step : steps) {
            if (step.getStepNumber() > 0) {
                if (step.getThought() != null) {
                    // 简单总结每一步
                    summary.append("- ").append(step.getThought()).append("\n");
                }
            }
        }

        if (collectedData.containsKey("rag_documents")) {
            summary.append("\n(参考了内部知识库文档)");
        }
        if (collectedData.containsKey("web_search_results")) {
            summary.append("\n(参考了网络搜索结果)");
        }

        return summary.toString();
    }

    /**
     * 构建最终响应
     */
    private AgentResponse buildFinalResponse(
            String sessionId,
            String userInput,
            String scenario,
            List<ReActStep> steps,
            String finalAnswer,
            List<AgentResponse.RetrievedDocument> documents,
            Map<String, Object> collectedData) {

        // 评估风险等级
        String riskLevel = evaluateRiskLevel(scenario, documents, collectedData);

        // 计算置信度
        double confidence = calculateConfidence(steps, documents, collectedData);

        return AgentResponse.builder()
                .responseId("resp_react_" + UUID.randomUUID().toString())
                .sessionId(sessionId)
                .scenario(scenario)
                .responseType(AgentResponse.ResponseType.ANALYSIS)
                .coreLogic("基于ReAct动态编排分析")
                .detailedAnalysis(finalAnswer)
                .riskLevel(riskLevel)
                .confidence(confidence)
                .supportingDocuments(documents)
                .metadata(Map.of(
                        "react_steps", steps.size(),
                        "tool_calls", collectedData.keySet().stream()
                                .filter(k -> k.startsWith("last_tool_"))
                                .count(),
                        "processing_time_ms", System.currentTimeMillis() -
                                steps.get(0).getTimestamp().toInstant(java.time.ZoneOffset.UTC)
                                        .toEpochMilli()
                ))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 评估风险等级
     */
    private String evaluateRiskLevel(
            String scenario,
            List<AgentResponse.RetrievedDocument> documents,
            Map<String, Object> collectedData) {
        // 此处可结合 LLM 对 finalAnswer 进行情感分析来动态判定，暂时保持默认逻辑
        return "MEDIUM";
    }

    /**
     * 计算置信度
     */
    private double calculateConfidence(
            List<ReActStep> steps,
            List<AgentResponse.RetrievedDocument> documents,
            Map<String, Object> collectedData) {

        double baseConfidence = 0.7;

        if (documents != null && !documents.isEmpty()) {
            baseConfidence += 0.15;
        }
        if (collectedData.containsKey("web_search_results")) {
            baseConfidence += 0.1;
        }
        // 步数越多不一定越好，适当的步数表示思考过程完整
        if (steps.size() >= 2 && steps.size() <= 4) {
            baseConfidence += 0.05;
        }

        return Math.min(baseConfidence, 0.95);
    }

    /**
     * 创建错误响应
     */
    private AgentResponse createErrorResponse(String sessionId, String userInput, String error) {
        return AgentResponse.builder()
                .responseId("resp_error_" + UUID.randomUUID().toString())
                .sessionId(sessionId)
                .scenario("系统错误")
                .responseType(AgentResponse.ResponseType.ERROR)
                .coreLogic("ReAct循环执行异常")
                .detailedAnalysis("抱歉，智能体分析过程中遇到技术问题：" + error)
                .riskLevel("UNKNOWN")
                .confidence(0.0)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 辅助方法：截断字符串
     */
    private String truncateString(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...(truncated)";
    }

    /**
     * 从响应中提取JSON
     */
    private String extractJsonFromResponse(String response) {
        if (response == null) return "{}";

        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');

        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }

        // 清理常见的 Markdown 标记
        return response.replaceAll("^```json\\s*", "")
                .replaceAll("\\s*```$", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "")
                .trim();
    }
}
