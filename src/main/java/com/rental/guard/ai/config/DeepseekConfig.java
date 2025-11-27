/**
 * @author qkcao
 * @date 2025/9/16 18:49
 */
package com.rental.guard.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云千问配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.deepseek")
public class DeepseekConfig {

    private String apiKey;

    private String endpoint =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

    private String model = "deepseek-r1";

    private Integer timeout = 30000;

    private Integer maxTokens = 1000;

    private Double temperature = 0.1;
}

