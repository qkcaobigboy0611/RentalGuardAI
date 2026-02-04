/**
 * @author qkcao
 * @date 2026/1/30 16:14
 */
package com.rental.guard.ai.domain.service.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.guard.ai.domain.dto.v1.ConversationSession;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class SlotManager {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 槽位定义注册表
    private final Map<String, SlotDefinition> slotDefinitions = new HashMap<>();

    public SlotManager() {
        initializeSlotDefinitions();
    }

    private void initializeSlotDefinitions() {
        // 房源相关槽位
        registerSlot(SlotDefinition.builder()
                .name("propertyAddress")
                .type(SlotType.STRING)
                .required(true)
                .validationRule("address_validation")
                .promptMessage("请提供房源详细地址")
                .validationTool("addressStandardizationAPI")
                .build());

        registerSlot(SlotDefinition.builder()
                .name("listingPrice")
                .type(SlotType.NUMBER)
                .required(true)
                .validationRule("price_range:500-50000")
                .promptMessage("月租金是多少？")
                .validationTool("marketPriceComparison")
                .build());

        registerSlot(SlotDefinition.builder()
                .name("depositAmount")
                .type(SlotType.NUMBER)
                .required(false)
                .validationRule("deposit_ratio_check")
                .promptMessage("押金金额是多少？")
                .riskIndicator("HIGH_DEPOSIT")
                .build());

        // 付款相关槽位
        registerSlot(SlotDefinition.builder()
                .name("paymentMethod")
                .type(SlotType.ENUM)
                .required(true)
                .allowedValues(Arrays.asList("平台担保", "银行转账", "微信", "支付宝", "现金"))
                .promptMessage("对方要求哪种付款方式？")
                .riskIndicator("CASH_PAYMENT")
                .build());

        // 身份验证槽位
        registerSlot(SlotDefinition.builder()
                .name("landlordVerified")
                .type(SlotType.BOOLEAN)
                .required(false)
                .validationTool("platformVerificationAPI")
                .source("tool_result")
                .build());
    }

    public void registerSlot(SlotDefinition definition) {
        slotDefinitions.put(definition.getName(), definition);
    }

    public SlotExtractionResult extractSlots(String userInput,
                                             ConversationSession session,
                                             String currentIntent) {

        List<ConversationSession.SlotValue> extracted = new ArrayList<>();
        List<String> missingRequired = new ArrayList<>();

        // 基于当前意图的必需槽位
        Set<String> requiredForIntent = getRequiredSlotsForIntent(currentIntent);

        // 1. 使用规则提取
        Map<String, Object> ruleBased = extractWithRules(userInput, currentIntent);

        // 2. 使用LLM提取（简化版本，实际应调用模型）
        Map<String, Object> llmBased = extractWithLLM(userInput, currentIntent, session);

        // 合并提取结果
        Map<String, Object> allExtracted = new HashMap<>(ruleBased);
        allExtracted.putAll(llmBased);

        // 验证和转换槽位值
        for (Map.Entry<String, Object> entry : allExtracted.entrySet()) {
            String slotName = entry.getKey();
            SlotDefinition def = slotDefinitions.get(slotName);

            if (def != null) {
                ValidationResult validation = validateSlotValue(slotName, entry.getValue(), def);

                if (validation.isValid()) {
                    ConversationSession.SlotValue slotValue = ConversationSession.SlotValue.builder()
                            .slotName(slotName)
                            .value(convertValue(entry.getValue(), def.getType()))
                            .collectedAt(LocalDateTime.now())
                            .source("user_input")
                            .confidence(validation.getConfidence())
                            .verified(false) // 需要后续工具验证
                            .build();
                    extracted.add(slotValue);
                } else {
                    log.warn("Slot validation failed for {}: {}", slotName, validation.getError());
                }
            }
        }

        // 检查缺失的必需槽位
        for (String requiredSlot : requiredForIntent) {
            boolean filled = extracted.stream()
                    .anyMatch(sv -> sv.getSlotName().equals(requiredSlot)) ||
                    session.getFilledSlots().containsKey(requiredSlot);

            if (!filled) {
                missingRequired.add(requiredSlot);
            }
        }

        return SlotExtractionResult.builder()
                .extractedSlots(extracted)
                .missingRequiredSlots(missingRequired)
                .nextPrompt(generateMissingSlotPrompt(missingRequired))
                .build();
    }

    private Set<String> getRequiredSlotsForIntent(String intent) {
        // 根据意图返回必需槽位
        Map<String, Set<String>> intentSlots = Map.of(
                "verify_property", Set.of("propertyAddress", "listingPrice"),
                "verify_payment", Set.of("paymentMethod", "paymentAmount"),
                "assess_risk", Set.of("propertyAddress", "paymentMethod", "depositAmount")
        );

        return intentSlots.getOrDefault(intent, Collections.emptySet());
    }

    private Map<String, Object> extractWithRules(String userInput, String intent) {
        Map<String, Object> result = new HashMap<>();

        // 简单的规则匹配
        if (userInput.contains("押金") || userInput.contains("定金")) {
            // 提取金额模式：数字+元/块
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+(\\.\\d+)?)[元块]");
            java.util.regex.Matcher matcher = pattern.matcher(userInput);
            if (matcher.find()) {
                result.put("depositAmount", Double.parseDouble(matcher.group(1)));
            }
        }

        if (userInput.contains("微信") || userInput.contains("支付宝")) {
            result.put("paymentMethod", userInput.contains("微信") ? "微信" : "支付宝");
        }

        return result;
    }

    private Map<String, Object> extractWithLLM(String userInput,
                                               String intent,
                                               ConversationSession session) {
        // 简化实现 - 实际应调用LLM API
        // 这里可以使用OpenAI API或其他LLM服务
        Map<String, Object> result = new HashMap<>();

        // 模拟LLM提取
        if (intent.equals("verify_property") && userInput.contains("地址")) {
            // 提取地址信息
            result.put("propertyAddress", extractAddress(userInput));
        }

        return result;
    }

    private String extractAddress(String text) {
        // 简化地址提取
        return text.replaceAll(".*地址[是：:]*", "").trim();
    }

    private ValidationResult validateSlotValue(String slotName, Object value, SlotDefinition definition) {
        // 实现验证逻辑
        try {
            switch (definition.getType()) {
                case NUMBER:
                    Double num = Double.parseDouble(value.toString());
                    if (definition.getValidationRule() != null &&
                            definition.getValidationRule().startsWith("price_range")) {
                        String[] range = definition.getValidationRule()
                                .replace("price_range:", "")
                                .split("-");
                        double min = Double.parseDouble(range[0]);
                        double max = Double.parseDouble(range[1]);
                        if (num < min || num > max) {
                            return ValidationResult.failed("价格超出合理范围");
                        }
                    }
                    break;

                case ENUM:
                    if (!definition.getAllowedValues().contains(value.toString())) {
                        return ValidationResult.failed("无效的选项");
                    }
                    break;
            }

            return ValidationResult.success(0.9); // 假设置信度0.9
        } catch (Exception e) {
            return ValidationResult.failed("格式错误: " + e.getMessage());
        }
    }

    private Object convertValue(Object value, SlotType type) {
        switch (type) {
            case NUMBER:
                return Double.parseDouble(value.toString());
            case BOOLEAN:
                return Boolean.parseBoolean(value.toString());
            default:
                return value.toString();
        }
    }

    private String generateMissingSlotPrompt(List<String> missingSlots) {
        if (missingSlots.isEmpty()) {
            return null;
        }

        StringBuilder prompt = new StringBuilder("为了继续处理，需要以下信息：\n");
        for (String slot : missingSlots) {
            SlotDefinition def = slotDefinitions.get(slot);
            if (def != null) {
                prompt.append("- ").append(def.getPromptMessage()).append("\n");
            }
        }
        return prompt.toString();
    }

    // 内部类定义
    public enum SlotType {
        STRING, NUMBER, BOOLEAN, ENUM, DATE, ARRAY
    }

    @Data
    @Builder
    public static class SlotDefinition {
        private String name;
        private SlotType type;
        private boolean required;
        private String validationRule;
        private String promptMessage;
        private List<String> allowedValues;
        private String validationTool;
        private String source; // user_input, tool_result
        private String riskIndicator;
    }

    @Data
    @Builder
    public static class SlotExtractionResult {
        private List<ConversationSession.SlotValue> extractedSlots;
        private List<String> missingRequiredSlots;
        private String nextPrompt;
        private Double extractionConfidence;
    }

    @Data
    @Builder
    public static class ValidationResult {
        private boolean valid;
        private String error;
        private Double confidence;

        public static ValidationResult success(Double confidence) {
            return ValidationResult.builder()
                    .valid(true)
                    .confidence(confidence)
                    .build();
        }

        public static ValidationResult failed(String error) {
            return ValidationResult.builder()
                    .valid(false)
                    .error(error)
                    .confidence(0.0)
                    .build();
        }
    }
}
