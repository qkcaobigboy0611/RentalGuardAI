/**
 * @author qkcao
 * @date 2026/2/11 11:38
 */
package com.rental.guard.ai.domain.service.Scenario;

import com.rental.guard.ai.domain.dto.v1.AgentResponse;
import com.rental.guard.ai.domain.dto.v1.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 合同审核场景处理器
 */
@Slf4j
@Component
public class ContractReviewHandler implements ScenarioHandler {

    @Override
    public void process(AgentResponse response, SessionManager session,
                        List<AgentResponse.RetrievedDocument> docs) {
        try {
            log.info("开始合同审核后处理 - 会话: {}", session.getSessionId());

            // 1. 提取合同相关的关键条款
            String analysis = response.getDetailedAnalysis();

            // 2. 风险条款识别与标记
            if (analysis.contains("押金") || analysis.contains("保证金")) {
                // 检查押金条款是否合规
                double depositRisk = calculateDepositRisk(analysis);
                if (depositRisk > 0.7) {
                    response.setRiskLevel("高风险");
                    response.setConfidence(Math.max(response.getConfidence(), 0.9));
                }
            }

            // 3. 维修责任分析
            if (analysis.contains("维修") || analysis.contains("损坏") || analysis.contains("责任")) {
                String maintenanceRisk = analyzeMaintenanceClause(analysis);
                if (maintenanceRisk.equals("租客全责")) {
                    response.addRiskFactor("不公平维修条款", 0.1);
                }
            }
            // 4. 违约金条款检查
            checkPenaltyClause(response, analysis);

            // 5. 关键日期提醒
            extractCriticalDates(response, analysis);

            // 6. 法律条款匹配度检查
            checkLegalCompliance(response, docs);

            log.info("合同审核后处理完成 - 风险等级: {}", response.getRiskLevel());

        } catch (Exception e) {
            log.error("合同审核后处理异常", e);
        }
    }

    private double calculateDepositRisk(String analysis) {
        // 模拟押金风险计算逻辑
        if (analysis.contains("押金不退") || analysis.contains("不退押金")) {
            return 0.9;
        } else if (analysis.contains("押金抵扣") && analysis.contains("自然损耗")) {
            return 0.8;
        } else if (analysis.contains("押金") && analysis.contains("30天")) {
            return 0.7;
        }
        return 0.3;
    }

    private String analyzeMaintenanceClause(String analysis) {
        if (analysis.contains("租客负责所有维修")) {
            return "租客全责";
        } else if (analysis.contains("自然损耗由房东负责")) {
            return "责任分明";
        }
        return "一般条款";
    }

    private void checkPenaltyClause(AgentResponse response, String analysis) {
        // 1. 基础条件触发
        if (analysis.contains("违约金") && analysis.contains("月租金")) {
            String penaltyText = extractPenaltyAmount(analysis);
            double penaltyRatio = calculatePenaltyRatio(penaltyText);

            // 2. 逻辑判断：如果违约金超过 2 个月租金（法律实践中通常认为 20%-30% 或 1-2 个月合理，超过则过高）
            if (penaltyRatio > 2.0) {
                response.addRiskFactor("违约金过高", 0.4); // 赋予 0.4 的风险权重
                response.appendDetailedAnalysis("\n⚠️ 发现违约金条款风险:");
                response.appendDetailedAnalysis(String.format("\n  - 检测到违约金为月租金的 %.1f 倍", penaltyRatio));
                response.appendDetailedAnalysis("\n  - 法律提示：根据《民法典》，约定的违约金过分高于造成的损失的，当事人可以请求适当减少。一般超过月租金 2 倍可能被法院认定为过高。");

                // 3. 联动添加行动建议
                response.addActionItem(AgentResponse.ActionItem.builder()
                        .action("要求将违约金下调至 1-2 个月租金水平")
                        .priority("HIGH")
                        .responsibleParty("租客")
                        .build());

            }
        }
    }

    private void extractCriticalDates(AgentResponse response, String analysis) {
        // 提取合同中的关键日期
        if (analysis.contains("起租日") || analysis.contains("租赁期限")) {
            response.appendDetailedAnalysis("\n📅 合同关键日期已识别，请确认:");
            if (analysis.contains("起租日")) {
                response.appendDetailedAnalysis("\n  - 起租日: " + extractDate(analysis, "起租日"));
            }
            if (analysis.contains("到期日")) {
                response.appendDetailedAnalysis("\n  - 到期日: " + extractDate(analysis, "到期日"));
            }
        }
    }

    private void checkLegalCompliance(AgentResponse response, List<AgentResponse.RetrievedDocument> docs) {
        // 检查是否符合相关法律法规
        List<AgentResponse.RetrievedDocument> legalDocs = docs.stream()
                .filter(doc -> doc.getType().equals("法律法规"))
                .collect(Collectors.toList());

        if (!legalDocs.isEmpty()) {
            response.appendDetailedAnalysis("\n⚖️ 参考法律法规:");
            legalDocs.forEach(doc ->
                    response.appendDetailedAnalysis("\n  - " + doc.getTitle() + ": " + doc.getSummary()));
        }
    }

    private String extractPenaltyAmount(String text) {
        // 简化实现，实际应用中可以使用正则表达式提取
        if (text.contains("违约金为月租金的3倍")) {
            return "月租金3倍";
        } else if (text.contains("违约金为月租金的2倍")) {
            return "月租金2倍";
        }
        return "未知";
    }

    private double calculatePenaltyRatio(String penaltyText) {
        if (penaltyText.contains("3倍")) return 3.0;
        if (penaltyText.contains("2倍")) return 2.0;
        if (penaltyText.contains("1倍")) return 1.0;
        return 0.0;
    }

    private String extractDate(String text, String keyword) {
        // 简化实现
        return "具体日期请查看合同原文";
    }
}
