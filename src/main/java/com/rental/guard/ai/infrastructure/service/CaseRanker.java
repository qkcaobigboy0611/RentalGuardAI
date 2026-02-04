/**
 * @author qkcao
 * @date 2025/12/31 16:22
 */
package com.rental.guard.ai.infrastructure.service;

import com.rental.guard.ai.infrastructure.po.PoFraudTrainingCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CaseRanker {

    /**
     * 合并和排名案例
     */
    public static List<PoFraudTrainingCase> mergeAndRank(
            List<PoFraudTrainingCase> vectorCases,
            List<PoFraudTrainingCase> keywordCases,
            int topK) {

        // 1. 使用Map去重
        Map<Integer, RankedCase> caseMap = new HashMap<>();

        // 添加向量搜索结果
        addCasesToMap(caseMap, vectorCases, 1.0, 0);

        // 添加关键词搜索结果
        addCasesToMap(caseMap, keywordCases, 0.8, 1);

        // 2. 计算综合得分
        List<RankedCase> rankedCases = new ArrayList<>(caseMap.values());

        // 3. 按综合得分排序
        rankedCases.sort((a, b) -> {
            double scoreA = calculateFinalScore(a);
            double scoreB = calculateFinalScore(b);
            return Double.compare(scoreB, scoreA); // 降序
        });

        // 4. 返回topK
        return rankedCases.stream()
                .limit(topK)
                .map(rc -> rc.trainingCase)
                .collect(Collectors.toList());
    }

    /**
     * 计算最终得分
     */
    private static double calculateFinalScore(RankedCase rankedCase) {
        double score = 0.0;

        // 1. 基础权重
        score += rankedCase.getTotalWeight();

        // 2. 置信度分数
        if (rankedCase.trainingCase.getConfidenceScore() != null) {
            score += rankedCase.trainingCase.getConfidenceScore().doubleValue() * 0.3;
        }

        // 3. 欺诈案例优先
        if (rankedCase.trainingCase.getIsFraud() != null &&
                rankedCase.trainingCase.getIsFraud() == 1) {
            score += 0.5;
        }

        // 4. 时间权重（新案例优先）
        if (rankedCase.trainingCase.getCreateTime() != null) {
            long daysOld = (System.currentTimeMillis() -
                    rankedCase.trainingCase.getCreateTime().getTime()) /
                    (1000 * 60 * 60 * 24);
            double timeWeight = Math.max(0, 1.0 - daysOld / 365.0); // 一年内有效
            score += timeWeight * 0.2;
        }

        return score;
    }

    /**
     * 添加案例到Map
     */
    private static void addCasesToMap(Map<Integer, RankedCase> caseMap,
                                      List<PoFraudTrainingCase> cases,
                                      double sourceWeight,
                                      int sourceType) {
        for (int i = 0; i < cases.size(); i++) {
            PoFraudTrainingCase trainingCase = cases.get(i);
            int id = trainingCase.getId();

            RankedCase rankedCase = caseMap.get(id);
            if (rankedCase == null) {
                rankedCase = new RankedCase(trainingCase);
                caseMap.put(id, rankedCase);
            }

            // 更新权重：考虑位置权重（排名靠前的权重更高）
            double positionWeight = 1.0 / (i + 1);
            rankedCase.addSource(sourceType, sourceWeight * positionWeight);
        }
    }

    /**
     * 内部类：带排名的案例
     */
    private static class RankedCase {
        PoFraudTrainingCase trainingCase;
        Map<Integer, Double> sourceWeights; // sourceType -> weight

        RankedCase(PoFraudTrainingCase trainingCase) {
            this.trainingCase = trainingCase;
            this.sourceWeights = new HashMap<>();
        }

        void addSource(int sourceType, double weight) {
            sourceWeights.put(sourceType, weight);
        }

        double getTotalWeight() {
            return sourceWeights.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();
        }

        boolean isFromMultipleSources() {
            return sourceWeights.size() > 1;
        }
    }
}
