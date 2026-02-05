/**
 * @author qkcao
 * @date 2026/2/4 18:35
 */
package com.rental.guard.ai.domain.service.v1.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.guard.ai.domain.dto.v1.SessionManager;
import com.rental.guard.ai.domain.service.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 风险评估工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskAssessmentTool implements AgentTool {

    private final LLMService llmService;
    private static final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public String getName() {
        return "risk_assessment";
    }

    @Override
    public String getDescription() {
        return "评估租房风险等级，基于合同条款、市场数据、用户情况等进行综合风险评估。";
    }

    @Override
    public String getParameters() {
        return """
                {
                    "type": "object",
                    "properties": {
                        "assessment_items": {
                            "type": "array",
                            "description": "需要评估的项目列表",
                            "items": {
                                "type": "string"
                            }
                        },
                        "reference_data": {
                            "type": "object",
                            "description": "参考数据"
                        }
                    },
                    "required": ["assessment_items"]
                }
                """;
    }

    @Override
    public CompletableFuture<Object> execute(Map<String, Object> parameters, SessionManager session) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<String> items = (List<String>) parameters.get("assessment_items");
                Map<String, Object> referenceData =
                        (Map<String, Object>) parameters.getOrDefault("reference_data", Map.of());

                log.info("执行风险评估工具: items={}", items);

                // 构建风险评估提示
                String prompt = buildRiskAssessmentPrompt(items, referenceData, session);

                // 调用LLM进行评估
                String assessmentResult = llmService.generate(prompt);

                // 解析评估结果
                Map<String, Object> parsedResult = parseAssessmentResult(assessmentResult);

                Map<String, Object> resultMap = Map.of(
                        "tool", getName(),
                        "items", items,
                        "assessment_result", parsedResult,
                        "timestamp", System.currentTimeMillis()
                );

                return resultMap;
            } catch (Exception e) {
                log.error("执行风险评估工具失败", e);
                return Map.of("error", e.getMessage());
            }
        });
    }

    private String buildRiskAssessmentPrompt(
            List<String> items,
            Map<String, Object> referenceData,
            SessionManager session) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一名专业的租房风险评估专家。请评估以下项目的风险：\n\n");

        prompt.append("## 待评估项目\n");
        for (int i = 0; i < items.size(); i++) {
            prompt.append(i + 1).append(". ").append(items.get(i)).append("\n");
        }

        if (!referenceData.isEmpty()) {
            prompt.append("\n## 参考数据\n");
            referenceData.forEach((key, value) -> {
                prompt.append("- ").append(key).append(": ")
                        .append(value.toString()).append("\n");
            });
        }

        prompt.append("\n## 评估要求\n");
        prompt.append("1. 为每个项目评估风险等级（低/中/高/极高）\n");
        prompt.append("2. 提供风险评估依据\n");
        prompt.append("3. 给出风险缓解建议\n");
        prompt.append("4. 计算整体风险分数（0-100）\n\n");

        prompt.append("## 输出格式\n");
        prompt.append("请返回JSON格式：\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"assessments\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"item\": \"评估项目\",\n");
        prompt.append("      \"risk_level\": \"低/中/高/极高\",\n");
        prompt.append("      \"rationale\": \"评估依据\",\n");
        prompt.append("      \"suggestion\": \"缓解建议\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"overall_score\": 75,\n");
        prompt.append("  \"summary\": \"整体风险评估摘要\"\n");
        prompt.append("}\n");
        prompt.append("```");

        return prompt.toString();
    }

    private Map<String, Object> parseAssessmentResult(String result) {
        try {
            // 1. 提取JSON部分
            int start = result.indexOf('{');
            int end = result.lastIndexOf('}');

            if (start != -1 && end != -1) {
                String jsonStr = result.substring(start, end + 1);

                // 2. 将 JSON 字符串解析为 Map
                Map<String, Object> resultMap = objectMapper.readValue(jsonStr,
                        new TypeReference<Map<String, Object>>() {});

                // 3. (可选) 可以在这里注入原始结果以防后续需要查验
                // resultMap.putIfAbsent("raw_result", result);

                return resultMap;
            }

            // 降级处理：无法找到 JSON 结构
            return Map.of(
                    "assessments", List.of(),
                    "overall_score", 50,
                    "summary", "无法解析详细评估结果",
                    "raw_result", result
            );
        } catch (Exception e) {
            log.error("解析风险评估结果失败, 原文: {}", result, e);
            // 4. 解析失败后的兜底，保证调用方不空指针
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("error", "解析失败: " + e.getMessage());
            fallback.put("raw_result", result);
            fallback.put("overall_score", 0);
            return fallback;
        }
    }
}
