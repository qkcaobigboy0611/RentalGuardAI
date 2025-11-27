/**
 * @author qkcao
 * @date 2025/9/18 18:26
 */
package com.rental.guard.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.rag")
public class ArgConfig {
    private String apiKey;

    private String endpoint =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

}

