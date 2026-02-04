/**
 * @author qkcao
 * @date 2026/1/30 16:22
 */
package com.rental.guard.ai.domain.service.v1;


import com.rental.guard.ai.domain.dto.v1.ConversationSession;
import com.rental.guard.ai.domain.enum1.FraudPreventionState;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 4. 对话引擎主控制器
 */

@Slf4j
@Service
public class ConversationEngine {

    @Autowired
    private SlotManager slotManager;

    @Autowired
    private ToolManager toolManager;

//    @Autowired
//    private StateMachine<FraudPreventionState, String> stateMachine;

    // 会话存储（实际应使用Redis或数据库）
    private final Map<String, ConversationSession> sessionStore = new HashMap<>();

    public ConversationResponse processMessage(String sessionId,
                                               String userInput) {

        // 获取或创建会话
        ConversationSession session = sessionStore.computeIfAbsent(
                sessionId,
                id -> createNewSession(id)
        );

        // 更新最后活动时间
        session.setLastActivity(LocalDateTime.now());

        // 1. 意图识别
        String intent = recognizeIntent(userInput, session);
        log.info("Recognized intent: {} for session: {}", intent, sessionId);

        // 2. 槽位提取
        SlotManager.SlotExtractionResult extractionResult =
                slotManager.extractSlots(userInput, session, intent);

        // 更新会话中的槽位
        updateSessionSlots(session, extractionResult.getExtractedSlots());

        // 3. 检查是否需要收集更多信息
        if (!extractionResult.getMissingRequiredSlots().isEmpty()) {
            return buildClarificationResponse(
                    extractionResult.getMissingRequiredSlots(),
                    session
            );
        }

        // 4. 基于当前状态和意图决定下一步
        ConversationAction nextAction = determineNextAction(intent, session);

        // 5. 执行动作
        switch (nextAction.getActionType()) {
            case CALL_TOOLS:
                return executeToolCalls(nextAction, session);

            case PROVIDE_ADVICE:
                return provideSafetyAdvice(session);

            case ESCALATE_TO_HUMAN:
                return escalateToHuman(session);

            case CLARIFY_INFORMATION:
                return requestClarification(nextAction.getClarificationPoints());

            case COMPLETE_CONVERSATION:
                return completeConversation(session);

            default:
                return fallbackResponse(session);
        }
    }

    private ConversationSession createNewSession(String sessionId) {
        return ConversationSession.builder()
                .sessionId(sessionId)
                .startTime(LocalDateTime.now())
                .lastActivity(LocalDateTime.now())
                .currentState(FraudPreventionState.INITIAL)
                .filledSlots(new HashMap<>())
                .conversationHistory(new ArrayList<>())
                .toolCalls(new ArrayList<>())
                .rollbackPoints(new ArrayList<>())
                .requiresHumanIntervention(false)
                .isCompleted(false)
                .build();
    }

    private String recognizeIntent(String userInput, ConversationSession session) {
        // 简化的意图识别逻辑
        // 实际可以使用ML模型或更复杂的规则

        FraudPreventionState currentState = session.getCurrentState();

        if (currentState == FraudPreventionState.INITIAL) {
            if (userInput.contains("房") && userInput.contains("租")) {
                return "rental_inquiry";
            } else if (userInput.contains("骗") || userInput.contains("风险")) {
                return "fraud_consultation";
            }
        } else if (currentState == FraudPreventionState.VERIFY_PROPERTY) {
            if (userInput.contains("地址") || userInput.contains("在哪")) {
                return "provide_property_address";
            } else if (userInput.contains("钱") || userInput.contains("价格")) {
                return "provide_price_info";
            }
        } else if (currentState == FraudPreventionState.ASSESS_PAYMENT_RISK) {
            if (userInput.contains("微信") || userInput.contains("支付宝") ||
                    userInput.contains("银行") || userInput.contains("现金")) {
                return "specify_payment_method";
            }
        }

        // 默认意图
        return "general_inquiry";
    }

    private void updateSessionSlots(ConversationSession session,
                                    List<ConversationSession.SlotValue> newSlots) {

        newSlots.forEach(slotValue -> {
            session.getFilledSlots().put(
                    slotValue.getSlotName(),
                    ConversationSession.SlotValue.builder()
                            .slotName(slotValue.getSlotName())
                            .value(slotValue.getValue())
                            .collectedAt(LocalDateTime.now())
                            .source(slotValue.getSource())
                            .confidence(slotValue.getConfidence())
                            .verified(false)
                            .build()
            );
        });
    }

