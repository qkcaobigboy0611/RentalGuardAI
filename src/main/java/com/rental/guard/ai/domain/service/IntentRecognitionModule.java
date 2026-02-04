/**
 * @author qkcao
 * @date 2026/1/22 17:22
 */
package com.rental.guard.ai.domain.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.rental.guard.ai.domain.dto.IntentTypeEnum;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * 意图理解模块 - 核心组件
 */
@Slf4j
@Component
public class IntentRecognitionModule {

    @Autowired
    private LLMService llmService;
    @Autowired
    private RuleBasedIntentDetector ruleDetector;

    private final ObjectMapper objectMapper;


    @Data
    @Builder
    public static class AgentIntent {
        // 意图类型
        private IntentTypeEnum intentType;

        // 提取的实体列表（如用户ID、手机号、聊天室ID等）
        private List<String> entities;

        // 关键参数
        private Map<String, Object> parameters;

        // 置信度
        private Double confidence;

        // 原始输入
        private String originalInput;

        // 时间范围
        private TimeRange timeRange;

        // 优先级
        private Priority priority;

        // 是否需要人工确认
        private Boolean requiresConfirmation;
    }

    @Data
    @Builder
    public static class TimeRange {
        private Date startTime;
        private Date endTime;
        private String timeExpression; // 如"最近7天"、"今天"等
    }

    public enum Priority {
        HIGH,    // 高优先级（如实时监控）
        MEDIUM,  // 中优先级（如批量处理）
        LOW      // 低优先级（如报告生成）
    }

    public IntentRecognitionModule(LLMService llmService) {
        this.objectMapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.llmService = llmService;
        this.ruleDetector = new RuleBasedIntentDetector();
    }

    /**
     * 主入口：识别用户意图
     */
    public AgentIntent recognizeIntent(String userInput, String sessionId) {
        log.info("开始意图识别 - 会话: {}, 输入: {}", sessionId, userInput);

        try {
            // 1. 预处理输入
            String processedInput = preprocessInput(userInput);

            // 2. 先尝试规则匹配（快速、准确）
            AgentIntent ruleBasedIntent = ruleDetector.detect(processedInput);
            if (ruleBasedIntent != null && ruleBasedIntent.getConfidence() > 0.9) {
                log.info("规则匹配成功，意图: {}", ruleBasedIntent.getIntentType());
                //return ruleBasedIntent;
            }

            // 3. LLM识别（兜底，处理复杂情况）
            AgentIntent llmIntent = recognizeWithLLM(processedInput);

            // 4. 融合结果（如果规则和LLM都识别成功）
            if (ruleBasedIntent != null && llmIntent != null) {
                return fuseIntents(ruleBasedIntent, llmIntent);
            }

            // 5. 返回识别结果
            return llmIntent != null ? llmIntent : createUnknownIntent(userInput);

        } catch (Exception e) {
            log.error("意图识别异常", e);
            return createErrorIntent(userInput, e.getMessage());
        }
    }

    /**
     * 使用LLM识别意图
     */
    private AgentIntent recognizeWithLLM(String input) {
        try {
            // 构建提示词
            String prompt = buildIntentRecognitionPrompt(input);

            // 调用LLM服务
            String aiResponse = llmService.generate(prompt);

            // 解析响应
            LLMResponse response = parseLLMResponse(aiResponse);

            // 转换为AgentIntent
            return convertToAgentIntent(input, response);

        } catch (Exception e) {
            log.warn("LLM意图识别失败，使用规则匹配", e);
            return null;
        }
    }

