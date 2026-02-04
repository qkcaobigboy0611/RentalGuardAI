/**
 * @author qkcao
 * @date 2026/1/30 16:04
 */
package com.rental.guard.ai.domain.dto.v1;


import com.rental.guard.ai.domain.enum1.FraudPreventionState;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 会话状态管理
 */
@Data
@Builder
public class ConversationSession {
    private String sessionId;
    private String userId;
    private LocalDateTime startTime;
    private LocalDateTime lastActivity;

    // 当前状态
    private FraudPreventionState currentState;

    // 已填充的槽位
    private Map<String, SlotValue> filledSlots;

    // 对话历史
    private List<ConversationTurn> conversationHistory;

    // 工具调用记录
    private List<ToolCallRecord> toolCalls;

    // 风险评估结果
    private RiskAssessment riskAssessment;

    // 流程控制标志
    private boolean requiresHumanIntervention;
    private boolean isCompleted;

    // 可回滚点
    private List<SessionSnapshot> rollbackPoints;

    @Data
    @Builder
    public static class SlotValue {
        private String slotName;
        private Object value;
        private LocalDateTime collectedAt;
        private String source; // "user_input", "tool_result", "system_inferred"
        private Double confidence;
        private boolean verified;
    }

    @Data
    @Builder
    public static class ConversationTurn {
        private String userInput;
        private String systemResponse;
        private String intent;
        private LocalDateTime timestamp;
        private Map<String, Object> extractedSlots;
    }

    @Data
    @Builder
    public static class ToolCallRecord {
        private String toolName;
        private Map<String, Object> parameters;
        private Object result;
        private LocalDateTime timestamp;
        private Double confidence;
        private String status; // SUCCESS, FAILED, TIMEOUT
    }

    @Data
    @Builder
    public static class RiskAssessment {
        private Double overallRiskScore;
        private Map<String, Double> riskFactors; // 各风险维度分数
        private String riskLevel; // LOW, MEDIUM, HIGH
        private List<String> riskIndicators;
        private List<String> recommendedActions;
    }

    @Data
    @Builder
    public static class SessionSnapshot {
        private String snapshotId;
        private FraudPreventionState state;
        private Map<String, SlotValue> slotsSnapshot;
        private LocalDateTime timestamp;
        private String trigger; // 什么触发了快照
    }
}
