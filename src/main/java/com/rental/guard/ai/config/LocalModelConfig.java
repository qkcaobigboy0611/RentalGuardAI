/**
 * @author qkcao
 * @date 2025/9/16 18:46
 */
package com.rental.guard.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本地模型配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.local-model")
public class LocalModelConfig {

    /**
     * 是否启用本地模型
     */
    private Boolean enabled = false;

    /**
     * 本地模型服务类型：ollama
     */
    private String serviceType = "ollama";

    /**
     * 本地服务端点
     */
    private String endpoint = "http://localhost:11434";

    /**
     * 模型名称
     */
    private String modelName = "deepseek-r1:7b";

    /**
     * 请求超时时间（毫秒）
     */
    private Integer timeout = 30000;

    /**
     * 最大生成token数
     */
    private Integer maxTokens = 1000;

    /**
     * 温度参数
     */
    private Double temperature = 0.1;

    /**
     * 并发请求数限制
     */
    private Integer maxConcurrency = 5;
}
