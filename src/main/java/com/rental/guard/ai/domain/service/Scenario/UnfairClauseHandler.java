/**
 * @author qkcao
 * @date 2026/2/11 11:41
 */
package com.rental.guard.ai.domain.service.Scenario;

import com.rental.guard.ai.domain.dto.v1.AgentResponse;
import com.rental.guard.ai.domain.dto.v1.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 霸王条款场景处理器
 */
@Slf4j
@Component
public class UnfairClauseHandler implements ScenarioHandler {
    private static final Map<String, String> UNFAIR_CLAUSE_PATTERNS = Map.of(
            "任意解除权", "房东有权随时解除合同",
            "单方解释权", "最终解释权归房东所有",
            "无限责任", "租客承担无限连带责任",
            "押金没收", "房东有权没收全部押金",
            "强制续租", "租客必须同意续租",
            "无理由涨价", "房东可随时调整租金"
    );


    @Override
    public void process(AgentResponse response, SessionManager session,
                        List<AgentResponse.RetrievedDocument> docs) {
        try {
            log.info("开始霸王条款分析后处理 - 会话: {}", session.getSessionId());
            String analysis = response.getDetailedAnalysis();

            // 1. 结构化识别：使用权重识别风险
            List<String> detectedClauses = new ArrayList<>();
            UNFAIR_CLAUSE_PATTERNS.forEach((clauseName, pattern) -> {
                if (analysis.contains(pattern)) {
                    detectedClauses.add(clauseName);
                    // 优化：不再只是 setRiskLevel，而是带权重的累加
                    // 霸王条款权重通常较高，每发现一处增加 0.3 风险分
                    response.addRiskFactor("存在霸王条款: " + clauseName, 0.35);
                }
            });

            // 2. 动态元数据注入：方便前端展示“风险分布图”
            if (!detectedClauses.isEmpty()) {
                response.putMetadata("detectedUnfairClauses", detectedClauses);
                response.setConfidence(Math.max(response.getConfidence(), 0.98)); // 识别出具体模式，置信度极高

                response.appendDetailedAnalysis("\n⚖️ 法律风险扫描结论：");
                response.appendDetailedAnalysis("\n  共识别出 " + detectedClauses.size() + " 处高度疑似违法/不公平的格式条款（俗称“霸王条款”）。");
            }

            // 3. 模块化检查（复用之前的优化逻辑）
            supplementLegalBasis(response, docs);       // 填充 legalReferences 法律依据补充
            checkConsumerProtection(response, analysis); // 检查缺失的保护项 消费者权益保护条款检查
            calculateFairnessScore(response, analysis, detectedClauses.size()); // 计算公平分 合同公平性评分

            // 4. 自动化行动建议 维权建议
            if (!detectedClauses.isEmpty()) {
                response.addActionItem(AgentResponse.ActionItem.builder()
                        .action("拒绝签署包含违法格式条款的初版合同")
                        .priority("HIGH")
                        .responsibleParty("租客")
                        .build());
                provideRemedySuggestions(response); // 填充 recommendations
            }

        } catch (Exception e) {
            log.error("霸王条款处理异常", e);
            response.setResponseType(AgentResponse.ResponseType.ERROR);
        }
    }

    private void supplementLegalBasis(AgentResponse response, List<AgentResponse.RetrievedDocument> docs) {
        List<AgentResponse.RetrievedDocument> legalDocs = docs.stream()
                .filter(doc -> doc.getType().equals("法律法规") &&
                        (doc.getTitle().contains("合同法") ||
                                doc.getTitle().contains("消费者权益")))
                .collect(Collectors.toList());

        if (!legalDocs.isEmpty()) {
            response.appendDetailedAnalysis("\n📚 相关法律依据:");
            legalDocs.forEach(doc ->
                    response.appendDetailedAnalysis("\n  - " + doc.getTitle() + ": " +
                            extractRelevantClause(doc.getContent())));
        }
    }

    private void checkConsumerProtection(AgentResponse response, String analysis) {
        // 1. 定义需要检查的维度及其描述
        Map<String, String> protectionMap = new LinkedHashMap<>();
        protectionMap.put("七天冷静期", "建议增加：签约后7日内如未入住可无偿反悔。");
        protectionMap.put("无理由解约", "建议增加：租客在提前30天通知的情况下，有权扣除部分押金后解约。");
        protectionMap.put("维修保障", "建议明确：非人为损坏的家电、漏水等由房东在24-48小时内负责维修。");

        List<String> missingLabels = new ArrayList<>();
        List<String> adviceList = new ArrayList<>();

        // 2. 逐项检查
        protectionMap.forEach((key, advice) -> {
            if (!analysis.contains(key)) {
                missingLabels.add(key);
                adviceList.add(advice);
            }
        });

        // 3. 根据结果更新响应
        if (!missingLabels.isEmpty()) {
            // 记录风险因素（带上具体数量）
            response.addRiskFactor("合同保护条款不全 (" + missingLabels.size() + "项缺失)", 0.2);

            // 结构化填充：将缺失项存入元数据，前端可以根据这个显示红色的“未达标”标签
            response.putMetadata("missingProtections", missingLabels);

            // 详细分析追加
            response.appendDetailedAnalysis("\n🛡️ 消费者权益保障分析：");
            response.appendDetailedAnalysis("\n  当前合同在【" + String.join("/", missingLabels) + "】方面缺乏明确约定。");

            // 直接加入到建议清单
            response.addRecommendations(adviceList);

            // 联动行动项
            response.addActionItem(AgentResponse.ActionItem.builder()
                    .action("在合同补充协议中加入缺失的保障条款")
                    .priority("MEDIUM")
                    .responsibleParty("租客")
                    .build());
        } else {
            response.appendDetailedAnalysis("\n✅ 该合同包含基础的消费者保护条款，合规性良好。");
        }
    }
    private void calculateFairnessScore(AgentResponse response, String analysis, int unfairCount) {
        int score = 10; // 基础分
        score -= unfairCount * 2; // 每条霸王条款扣2分

        // 加分项
        if (analysis.contains("公平协商")) score += 1;
        if (analysis.contains("双方权利对等")) score += 1;
        if (analysis.contains("明确违约责任")) score += 1;

        score = Math.max(0, Math.min(score, 10));

        response.appendDetailedAnalysis("\n📊 合同公平性评分: " + score + "/10");
        if (score < 6) {
            response.appendDetailedAnalysis("\n  ⚠️ 合同公平性较差，建议谨慎签署");
        }
    }

    private void provideRemedySuggestions(AgentResponse response) {
        if (response.getRiskLevel().equals("高风险")) {
            response.appendDetailedAnalysis("\n💡 维权建议:");
            response.appendDetailedAnalysis("\n  1. 要求修改不平等条款");
            response.appendDetailedAnalysis("\n  2. 保留所有沟通记录");
            response.appendDetailedAnalysis("\n  3. 咨询专业法律人士");
            response.appendDetailedAnalysis("\n  4. 向消费者协会投诉");
        }
    }
    private String extractRelevantClause(String content) {
        // 简化实现，提取相关条款
        if (content.contains("格式条款")) {
            return "提供方应合理提示格式条款";
        } else if (content.contains("不公平")) {
            return "排除对方主要权利的条款无效";
        }
        return "相关法律规定";
    }
}
