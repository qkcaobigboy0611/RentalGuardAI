/**
 * @author qkcao
 * @date 2025/9/17 19:02
 */
package com.rental.guard.ai.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class IpCheckTool {

    @Value("${ai.mcp.endpoint}")
    private String mcpServerUrl;

    @Value("${ai.mcp.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public IpCheckTool(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }


    @Tool(name = "ipCheck", description = "查询IP地址的归属地信息")
    public String ipCheck(@Parameter(description = "要查询的IP地址") String ipAddress) {
        try {
            // 构建MCP请求
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ip", ipAddress);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 发送请求到MCP服务
            ResponseEntity<String> response = restTemplate.exchange(
                    mcpServerUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // 解析MCP响应
                JsonNode jsonNode = new ObjectMapper().readTree(response.getBody());
                // 提取关键信息
                String country = jsonNode.path("country").asText("未知");
                String region = jsonNode.path("region").asText("未知");
                String city = jsonNode.path("city").asText("未知");
                String isp = jsonNode.path("isp").asText("未知");

                return String.format("IP地址 %s 位于 %s%s%s，运营商为 %s",
                        ipAddress, country, region, city, isp);
            } else {
                return String.format("无法查询IP地址 %s 的归属地信息", ipAddress);
            }
        } catch (Exception e) {
            log.error("调用MCP服务失败: {}", e.getMessage());
            return String.format("查询IP地址 %s 归属地时发生错误: %s", ipAddress, e.getMessage());
        }
    }
}
