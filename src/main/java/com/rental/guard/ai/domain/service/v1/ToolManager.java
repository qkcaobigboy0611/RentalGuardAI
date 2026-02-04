/**
 * @author qkcao
 * @date 2026/1/30 16:20
 */
package com.rental.guard.ai.domain.service.v1;

import com.rental.guard.ai.domain.dto.v1.ConversationSession;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 工具调用管理器
 */
@Slf4j
@Component
public class ToolManager {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    // 工具注册表
    private final Map<String, ToolDefinition> toolRegistry = new HashMap<>();

    @Value("${api.property.registry.url}")
    private String propertyRegistryUrl;

    @Value("${api.platform.verification.url}")
    private String platformVerificationUrl;

    @Value("${api.risk.assessment.url}")
    private String riskAssessmentUrl;

    public ToolManager() {
        initializeToolRegistry();
    }

    private void initializeToolRegistry() {
        // 房源验证工具
        registerTool(ToolDefinition.builder()
                .name("propertyRegistryCheck")
                .description("验证房源在房管局的备案信息")
                .endpoint(propertyRegistryUrl)
                .method(HttpMethod.POST)
                .requiredParams(Arrays.asList("propertyAddress", "listingId"))
                .timeoutSeconds(10)
                .retryCount(2)
                .build());

        // 平台验证工具
        registerTool(ToolDefinition.builder()
                .name("platformVerification")
                .description("验证房东/租客在平台的身份认证")
                .endpoint(platformVerificationUrl)
                .method(HttpMethod.GET)
                .requiredParams(Arrays.asList("phoneNumber", "userId"))
                .timeoutSeconds(5)
                .build());

        // 风险评估工具
        registerTool(ToolDefinition.builder()
                .name("paymentRiskAssessment")
                .description("评估付款方式的风险等级")
                .endpoint(riskAssessmentUrl)
                .method(HttpMethod.POST)
                .requiredParams(Arrays.asList("paymentMethod", "amount", "urgency"))
                .timeoutSeconds(8)
                .build());

        // 图像验证工具（简化）
        registerTool(ToolDefinition.builder()
                .name("imageVerification")
                .description("验证房源图像的真实性")
                .endpoint("internal://image/verify")
                .method(HttpMethod.POST)
                .requiredParams(Collections.singletonList("imageUrls"))
                .timeoutSeconds(15)
                .build());
    }

    public void registerTool(ToolDefinition definition) {
        toolRegistry.put(definition.getName(), definition);
    }

    public ToolCallResult callTool(String toolName,
                                   Map<String, Object> parameters,
                                   ConversationSession session) {

        ToolDefinition toolDef = toolRegistry.get(toolName);
        if (toolDef == null) {
            return ToolCallResult.failed("Tool not found: " + toolName);
        }

        // 验证必需参数
        List<String> missingParams = toolDef.getRequiredParams().stream()
                .filter(param -> !parameters.containsKey(param))
                .collect(Collectors.toList());

        if (!missingParams.isEmpty()) {
            return ToolCallResult.failed("Missing required parameters: " + missingParams);
        }

        // 执行工具调用（同步或异步）
        try {
            if (toolDef.getEndpoint().startsWith("internal://")) {
                // 内部工具调用
                return callInternalTool(toolName, parameters, session);
            } else {
                // 外部API调用
                return callExternalTool(toolDef, parameters);
            }
        } catch (Exception e) {
            log.error("Tool call failed: {}", toolName, e);
            return ToolCallResult.failed("Tool execution error: " + e.getMessage());
        }
    }

