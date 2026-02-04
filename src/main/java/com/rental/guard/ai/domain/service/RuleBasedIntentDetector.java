/**
 * @author qkcao
 * @date 2026/1/22 17:25
 */
package com.rental.guard.ai.domain.service;

import com.rental.guard.ai.domain.dto.IntentTypeEnum;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则匹配实现
 */
@Component
public class RuleBasedIntentDetector {

    // 意图关键词映射
    private static final Map<IntentTypeEnum, List<String>> KEYWORD_MAP = new HashMap<>();

    // 正则模式
    private static final Pattern USER_ID_PATTERN =
            Pattern.compile("(用户|用户ID|user)[:：]?\\s*(\\w{6,20})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(1[3-9]\\d{9})|(\\d{3,4}-?\\d{7,8})");
    private static final Pattern ROOM_ID_PATTERN =
            Pattern.compile("(聊天室|房间|room)[:：]?\\s*(\\w{8,20})", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_PATTERN =
            Pattern.compile("(最近|过去|近)?\\s*(\\d+)\\s*(天|小时|分钟|月)", Pattern.CASE_INSENSITIVE);

    static {
        // 初始化关键词
        KEYWORD_MAP.put(IntentTypeEnum.SINGLE_ANALYSIS,
                Arrays.asList("分析", "检测", "查看", "这条", "这个聊天"));

        KEYWORD_MAP.put(IntentTypeEnum.USER_INVESTIGATION,
                Arrays.asList("调查用户", "用户调查", "查用户", "用户风险", "用户历史"));

        KEYWORD_MAP.put(IntentTypeEnum.REAL_TIME_MONITORING,
                Arrays.asList("监控", "实时监控", "实时关注", "盯住", "持续观察"));

        KEYWORD_MAP.put(IntentTypeEnum.REPORT_GENERATION,
                Arrays.asList("报告", "生成报告", "导出报告", "日报", "周报"));

        KEYWORD_MAP.put(IntentTypeEnum.RULE_CONFIGURATION,
                Arrays.asList("配置", "设置规则", "修改规则", "规则配置", "调整参数"));

        KEYWORD_MAP.put(IntentTypeEnum.BATCH_PROCESSING,
                Arrays.asList("批量", "批量处理", "批量分析", "批量导入", "批量检测"));

        KEYWORD_MAP.put(IntentTypeEnum.DATA_QUERY,
                Arrays.asList("查询", "搜索", "查找", "检索", "查一下"));

        KEYWORD_MAP.put(IntentTypeEnum.ALERT_MANAGEMENT,
                Arrays.asList("告警", "报警", "预警", "通知", "提醒"));
    }

    /**
     * 基于规则的意图检测
     */
    public IntentRecognitionModule.AgentIntent detect(String input) {
        // 提取实体
        List<String> entities = extractEntities(input);
        Map<String, Object> parameters = extractParameters(input);

        // 识别意图
        IntentTypeEnum intentType = classifyIntent(input);

        if (intentType == IntentTypeEnum.UNKNOWN) {
            return null;
        }

        // 计算置信度
        double confidence = calculateConfidence(input, intentType);

        return IntentRecognitionModule.AgentIntent.builder()
                .intentType(intentType)
                .entities(entities)
                .parameters(parameters)
                .confidence(confidence)
                .originalInput(input)
                .priority(determinePriority(intentType))
                .requiresConfirmation(confidence < 0.8)
                .build();
    }

    /**
     * 提取实体
     */
    private List<String> extractEntities(String input) {
        List<String> entities = new ArrayList<>();

        // 提取用户ID
        Matcher userIdMatcher = USER_ID_PATTERN.matcher(input);
        while (userIdMatcher.find()) {
            entities.add(userIdMatcher.group(2));
        }

        // 提取手机号
        Matcher phoneMatcher = PHONE_PATTERN.matcher(input);
        while (phoneMatcher.find()) {
            entities.add(phoneMatcher.group());
        }

        // 提取聊天室ID
        Matcher roomMatcher = ROOM_ID_PATTERN.matcher(input);
        while (roomMatcher.find()) {
            entities.add(roomMatcher.group(2));
        }

        return entities;
    }

    /**
     * 提取参数
     */
    private Map<String, Object> extractParameters(String input) {
        Map<String, Object> params = new HashMap<>();

        // 提取时间范围
        Matcher timeMatcher = TIME_PATTERN.matcher(input);
        if (timeMatcher.find()) {
            String unit = timeMatcher.group(3);
            int value = Integer.parseInt(timeMatcher.group(2));
            params.put("time_range", String.format("最近%d%s", value, unit));
        }

        // 提取风险等级
        if (input.contains("高风险") || input.contains("严重风险")) {
            params.put("risk_level", "HIGH");
        } else if (input.contains("中风险") || input.contains("中等风险")) {
            params.put("risk_level", "MEDIUM");
        } else if (input.contains("低风险")) {
            params.put("risk_level", "LOW");
        }

        return params;
    }

    /**
     * 意图分类
     */
    private IntentTypeEnum classifyIntent(String input) {
        // 计算每个意图的匹配分数
        Map<IntentTypeEnum, Integer> scores = new HashMap<>();

        for (Map.Entry<IntentTypeEnum, List<String>> entry : KEYWORD_MAP.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (input.contains(keyword)) {
                    score++;
                }
            }
            scores.put(entry.getKey(), score);
        }

        // 找到最高分
        IntentTypeEnum bestIntent = IntentTypeEnum.UNKNOWN;
        int maxScore = 0;

        for (Map.Entry<IntentTypeEnum, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                bestIntent = entry.getKey();
            }
        }

        return bestIntent;
    }

    /**
     * 计算置信度
     */
    private double calculateConfidence(String input, IntentTypeEnum intentType) {
        double baseConfidence = 0.7;

        // 根据实体数量调整
        List<String> entities = extractEntities(input);
        if (!entities.isEmpty()) {
            baseConfidence += 0.1;
        }

        // 根据参数完整性调整
        Map<String, Object> params = extractParameters(input);
        if (!params.isEmpty()) {
            baseConfidence += 0.1;
        }

        // 根据关键词匹配数量调整
        List<String> keywords = KEYWORD_MAP.get(intentType);
        int keywordCount = 0;
        for (String keyword : keywords) {
            if (input.contains(keyword)) {
                keywordCount++;
            }
        }
        baseConfidence += keywordCount * 0.05;

        return Math.min(baseConfidence, 0.95);
    }

    /**
     * 确定优先级
     */
    private IntentRecognitionModule.Priority determinePriority(IntentTypeEnum intentType) {
        switch (intentType) {
            case REAL_TIME_MONITORING:
            case ALERT_MANAGEMENT:
                return IntentRecognitionModule.Priority.HIGH;
            case USER_INVESTIGATION:
            case SINGLE_ANALYSIS:
                return IntentRecognitionModule.Priority.MEDIUM;
            default:
                return IntentRecognitionModule.Priority.LOW;
        }
    }
}
