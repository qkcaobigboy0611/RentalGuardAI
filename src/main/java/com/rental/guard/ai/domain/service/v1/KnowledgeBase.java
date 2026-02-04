/**
 * @author qkcao
 * @date 2026/1/27 10:28
 */
package com.rental.guard.ai.domain.service.v1;

import java.util.*;

/**
 * 知识库模块：存储欺诈案例、规则和模式
 */
public class KnowledgeBase {
    private final List<FraudRule> fraudRules = new ArrayList<>();
    private final Map<String, FraudPattern> fraudPatterns = new HashMap<>();

    public KnowledgeBase() {
        initializeKnowledge();
    }

    private void initializeKnowledge() {
        // 初始化欺诈规则
        fraudRules.add(new FraudRule("RU001", "要求提前支付押金", 0.8,
                Arrays.asList("押金", "定金", "先付")));
        fraudRules.add(new FraudRule("RU002", "催促立即决策", 0.6,
                Arrays.asList("马上", "立即", "今天")));
        fraudRules.add(new FraudRule("RU003", "拒绝提供正规合同", 0.7,
                Arrays.asList("没合同", "简单协议", "不用合同")));

        // 初始化欺诈模式
        fraudPatterns.put("DEPOSIT_FRAUD", new FraudPattern("押金欺诈",
                Arrays.asList("要求转账", "拒绝见面", "虚假房源")));
        fraudPatterns.put("PHISHING_FRAUD", new FraudPattern("钓鱼欺诈",
                Arrays.asList("索要个人信息", "要求点击链接", "验证码诈骗")));
    }

    public KnowledgeData queryRelevantKnowledge(ConversationContext context,
                                                RiskFeatures features) {
        KnowledgeData data = new KnowledgeData();

        // 查询相关规则
        for (FraudRule rule : fraudRules) {
            if (matchesRule(context.getLatestMessage(), rule)) {
                data.addRelevantRule(rule);
            }
        }

        // 查询相关模式
        for (FraudPattern pattern : fraudPatterns.values()) {
            if (matchesPattern(context.getAllMessages(), pattern)) {
                data.addRelevantPattern(pattern);
            }
        }

        return data;
    }

    private boolean matchesRule(String text, FraudRule rule) {
        for (String keyword : rule.getKeywords()) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPattern(List<String> messages, FraudPattern pattern) {
        int matchCount = 0;
        String allText = String.join(" ", messages);
        for (String indicator : pattern.getIndicators()) {
            if (allText.contains(indicator)) {
                matchCount++;
            }
        }
        return matchCount >= 2; // 至少匹配两个指标
    }
}
