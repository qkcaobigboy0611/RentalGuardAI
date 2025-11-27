/**
 * @author qkcao
 * @date 2025/9/16 18:48
 */
package com.rental.guard.ai.domain.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.rental.guard.ai.config.DeepseekConfig;
import com.rental.guard.ai.domain.dto.AIAnalysisRequest;
import com.rental.guard.ai.domain.dto.AIAnalysisResult;
import com.rental.guard.ai.domain.dto.DeepseekRequest;
import com.rental.guard.ai.domain.dto.DeepseekResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * DeepseekAI分析服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeepseekAnalysisServiceImpl implements AIAnalysisService {

    private final DeepseekConfig deepseekConfig;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public AIAnalysisResult analyze(AIAnalysisRequest request) {
        if (!isAvailable()) {
            return AIAnalysisResult.failure("deepseek API配置不可用", 0L);
        }
        long startTime = System.currentTimeMillis();
        try {
            // 构建deepseek请求
            DeepseekRequest deepseekRequest = DeepseekRequest.create(deepseekConfig.getModel(), buildPrompt(request),
                    request.getMaxTokens() != null ? request.getMaxTokens() : deepseekConfig.getMaxTokens(),
                    request.getTemperature() != null ? request.getTemperature()
                            : deepseekConfig.getTemperature());

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + deepseekConfig.getApiKey());
            headers.set("X-DashScope-SSE", "disable");

            HttpEntity<DeepseekRequest> entity = new HttpEntity<>(deepseekRequest, headers);

            log.info("调用Deepseek API开始，请求: {}", JSON.toJSONString(deepseekRequest));

            // 发送请求，先获取原始字符串响应
            ResponseEntity<String> response = restTemplate.exchange(deepseekConfig.getEndpoint(),
                    HttpMethod.POST, entity, String.class);

            long costTime = System.currentTimeMillis() - startTime;

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String rawResponse = response.getBody();
                log.info("deepseek API调用成功，耗时: {}ms, 原始响应: {}", costTime, rawResponse);

                try {
                    // 解析原始JSON响应
                    DeepseekResponse deepseekResponse = JSON.parseObject(rawResponse, DeepseekResponse.class);

                    // 尝试获取响应文本
                    String responseText = deepseekResponse.getText();
                    if (responseText == null || responseText.isEmpty()) {
                        // 尝试从原始JSON中直接提取文本内容
                        responseText = extractTextFromRawResponse(rawResponse);
                        if (responseText == null || responseText.isEmpty()) {
                            log.warn("deepseek API响应文本为空，原始响应: {}", rawResponse);
                            return AIAnalysisResult.failure("千问API响应内容为空", costTime);
                        }
                    }

                    return AIAnalysisResult.success(responseText, deepseekResponse.getTotalTokens(), costTime);

                } catch (Exception e) {
                    log.error("解析deepseek API响应失败，原始响应: {}", rawResponse, e);
                    return AIAnalysisResult.failure("解析千问API响应失败: " + e.getMessage(), costTime);
                }
            } else {
                log.error("deepseek API调用失败，状态码: {}", response.getStatusCode());
                return AIAnalysisResult.failure("deepseek API调用失败", costTime);
            }

        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("deepseek API调用异常，耗时: {}ms", costTime, e);
            return AIAnalysisResult.failure("deepseek API调用异常: " + e.getMessage(), costTime);
        }
    }

    @Override
    public boolean isAvailable() {
        return StringUtils.isNotBlank(deepseekConfig.getApiKey())
                && StringUtils.isNotBlank(deepseekConfig.getEndpoint());
    }

    @Override
    public String getServiceType() {
        return "deepseek";
    }

    private String buildPrompt(AIAnalysisRequest request) {
        String content;
        if (StringUtils.isNotBlank(request.getPrompt())) {
            content = request.getPrompt().replace("{content}", request.getContent());
        } else {
            content = request.getContent();
        }
        String basePrompt = "请分析以下内容: " + content;
        // 如果有IP地址，添加提示让模型可以使用ipCheck工具
        if (StringUtils.isNotBlank(request.getIp1()) && StringUtils.isNotBlank(request.getIp2())) {
            basePrompt += "\n\n如果需要了解用户的位置信息，可以使用ipCheck工具查询IP地址 " +
                    request.getIp1() + "和" + request.getIp2() + " 的详细地址，并将详细地址放在上面JSON格式分析结果中的ip1_address和ip2_address";
        }
        return basePrompt;
    }

    /**
     * 从原始JSON响应中提取文本内容
     */
    private String extractTextFromRawResponse(String rawResponse) {
        try {
            // 使用通用的JSON解析方式提取可能的文本字段
            com.alibaba.fastjson2.JSONObject jsonObject = JSON.parseObject(rawResponse);

            // 尝试从不同可能的字段提取文本
            if (jsonObject.containsKey("output")) {
                com.alibaba.fastjson2.JSONObject output = jsonObject.getJSONObject("output");
                if (output != null) {
                    // 尝试 text 字段
                    if (output.containsKey("text") && output.getString("text") != null) {
                        return output.getString("text");
                    }
                    // 尝试 message 字段
                    if (output.containsKey("message")) {
                        com.alibaba.fastjson2.JSONObject message = output.getJSONObject("message");
                        if (message != null && message.containsKey("content")) {
                            return message.getString("content");
                        }
                    }
                    // 尝试 choices 字段（GPT风格）
                    if (output.containsKey("choices")) {
                        com.alibaba.fastjson2.JSONArray choices = output.getJSONArray("choices");
                        if (choices != null && !choices.isEmpty()) {
                            com.alibaba.fastjson2.JSONObject choice = choices.getJSONObject(0);
                            if (choice.containsKey("message")) {
                                com.alibaba.fastjson2.JSONObject message = choice.getJSONObject("message");
                                if (message != null && message.containsKey("content")) {
                                    return message.getString("content");
                                }
                            }
                        }
                    }
                }
            }
            // 如果以上都没有，尝试直接从顶级字段获取
            if (jsonObject.containsKey("text")) {
                return jsonObject.getString("text");
            }
            if (jsonObject.containsKey("content")) {
                return jsonObject.getString("content");
            }
            if (jsonObject.containsKey("message")) {
                return jsonObject.getString("message");
            }

        } catch (Exception e) {
            log.error("从原始JSON响应提取文本失败", e);
        }
        return null;
    }
}

