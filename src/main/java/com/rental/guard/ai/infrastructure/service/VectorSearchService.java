/**
 * @author qkcao
 * @date 2025/12/31 16:06
 */
package com.rental.guard.ai.infrastructure.service;

import com.rental.guard.ai.config.MilvusClientManager;
import com.rental.guard.ai.infrastructure.po.PoFraudTrainingCase;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class VectorSearchService {

    @Autowired
    private MilvusClientManager milvusClient;
    @Autowired
    private EmbeddingService embeddingService;

    /**
     * 搜索相似案例
     */
    public List<PoFraudTrainingCase> searchSimilarCases(String query, int topK) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 获取查询文本的向量
            List<Float> queryVector = embeddingService.getEmbedding(query);

            // 2. Milvus向量搜索
            SearchResultsWrapper searchResults = milvusClient.search(queryVector, topK);
            if (searchResults == null) {
                log.warn("Milvus搜索返回空结果");
                return new ArrayList<>();
            }

            // 3. 解析搜索结果
            List<PoFraudTrainingCase> similarCases = parseSearchResults(searchResults);

            long costTime = System.currentTimeMillis() - startTime;
            log.info("向量搜索完成 - 查询文本: {}, 返回数量: {}, 耗时: {}ms",
                    query, similarCases.size(), costTime);

            return similarCases;

        } catch (Exception e) {
            log.error("向量搜索失败", e);
            // 降级：返回空列表，由上层处理
            return new ArrayList<>();
        }
    }

    /**
     * 混合搜索：向量搜索 + 关键词过滤
     */
    public List<PoFraudTrainingCase> hybridSearch(String query, int topK, List<String> keywords) {
        List<PoFraudTrainingCase> vectorResults = searchSimilarCases(query, topK * 2);

        // 如果有关键词，进行过滤
        if (keywords != null && !keywords.isEmpty()) {
            return vectorResults.stream()
                    .filter(caseItem -> containsKeywords(caseItem.getChatContent(), keywords))
                    .limit(topK)
                    .collect(Collectors.toList());
        }

        return vectorResults.stream()
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * 解析Milvus搜索结果
     */
    private List<PoFraudTrainingCase> parseSearchResults(SearchResultsWrapper searchResults) {
        List<PoFraudTrainingCase> cases = new ArrayList<>();

        try {
            for (int i = 0; i < searchResults.getRowRecords().size(); i++) {
                SearchResultsWrapper.IDScore idScore = searchResults.getIDScore(i).get(0);
                PoFraudTrainingCase trainingCase = PoFraudTrainingCase.builder()
                        .id(Integer.parseInt(idScore.get("id").toString()))
                        .chatContent(idScore.get("chat_content").toString())
                        .isFraud(Integer.parseInt(idScore.get("is_fraud").toString()))
                        .fraudType(idScore.get("fraud_type").toString())
                        .confidenceScore(new java.math.BigDecimal(idScore.get("confidence_score").toString()))
                        .build();

                // 设置相似度分数（可以存储到临时字段）
                // trainingCase.setSimilarityScore(similarity);

                cases.add(trainingCase);
            }
        } catch (Exception e) {
            log.error("解析搜索结果失败", e);
        }

        return cases;
    }


    private Object getFieldValue(Map<String, List<?>> fieldMap, String fieldName, int index) {
        List<?> values = fieldMap.get(fieldName);
        if (values != null && index < values.size()) {
            return values.get(index);
        }
        return null;
    }


    private boolean containsKeywords(String content, List<String> keywords) {
        String lowerContent = content.toLowerCase();
        return keywords.stream()
                .anyMatch(keyword -> lowerContent.contains(keyword.toLowerCase()));
    }
}
