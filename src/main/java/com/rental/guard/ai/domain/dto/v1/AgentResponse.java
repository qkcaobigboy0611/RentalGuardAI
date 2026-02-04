/**
 * @author qkcao
 * @date 2026/1/28 15:24
 */
package com.rental.guard.ai.domain.dto.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 智能体响应 - 结构化响应数据
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentResponse {

    public enum ResponseType {
        ANALYSIS,           // 分析结果
        RECOMMENDATION,     // 建议
        WARNING,            // 警告
        LEGAL_ADVICE,       // 法律建议
        DATA_ANALYSIS,      // 数据分析
        ACTION_PLAN,        // 行动计划
        ERROR
    }

    // 基础信息
    private String responseId;
    private String sessionId;
    private String scenario;
    private ResponseType responseType;
    private LocalDateTime generatedAt;

    // 核心内容
    private String coreLogic;
    private String detailedAnalysis;
    private String riskLevel; // 极高、高、中、低
    private Double confidence; // 置信度 0-1

    // 结构化数据
    @Builder.Default
    private List<String> keyFindings = new ArrayList<>();
    @Builder.Default
    private List<String> recommendations = new ArrayList<>();
    @Builder.Default
    private List<String> legalReferences = new ArrayList<>();
    @Builder.Default
    private List<String> dataReferences = new ArrayList<>();
    @Builder.Default
    private List<ActionItem> actionItems = new ArrayList<>();
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    // RAG相关
    @Builder.Default
    private List<RetrievedDocument> supportingDocuments = new ArrayList<>();
    private String reasoningChain; // 推理链

    // MCP相关
    private String modelUsed;
    @Builder.Default
    private Map<String, Object> modelParameters = new HashMap<>();
    private Integer tokenUsage;
    private Double processingTime;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ActionItem {
        private String action;
        private String priority; // HIGH, MEDIUM, LOW
        private String deadline; // 截止时间建议
        private String responsibleParty; // 负责方
        @Builder.Default
        private Map<String, Object> metadata = new HashMap<>();
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RetrievedDocument {
        private String documentId;
        private String source; // 来源：法律条文、市场数据、历史案例等
        private String content;
        private Double relevanceScore;
        @Builder.Default
        private Map<String, Object> metadata = new HashMap<>();
    }

    // 根据场景创建预定义响应
    public static AgentResponse createForScenario(String scenario, String userInput, Double confidence) {
        // 创建 builder
        AgentResponseBuilder builder = AgentResponse.builder()
                .responseId("resp_" + UUID.randomUUID().toString())
                .scenario(scenario)
                .generatedAt(LocalDateTime.now())
                .confidence(confidence);

        // 先构建列表
        List<String> keyFindings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        List<String> legalReferences = new ArrayList<>();
        List<String> dataReferences = new ArrayList<>();
        List<ActionItem> actionItems = new ArrayList<>();

        switch (scenario) {
            case "合同审核":
                builder.responseType(ResponseType.LEGAL_ADVICE)
                        .riskLevel("中");

                // 填充数据
                builder.coreLogic("条款模糊：未定义设施损坏标准。建议明确自然损耗不赔")
                        .detailedAnalysis("押金条款存在模糊地带，建议明确：\n" +
                                "1. 设施损坏的具体评估标准\n" +
                                "2. 自然损耗的定义和范围\n" +
                                "3. 押金退还的具体时间节点和条件");

                keyFindings.add("条款中对'损坏'定义不明确");
                keyFindings.add("未区分自然损耗和人为损坏");
                keyFindings.add("押金退还时间节点缺失");

                recommendations.add("要求房东提供明确的损坏评估标准");
                recommendations.add("添加自然损耗免责条款");
                recommendations.add("明确押金在退租后7-14个工作日内退还");

                legalReferences.add("《民法典》第七百一十四条：承租人应当妥善保管租赁物");
                legalReferences.add("《民法典》第七百一十六条：承租人经出租人同意转租");
                break;

            case "距离欺诈":
                builder.responseType(ResponseType.DATA_ANALYSIS)
                        .riskLevel("高")
                        .coreLogic("根据地图数据，实际步行距离约1.5公里，通常需要20分钟")
                        .detailedAnalysis("中介声称的'5分钟'可能存在夸大宣传：\n" +
                                "1. 实际测量距离1.5公里\n" +
                                "2. 正常步行速度需要18-22分钟\n" +
                                "3. 可能存在虚假宣传风险");

                keyFindings.add("宣传距离与实际距离严重不符");
                keyFindings.add("涉嫌违反广告法关于真实宣传的规定");
                keyFindings.add("影响居住便利性和房产价值");

                recommendations.add("使用地图软件验证实际距离");
                recommendations.add("要求中介提供书面距离承诺");
                recommendations.add("如确认虚假宣传可要求价格调整");

                legalReferences.add("《广告法》第四条：广告不得含有虚假或者引人误解的内容");
                legalReferences.add("《消费者权益保护法》第二十条：经营者提供信息应当真实、全面");
                break;

            case "租金欺诈":
                builder.responseType(ResponseType.DATA_ANALYSIS)
                        .riskLevel("低")
                        .coreLogic("同小区同户型均价约4200-4500，建议以此为依据砍价")
                        .detailedAnalysis("房东报价5000元高于市场价：\n" +
                                "1. 同小区市场均价：4200-4500元\n" +
                                "2. 报价溢价：11%-19%\n" +
                                "3. 可能存在隐形费用");

                keyFindings.add("租金报价显著高于市场价");
                keyFindings.add("可能存在物业费、维修费等额外费用");
                keyFindings.add("周边配套设施影响租金合理性");

                recommendations.add("以市场价4200-4500元为基准进行协商");
                recommendations.add("要求明确所有费用明细（物业、水电、网络等）");
                recommendations.add("考虑周边类似房源对比");

                dataReferences.add("同小区历史租金数据");
                dataReferences.add("周边配套设施评分");
                break;

            case "霸王条款":
                builder.responseType(ResponseType.WARNING)
                        .riskLevel("极高")
                        .coreLogic("该条款违法。根据《民法典》，除非你违约造成损失，否则押金必须退还")
                        .detailedAnalysis("'断租不退押金'条款属于违法条款：\n" +
                                "1. 违反《民法典》关于押金的规定\n" +
                                "2. 属于典型的霸王条款\n" +
                                "3. 严重损害承租人合法权益");

                keyFindings.add("条款内容明显违法");
                keyFindings.add("涉嫌利用格式条款排除自身责任");
                keyFindings.add("可能构成欺诈");

                recommendations.add("坚决拒绝签署该条款");
                recommendations.add("要求修改为合法条款");
                recommendations.add("必要时向监管部门举报");

                legalReferences.add("《民法典》第四百九十七条：格式条款无效情形");
                legalReferences.add("《民法典》第五百八十六条：定金合同");
                legalReferences.add("《消费者权益保护法》第二十六条：格式条款限制");
                break;

            default:
                builder.responseType(ResponseType.ANALYSIS)
                        .riskLevel("未知")
                        .coreLogic("正在分析您的问题...")
                        .confidence(0.5);
        }

        // 设置列表到 builder
        builder.keyFindings(keyFindings)
                .recommendations(recommendations)
                .legalReferences(legalReferences)
                .dataReferences(dataReferences);

        // 添加通用行动项
        actionItems.add(ActionItem.builder()
                .action("保存所有沟通记录和文件")
                .priority("HIGH")
                .responsibleParty("租客")
                .build());

        builder.actionItems(actionItems);

        return builder.build();
    }

    // 链式调用方法 - 实例方法，用于构建后修改对象
    public AgentResponse addKeyFinding(String finding) {
        if (this.keyFindings == null) {
            this.keyFindings = new ArrayList<>();
        }
        this.keyFindings.add(finding);
        return this;
    }

    public AgentResponse addRecommendation(String recommendation) {
        if (this.recommendations == null) {
            this.recommendations = new ArrayList<>();
        }
        this.recommendations.add(recommendation);
        return this;
    }

    public AgentResponse addLegalReference(String reference) {
        if (this.legalReferences == null) {
            this.legalReferences = new ArrayList<>();
        }
        this.legalReferences.add(reference);
        return this;
    }

    public AgentResponse addDataReference(String reference) {
        if (this.dataReferences == null) {
            this.dataReferences = new ArrayList<>();
        }
        this.dataReferences.add(reference);
        return this;
    }

    public AgentResponse addActionItem(ActionItem actionItem) {
        if (this.actionItems == null) {
            this.actionItems = new ArrayList<>();
        }
        this.actionItems.add(actionItem);
        return this;
    }

    public AgentResponse addSupportingDocument(RetrievedDocument document) {
        if (this.supportingDocuments == null) {
            this.supportingDocuments = new ArrayList<>();
        }
        this.supportingDocuments.add(document);
        return this;
    }

    public String getFormattedResponse() {
        StringBuilder sb = new StringBuilder();
        sb.append("【智能体分析报告】\n");
        sb.append("=").append("=".repeat(50)).append("\n");
        sb.append("场景：").append(scenario).append("\n");
        sb.append("风险等级：").append(riskLevel).append("\n");
        sb.append("置信度：").append(String.format("%.1f%%", confidence * 100)).append("\n\n");

        sb.append("核心分析：\n");
        sb.append("- ").append(coreLogic).append("\n\n");

        if (detailedAnalysis != null && !detailedAnalysis.isEmpty()) {
            sb.append("详细分析：\n");
            for (String line : detailedAnalysis.split("\n")) {
                sb.append("  ").append(line).append("\n");
            }
            sb.append("\n");
        }

        if (keyFindings != null && !keyFindings.isEmpty()) {
            sb.append("关键发现：\n");
            for (int i = 0; i < keyFindings.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(keyFindings.get(i)).append("\n");
            }
            sb.append("\n");
        }

        if (recommendations != null && !recommendations.isEmpty()) {
            sb.append("建议行动：\n");
            for (int i = 0; i < recommendations.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(recommendations.get(i)).append("\n");
            }
            sb.append("\n");
        }

        if (legalReferences != null && !legalReferences.isEmpty()) {
            sb.append("法律依据：\n");
            for (String ref : legalReferences) {
                sb.append("  - ").append(ref).append("\n");
            }
            sb.append("\n");
        }

        if (actionItems != null && !actionItems.isEmpty()) {
            sb.append("行动清单：\n");
            for (ActionItem item : actionItems) {
                sb.append("  ✓ ").append(item.getAction())
                        .append(" [").append(item.getPriority()).append("]")
                        .append(" → ").append(item.getResponsibleParty())
                        .append("\n");
            }
        }

        if (supportingDocuments != null && !supportingDocuments.isEmpty()) {
            sb.append("\n参考文档：\n");
            for (RetrievedDocument doc : supportingDocuments) {
                sb.append("  📄 ").append(doc.getSource())
                        .append(" (相关度：").append(String.format("%.1f%%", doc.getRelevanceScore() * 100)).append(")\n");
            }
        }

        return sb.toString();
    }
}
