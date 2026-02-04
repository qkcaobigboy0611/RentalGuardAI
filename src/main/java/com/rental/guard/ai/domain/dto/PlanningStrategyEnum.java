/**
 * @author qkcao
 * @date 2026/1/22 19:01
 */
package com.rental.guard.ai.domain.dto;

import lombok.Getter;

@Getter
public enum PlanningStrategyEnum {
    LINEAR("线性执行", "任务按顺序执行"),
    PARALLEL("并行执行", "任务可以并行执行"),
    CONDITIONAL("条件分支", "根据条件选择执行路径"),
    ITERATIVE("迭代执行", "循环执行某些任务"),
    BATCH("批量处理", "批量处理数据"),
    MONITORING("监控模式", "持续监控状态");

    private String displayName;
    private String description;

    PlanningStrategyEnum(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

}
