/**
 * @author qkcao
 * @date 2026/1/30 15:57
 */
package com.rental.guard.ai.domain.enum1;

public enum FraudPreventionEvent {
    // 用户输入事件
    USER_PROVIDED_DETAILS,
    USER_CONFIRMED,
    USER_DECLINED,
    USER_REQUESTED_HUMAN,

    // 系统验证事件
    PROPERTY_VERIFIED,
    PARTY_VERIFIED,
    PAYMENT_VERIFIED,

    // 风险评估事件
    LOW_RISK_DETECTED,
    MEDIUM_RISK_DETECTED,
    HIGH_RISK_DETECTED,

    // 流程控制事件
    NEXT_STEP,
    PREVIOUS_STEP,
    COMPLETE_FLOW,
    ESCALATE_FLOW
}
