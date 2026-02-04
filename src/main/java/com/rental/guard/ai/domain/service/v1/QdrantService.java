/**
 * @author qkcao
 * @date 2026/1/27 17:01
 */
package com.rental.guard.ai.domain.service.v1;

import com.google.common.util.concurrent.ListenableFuture;
import com.rental.guard.ai.infrastructure.po.PoFraudTrainingCase;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class QdrantService {


    String host = "localhost";
    boolean useGrpc = true; // 使用gRPC
    int httpPort = 6333;    // HTTP端口
    int grpcPort = 6334;    // gRPC端口
    int timeoutSeconds = 10;

    private QdrantClient client;
    private String collectionName = "fraud_training_cases";

    @PostConstruct
    public void init() {
        try {
            QdrantGrpcClient grpcClient = QdrantGrpcClient.newBuilder(host, grpcPort, false)
                    .build();
            client = new QdrantClient(grpcClient);

            // 创建collection
            client.createCollectionAsync(collectionName,
                    Collections.VectorParams.newBuilder()
                            .setDistance(Collections.Distance.Cosine)
                            .setSize(1024)
                            .build()).get();
        } catch (Exception e) {
            log.error("初始化Qdrant客户端失败", e);
        }
    }

    /**
     * 存储训练案例到向量数据库
     */
    public void storeTrainingCase(PoFraudTrainingCase trainingCase, List<Float> vector) {
        try {
            if (trainingCase == null || vector == null || vector.isEmpty()) {
                log.error("训练案例或向量为空，无法存储");
                return;
            }

            // 构建payload（元数据）
            Map<String, Value> payload = new HashMap<>();

            // 添加必填字段
            payload.put("id", Value.newBuilder().setIntegerValue(trainingCase.getId()).build());
            payload.put("chat_content", Value.newBuilder().setStringValue(
                    trainingCase.getChatContent() != null ? trainingCase.getChatContent() : ""
            ).build());

            payload.put("is_fraud", Value.newBuilder().setIntegerValue(
                    trainingCase.getIsFraud() != null ? trainingCase.getIsFraud() : 0
            ).build());

            // 添加可选字段
            if (trainingCase.getFraudType() != null && !trainingCase.getFraudType().trim().isEmpty()) {
                payload.put("fraud_type", Value.newBuilder()
                        .setStringValue(trainingCase.getFraudType())
                        .build());
            }

            if (trainingCase.getSource() != null && !trainingCase.getSource().trim().isEmpty()) {
                payload.put("source", Value.newBuilder()
                        .setStringValue(trainingCase.getSource())
                        .build());
            }

            if (trainingCase.getDescription() != null && !trainingCase.getDescription().trim().isEmpty()) {
                payload.put("description", Value.newBuilder()
                        .setStringValue(trainingCase.getDescription())
                        .build());
            }

            if (trainingCase.getConfidenceScore() != null) {
                payload.put("confidence_score", Value.newBuilder()
                        .setDoubleValue(trainingCase.getConfidenceScore().doubleValue())
                        .build());
            }

            if (trainingCase.getCreateTime() != null) {
                payload.put("create_time", Value.newBuilder()
                        .setStringValue(trainingCase.getCreateTime().toString())
                        .build());
            }

            if (trainingCase.getUpdateTime() != null) {
                payload.put("update_time", Value.newBuilder()
                        .setStringValue(trainingCase.getUpdateTime().toString())
                        .build());
            }

            // 构建向量点
            Points.PointStruct.Builder pointBuilder = Points.PointStruct.newBuilder()
                    .setId(Points.PointId.newBuilder()
                            .setNum(trainingCase.getId())
                            .build())
                    .setVectors(Points.Vectors.newBuilder()
                            .setVector(Points.Vector.newBuilder()
                                    .addAllData(vector)
                                    .build())
                            .build());

            // 添加payload
            pointBuilder.putAllPayload(payload);

            Points.PointStruct point = pointBuilder.build();

            // 执行插入操作
            client.upsertAsync(
                    Points.UpsertPoints.newBuilder()
                            .setCollectionName(collectionName)
                            .addPoints(point)
                            .setWait(true) // 等待写入完成
                            .build()
            ).get(); // 同步等待完成

            log.info("成功存储训练案例到向量数据库，ID: {}, 向量维度: {}",
                    trainingCase.getId(), vector.size());

        } catch (Exception e) {
            log.error("存储训练案例到向量数据库失败，ID: {}, 错误信息: {}",
                    trainingCase != null ? trainingCase.getId() : "null",
                    e.getMessage(), e);
            throw new RuntimeException("存储向量数据失败", e);
        }
    }

    /**
     * 等价于 vectorStore.similaritySearch(
     *             SearchRequest.query("租房合同").withTopK(1)).isEmpty()
     * 返回 true 表示“没有相似文档”
     */
    public boolean isEmptySimilarDocs(String queryText,
                                             String collectionName,
                                      List<Float> aaaa) throws Exception {


        // 2. 构造 Qdrant 搜索参数
        Points.SearchPoints searchReq = Points.SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllVector(aaaa)  // Guava Floats.asList 或自己 for-loop
                .setLimit(1)                          // Top 1// 需要文档字段
                .build();

        // 3. 执行 ANN 搜索
        List<Points.ScoredPoint> hits = client.searchAsync(searchReq).get();

        // 4. 判断是否为空
        return hits == null || hits.isEmpty();
    }

    public ListenableFuture<List<Points.ScoredPoint>> searchAsync(Points.SearchPoints searchReq) {
        ListenableFuture<List<Points.ScoredPoint>> listListenableFuture = client.searchAsync(searchReq);
        return listListenableFuture;
    }
}