    private ConversationResponse buildClarificationResponse(
            List<String> missingSlots,
            ConversationSession session) {

        StringBuilder message = new StringBuilder();
        message.append("为了准确评估风险，需要以下信息：\n");

        for (String slot : missingSlots) {
            switch (slot) {
                case "propertyAddress":
                    message.append("- 房源详细地址（例如：XX市XX区XX路XX号）\n");
                    break;
                case "listingPrice":
                    message.append("- 月租金金额\n");
                    break;
                case "paymentMethod":
                    message.append("- 付款方式（平台担保/银行转账/微信/支付宝/现金）\n");
                    break;
                case "depositAmount":
                    message.append("- 押金金额（如无押金请说明）\n");
                    break;
            }
        }

        message.append("\n请逐项提供上述信息。");

        return ConversationResponse.builder()
                .message(message.toString())
                .requiresUserInput(true)
                .nextExpectedInput("slot_filling")
                .sessionState(session.getCurrentState().name())
                .build();
    }

    private ConversationAction determineNextAction(String intent,
                                                   ConversationSession session) {

        FraudPreventionState currentState = session.getCurrentState();

        // 基于状态和意图的决策逻辑
        switch (currentState) {
            case INITIAL:
                if ("rental_inquiry".equals(intent)) {
                    return ConversationAction.builder()
                            .actionType(ActionType.CLARIFY_INFORMATION)
                            .clarificationPoints(Arrays.asList(
                                    "您是要出租房屋还是寻找租房？",
                                    "请描述您遇到的具体情况"
                            ))
                            .nextState(FraudPreventionState.VERIFY_PROPERTY)
                            .build();
                }
                break;

            case VERIFY_PROPERTY:
                // 检查是否已收集足够信息
                boolean hasAddress = session.getFilledSlots().containsKey("propertyAddress");
                boolean hasPrice = session.getFilledSlots().containsKey("listingPrice");

                if (hasAddress && hasPrice) {
                    return ConversationAction.builder()
                            .actionType(ActionType.CALL_TOOLS)
                            .toolsToCall(Arrays.asList(
                                    "propertyRegistryCheck",
                                    "imageVerification"
                            ))
                            .nextState(FraudPreventionState.ASSESS_PAYMENT_RISK)
                            .build();
                } else {
                    return ConversationAction.builder()
                            .actionType(ActionType.CLARIFY_INFORMATION)
                            .clarificationPoints(Arrays.asList(
                                    "需要房源地址和价格信息"
                            ))
                            .build();
                }

            case ASSESS_PAYMENT_RISK:
                if (session.getFilledSlots().containsKey("paymentMethod")) {
                    return ConversationAction.builder()
                            .actionType(ActionType.CALL_TOOLS)
                            .toolsToCall(Arrays.asList("paymentRiskAssessment"))
                            .nextState(FraudPreventionState.ASSESS_OVERALL_RISK)
                            .build();
                }
                break;

            case ASSESS_OVERALL_RISK:
                // 检查风险评估结果
                ConversationSession.RiskAssessment risk = session.getRiskAssessment();
                if (risk != null && "HIGH".equals(risk.getRiskLevel())) {
                    return ConversationAction.builder()
                            .actionType(ActionType.ESCALATE_TO_HUMAN)
                            .build();
                } else {
                    return ConversationAction.builder()
                            .actionType(ActionType.PROVIDE_ADVICE)
                            .nextState(FraudPreventionState.COMPLETED)
                            .build();
                }
        }

        // 默认动作
        return ConversationAction.builder()
                .actionType(ActionType.CLARIFY_INFORMATION)
                .clarificationPoints(Arrays.asList("请提供更多细节"))
                .build();
    }

