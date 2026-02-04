/**
 * @author qkcao
 * @date 2025/12/31 16:07
 */
package com.rental.guard.ai.infrastructure.service;

import com.rental.guard.ai.config.ArgConfig;
import com.rental.guard.ai.infrastructure.po.PoFraudTrainingCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class EmbeddingService {
    @Autowired
    private ArgConfig argConfig;

    // 调用阿里云百炼向量模型API
    public List<Float> getEmbedding(String text) {
        try {
            // 构建请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + argConfig.getApiKey());

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "text-embedding-v4");
            requestBody.put("input", text);
            requestBody.put("dimensions", 1024);
            requestBody.put("encoding_format", "float");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 发送请求
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    argConfig.getEndpoint(),
                    entity,
                    Map.class
            );

            // 解析响应
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
                if (data != null && !data.isEmpty()) {
                    List<Double> embeddingDoubles = (List<Double>) data.get(0).get("embedding");
                    // 转换为Float列表
                    List<Float> embeddingFloats = new ArrayList<>();
                    for (Double d : embeddingDoubles) {
                        embeddingFloats.add(d.floatValue());
                    }
                    return embeddingFloats;
                }
            }

            throw new RuntimeException("获取向量失败: " + response.getStatusCode());
        } catch (Exception e) {
            log.error("调用向量模型API失败", e);
            throw new RuntimeException("获取文本向量失败", e);
        }
    }

    // 计算余弦相似度
    private float cosineSimilarity(List<Float> vectorA, List<Float> vectorB) {
        if (vectorA == null || vectorB == null || vectorA.size() != vectorB.size()) {
            return 0;
        }

        float dotProduct = 0;
        float normA = 0;
        float normB = 0;

        for (int i = 0; i < vectorA.size(); i++) {
            dotProduct += vectorA.get(i) * vectorB.get(i);
            normA += vectorA.get(i) * vectorA.get(i);
            normB += vectorB.get(i) * vectorB.get(i);
        }

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    /**
     * 结果融合策略
     */
    private List<PoFraudTrainingCase> mergeAndRankCases(
            List<PoFraudTrainingCase> vectorCases,
            List<PoFraudTrainingCase> keywordCases,
            int topK) {

        // 使用优先队列进行融合排序
        return CaseRanker.mergeAndRank(vectorCases, keywordCases, topK);
    }


    /**
     * 批量获取向量（优化性能）
     */
    //@Async("embeddingExecutor")
    public CompletableFuture<List<List<Float>>> getEmbeddingsBatch(List<String> texts) {
        long startTime = System.currentTimeMillis();

        try {
            // 构建请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + argConfig.getApiKey());

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "text-embedding-v4");
            requestBody.put("input", texts);
            requestBody.put("dimensions", 1024);
            requestBody.put("encoding_format", "float");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 发送请求
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    argConfig.getEndpoint(),
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");

                List<List<Float>> allEmbeddings = new ArrayList<>();
                for (Map<String, Object> item : data) {
                    List<Double> embeddingDoubles = (List<Double>) item.get("embedding");
                    List<Float> embeddingFloats = new ArrayList<>(embeddingDoubles.size());

                    for (Double d : embeddingDoubles) {
                        embeddingFloats.add(d.floatValue());
                    }

                    allEmbeddings.add(embeddingFloats);
                }

                long costTime = System.currentTimeMillis() - startTime;
                log.info("批量获取向量成功 - 数量: {}, 平均耗时: {}ms/个",
                        texts.size(), costTime / texts.size());

                return CompletableFuture.completedFuture(allEmbeddings);
            }

            throw new RuntimeException("批量获取向量失败: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("批量调用向量模型API失败", e);
            return CompletableFuture.failedFuture(e);
        }
    }
}
