/**
 * @author qkcao
 * @date 2025/9/18 06:44
 */
package com.rental.guard.ai.config;

import com.rental.guard.ai.utils.IpCheckTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ToolConfig {
    @Bean
    public ToolCallbackProvider ipCheckToolCallbackProvider(IpCheckTool ipCheckTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(ipCheckTool) // 注册工具对象
                .build();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