    /**
     * 构建意图识别提示词
     */
    private String buildIntentRecognitionPrompt(String userInput) {
        return String.format("""
                你是一个防欺诈租房系统的意图理解助手。请分析用户请求，识别意图并提取信息。
                            
                用户输入："%s"
                            
                请分析以下内容：
                1. 主要意图是什么？
                2. 涉及哪些实体（用户ID、手机号、聊天室ID等）？
                3. 需要哪些参数（时间范围、风险等级等）？
                4. 任务优先级如何？
                            
                可选意图类型：
                %s
                            
                返回严格的JSON格式，必须包含以下字段：
                {
                    "intent": "意图类型（使用上述枚举值）",
                    "entities": ["实体1", "实体2"],
                    "parameters": {
                        "time_range": "时间范围描述",
                        "risk_level": "风险等级",
                        "report_type": "报告类型"
                    },
                    "confidence": 0.95,
                    "priority": "HIGH|MEDIUM|LOW",
                    "requires_confirmation": true/false
                }
                            
                如果意图不明确，confidence设为低于0.7。
                """, userInput, getIntentDescriptions());
    }

    /**
     * 获取意图描述
     */
    private String getIntentDescriptions() {
        StringBuilder sb = new StringBuilder();
        for (IntentTypeEnum intent : IntentTypeEnum.values()) {
            sb.append(String.format("- %s: %s\n", intent.name(), intent.getDescription()));
        }
        return sb.toString();
    }

    /**
     * 解析LLM响应
     */
    private LLMResponse parseLLMResponse(String response) throws JsonProcessingException {
        // 尝试从响应中提取JSON部分
        String jsonStr = extractJsonFromResponse(response);
        return objectMapper.readValue(jsonStr, LLMResponse.class);
    }

    /**
     * 从LLM响应中提取JSON
     */
    private String extractJsonFromResponse(String response) {
        // 简单的JSON提取逻辑
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}") + 1;
        if (start >= 0 && end > start) {
            return response.substring(start, end);
        }
        return response;
    }

    /**
     * 转换为AgentIntent
     */
    private AgentIntent convertToAgentIntent(String originalInput, LLMResponse response) {
        TimeRange timeRange = extractTimeRange(response.getParameters());

        return AgentIntent.builder()
                .intentType(IntentTypeEnum.valueOf(response.getIntent().toUpperCase()))
                .entities(response.getEntities())
                .parameters(response.getParameters())
                .confidence(response.getConfidence())
                .originalInput(originalInput)
                .timeRange(timeRange)
                .priority(Priority.valueOf(response.getPriority().toUpperCase()))
                .requiresConfirmation(response.isRequiresConfirmation())
                .build();
    }

    /**
     * 提取时间范围
     */
    private TimeRange extractTimeRange(Map<String, Object> parameters) {
        if (parameters == null || !parameters.containsKey("time_range")) {
            return null;
        }

        String timeExpr = parameters.get("time_range").toString();
        return TimeRange.builder()
                .timeExpression(timeExpr)
                .build();
    }

    /**
     * 预处理输入
     */
    private String preprocessInput(String input) {
        // 去除多余空格
        input = input.trim();

        // 统一小写（可选，根据实际情况）
        // input = input.toLowerCase();

        // 移除特殊字符
        input = input.replaceAll("[\\r\\n]+", " ");

        return input;
    }

    /**
     * 创建未知意图
     */
    private AgentIntent createUnknownIntent(String originalInput) {
        return AgentIntent.builder()
                .intentType(IntentTypeEnum.UNKNOWN)
                .entities(new ArrayList<>())
                .parameters(new HashMap<>())
                .confidence(0.3)
                .originalInput(originalInput)
                .priority(Priority.LOW)
                .requiresConfirmation(true)
                .build();
    }

    /**
     * 创建错误意图
     */
    private AgentIntent createErrorIntent(String originalInput, String errorMsg) {
        AgentIntent intent = createUnknownIntent(originalInput);
        intent.getParameters().put("error", errorMsg);
        return intent;
    }

    /**
     * 融合多个识别结果
     */
    private AgentIntent fuseIntents(AgentIntent intent1, AgentIntent intent2) {
        // 选择置信度高的
        if (intent1.getConfidence() >= intent2.getConfidence()) {
            return intent1;
        }
        return intent2;
    }

    /**
     * LLM响应数据结构
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class LLMResponse {
        private String intent;
        private List<String> entities;
        private Map<String, Object> parameters;
        private Double confidence;
        private String priority;

        @JsonProperty("requires_confirmation")
        private boolean requiresConfirmation;
    }
}
