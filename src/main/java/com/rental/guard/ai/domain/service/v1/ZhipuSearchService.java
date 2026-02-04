/**
 * @author qkcao
 * @date 2026/2/4 16:06
 */
package com.rental.guard.ai.domain.service.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class ZhipuSearchService {

    @Value("${ai.mcp.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ZhipuSearchService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 异步执行联网搜索
     */
    public String searchInternetAsync(String query, String scenario) {
        try {
            // 针对租房场景优化搜索词
            String refinedQuery = String.format("租房风险分析: %s %s", scenario, query);

            log.info("执行智谱联网搜索: {}", refinedQuery);

            // 构造请求（此处以智谱标准 API 为例，实际可根据具体 SDK 调整）
            try {
                // 构建MCP请求，符合智谱Web Search规范
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("search_query", refinedQuery);
                requestBody.put("content_size", "medium");
                requestBody.put("count", 10);
                requestBody.put("search_recency_filter", "oneYear"); // 设置搜索最近一年的数据

                // 如果需要限定搜索域名，可以添加
                // requestBody.put("search_domain_filter", "www.example.com");

                // 设置请求头
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + apiKey);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                // 发送请求到智谱MCP服务
                ResponseEntity<String> response = restTemplate.exchange(
                        "https://dashscope.aliyuncs.com/api/v1/mcps/zhipu-websearch/sse",
                        HttpMethod.POST,
                        entity,
                        String.class);
                // 5. 处理响应
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    // 1. 解析原始JSON响应
                    JsonNode rootNode = objectMapper.readTree(response.getBody());

                    // 2. 检查是否有错误
                    if (rootNode.has("isError") && rootNode.get("isError").asBoolean()) {
                        return "搜索服务返回错误";
                    }

                    // 3. 提取content数组
                    JsonNode contentArray = rootNode.path("content");
                    if (contentArray.isMissingNode() || !contentArray.isArray()) {
                        return "未找到有效搜索结果";
                    }

                    // 4. 遍历content数组，查找text类型的内容
                    StringBuilder resultBuilder = new StringBuilder();
                    for (JsonNode contentItem : contentArray) {
                        if (contentItem.has("type") && "text".equals(contentItem.get("type").asText())) {
                            String textContent = contentItem.path("text").asText();
                            // textContent是一个包含JSON数组的字符串，需要进一步解析
                            resultBuilder.append(parseSearchResults(textContent));
                        }
                    }
                    return resultBuilder.length() > 0 ?
                            resultBuilder.toString() :
                            "未找到文本类型的搜索结果";
                } else {
                    return String.format("搜索失败，状态码：%s，响应：%s",
                            response.getStatusCode(),
                            response.getBody());
                }


            } catch (Exception e) {
                log.error("调用智谱联网搜索失败: {}", e.getMessage());
                return "搜索失败: " + e.getMessage();
            }

            // 调用 API...
            // String response = restTemplate.postForObject(URL, entity, String.class);

            // 模拟返回搜索到的网页摘要
        } catch (Exception e) {
            log.error("联网搜索失败", e);
            return "暂时无法获取联网搜索数据";
        }
    }

    /**
     * 解析搜索结果的JSON数组
     *
     * @param jsonArrayStr 包含JSON数组的字符串
     * @return 格式化的搜索结果
     */
    private String parseSearchResults(String jsonArrayStr) {
        try {
            // 1. 去除转义字符，解析JSON数组
            String cleanJson = jsonArrayStr.replace("\\\"", "\"");
            // 去掉首尾的引号（如果是字符串形式的JSON）
            if (cleanJson.startsWith("\"") && cleanJson.endsWith("\"")) {
                cleanJson = cleanJson.substring(1, cleanJson.length() - 1);
            }

            // 2. 解析为JSON数组
            List<Map<String, String>> searchResults = objectMapper.readValue(
                    cleanJson,
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class,
                            Map.class
                    )
            );

            // 3. 格式化输出
            StringBuilder formattedResults = new StringBuilder();
            formattedResults.append("共找到 ").append(searchResults.size()).append(" 条搜索结果：\n\n");

            for (int i = 0; i < searchResults.size(); i++) {
                Map<String, String> result = searchResults.get(i);
                formattedResults.append("结果 ").append(i + 1).append(":\n");
                formattedResults.append("标题: ").append(result.get("title")).append("\n");
                formattedResults.append("链接: ").append(result.get("link")).append("\n");
                formattedResults.append("来源: ").append(result.get("media")).append("\n");
                formattedResults.append("内容摘要: ").append(
                        result.get("content").length() > 200 ?
                                result.get("content").substring(0, 200) + "..." :
                                result.get("content")
                ).append("\n");
                formattedResults.append("发布时间: ").append(result.get("publish_date")).append("\n");
                formattedResults.append("---\n");
            }

            return formattedResults.toString();

        } catch (Exception e) {
            return String.format("解析搜索结果详情时发生异常：%s，原始内容：%s",
                    e.getMessage(),
                    jsonArrayStr.substring(0, Math.min(100, jsonArrayStr.length())));
        }
    }
}
