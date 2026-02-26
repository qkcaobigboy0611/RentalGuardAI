/**
 * @author qkcao
 * @date 2026/2/11 11:44
 */
package com.rental.guard.ai.domain.service.Scenario;

import com.rental.guard.ai.domain.dto.v1.AgentResponse;
import com.rental.guard.ai.domain.dto.v1.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 租金欺诈场景处理器
 */
@Slf4j
@Component
public class RentFraudHandler implements ScenarioHandler {

    @Override
    public void process(AgentResponse response, SessionManager session,
                        List<AgentResponse.RetrievedDocument> docs) {
        try {
            log.info("开始租金欺诈分析后处理 - 会话: {}", session.getSessionId());
            String analysis = response.getDetailedAnalysis();

            // 1. 价格溢价量化分析 (权重: 0.5)
            double marketPrice = getAverageMarketPrice(docs);
            double listingPrice = extractRentPrice(analysis);
            if (marketPrice > 0 && listingPrice > 0) {
                double ratio = listingPrice / marketPrice;
                response.putMetadata("rentMarketRatio", ratio); // 存入元数据供前端绘图

                if (ratio > 1.3) {
                    response.addRiskFactor("租金显著高于市场价 (溢价" + Math.round((ratio - 1) * 100) + "%)", 0.5);
                    response.addActionItem(AgentResponse.ActionItem.builder()
                            .action("对比周边同户型房源，以此溢价率为依据进行议价")
                            .priority("HIGH").responsibleParty("租客").build());
                }
            }

            // 2. 隐形费用深度检测
            detectHiddenFees(response, analysis);

            // 3. 押金杠杆风险 (权重: 0.3)
            checkDepositReasonableness(response, analysis);

            // 4. 付款安全性分析
            analyzePaymentMethod(response, analysis);

            // 5. 涨幅保护条款
            checkRentIncreaseClause(response, analysis);

            // 6. RAG 市场趋势增强
            analyzeMarketTrend(response, docs);

            log.info("租金欺诈分析完成 - 最终风险分: {}", response.getRiskScore());
        } catch (Exception e) {
            log.error("租金欺诈处理异常", e);
        }
    }

    private double analyzeMarketPrice(String analysis, List<AgentResponse.RetrievedDocument> docs) {
        // 获取市场参考价
        double marketPrice = getAverageMarketPrice(docs);
        double listingPrice = extractRentPrice(analysis);

        if (marketPrice > 0 && listingPrice > 0) {
            return listingPrice / marketPrice;
        }
        return 1.0;
    }

    private void detectHiddenFees(AgentResponse response, String analysis) {
        // 定义费用项及其默认责任方
        Map<String, String> feeRules = new HashMap<>();
        feeRules.put("管理费", "通常包含在租金中，建议明确是否重复收取");
        feeRules.put("维修基金", "法律规定由房东承担，租客无需支付");
        feeRules.put("清洁费", "应明确是公共区域还是室内，建议拒绝分摊");

        List<String> detectedFees = new ArrayList<>();
        feeRules.forEach((fee, advice) -> {
            if (analysis.contains(fee)) {
                detectedFees.add(fee);
                response.addRecommendation(fee + ": " + advice);
            }
        });

        if (detectedFees.size() >= 2) {
            response.addRiskFactor("合同包含非典型隐形费用", 0.3);
            response.putMetadata("hiddenFeeList", detectedFees);
        }
    }

    private void checkDepositReasonableness(AgentResponse response, String analysis) {
        double depositRatio = RegexUtils.extractDepositRatio(analysis);
        if (depositRatio > 2.0) { // 超过押二
            response.addRiskFactor("押金比例过高 (押" + depositRatio + ")", 0.4);
            response.addLegalReference("《住房租赁条例》相关建议：押金通常不应超过一个月租金（部分地区标准）");

            response.addActionItem(AgentResponse.ActionItem.builder()
                    .action("协商调整为‘押一付三’或‘押一付一’，减少资金沉淀")
                    .priority("MEDIUM").responsibleParty("租客").build());
        }
    }

    private void analyzePaymentMethod(AgentResponse response, String analysis) {
        // 1. 定义风险权重矩阵
        if (analysis.contains("贷款") || analysis.contains("分期支付") || analysis.contains("消费贷")) {
            response.addRiskFactor("疑似租房贷陷阱", 0.9); // 极高风险
            response.appendDetailedAnalysis("\n🚫 警惕：合同包含贷款/分期支付条款。这可能涉及‘租房贷’，一旦平台爆雷，你仍需向银行还款。");
            response.addActionItem(AgentResponse.ActionItem.builder()
                    .action("拒绝签署任何涉及银行贷款授权的条款")
                    .priority("HIGH").responsibleParty("租客").build());
        } else if (analysis.contains("年付") || analysis.contains("半年付")) {
            response.addRiskFactor("大额预付风险", 0.6); // 高风险
            response.appendDetailedAnalysis("\n💳 预付周期过长：年付/半年付虽可能有优惠，但面临‘卷款跑路’的资金安全性较低。");
            response.addActionItem(AgentResponse.ActionItem.builder()
                    .action("要求改为‘押一付三’，或要求资金监管")
                    .priority("MEDIUM").responsibleParty("租客").build());
        } else if (analysis.contains("押一付一") || analysis.contains("月付")) {
            response.appendDetailedAnalysis("\n✅ 付款方式：押一付一/月付。资金占用少，灵活性高，属于推荐方式。");
        }
    }