    private ToolCallResult callExternalTool(ToolDefinition toolDef,
                                            Map<String, Object> parameters) {

        long startTime = System.currentTimeMillis();

        try {
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Request-ID", UUID.randomUUID().toString());

            // 构建请求体
            String requestBody = objectMapper.writeValueAsString(parameters);
            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

            // 执行请求
            ResponseEntity<String> response = restTemplate.exchange(
                    toolDef.getEndpoint(),
                    toolDef.getMethod(),
                    request,
                    String.class
            );

            // 解析响应
            long duration = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful()) {
                ToolResponse toolResponse = objectMapper.readValue(
                        response.getBody(),
                        ToolResponse.class
                );

                return ToolCallResult.success(
                        "",
                        toolResponse.getData(),
                        duration,
                        toolResponse.getConfidence()
                );
            } else {
                return ToolCallResult.failed(
                        "API returned error: " + response.getStatusCode()
                );
            }

        } catch (HttpClientErrorException e) {
            return ToolCallResult.failed("HTTP error: " + e.getStatusCode());
        } catch (Exception e) {
            return ToolCallResult.failed("Unexpected error: " + e.getMessage());
        }
    }

    private ToolCallResult callInternalTool(String toolName,
                                            Map<String, Object> parameters,
                                            ConversationSession session) {

        switch (toolName) {
            case "imageVerification":
                return executeImageVerification(parameters);

            case "riskScoringEngine":
                return executeRiskScoring(parameters, session);

            default:
                return ToolCallResult.failed("Unknown internal tool: " + toolName);
        }
    }

    private ToolCallResult executeImageVerification(Map<String, Object> params) {
        // 简化的图像验证逻辑
        List<String> imageUrls = (List<String>) params.get("imageUrls");

        // 模拟验证结果
        Map<String, Object> result = new HashMap<>();
        result.put("totalImages", imageUrls.size());
        result.put("verifiedCount", imageUrls.size() > 0 ? imageUrls.size() - 1 : 0);
        result.put("hasWatermark", true);
        result.put("reverseSearchMatches", 0);
        result.put("riskScore", imageUrls.isEmpty() ? 0.8 : 0.2);

        return ToolCallResult.success(
                "imageVerification",
                result,
                100L, // 模拟耗时
                0.85  // 置信度
        );
    }

    private ToolCallResult executeRiskScoring(Map<String, Object> params,
                                              ConversationSession session) {

        // 基于已收集的信息进行风险评估
        double riskScore = 0.0;
        List<String> riskFactors = new ArrayList<>();

        // 检查付款方式
        String paymentMethod = (String) params.get("paymentMethod");
        if ("现金".equals(paymentMethod)) {
            riskScore += 0.4;
            riskFactors.add("现金交易风险");
        } else if ("平台担保".equals(paymentMethod)) {
            riskScore -= 0.2;
        }

        // 检查押金比例
        Double depositAmount = (Double) params.get("depositAmount");
        Double monthlyRent = (Double) params.get("monthlyRent");
        if (depositAmount != null && monthlyRent != null) {
            double ratio = depositAmount / monthlyRent;
            if (ratio > 2) {
                riskScore += 0.3;
                riskFactors.add("高额押金风险");
            }
        }

        // 检查紧急程度
        String urgency = (String) params.get("urgency");
        if ("立即".equals(urgency) || "今天".equals(urgency)) {
            riskScore += 0.2;
            riskFactors.add("紧急交易风险");
        }

        // 检查房源验证状态
        Boolean propertyVerified = (Boolean) session.getFilledSlots()
                .get("propertyVerified").isVerified();
        if (propertyVerified == null || !propertyVerified) {
            riskScore += 0.3;
            riskFactors.add("房源未验证");
        }

        // 确保风险分数在0-1之间
        riskScore = Math.max(0.0, Math.min(1.0, riskScore));

        // 确定风险等级
        String riskLevel;
        if (riskScore >= 0.7) {
            riskLevel = "HIGH";
        } else if (riskScore >= 0.4) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("riskScore", riskScore);
        result.put("riskLevel", riskLevel);
        result.put("riskFactors", riskFactors);
        result.put("recommendedActions", generateRecommendations(riskLevel, riskFactors));

        return ToolCallResult.success(
                "riskScoringEngine",
                result,
                50L,
                0.9
        );
    }

    private List<String> generateRecommendations(String riskLevel,
                                                 List<String> riskFactors) {

        List<String> recommendations = new ArrayList<>();

        if ("HIGH".equals(riskLevel)) {
            recommendations.add("立即转人工审核");
            recommendations.add("暂停所有资金交易");
            recommendations.add("要求对方提供身份证明和房产证明");
        } else if ("MEDIUM".equals(riskLevel)) {
            recommendations.add("使用平台担保交易");
            recommendations.add("签订正式合同");
            recommendations.add("保留所有沟通记录");
        } else {
            recommendations.add("正常交易但仍需谨慎");
            recommendations.add("建议使用平台标准合同");
        }

        return recommendations;
    }

    // 批量调用工具（并行）
    public Map<String, ToolCallResult> callToolsInParallel(
            List<String> toolNames,
            Map<String, Map<String, Object>> toolParameters,
            ConversationSession session) {

        Map<String, CompletableFuture<ToolCallResult>> futures = new HashMap<>();

        for (String toolName : toolNames) {
            Map<String, Object> params = toolParameters.getOrDefault(toolName, new HashMap<>());

            CompletableFuture<ToolCallResult> future = CompletableFuture.supplyAsync(
                    () -> callTool(toolName, params, session),
                    executorService
            );

            futures.put(toolName, future);
        }

        // 等待所有工具调用完成
        Map<String, ToolCallResult> results = new HashMap<>();
        for (Map.Entry<String, CompletableFuture<ToolCallResult>> entry : futures.entrySet()) {
            try {
                results.put(entry.getKey(), entry.getValue().get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                results.put(entry.getKey(), ToolCallResult.failed("Timeout or error: " + e.getMessage()));
            }
        }

        return results;
    }

    // 内部类定义
    @Data
    @Builder
    public static class ToolDefinition {
        private String name;
        private String description;
        private String endpoint;
        private HttpMethod method;
        private List<String> requiredParams;
        private int timeoutSeconds;
        private int retryCount;
        private Map<String, String> headers;
    }

    @Data
    @Builder
    public static class ToolCallResult {
        private String toolName;
        private Object result;
        private Long durationMs;
        private Double confidence;
        private String status; // SUCCESS, FAILED, TIMEOUT
        private String errorMessage;

        public static ToolCallResult success(String toolName,
                                             Object result,
                                             Long duration,
                                             Double confidence) {
            return ToolCallResult.builder()
                    .toolName(toolName)
                    .result(result)
                    .durationMs(duration)
                    .confidence(confidence)
                    .status("SUCCESS")
                    .build();
        }

        public static ToolCallResult failed(String errorMessage) {
            return ToolCallResult.builder()
                    .status("FAILED")
                    .errorMessage(errorMessage)
                    .build();
        }
    }

    @Data
    public static class ToolResponse {
        private int code;
        private String message;
        private Object data;
        private Double confidence;
    }
}