    private ConversationResponse executeToolCalls(ConversationAction action,
                                                  ConversationSession session) {

        // 准备工具参数
        Map<String, Map<String, Object>> toolParams = new HashMap<>();

        for (String toolName : action.getToolsToCall()) {
            Map<String, Object> params = new HashMap<>();

            // 从会话中提取参数
            switch (toolName) {
                case "propertyRegistryCheck":
                    params.put("propertyAddress",
                            session.getFilledSlots().get("propertyAddress").getValue());
                    params.put("listingId",
                            session.getFilledSlots().get("listingId"));
                    break;

                case "paymentRiskAssessment":
                    params.put("paymentMethod",
                            session.getFilledSlots().get("paymentMethod").getValue());
                    params.put("amount",
                            session.getFilledSlots().get("depositAmount"));
                    params.put("urgency", "normal");
                    break;
            }

            toolParams.put(toolName, params);
        }

        // 调用工具
        Map<String, ToolManager.ToolCallResult> results =
                toolManager.callToolsInParallel(
                        action.getToolsToCall(),
                        toolParams,
                        session
                );

        // 处理工具结果
        List<ConversationSession.ToolCallRecord> records = new ArrayList<>();
        StringBuilder summary = new StringBuilder("验证结果：\n");

        for (Map.Entry<String, ToolManager.ToolCallResult> entry : results.entrySet()) {
            ToolManager.ToolCallResult result = entry.getValue();

            // 记录工具调用
            ConversationSession.ToolCallRecord record =
                    ConversationSession.ToolCallRecord.builder()
                            .toolName(entry.getKey())
                            .parameters(toolParams.get(entry.getKey()))
                            .result(result.getResult())
                            .timestamp(LocalDateTime.now())
                            .confidence(result.getConfidence())
                            .status(result.getStatus())
                            .build();

            records.add(record);

            // 生成用户友好的摘要
            summary.append(generateToolResultSummary(entry.getKey(), result));
        }

        session.getToolCalls().addAll(records);

        // 更新会话状态
        if (action.getNextState() != null) {
            session.setCurrentState(action.getNextState());
        }

        // 创建保存点（用于回滚）
        createRollbackPoint(session, "after_tool_calls");

        // todo toolResults 有问题
        return ConversationResponse.builder()
                .message(summary.toString())
                .requiresUserInput(true)
                .nextExpectedInput("acknowledge_results")
                .sessionState(session.getCurrentState().name())
                .toolResults(null)
                .build();
    }

    private String generateToolResultSummary(String toolName,
                                             ToolManager.ToolCallResult result) {

        switch (toolName) {
            case "propertyRegistryCheck":
                Map<String, Object> data = (Map<String, Object>) result.getResult();
                boolean isRegistered = (Boolean) data.get("isRegistered");
                return String.format("- 房源备案：%s\n",
                        isRegistered ? "✅ 已备案" : "❌ 未备案");

            case "paymentRiskAssessment":
                Map<String, Object> riskData = (Map<String, Object>) result.getResult();
                double score = (Double) riskData.get("riskScore");
                String level = (String) riskData.get("riskLevel");
                return String.format("- 付款风险：%s (%.1f/1.0)\n", level, score);

            default:
                return String.format("- %s：%s\n",
                        toolName,
                        "SUCCESS".equals(result.getStatus()) ? "完成" : "失败");
        }
    }

    private ConversationResponse provideSafetyAdvice(ConversationSession session) {
        // 基于风险评估生成建议
        ConversationSession.RiskAssessment risk = session.getRiskAssessment();

        StringBuilder advice = new StringBuilder();
        advice.append("## 安全建议\n\n");

        if (risk != null) {
            advice.append("**风险评估等级：** ").append(risk.getRiskLevel()).append("\n");
            advice.append("**风险分数：** ").append(String.format("%.1f/1.0",
                    risk.getOverallRiskScore())).append("\n\n");

            if (!risk.getRiskIndicators().isEmpty()) {
                advice.append("**发现的风险因素：**\n");
                for (String indicator : risk.getRiskIndicators()) {
                    advice.append("- ").append(indicator).append("\n");
                }
                advice.append("\n");
            }

            advice.append("**建议采取的措施：**\n");
            for (String action : risk.getRecommendedActions()) {
                advice.append("1. ").append(action).append("\n");
            }
        } else {
            advice.append("由于信息不足，无法提供具体风险评估。\n");
            advice.append("**一般建议：**\n");
            advice.append("1. 始终使用平台担保交易\n");
            advice.append("2. 签订正式合同\n");
            advice.append("3. 保留所有沟通记录\n");
            advice.append("4. 大额交易前进行身份验证\n");
        }

        advice.append("\n如需进一步协助，请随时联系人工客服。");

        // 标记会话为完成
        session.setCompleted(true);
        session.setCurrentState(FraudPreventionState.COMPLETED);

        return ConversationResponse.builder()
                .message(advice.toString())
                .requiresUserInput(false)
                .sessionState("COMPLETED")
                .build();
    }

    private ConversationResponse escalateToHuman(ConversationSession session) {
        session.setRequiresHumanIntervention(true);
        session.setCurrentState(FraudPreventionState.ESCALATED_TO_HUMAN);

        // 创建人工交接包
        Map<String, Object> handoverPackage = createHandoverPackage(session);

        // 实际应调用通知服务
        log.info("Escalating session {} to human agent. Package: {}",
                session.getSessionId(), handoverPackage);

        return ConversationResponse.builder()
                .message("已为您转接人工反欺诈专员。专员将在2分钟内与您联系，请保持在线。\n" +
                        "在此之前，请勿进行任何资金操作。")
                .requiresUserInput(false)
                .sessionState("ESCALATED")
                .handoverData(handoverPackage)
                .build();
    }

