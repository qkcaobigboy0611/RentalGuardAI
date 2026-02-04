/**
 * @author qkcao
 * @date 2026/1/23 17:48
 */
package com.rental.guard.ai.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.guard.ai.config.LocalModelConfig;
import com.rental.guard.ai.domain.dto.OllamaException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OllamaService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final LocalModelConfig localModelConfig;
    private final RestTemplate restTemplate;


    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ollama.model:llama2}")
    private String model;

    @Value("${ollama.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${ollama.max-retries:3}")
    private int maxRetries;

    @Value("${ollama.temperature:0.1}")
    private double temperature;

    @Value("${ollama.top-p:0.9}")
    private double topP;


    public OllamaService(WebClient.Builder webClientBuilder,
                         ObjectMapper objectMapper,
                         LocalModelConfig localModelConfig,
                         RestTemplate restTemplate) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.localModelConfig = localModelConfig;
        this.restTemplate = restTemplate;
    }

    /**
     * 生成文本
     */
    public String generateText(String prompt) {
        try {
            return generate(prompt);
        } catch (Exception e) {
            log.error("Ollama调用失败", e);
            throw new OllamaException("调用Ollama失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成结构化JSON响应
     */
    public <T> T generateStructuredResponse(String prompt, Class<T> responseType) {
        try {
            // 构建包含JSON格式要求的prompt
            String jsonPrompt = String.format("""
                请以严格的JSON格式返回响应，不要包含任何其他文本。
                响应格式要求：
                %s
                
                用户请求：
                %s
                
                请返回JSON：
                """, getJsonSchema(responseType), prompt);

            String response = generate(jsonPrompt);

            // 清理响应，提取JSON部分
            String jsonStr = extractJsonFromResponse(response);

            return objectMapper.readValue(jsonStr, responseType);

        } catch (Exception e) {
            log.error("生成结构化响应失败", e);
            throw new OllamaException("生成结构化响应失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式生成（用于长时间任务）
     */
    public Mono<String> generateStream(String prompt, String sessionId) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("prompt", prompt);
        request.put("stream", true);

        return webClient.post()
                .uri("/api/generate")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .map(chunk -> {
                    try {
                        // 解析流式响应
                        OllamaStreamResponse streamResponse = objectMapper.readValue(chunk, OllamaStreamResponse.class);
                        return streamResponse.getResponse();
                    } catch (Exception e) {
                        log.warn("解析流式响应失败", e);
                        return "";
                    }
                })
                .collect(StringBuilder::new, StringBuilder::append)
                .map(StringBuilder::toString)
                .onErrorResume(e -> {
                    log.error("流式生成失败", e);
                    return Mono.error(new OllamaException("流式生成失败: " + e.getMessage()));
                });
    }

    /**
     * 对话生成（带上下文）
     */
    public String generateWithContext(String prompt, List<Message> context) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", model);
            request.put("messages", buildMessages(prompt, context));
            request.put("stream", false);
            request.put("options", Map.of(
                    "temperature", 0.2,
                    "num_predict", 4096
            ));

            OllamaChatResponse response = webClient.post()
                    .uri("/api/chat")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaChatResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            if (response != null && response.getMessage() != null) {
                return response.getMessage().getContent();
            }

            throw new OllamaException("对话响应为空");

        } catch (Exception e) {
            log.error("对话生成失败", e);
            throw new OllamaException("对话生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 思维链推理
     */
    public ChainOfThoughtResponse chainOfThought(String problem) {
        String prompt = String.format("""
            请使用思维链（Chain of Thought）方法分析以下问题。
            逐步思考，最后给出结论。
            
            问题：%s
            
            请按以下格式返回：
            思考过程：<你的逐步推理>
            最终结论：<你的结论>
            置信度：<0-1之间的数字>
            """, problem);

        String response = generateText(prompt);
        return parseChainOfThought(response);
    }

    /**
     * 批量生成（用于并行处理）
     */
    public List<String> batchGenerate(List<String> prompts) {
        return prompts.parallelStream()
                .map(prompt -> {
                    try {
                        return generateText(prompt);
                    } catch (Exception e) {
                        log.warn("批量生成失败，prompt: {}", prompt, e);
                        return "生成失败: " + e.getMessage();
                    }
                })
                .collect(Collectors.toList());
    }

    private String extractJsonFromResponse(String response) {
        // 尝试从响应中提取JSON
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}") + 1;

        if (start >= 0 && end > start) {
            return response.substring(start, end);
        }

        // 如果不是JSON，尝试包装成JSON
        return String.format("{\"text\": \"%s\"}", response.replace("\"", "\\\""));
    }

    private String getJsonSchema(Class<?> clazz) {
        // 为常见响应类型提供模式描述
        if (clazz == TaskDecompositionResponse.class) {
            return """
                返回JSON格式：
                {
                    "subtasks": [
                        {
                            "task_type": "任务类型",
                            "name": "任务名称",
                            "description": "任务描述",
                            "parameters": {"参数键": "参数值"},
                            "estimated_duration": 预估时间（秒）
                        }
                    ],
                    "dependencies": [["任务ID1", "任务ID2"]],
                    "reasoning": "分解理由"
                }
                """;
        } else if (clazz == PlanningStrategyResponse.class) {
            return """
                返回JSON格式：
                {
                    "strategy": "策略名称",
                    "reasoning": "选择理由",
                    "parameters": {"并发数": 5, "超时时间": 300},
                    "confidence": 0.95
                }
                """;
        }

        return "返回有效的JSON格式";
    }

    private List<Map<String, Object>> buildMessages(String prompt, List<Message> context) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // 添加系统提示
        messages.add(Map.of(
                "role", "system",
                "content", "你是一个专业的防欺诈系统任务规划助手。"
        ));

        // 添加上下文
        for (Message msg : context) {
            messages.add(Map.of(
                    "role", msg.getRole(),
                    "content", msg.getContent()
            ));
        }

        // 添加当前请求
        messages.add(Map.of(
                "role", "user",
                "content", prompt
        ));

        return messages;
    }

    private ChainOfThoughtResponse parseChainOfThought(String response) {
        ChainOfThoughtResponse result = new ChainOfThoughtResponse();

        try {
            String[] lines = response.split("\n");
            StringBuilder reasoning = new StringBuilder();
            StringBuilder conclusion = new StringBuilder();
            Double confidence = 0.5;

            boolean inReasoning = false;
            boolean inConclusion = false;

            for (String line : lines) {
                if (line.startsWith("思考过程：")) {
                    inReasoning = true;
                    inConclusion = false;
                    reasoning.append(line.substring(4).trim());
                } else if (line.startsWith("最终结论：")) {
                    inReasoning = false;
                    inConclusion = true;
                    conclusion.append(line.substring(4).trim());
                } else if (line.startsWith("置信度：")) {
                    try {
                        confidence = Double.parseDouble(line.substring(4).trim());
                    } catch (NumberFormatException e) {
                        confidence = 0.5;
                    }
                } else if (inReasoning) {
                    reasoning.append("\n").append(line);
                } else if (inConclusion) {
                    conclusion.append("\n").append(line);
                }
            }

            result.setReasoning(reasoning.toString());
            result.setConclusion(conclusion.toString());
            result.setConfidence(confidence);

        } catch (Exception e) {
            log.warn("解析思维链响应失败", e);
            result.setReasoning(response);
            result.setConclusion("解析失败");
            result.setConfidence(0.3);
        }

        return result;
    }

    // 响应数据结构
    @Data
    public static class OllamaResponse {
        private String model;
        private String createdAt;
        private String response;
        private Boolean done;
        private Integer promptEvalCount;
        private Integer evalCount;
    }

    @Data
    public static class OllamaStreamResponse {
        private String model;
        private String createdAt;
        private String response;
        private Boolean done;
    }

    @Data
    public static class OllamaChatResponse {
        private Message message;
        private Boolean done;
    }

    @Data
    public static class Message {
        private String role; // system, user, assistant
        private String content;
    }

    @Data
    public static class ChainOfThoughtResponse {
        private String reasoning;
        private String conclusion;
        private Double confidence;
    }

    // 结构化响应类型
    @Data
    public static class TaskDecompositionResponse {
        private List<Subtask> subtasks;
        private List<List<String>> dependencies;
        private String reasoning;

        @Data
        public static class Subtask {
            private String task_type;
            private String name;
            private String description;
            private Map<String, Object> parameters;
            private Integer estimated_duration;
        }
    }

    @Data
    public static class PlanningStrategyResponse {
        private String strategy;
        private String reasoning;
        private Map<String, Object> parameters;
        private Double confidence;
    }

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
}
