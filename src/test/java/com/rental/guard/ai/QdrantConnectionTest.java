/**
 * @author qkcao
 * @date 2026/1/27 14:40
 */
package com.rental.guard.ai;


import com.google.common.util.concurrent.ListenableFuture;
import com.rental.guard.ai.domain.service.FraudDetectionService;
import com.rental.guard.ai.domain.service.v1.QdrantService;
import com.rental.guard.ai.infrastructure.mapper.FraudTrainingCaseMapper;
import com.rental.guard.ai.infrastructure.po.PoFraudTrainingCase;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

@Slf4j
@SpringBootTest
public class QdrantConnectionTest {

    @Autowired
    private QdrantService qdrantService;
    @Autowired
    private FraudTrainingCaseMapper fraudTrainingCaseMapper;
    @Autowired
    private FraudDetectionService fraudDetectionService;

    public static void main(String[] args) {
        System.out.println("=== Qdrant Java 客户端连接测试 ===");

        String host = "localhost";
        boolean useGrpc = true; // 使用gRPC
        int httpPort = 6333;    // HTTP端口
        int grpcPort = 6334;    // gRPC端口
        int timeoutSeconds = 10;

        QdrantClient client = null;

        try {
            // 1. 创建客户端
            System.out.println("1. 创建客户端连接...");

            if (useGrpc) {
                // 使用gRPC客户端（注意端口是6334）
                System.out.println("   使用gRPC客户端，端口: " + grpcPort);
                QdrantGrpcClient grpcClient = QdrantGrpcClient.newBuilder(host, grpcPort, false)
                        .build();
                client = new QdrantClient(grpcClient);

                ListenableFuture<List<String>> listListenableFuture = client.listCollectionsAsync();

                // 创建collection
                client.createCollectionAsync("my_collection1",
                        Collections.VectorParams.newBuilder()
                                .setDistance(Collections.Distance.Cosine)
                                .setSize(4)
                                .build()).get();
                // 插入向量
                HashMap<String, JsonWithInt.Value> map1 = new HashMap<>();
                map1.put("color", value("red"));
                map1.put("rand_number", value(32));
                HashMap<String, JsonWithInt.Value> map2 = new HashMap<>();
                map2.put("color", value("blue"));
                map2.put("rand_number", value(53));
                map2.put("extra_field", value(true));
                List<Points.PointStruct> points = new ArrayList<>();
                points.add(Points.PointStruct.newBuilder()
                        .setId(id(1))
                        .setVectors(vectors(0.32f, 0.52f, 0.21f, 0.52f))
                        .putAllPayload(map1)
                        .build());
                points.add(Points.PointStruct.newBuilder()
                        .setId(id(2))
                        .setVectors(vectors(0.42f, 0.52f, 0.67f, 0.632f))
                        .putAllPayload(map2)
                        .build());
                Points.UpdateResult updateResult = client.upsertAsync("my_collection", points).get();
                // 搜索相似结果
                List<Points.ScoredPoint> points1 = client.searchAsync(
                        Points.SearchPoints.newBuilder()
                                .setCollectionName("my_collection")
                                .addAllVector(Arrays.asList(0.6235f, 0.123f, 0.532f, 0.123f))
                                .setLimit(5)
                                .build()).get();
                System.out.println(points1);


            }
            System.out.println("   ✅ 客户端创建成功");

            // 2. 测试连接 - 简单健康检查
        } catch (Exception e) {
            System.out.println("❌ 测试过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (client != null) {
                try {
                    client.close();
                    System.out.println("🔒 客户端连接已关闭");
                } catch (Exception e) {
                    System.out.println("⚠️ 关闭客户端时出错: " + e.getMessage());
                }
            }
        }
    }

    @Test
    public void test() {
        List<PoFraudTrainingCase> trainingCases = fraudTrainingCaseMapper.getALlPoFraudTrainingCase();
        for (PoFraudTrainingCase trainingCase : trainingCases) {
            List<Float> embedding = fraudDetectionService.getEmbedding(trainingCase.getChatContent());
            qdrantService.storeTrainingCase(trainingCase, embedding);
        }
    }
}
