/**
 * @author qkcao
 * @date 2026/1/22 17:29
 */
package com.rental.guard.ai.domain.service;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.utils.Constants;
import com.rental.guard.ai.config.LocalModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service
public class OllamaLLMServiceImpl implements LLMService{
    private final LocalModelConfig localModelConfig;
    private final RestTemplate restTemplate;

    public OllamaLLMServiceImpl(LocalModelConfig localModelConfig) {
        this.localModelConfig = localModelConfig;
        this.restTemplate = createRestTemplate();
    }

    static {
        Constants.baseHttpApiUrl="https://dashscope.aliyuncs.com/api/v1";
    }

    @Override
    public String generate(String prompt) {
        long startTime = System.currentTimeMillis();

        try {
            // 简单构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", localModelConfig.getModelName());
            requestBody.put("prompt", prompt);
            requestBody.put("stream", false);

            log.info("调用Ollama本地模型，模型: {}, 提示词长度: {}",
                    localModelConfig.getModelName(), prompt.length());

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 发送请求
            String endpoint = localModelConfig.getEndpoint() + "/api/generate";
            ResponseEntity<Map> response = restTemplate.exchange(
                    endpoint, HttpMethod.POST, entity, Map.class);

            long costTime = System.currentTimeMillis() - startTime;

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                String responseText = (String) responseBody.get("response");

                if (responseText == null || responseText.trim().isEmpty()) {
                    log.warn("模型响应为空，原始响应: {}", responseBody);
                    return "模型响应为空";
                }

                log.info("调用成功，耗时: {}ms, 响应长度: {}字符", costTime, responseText.length());
                return responseText;
            } else {
                log.error("调用失败，状态码: {}", response.getStatusCode());
                return "模型调用失败，状态码: " + response.getStatusCode();
            }

        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("调用异常，耗时: {}ms", costTime, e);
            return "模型调用异常: " + e.getMessage();
        }
    }

    @Override
    public String generate(String prompt, Map<String, Object> parameters) {
        return null;
    }

    @Override
    public String simpleMultiModalConversationCall(String localPath) throws NoApiKeyException, UploadFileException, IOException {
        return callWithLocalFile(localPath);
    }

    private static String encodeImageToBase64(String imagePath) throws IOException {
        Path path = Paths.get(imagePath);
        byte[] imageBytes = Files.readAllBytes(path);
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    public String callWithLocalFile(String localPath) throws ApiException, NoApiKeyException, UploadFileException, IOException {

        String base64Image = encodeImageToBase64(localPath); // Base64编码

        MultiModalConversation conv = new MultiModalConversation();
        MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue())
                .content(Arrays.asList(
                        new HashMap<String, Object>() {{ put("image", "data:image/png;base64," + base64Image); }},
                        new HashMap<String, Object>() {{ put("text", "图中描绘的是什么景象？"); }}
                )).build();

        MultiModalConversationParam param = MultiModalConversationParam.builder()
                // 各地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
                .apiKey("sk-630b7df247bd49df89d50cf5373b1c1f")
                .model("qwen3-vl-flash")
                .messages(Arrays.asList(userMessage))
                .build();

        MultiModalConversationResult result = conv.call(param);
        return result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text").toString();
    }

    private RestTemplate createRestTemplate() {
        RestTemplate template = new RestTemplate();
        // 设置超时时间
        template.getRequestFactory().getClass();
        // 这里可以进一步配置连接和读取超时
        return template;
    }
}
