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
                        finalAnswer.append((String) decisionMap.get("answer"));
                        currentStep = new ReActStep(iteration,
                                "决定给出最终答案: " + decisionMap.get("reasoning"));
                        currentStep.setAction("final_answer");
                        steps.add(currentStep);
                        break;
                    } else if ("tool_call".equals(actionType)) {
                        // 执行工具调用
                        List<Map<String, Object>> toolCalls =
                                (List<Map<String, Object>>) decisionMap.get("tool_calls");

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
                            }
                        }

                        // 等待所有工具完成
                        CompletableFuture.allOf(
                                toolFutures.toArray(new CompletableFuture[0])
                        ).join();

                        // 收集工具结果
                        List<Map<String, Object>> observations = new ArrayList<>();
                        for (CompletableFuture<Map<String, Object>> future : toolFutures) {
                            observations.add(future.join());
                        }

                        currentStep.setObservation(observations);

                        // 处理工具结果
                        processToolResults(observations, collectedData, allDocuments);

                        // 检查是否应该继续
                        boolean shouldContinue = evaluateShouldContinue(
                                observations, iteration, scenario, session
                        );

                        if (!shouldContinue) {
                            log.info("ReAct循环提前结束，迭代次数: {}", iteration);
                            break;
                        }

                    } else {
                        log.warn("未知的动作类型: {}", actionType);
                        break;
                    }

                    // 检查是否达到最大迭代次数
                    if (iteration >= MAX_ITERATIONS) {
                        log.info("达到最大迭代次数: {}", MAX_ITERATIONS);
                        finalAnswer.append("经过多轮分析，结论如下：\n");
                        finalAnswer.append(generateSummaryFromSteps(steps, collectedData));
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
     * 构建思考提示
     */
    private String buildThinkingPrompt(
            String userInput,
            String scenario,
            List<ReActStep> steps,
            Map<String, Object> collectedData,
            SessionManager session) {

        StringBuilder prompt = new StringBuilder();

        // 系统指令
        prompt.append("你是一个租房风险分析智能体。使用ReAct（思考-行动）方法处理问题。\n\n");

        // 当前状态
        prompt.append("## 当前状态\n");
        prompt.append("- 用户问题: ").append(userInput).append("\n");
        prompt.append("- 场景: ").append(scenario).append("\n");
        prompt.append("- 当前步骤: ").append(steps.size()).append("\n\n");

        // 已收集的信息
        if (!collectedData.isEmpty()) {
            prompt.append("## 已收集的信息\n");
            for (Map.Entry<String, Object> entry : collectedData.entrySet()) {
                prompt.append("- ").append(entry.getKey()).append(": ")
                        .append(truncateString(entry.getValue().toString(), 200))
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
                                step.getObservation().toString(), 150)).append("\n");
                    }
                }
                prompt.append("\n");
            }
        }

        // 决策指令
        prompt.append("## 决策\n");
        prompt.append("基于以上信息，决定下一步行动。选择以下之一：\n");
        prompt.append("1. 调用工具（如果需要更多信息）\n");
        prompt.append("2. 给出最终答案（如果信息足够）\n\n");

        prompt.append("## 输出格式\n");
        prompt.append("返回JSON格式，包含以下字段：\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"action\": \"tool_call\" 或 \"final_answer\",\n");
        prompt.append("  \"reasoning\": \"你的推理过程\",\n");

        prompt.append("  // 如果action是tool_call\n");
        prompt.append("  \"tool_calls\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"tool\": \"工具名称\",\n");
        prompt.append("      \"parameters\": {\"key\": \"value\"}\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");

        prompt.append("  // 如果action是final_answer\n");
        prompt.append("  \"answer\": \"你的回答\",\n");
        prompt.append("  \"confidence\": 0.95\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        prompt.append("## 你的决策\n");
        prompt.append("现在，输出你的决策JSON：\n");

        return prompt.toString();
    }

    /**
     * 解析决策JSON
     */
    private Map<String, Object> parseDecision(String decisionJson) {
        try {
            // 尝试从响应中提取JSON
            String jsonStr = extractJsonFromResponse(decisionJson);
            return objectMapper.readValue(jsonStr, Map.class);
        } catch (Exception e) {
            log.error("解析决策JSON失败: {}", decisionJson, e);
            // 返回默认决策
            return Map.of(
                    "action", "final_answer",
                    "answer", "系统处理过程中发生错误，请稍后重试。",
                    "reasoning", "解析决策时出错",
                    "confidence", 0.5
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
     * 评估是否应该继续循环
     */
    private boolean evaluateShouldContinue(
            List<Map<String, Object>> observations,
            int iteration,
            String scenario,
            SessionManager session) {

        // 检查是否有工具执行失败
        boolean hasError = observations.stream()
                .anyMatch(obs -> {
                    Object result = obs.get("result");
                    if (result instanceof Map) {
                        return ((Map<?, ?>) result).containsKey("error");
                    }
                    return false;
                });

        if (hasError) {
            log.warn("检测到工具执行错误，停止循环");
            return false;
        }

        // 检查是否收集到足够信息
        boolean hasRagResults = observations.stream()
                .anyMatch(obs -> "rag_retrieval".equals(obs.get("tool")));

        boolean hasSearchResults = observations.stream()
                .anyMatch(obs -> "web_search".equals(obs.get("tool")));

        // 根据场景判断
        switch (scenario) {
            case "合同审核":
                return iteration < 3 || !hasRagResults;
            case "距离欺诈":
                return iteration < 2 || !hasSearchResults;
            case "租金欺诈":
                return iteration < 3 || (!hasRagResults && !hasSearchResults);
            default:
                return iteration < 3;
        }
    }

    /**
     * 从步骤中生成摘要
     */
    private String generateSummaryFromSteps(
            List<ReActStep> steps,
            Map<String, Object> collectedData) {

        StringBuilder summary = new StringBuilder();
        summary.append("分析过程摘要：\n");

        for (ReActStep step : steps) {
            if (step.getStepNumber() > 0) {
                summary.append(step.getStepNumber()).append(". ")
                        .append(step.getThought()).append("\n");
            }
        }

        if (collectedData.containsKey("rag_documents")) {
            summary.append("\n参考文档：").append(collectedData.get("rag_documents")).append("\n");
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
                .coreLogic("基于ReAct多轮分析得出结论")
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

        // 基于收集的数据评估风险
        // 这里可以实现更复杂的风险评估逻辑
        return "MEDIUM"; // 默认中等风险
    }

    /**
     * 计算置信度
     */
    private double calculateConfidence(
            List<ReActStep> steps,
            List<AgentResponse.RetrievedDocument> documents,
            Map<String, Object> collectedData) {

        // 基于步骤数量、文档数量、数据完整性计算置信度
        double baseConfidence = 0.7;

        if (documents != null && !documents.isEmpty()) {
            baseConfidence += 0.15;
        }

        if (collectedData.containsKey("web_search_results")) {
            baseConfidence += 0.1;
        }

        if (steps.size() >= 2) {
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
                .coreLogic("ReAct循环执行失败")
                .detailedAnalysis("错误信息：" + error)
                .riskLevel("未知")
                .confidence(0.0)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 辅助方法：截断字符串
     */
    private String truncateString(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * 从响应中提取JSON
     */
    private String extractJsonFromResponse(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');

        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }

        return response.replaceAll("^```json\\s*", "")
                .replaceAll("\\s*```$", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "")
                .trim();
    }
}
