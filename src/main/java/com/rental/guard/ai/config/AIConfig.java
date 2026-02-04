/**
 * @author qkcao
 * @date 2025/9/16 18:50
 */
package com.rental.guard.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI总体配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AIConfig {

    private Boolean enabled = true;

    /**
     * AI服务提供商：deepseek, local（本地模型）
     */
    //private String provider = "deepseek";
    private String provider = "ollama";

    private FraudDetectionConfig fraudDetection = new FraudDetectionConfig();

    @Data
    public static class FraudDetectionConfig {
        private Boolean enabled = true;
        private Boolean async = true;
        private Integer timeout = 30000;
        private Integer contextMessageCount = 100;
    }
}

