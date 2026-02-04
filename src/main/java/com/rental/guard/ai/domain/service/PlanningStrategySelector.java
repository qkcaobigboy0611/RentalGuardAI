/**
 * @author qkcao
 * @date 2026/1/22 19:09
 */
package com.rental.guard.ai.domain.service;

import com.rental.guard.ai.domain.dto.PlanningConstraints;
import com.rental.guard.ai.domain.dto.PlanningStrategyEnum;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
public class PlanningStrategySelector {

    /**
     * 选择规划策略
     */
    public PlanningStrategyEnum selectStrategy(IntentRecognitionModule.AgentIntent intent) {
        // 基于意图类型选择策略
        switch (intent.getIntentType()) {
            case SINGLE_ANALYSIS:
                return PlanningStrategyEnum.LINEAR;

            case USER_INVESTIGATION:
                return PlanningStrategyEnum.CONDITIONAL;

            case REAL_TIME_MONITORING:
                return PlanningStrategyEnum.MONITORING;

            case REPORT_GENERATION:
                return PlanningStrategyEnum.LINEAR;

            case RULE_CONFIGURATION:
                return PlanningStrategyEnum.LINEAR;

            case BATCH_PROCESSING:
                return PlanningStrategyEnum.PARALLEL;

            case DATA_QUERY:
                return PlanningStrategyEnum.PARALLEL;

            case ALERT_MANAGEMENT:
                return PlanningStrategyEnum.CONDITIONAL;

            default:
                return PlanningStrategyEnum.LINEAR;
        }
    }

    /**
     * 基于约束选择策略
     */
    public PlanningStrategyEnum selectStrategyWithConstraints(
            IntentRecognitionModule.AgentIntent intent,
            PlanningConstraints constraints) {

        PlanningStrategyEnum baseStrategy = selectStrategy(intent);

        // 根据约束调整策略
        if (Boolean.TRUE.equals(constraints.getTimeCritical()) && !baseStrategy.equals(PlanningStrategyEnum.PARALLEL)) {
            return PlanningStrategyEnum.PARALLEL;
        }

        if (Boolean.TRUE.equals(constraints.getResourceConstrained()) && baseStrategy.equals(PlanningStrategyEnum.PARALLEL)) {
            return PlanningStrategyEnum.LINEAR;
        }

        if (Boolean.TRUE.equals(constraints.getRequiresHighAvailability()) && baseStrategy.equals(PlanningStrategyEnum.LINEAR)) {
            return PlanningStrategyEnum.CONDITIONAL;
        }

        return baseStrategy;
    }

}
