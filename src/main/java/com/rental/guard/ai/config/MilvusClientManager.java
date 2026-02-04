/**
 * @author qkcao
 * @date 2025/12/31 16:00
 */
package com.rental.guard.ai.config;

import com.rental.guard.ai.infrastructure.service.FraudCaseSchema;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.FieldData;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.QueryResults;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class MilvusClientManager {

//    @Autowired
//    private MilvusServiceClient milvusClient;

//    @Autowired
//    private MilvusConfig milvusConfig;

//    /**
//     * 检查集合是否存在，不存在则创建
//     */
//    @PostConstruct
//    public void init() {
//        String collectionName = milvusConfig.getCollectionName();
//
//        if (!hasCollection(collectionName)) {
//            createCollection(collectionName);
//            createIndex(collectionName);
//            loadCollection(collectionName);
//            log.info("Milvus集合 {} 初始化完成", collectionName);
//        } else {
//            loadCollection(collectionName);
//            log.info("Milvus集合 {} 已存在，加载成功", collectionName);
//        }
//    }

    private boolean hasCollection(String collectionName) {
//        R<Boolean> response = milvusClient.hasCollection(
//                HasCollectionParam.newBuilder()
//                        .withCollectionName(collectionName)
//                        .build()
//        );
//        return response.getData();
        return false;
    }

    private void createCollection(String collectionName) {
        // 创建Field Schema
//        List<FieldType> fields = FraudCaseSchema.buildSchema(
//                milvusConfig.getVectorDimension()
//        );
        List<FieldType> fields = new ArrayList<>();

        // 创建集合
        CreateCollectionParam createCollectionParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription("欺诈训练案例向量集合")
                .withShardsNum(2)
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .addFieldType(fields.get(0))  // id
                .addFieldType(fields.get(1))  // embedding_vector
                .addFieldType(fields.get(2))  // chat_content
                .addFieldType(fields.get(3))  // is_fraud
                .addFieldType(fields.get(4))  // fraud_type
                .addFieldType(fields.get(5))  // confidence_score
                .addFieldType(fields.get(6))  // create_time
                .build();

//        R<RpcStatus> response = milvusClient.createCollection(createCollectionParam);
//        R<RpcStatus> response = new R<>();
//        if (response.getStatus() != R.Status.Success.getCode()) {
//            //throw new RuntimeException("创建Milvus集合失败: " + response.getMessage());
//        }
    }

    private void createIndex(String collectionName) {
        CreateIndexParam createIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName("embedding_vector")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"nlist\":1024}")
                .withSyncMode(Boolean.TRUE)
                .build();

//        R<RpcStatus> response = milvusClient.createIndex(createIndexParam);
        R<RpcStatus> response = null;
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("创建索引失败: " + response.getMessage());
        }
    }

    private void loadCollection(String collectionName) {
        LoadCollectionParam loadCollectionParam = LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();

//        R<RpcStatus> response = milvusClient.loadCollection(loadCollectionParam);
        R<RpcStatus> response = new R<>();
        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("加载集合失败: " + response.getMessage());
        }
    }

    /**
     * 批量插入向量数据
     */
    public void insertBatch(List<InsertParam.Field> fields) {
        InsertParam insertParam = InsertParam.newBuilder()
//                .withCollectionName(milvusConfig.getCollectionName())
                .withFields(fields)
                .build();

//        R<MutationResult> response = milvusClient.insert(insertParam);
        R<MutationResult> response = new R<>();
        if (response.getStatus() != R.Status.Success.getCode()) {
            log.error("插入数据失败: {}", response.getMessage());
        }
    }

    /**
     * 向量搜索
     */
    public SearchResultsWrapper search(List<Float> queryVector, int topK) {
        List<List<Float>> vectors = new ArrayList<>();
        vectors.add(queryVector);

        SearchParam searchParam = SearchParam.newBuilder()
//                .withCollectionName(milvusConfig.getCollectionName())
                .withMetricType(MetricType.COSINE)
                .withTopK(topK)
                .withVectors(vectors)
                .withVectorFieldName("embedding_vector")
                .withParams("{\"nprobe\":10}")
                .withOutFields(List.of("id", "chat_content", "is_fraud", "fraud_type", "confidence_score"))
                .withExpr("is_fraud == 1")  // 只检索欺诈案例（可选）
                .build();

//        R<SearchResults> response = milvusClient.search(searchParam);
        R<SearchResults> response = new R<>();
        if (response.getStatus() != R.Status.Success.getCode()) {
            log.error("向量搜索失败: {}", response.getMessage());
            return null;
        }

        return new SearchResultsWrapper(response.getData().getResults());
    }

    /**
     * 根据ID查询
     */
    public List<FieldData> queryByIds(List<Integer> ids) {
        String expr = "id in [" + ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";

        QueryParam queryParam = QueryParam.newBuilder()
//                .withCollectionName(milvusConfig.getCollectionName())
                .withExpr(expr)
                .withOutFields(List.of("id", "chat_content", "is_fraud", "fraud_type"))
                .build();

//        R<QueryResults> response = milvusClient.query(queryParam);
        R<QueryResults> response = new R<>();
        if (response.getStatus() != R.Status.Success.getCode()) {
            log.error("查询失败: {}", response.getMessage());
            return new ArrayList<>();
        }

        return response.getData().getFieldsDataList();
    }

    @PreDestroy
    public void cleanup() {
//        if (milvusClient != null) {
//            milvusClient.close();
//            log.info("Milvus客户端已关闭");
//        }
    }
}
