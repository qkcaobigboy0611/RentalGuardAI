/**
 * @author qkcao
 * @date 2026/1/30 15:56
 */
package com.rental.guard.ai.domain.enum1;

public enum FraudPreventionState {
    // 初始状态
    INITIAL,

    // 验证流程状态
    VERIFY_PROPERTY,
    VERIFY_LANDLORD,
    VERIFY_TENANT,

    // 风险评估状态
    ASSESS_PAYMENT_RISK,
    ASSESS_CONTRACT_RISK,
    ASSESS_OVERALL_RISK,

    // 建议与确认状态
    PROVIDE_SAFETY_ADVICE,
    CONFIRM_ACTION,

    // 终止状态
    COMPLETED,
    ESCALATED_TO_HUMAN,
    FAILED
}
