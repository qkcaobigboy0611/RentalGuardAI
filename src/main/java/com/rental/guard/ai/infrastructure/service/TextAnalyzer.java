/**
 * @author qkcao
 * @date 2025/12/31 16:21
 */
package com.rental.guard.ai.infrastructure.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
@Slf4j
public class TextAnalyzer {

    // 外部平台关键词
    private static final Set<String> EXTERNAL_PLATFORM_KEYWORDS = new HashSet<>(Arrays.asList(
            "微信", "QQ", "钉钉", "百度网盘", "telegram", "whatsapp",
            "skype", "line", "kakao", "加我", "加好友", "私聊"
    ));

    // 金钱相关关键词
    private static final Set<String> MONEY_RELATED_KEYWORDS = new HashSet<>(Arrays.asList(
            "转账", "汇款", "付款", "定金", "押金", "保证金", "钱",
            "费用", "租金", "价格", "收费", "付费", "元", "块"
    ));

    // 紧急词汇
    private static final Set<String> URGENT_KEYWORDS = new HashSet<>(Arrays.asList(
            "尽快", "马上", "立即", "紧急", "快点", "急", "速",
            "赶快", "火速", "迅速", "立刻", "即刻", "赶紧"
    ));

    // 长租相关词汇
    private static final Set<String> LONG_TERM_KEYWORDS = new HashSet<>(Arrays.asList(
            "长租", "长期", "一年", "两年", "三年", "长期租",
            "老板", "公司", "办公", "员工", "宿舍"
    ));

    // 电话/联系方式模式
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8}"
    );

    // 网址模式
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?|ftp|file)://[-A-Za-z0-9+&@#/%?=~_|!:,.;]+[-A-Za-z0-9+&@#/%=~_|]"
    );

    /**
     * 分析文本特征
     */
    public Features analyze(String text) {
        Features features = new Features();

        if (StringUtils.isBlank(text)) {
            return features;
        }

        String lowerText = text.toLowerCase();

        // 检查外部平台
        features.setContainsExternalPlatform(
                containsAnyKeyword(lowerText, EXTERNAL_PLATFORM_KEYWORDS)
        );

        // 检查金钱相关
        features.setContainsMoneyRelated(
                containsAnyKeyword(lowerText, MONEY_RELATED_KEYWORDS)
        );

        // 检查紧急词汇
        features.setContainsUrgentWords(
                containsAnyKeyword(lowerText, URGENT_KEYWORDS)
        );

        // 检查长租相关
        features.setContainsLongTermRelated(
                containsAnyKeyword(lowerText, LONG_TERM_KEYWORDS)
        );

        // 检查联系方式
        features.setContainsContactInfo(
                PHONE_PATTERN.matcher(text).find() ||
                        URL_PATTERN.matcher(lowerText).find()
        );

        // 计算文本长度特征
        features.setTextLength(text.length());
        features.setWordCount(countWords(text));

        // 计算重复消息比例
        features.setRepeatMessageRatio(calculateRepeatRatio(text));

        return features;
    }

    /**
     * 分析对话模式
     */
    public ConversationPattern analyzeConversationPattern(String conversation) {
        ConversationPattern pattern = new ConversationPattern();

        if (StringUtils.isBlank(conversation)) {
            return pattern;
        }

        // 分割对话行
        String[] lines = conversation.split("\n");
        List<String> userMessages = new ArrayList<>();
        List<String> systemMessages = new ArrayList<>();

        for (String line : lines) {
            if (line.contains("系统") || line.contains("客服") || line.contains("平台")) {
                systemMessages.add(line);
            } else if (line.contains("用户") || line.matches(".*\\d+.*:")) {
                userMessages.add(line);
            }
        }

        pattern.setUserMessageCount(userMessages.size());
        pattern.setSystemMessageCount(systemMessages.size());
        pattern.setTotalMessageCount(lines.length);

        // 分析消息间隔时间（如果有时间戳）
        if (containsTimestamps(conversation)) {
            pattern.setAverageResponseTime(calculateAverageResponseTime(conversation));
        }

        // 分析消息长度分布
        pattern.setAverageMessageLength(calculateAverageLength(lines));
        pattern.setMaxMessageLength(calculateMaxLength(lines));

        return pattern;
    }

    /**
     * 检查是否包含任何关键词
     */
    private boolean containsAnyKeyword(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算词数
     */
    private int countWords(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }

        // 简单分词：按空格和标点分割
        String[] words = text.split("[\\s\\p{Punct}]+");
        return (int) Arrays.stream(words)
                .filter(word -> !word.trim().isEmpty())
                .count();
    }

    /**
     * 计算重复消息比例
     */
    private double calculateRepeatRatio(String text) {
        String[] lines = text.split("\n");
        if (lines.length <= 1) {
            return 0.0;
        }

        int repeatCount = 0;
        Set<String> uniqueLines = new HashSet<>();

        for (String line : lines) {
            String cleanedLine = line.trim();
            if (!cleanedLine.isEmpty()) {
                if (!uniqueLines.add(cleanedLine)) {
                    repeatCount++;
                }
            }
        }

        return (double) repeatCount / lines.length;
    }

    /**
     * 检查是否包含时间戳
     */
    private boolean containsTimestamps(String conversation) {
        // 简单的时间戳模式匹配
        return conversation.matches(".*\\[\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\].*") ||
                conversation.matches(".*\\d{2}:\\d{2}:\\d{2}.*");
    }

    /**
     * 计算平均响应时间
     */
    private double calculateAverageResponseTime(String conversation) {
        // 简化实现：实际应用中需要解析时间戳
        // 这里返回一个估计值
        return 60.0; // 默认60秒
    }

    /**
     * 计算平均消息长度
     */
    private int calculateAverageLength(String[] lines) {
        if (lines.length == 0) {
            return 0;
        }

        int totalLength = 0;
        for (String line : lines) {
            totalLength += line.length();
        }

        return totalLength / lines.length;
    }

    /**
     * 计算最大消息长度
     */
    private int calculateMaxLength(String[] lines) {
        if (lines.length == 0) {
            return 0;
        }

        int maxLength = 0;
        for (String line : lines) {
            maxLength = Math.max(maxLength, line.length());
        }

        return maxLength;
    }

    /**
     * 文本特征类
     */
    @Data
    public static class Features {
        private boolean containsExternalPlatform;
        private boolean containsMoneyRelated;
        private boolean containsUrgentWords;
        private boolean containsLongTermRelated;
        private boolean containsContactInfo;
        private int textLength;
        private int wordCount;
        private double repeatMessageRatio;

        public String getFeatureSummary() {
            List<String> features = new ArrayList<>();

            if (containsExternalPlatform) features.add("外部平台");
            if (containsMoneyRelated) features.add("金钱相关");
            if (containsUrgentWords) features.add("紧急词汇");
            if (containsLongTermRelated) features.add("长租相关");
            if (containsContactInfo) features.add("联系方式");

            return String.join(", ", features);
        }
    }

    /**
     * 对话模式类
     */
    @Data
    public static class ConversationPattern {
        private int userMessageCount;
        private int systemMessageCount;
        private int totalMessageCount;
        private double averageResponseTime;
        private int averageMessageLength;
        private int maxMessageLength;

        public double getUserMessageRatio() {
            return totalMessageCount > 0 ? (double) userMessageCount / totalMessageCount : 0.0;
        }
    }
}
