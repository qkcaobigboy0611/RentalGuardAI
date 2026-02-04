/**
 * @author qkcao
 * @date 2026/1/23 10:47
 */
package com.rental.guard.ai.domain.dto;

import lombok.Getter;

/**
 * 执行策略枚举
 */
@Getter
public enum ExecutionStrategyEnum {
    LINEAR("线性执行", "任务按顺序依次执行"),
    PARALLEL("并行执行", "任务尽可能并行执行"),
    CONDITIONAL("条件执行", "根据条件选择执行路径"),
    ITERATIVE("迭代执行", "循环执行某些任务"),
    BATCH("批量执行", "批量处理任务"),
    STREAMING("流式执行", "流式处理任务"),
    HYBRID("混合执行", "多种策略组合");

    private final String displayName;
    private final String description;

    ExecutionStrategyEnum(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
