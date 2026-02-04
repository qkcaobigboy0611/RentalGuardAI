/**
 * @author qkcao
 * @date 2025/12/31 16:18
 */
package com.rental.guard.ai.infrastructure.service;

import com.rental.guard.ai.infrastructure.mapper.FraudTrainingCaseMapper;
import com.rental.guard.ai.infrastructure.po.PoFraudTrainingCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TrainingCaseRetrieverImpl implements TrainingCaseRetriever {

    private final VectorSearchService vectorSearchService;
    private final FraudTrainingCaseMapper fraudTrainingCaseMapper;
    private final KeywordExtractor keywordExtractor;
    private final TextAnalyzer textAnalyzer;

    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    // 关键词权重配置
    private static final Map<String, Double> KEYWORD_WEIGHTS = Map.of(
            "微信", 2.0,
            "QQ", 2.0,
            "转账", 1.8,
            "投资", 1.8,
            "老板", 1.5,
            "长租", 1.5,
            "保证金", 1.7,
            "押金", 1.3,
            "见面", 0.8,
            "看房", 0.8
    );

    /**
     * 综合检索：向量 + 关键词 + 规则
     */
    @Override
    public List<PoFraudTrainingCase> retrieve(String query, int topK) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 并行执行多种检索
            CompletableFuture<List<PoFraudTrainingCase>> vectorFuture =
                    CompletableFuture.supplyAsync(() ->
                            vectorSearchService.searchSimilarCases(query, topK * 2), executor);

            CompletableFuture<List<PoFraudTrainingCase>> keywordFuture =
                    CompletableFuture.supplyAsync(() ->
                            retrieveByKeywords(query, topK), executor);

            CompletableFuture<List<PoFraudTrainingCase>> ruleFuture =
                    CompletableFuture.supplyAsync(() ->
                            retrieveByRules(query, topK), executor);

            // 2. 等待所有结果
            List<PoFraudTrainingCase> vectorResults = vectorFuture.get();
            List<PoFraudTrainingCase> keywordResults = keywordFuture.get();
            List<PoFraudTrainingCase> ruleResults = ruleFuture.get();

            // 3. 结果融合和去重
            List<PoFraudTrainingCase> mergedResults =
                    mergeAndRankResults(vectorResults, keywordResults, ruleResults, query);

            // 4. 截取topK
            List<PoFraudTrainingCase> finalResults = mergedResults.stream()
                    .limit(topK)
                    .collect(Collectors.toList());

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("综合检索完成 - 查询: {}, 结果数: {}, 耗时: {}ms",
                    StringUtils.abbreviate(query, 50), finalResults.size(), costTime);

            return finalResults;

        } catch (Exception e) {
            log.error("综合检索失败，降级到向量检索", e);
            // 降级处理
            return vectorSearchService.searchSimilarCases(query, topK);
        }
    }

    /**
     * 关键词检索
     */
    @Override
    public List<PoFraudTrainingCase> retrieveByKeywords(String query, int topK) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 提取关键词
            List<String> keywords = keywordExtractor.extractKeywords(query);
            if (keywords.isEmpty()) {
                return new ArrayList<>();
            }

            // 3. 查询数据库
            List<PoFraudTrainingCase> cases = fraudTrainingCaseMapper
                    .selectByPlatformKeywords(keywords, topK);

            // 4. 按关键词匹配度排序
            cases.sort((a, b) -> {
                double scoreA = calculateKeywordScore(a.getChatContent(), keywords);
                double scoreB = calculateKeywordScore(b.getChatContent(), keywords);
                return Double.compare(scoreB, scoreA); // 降序
            });

            long costTime = System.currentTimeMillis() - startTime;
            log.debug("关键词检索完成 - 关键词: {}, 结果数: {}, 耗时: {}ms",
                    keywords, cases.size(), costTime);

            return cases;

        } catch (Exception e) {
            log.error("关键词检索失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 混合检索
     */
    @Override
    public List<PoFraudTrainingCase> hybridRetrieve(String query, int topK) {
        return retrieve(query, topK);
    }

    /**
     * 根据欺诈类型检索
     */
    @Override
    public List<PoFraudTrainingCase> retrieveByFraudType(String fraudType, int topK) {
        try {
            return fraudTrainingCaseMapper.selectByFraudType(fraudType, topK);
        } catch (Exception e) {
            log.error("按欺诈类型检索失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取高置信度案例
     */
    @Override
    public List<PoFraudTrainingCase> retrieveHighConfidenceCases(BigDecimal minConfidence, int topK) {
        try {
            return fraudTrainingCaseMapper.selectHighConfidenceCases(minConfidence, topK);
        } catch (Exception e) {
            log.error("获取高置信度案例失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * ========== 私有方法 ==========
     */

    /**
     * 规则检索
     */
    private List<PoFraudTrainingCase> retrieveByRules(String query, int topK) {
        List<PoFraudTrainingCase> results = new ArrayList<>();

        try {
            // 1. 分析文本特征
            TextAnalyzer.Features features = textAnalyzer.analyze(query);

            // 2. 根据特征检索
            if (features.isContainsExternalPlatform()) {
                results.addAll(retrieveExternalPlatformCases(topK));
            }

            if (features.isContainsMoneyRelated()) {
                results.addAll(retrieveMoneyRelatedCases(topK));
            }

            if (features.isContainsUrgentWords()) {
                results.addAll(retrieveUrgentCases(topK));
            }

            // 去重
            return results.stream()
                    .distinct()
                    .limit(topK)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("规则检索失败", e);
            return new ArrayList<>();
        }
    }


    /**
     * 计算关键词得分
     */
    private double calculateKeywordScore(String content, List<String> keywords) {
        if (StringUtils.isBlank(content)) {
            return 0.0;
        }

        double score = 0.0;
        String lowerContent = content.toLowerCase();

        for (String keyword : keywords) {
            String lowerKeyword = keyword.toLowerCase();

            // 计算出现频率
            int frequency = StringUtils.countMatches(lowerContent, lowerKeyword);
            if (frequency > 0) {
                double weight = KEYWORD_WEIGHTS.getOrDefault(keyword, 1.0);
                score += frequency * weight;
            }
        }

        // 归一化处理
        return score / (content.length() * 0.1 + 1);
    }

    /**
     * 结果融合和排序
     */
    private List<PoFraudTrainingCase> mergeAndRankResults(
            List<PoFraudTrainingCase> vectorResults,
            List<PoFraudTrainingCase> keywordResults,
            List<PoFraudTrainingCase> ruleResults,
            String query) {

        // 1. 合并所有结果
        Map<Integer, PoFraudTrainingCase> mergedMap = new LinkedHashMap<>();

        // 向量结果（权重最高）
        addResultsWithWeight(mergedMap, vectorResults, 1.0);

        // 关键词结果
        addResultsWithWeight(mergedMap, keywordResults, 0.8);

        // 规则结果
        addResultsWithWeight(mergedMap, ruleResults, 0.6);

        // 2. 计算综合得分并排序
        List<CaseScore> scoredCases = new ArrayList<>();
        for (PoFraudTrainingCase trainingCase : mergedMap.values()) {
            double score = calculateCompositeScore(trainingCase, query);
            scoredCases.add(new CaseScore(trainingCase, score));
        }

        // 3. 按得分排序
        scoredCases.sort((a, b) -> Double.compare(b.score, a.score));

        return scoredCases.stream()
                .map(cs -> cs.trainingCase)
                .collect(Collectors.toList());
    }

    /**
     * 计算综合得分
     */
    private double calculateCompositeScore(PoFraudTrainingCase trainingCase, String query) {
        double score = 0.0;

        // 1. 置信度分数（如果有）
        if (trainingCase.getConfidenceScore() != null) {
            score += trainingCase.getConfidenceScore().doubleValue() * 0.3;
        }

        // 2. 是否欺诈案例（欺诈案例权重更高）
        if (trainingCase.getIsFraud() != null && trainingCase.getIsFraud() == 1) {
            score += 0.4;
        }

        // 3. 内容相似度（通过向量搜索已经考虑了）
        // 这里可以添加其他特征分数

        return score;
    }

    private void addResultsWithWeight(Map<Integer, PoFraudTrainingCase> map,
                                      List<PoFraudTrainingCase> results,
                                      double weight) {
        for (PoFraudTrainingCase trainingCase : results) {
            if (!map.containsKey(trainingCase.getId())) {
                map.put(trainingCase.getId(), trainingCase);
            }
        }
    }

    /**
     * 检索外部平台相关案例
     */
    private List<PoFraudTrainingCase> retrieveExternalPlatformCases(int topK) {
        List<String> platformKeywords = Arrays.asList("微信", "QQ", "钉钉", "telegram", "whatsapp");
        String condition = platformKeywords.stream()
                .map(keyword -> "chat_content LIKE '%" + keyword + "%'")
                .collect(Collectors.joining(" OR "));

        return fraudTrainingCaseMapper.selectByPlatformKeywords(platformKeywords, topK);
    }

    /**
     * 检索金钱相关案例
     */
    private List<PoFraudTrainingCase> retrieveMoneyRelatedCases(int topK) {
        List<String> moneyKeywords = Arrays.asList("转账", "汇款", "付款", "定金", "押金", "保证金");
        String condition = moneyKeywords.stream()
                .map(keyword -> "chat_content LIKE '%" + keyword + "%'")
                .collect(Collectors.joining(" OR "));

        return fraudTrainingCaseMapper.selectByPlatformKeywords(moneyKeywords, topK);
    }

    /**
     * 检索紧急词汇相关案例
     */
    private List<PoFraudTrainingCase> retrieveUrgentCases(int topK) {
        List<String> urgentKeywords = Arrays.asList("尽快", "马上", "立即", "紧急", "快点", "急");
        String condition = urgentKeywords.stream()
                .map(keyword -> "chat_content LIKE '%" + keyword + "%'")
                .collect(Collectors.joining(" OR "));

        return fraudTrainingCaseMapper.selectByPlatformKeywords(urgentKeywords, topK);
    }

    /**
     * 内部类：案例得分
     */
    private static class CaseScore {
        PoFraudTrainingCase trainingCase;
        double score;

        CaseScore(PoFraudTrainingCase trainingCase, double score) {
            this.trainingCase = trainingCase;
            this.score = score;
        }
    }
}
