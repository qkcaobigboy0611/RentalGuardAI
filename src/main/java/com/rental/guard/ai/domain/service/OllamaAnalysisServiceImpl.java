/**
 * @author qkcao
 * @date 2025/9/16 18:45
 */
package com.rental.guard.ai.domain.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.rental.guard.ai.config.LocalModelConfig;
import com.rental.guard.ai.domain.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Semaphore;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "ai.local-model", name = "enabled", havingValue = "true")
public class OllamaAnalysisServiceImpl implements AIAnalysisService {
    private final LocalModelConfig localModelConfig;
    private final RestTemplate restTemplate;
    // 使用信号量限制并发请求数
    private final Semaphore semaphore;

    public OllamaAnalysisServiceImpl(LocalModelConfig localModelConfig) {
        this.localModelConfig = localModelConfig;
        this.restTemplate = createRestTemplate();
        this.semaphore = new Semaphore(localModelConfig.getMaxConcurrency());
    }

    @Override
    public AIAnalysisResult analyze(AIAnalysisRequest request) {
        if (!isAvailable()) {
            return AIAnalysisResult.failure("Ollama本地模型服务不可用", 0L);
        }
        // 获取并发许可
        try {
            if (!semaphore.tryAcquire()) {
                log.warn("Ollama服务并发请求已达上限，请求被拒绝");
                return AIAnalysisResult.failure("服务繁忙，请稍后重试", 0L);
            }
        } catch (Exception e) {
            log.error("获取Ollama并发许可失败", e);
            return AIAnalysisResult.failure("服务异常", 0L);
        }

        long startTime = System.currentTimeMillis();

        try {
            //  构建Ollama请求 基本欺诈检测
            OllamaRequest ollamaRequest = RentalFraudRequestBuilder.newBuilder()
                    .model(localModelConfig.getModelName())
                    .prompt(buildPrompt(request))
                    .taskType(request.getAnalysisType())
                    .riskLevel("HIGH")
                    .includeThinking(true)
                    .streaming(false)
                    .build();

            log.info("调用Ollama本地模型开始，模型: {}, 请求: {}", localModelConfig.getModelName(), JSON.toJSONString(ollamaRequest));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<OllamaRequest> entity = new HttpEntity<>(ollamaRequest, headers);

            // 发送请求
            String endpoint = localModelConfig.getEndpoint() + "/api/generate";
            ResponseEntity<OllamaResponse> response = restTemplate.exchange(endpoint, HttpMethod.POST, entity, OllamaResponse.class);

            long costTime = System.currentTimeMillis() - startTime;

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                OllamaResponse ollamaResponse = response.getBody();
                log.info("Ollama本地模型调用成功，耗时: {}ms, 响应长度: {} 字符", costTime, ollamaResponse.getResponse() != null ? ollamaResponse.getResponse().length() : 0);

                String responseText = ollamaResponse.getText();
                if (responseText == null || responseText.trim().isEmpty()) {
                    log.info("Ollama响应文本为空，原始响应: {}", JSON.toJSONString(ollamaResponse));
                    return AIAnalysisResult.failure("模型响应内容为空", costTime);
                }
                return AIAnalysisResult.success(responseText.replace("```json", "").replace("```", ""), ollamaResponse.getEstimatedTokens(), costTime);
            } else {
                log.error("Ollama调用失败，状态码: {}", response.getStatusCode());
                return AIAnalysisResult.failure("Ollama调用失败", costTime);
            }

        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("Ollama调用异常，耗时: {}ms", costTime, e);
            return AIAnalysisResult.failure("Ollama调用异常: " + e.getMessage(), costTime);
        } finally {
            // 释放并发许可
            semaphore.release();
        }
    }

    @Override
    public boolean isAvailable() {
        if (!localModelConfig.getEnabled()) {
            return false;
        }

        if (StringUtils.isBlank(localModelConfig.getEndpoint()) ||
                StringUtils.isBlank(localModelConfig.getModelName())) {
            return false;
        }

        // 检查Ollama服务是否可用
        try {
            String healthEndpoint = localModelConfig.getEndpoint() + "/api/tags";
            ResponseEntity<String> response = restTemplate.getForEntity(healthEndpoint, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.debug("Ollama服务健康检查失败", e);
            return false;
        }
    }

    @Override
    public String getServiceType() {
        return "ollama";
    }

    private String buildPrompt(AIAnalysisRequest request) {
        if (StringUtils.isNotBlank(request.getPrompt())) {
            return request.getPrompt().replace("{content}", request.getContent());
        }
        return request.getContent();
    }

    private RestTemplate createRestTemplate() {
        RestTemplate template = new RestTemplate();
        // 设置超时时间
        template.getRequestFactory().getClass();
        // 这里可以进一步配置连接和读取超时
        return template;
    }
}
