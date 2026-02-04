/**
 * @author qkcao
 * @date 2025/12/31 15:54
 */
package com.rental.guard.ai.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
//@ConfigurationProperties(prefix = "milvus")
@Data
public class MilvusConfig {
    private String host;
    private Integer port;
    private String database;
    private String username;
    private String password;

    // 集合配置
    private String collectionName;
    private Integer vectorDimension;
    private Integer maxCollectionSize;

    // 检索配置
    private Integer searchTopK;
    private String metricType;
    private Integer nProbe;

//    @Bean
//    public MilvusServiceClient milvusClient() {
//        ConnectParam connectParam = ConnectParam.newBuilder()
//                .withHost(host)
//                .withPort(port)
//                .withDatabaseName(database)
//                .withAuthorization(username, password)
//                .build();
//
//        return new MilvusServiceClient(connectParam);
//    }
}
