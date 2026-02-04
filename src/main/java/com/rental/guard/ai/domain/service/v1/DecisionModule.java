/**
 * @author qkcao
 * @date 2026/1/27 10:33
 */
package com.rental.guard.ai.domain.service.v1;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 决策模块：基于分析和特征做出决策
 */
public class DecisionModule {


    public AgentDecision makeDecision(ConversationContext context,
                                      RiskFeatures features,
                                      FraudAnalysisResult analysis) {
        AgentDecision decision = new AgentDecision();
        decision.setTimestamp(LocalDateTime.now());

        // 计算综合风险分数
        double compositeScore = calculateCompositeScore(features, analysis);
        decision.setRiskScore(compositeScore);

        // 确定风险等级
        RiskLevel riskLevel = determineRiskLevel(compositeScore);
        decision.setRiskLevel(riskLevel);

        // 制定决策策略
        DecisionStrategy strategy = determineStrategy(riskLevel, analysis);
        decision.setStrategy(strategy);

        // 生成建议
        List<String> recommendations = generateRecommendations(riskLevel, analysis);
        decision.setRecommendations(recommendations);

        return decision;
    }

    private double calculateCompositeScore(RiskFeatures features, FraudAnalysisResult analysis) {
        double score = analysis.getRiskScore() * 0.6; // AI分析权重60%

        // 特征加权
        score += features.getUrgencyScore() * 0.15;
        score += features.getPressureScore() * 0.15;
        score += Math.min(features.getConversationLength() * 2, 10) * 0.1;

        return Math.min(score, 100);
    }

    private RiskLevel determineRiskLevel(double score) {
        if (score >= 70) return RiskLevel.HIGH;
        if (score >= 40) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    private DecisionStrategy determineStrategy(RiskLevel riskLevel, FraudAnalysisResult analysis) {
        switch (riskLevel) {
            case HIGH:
                return DecisionStrategy.BLOCK_AND_WARN;
            case MEDIUM:
                return DecisionStrategy.WARN_AND_MONITOR;
            case LOW:
                return analysis.isFraud() ? DecisionStrategy.WARN_AND_MONITOR : DecisionStrategy.CONTINUE;
            default:
                return DecisionStrategy.CONTINUE;
        }
    }

    private List<String> generateRecommendations(RiskLevel riskLevel, FraudAnalysisResult analysis) {
        List<String> recommendations = new ArrayList<>();

        switch (riskLevel) {
            case HIGH:
                recommendations.add("立即终止对话");
                recommendations.add("报告可疑行为");
                recommendations.add("警告其他用户");
                break;
            case MEDIUM:
                recommendations.add("保持警惕");
                recommendations.add("要求正规合同");
                recommendations.add("避免线下交易");
                break;
            case LOW:
                recommendations.add("正常交流");
                recommendations.add("注意信息保护");
                break;
        }

        if (analysis.getSuggestions() != null) {
            recommendations.addAll(analysis.getSuggestions());
        }

        return recommendations;
    }
}
