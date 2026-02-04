/**
 * @author qkcao
 * @date 2025/12/31 16:01
 */
package com.rental.guard.ai.infrastructure.service;

import io.milvus.param.collection.FieldType;
import io.milvus.grpc.DataType;

import java.util.ArrayList;
import java.util.List;

public class FraudCaseSchema {

    /**
     * 构建欺诈案例的字段Schema
     */
    public static List<FieldType> buildSchema(int vectorDimension) {
        List<FieldType> fields = new ArrayList<>();

        // 1. ID字段
        fields.add(FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build());

        // 2. 向量字段
        fields.add(FieldType.newBuilder()
                .withName("embedding_vector")
                .withDataType(DataType.FloatVector)
                .withDimension(vectorDimension)
                .build());

        // 3. 聊天内容
        fields.add(FieldType.newBuilder()
                .withName("chat_content")
                .withDataType(DataType.VarChar)
                .withMaxLength(65535)
                .build());

        // 4. 是否欺诈
        fields.add(FieldType.newBuilder()
                .withName("is_fraud")
                .withDataType(DataType.Int32)
                .build());

        // 5. 欺诈类型
        fields.add(FieldType.newBuilder()
                .withName("fraud_type")
                .withDataType(DataType.VarChar)
                .withMaxLength(255)
                .build());

        // 6. 置信度分数
        fields.add(FieldType.newBuilder()
                .withName("confidence_score")
                .withDataType(DataType.Float)
                .build());

        // 7. 创建时间（存储时间戳）
        fields.add(FieldType.newBuilder()
                .withName("create_time")
                .withDataType(DataType.Int64)
                .build());

        return fields;
    }
}