    private void checkRentIncreaseClause(AgentResponse response, String analysis) {
        if (analysis.contains("租金") && (analysis.contains("上调") || analysis.contains("涨幅"))) {
            double increaseRate = RegexUtils.extractRate(analysis);

            // 存储结构化数据，方便前端做租金成本曲线预测
            response.putMetadata("annualIncreaseRate", increaseRate);

            if (increaseRate > 0.05) { // 超过 5% 即需关注（目前多地倡导租金涨幅平稳）
                double riskWeight = increaseRate > 0.1 ? 0.5 : 0.2;
                response.addRiskFactor("租金涨幅预期过高", riskWeight);

                response.appendDetailedAnalysis(String.format("\n📈 租金波动预警：年涨幅为 %.1f%%。", increaseRate * 100));

                if (analysis.contains("房东有权根据市场情况调整")) {
                    response.addRiskFactor("涨价解释权不对等", 0.4);
                    response.appendDetailedAnalysis("\n⚠️ 合同包含‘单方面调价’描述，缺乏明确限制。");
                }

                response.addLegalReference("《住房租赁条例》：鼓励出租人与承租人签订长期住房租赁合同，稳定租金。");
                response.addRecommendation("建议在合同中明确：‘租期内租金不变，续租涨幅不超过 3%-5%’。");
            }
        } else {
            // 如果没写涨幅，也是一种风险：房东可以随时要求续租加价
            response.addRecommendation("建议在合同中补充‘续租租金涨幅上限’条款，避免到期被恶意加价。");
        }
    }

    private void analyzeMarketTrend(AgentResponse response, List<AgentResponse.RetrievedDocument> docs) {
        List<AgentResponse.RetrievedDocument> marketDocs = docs.stream()
                .filter(doc -> doc.getType().equals("市场报告") || doc.getTitle().contains("租金趋势"))
                .collect(Collectors.toList());

        if (!marketDocs.isEmpty()) {
            response.appendDetailedAnalysis("\n📊 当前市场趋势:");
            marketDocs.forEach(doc ->
                    response.appendDetailedAnalysis("\n  - " + doc.getSummary()));
        }
    }


    private double getAverageMarketPrice(List<AgentResponse.RetrievedDocument> docs) {
        if (docs == null || docs.isEmpty()) {
            log.warn("未获取到市场参考文档，无法计算均价");
            return 0.0;
        }

        // 筛选出与价格相关的文档并提取数值
        List<Double> marketPrices = docs.stream()
                // 过滤掉无关文档
                .filter(doc -> "市场报告".equals(doc.getType()) || doc.getContent().contains("元"))
                .map(doc -> {
                    // 优先从元数据提取（如果爬虫/数据库预处理过）
                    Object metaPrice = doc.getMetadata().get("averagePrice");
                    if (metaPrice instanceof Number) {
                        return ((Number) metaPrice).doubleValue();
                    }
                    // 否则从正文中正则提取
                    return RegexUtils.extractRent(doc.getContent());
                })
                .filter(price -> price > 0) // 过滤掉提取失败的 0.0
                .collect(Collectors.toList());

        if (marketPrices.isEmpty()) return 0.0;

        // 计算平均值
        double sum = 0;
        for (Double p : marketPrices) sum += p;
        double average = sum / marketPrices.size();

        log.info("从 {} 份参考资料中计算出市场均价: {}", marketPrices.size(), average);
        return average;
    }

    private double extractRentPrice(String analysis) {
        if (analysis == null || analysis.isEmpty()) {
            return 0.0;
        }

        // 1. 基础清洗：统一将 "k" 或 "K" 替换为 "000"（处理 6k -> 6000）
        String normalizedText = analysis.toLowerCase()
                .replace("k", "000")
                .replace("千", "000");

        // 2. 调用 RegexUtils 进行正则捕获
        double price = RegexUtils.extractRent(normalizedText);

        // 3. 极端情况补丁：如果没带“租金”关键字，直接捕获文本中第一个 3-5 位的数字
        if (price == 0.0) {
            Pattern p = Pattern.compile("(\\d{4,5})");
            Matcher m = p.matcher(analysis);
            if (m.find()) {
                price = Double.parseDouble(m.group(1));
            }
        }

        log.debug("租金提取结果：原始文本片段->{}，提取数值->{}",
                analysis.length() > 20 ? analysis.substring(0, 20) : analysis, price);

        return price;
    }

}