    private Map<String, Object> createHandoverPackage(ConversationSession session) {
        Map<String, Object> map =  new HashMap<>();

        // 收集关键信息
        map.put("sessionId", session.getSessionId());
        map.put("userId", session.getUserId());
        map.put("riskLevel",
                session.getRiskAssessment() != null ?
                        session.getRiskAssessment().getRiskLevel() : "UNKNOWN");

        // 已验证的事实
        List<Map<String, Object>> verifiedFacts = new ArrayList<>();
        session.getFilledSlots().forEach((key, value) -> {
            if (value.isVerified()) {
                verifiedFacts.add(Map.of(
                        "fact", key,
                        "value", value.getValue(),
                        "confidence", value.getConfidence()
                ));
            }
        });
        map.put("verifiedFacts", verifiedFacts);

        // 工具调用结果
        map.put("toolResults", session.getToolCalls());

        // 风险评估摘要
        if (session.getRiskAssessment() != null) {
            map.put("riskSummary", Map.of(
                    "score", session.getRiskAssessment().getOverallRiskScore(),
                    "indicators", session.getRiskAssessment().getRiskIndicators()
            ));
        }

        // 建议行动
        map.put("recommendedActions", Arrays.asList(
                "立即联系用户确认细节",
                "检查是否有历史投诉记录",
                "必要时联系相关部门"
        ));

        return map;
    }

    private ConversationResponse requestClarification(List<String> clarificationPoints) {
        StringBuilder message = new StringBuilder();
        message.append("请澄清以下问题：\n");

        for (int i = 0; i < clarificationPoints.size(); i++) {
            message.append(i + 1).append(". ")
                    .append(clarificationPoints.get(i)).append("\n");
        }

        return ConversationResponse.builder()
                .message(message.toString())
                .requiresUserInput(true)
                .nextExpectedInput("clarification")
                .build();
    }

    private ConversationResponse completeConversation(ConversationSession session) {
        session.setCompleted(true);

        return ConversationResponse.builder()
                .message("风险评估已完成。如需进一步协助，请重新开始对话。")
                .requiresUserInput(false)
                .sessionState("COMPLETED")
                .build();
    }

    private ConversationResponse fallbackResponse(ConversationSession session) {
        return ConversationResponse.builder()
                .message("抱歉，我没有理解您的意思。您可以重新描述问题，或者输入'人工客服'联系专员。")
                .requiresUserInput(true)
                .sessionState(session.getCurrentState().name())
                .build();
    }

    private void createRollbackPoint(ConversationSession session, String trigger) {
        // 创建会话快照
        ConversationSession.SessionSnapshot snapshot =
                ConversationSession.SessionSnapshot.builder()
                        .snapshotId(UUID.randomUUID().toString())
                        .state(session.getCurrentState())
                        .slotsSnapshot(new HashMap<>(session.getFilledSlots()))
                        .timestamp(LocalDateTime.now())
                        .trigger(trigger)
                        .build();

        session.getRollbackPoints().add(snapshot);

        // 限制回滚点数量
        if (session.getRollbackPoints().size() > 10) {
            session.getRollbackPoints().remove(0);
        }
    }

    public boolean rollbackToPoint(String sessionId, String snapshotId) {
        ConversationSession session = sessionStore.get(sessionId);
        if (session == null) {
            return false;
        }

        // 查找快照
        Optional<ConversationSession.SessionSnapshot> snapshotOpt =
                session.getRollbackPoints().stream()
                        .filter(s -> s.getSnapshotId().equals(snapshotId))
                        .findFirst();

        if (snapshotOpt.isPresent()) {
            ConversationSession.SessionSnapshot snapshot = snapshotOpt.get();

            // 恢复状态
            session.setCurrentState(snapshot.getState());
            session.setFilledSlots(new HashMap<>(snapshot.getSlotsSnapshot()));

            // 移除该快照之后的所有回滚点
            int index = session.getRollbackPoints().indexOf(snapshot);
            if (index >= 0) {
                session.getRollbackPoints().subList(index + 1,
                        session.getRollbackPoints().size()).clear();
            }

            return true;
        }

        return false;
    }

    // 内部类定义
    public enum ActionType {
        CALL_TOOLS,
        PROVIDE_ADVICE,
        ESCALATE_TO_HUMAN,
        CLARIFY_INFORMATION,
        COMPLETE_CONVERSATION
    }

    @Data
    @Builder
    public static class ConversationAction {
        private ActionType actionType;
        private List<String> toolsToCall;
        private List<String> clarificationPoints;
        private FraudPreventionState nextState;
    }

    @Data
    @Builder
    public static class ConversationResponse {
        private String message;
        private boolean requiresUserInput;
        private String nextExpectedInput;
        private String sessionState;
        private Map<String, Object> toolResults;
        private Map<String, Object> handoverData;
        private LocalDateTime timestamp;
    }
}
