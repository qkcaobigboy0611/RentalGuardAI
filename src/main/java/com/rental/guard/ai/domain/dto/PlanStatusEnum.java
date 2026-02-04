/**
 * @author qkcao
 * @date 2026/1/23 10:46
 */
package com.rental.guard.ai.domain.dto;

import lombok.Getter;

/**
 * 计划状态枚举
 */
@Getter
public enum PlanStatusEnum {
    DRAFT("草稿", "计划正在创建中"),
    PLANNING("规划中", "正在生成任务序列"),
    READY("就绪", "计划已生成，等待执行"),
    VALIDATING("验证中", "正在验证计划可行性"),
    SCHEDULED("已调度", "计划已安排执行时间"),
    EXECUTING("执行中", "计划正在执行"),
    PAUSED("已暂停", "计划被暂停"),
    COMPLETED("已完成", "计划所有任务完成"),
    PARTIALLY_COMPLETED("部分完成", "部分任务完成，部分失败或跳过"),
    FAILED("失败", "计划执行失败"),
    CANCELLED("已取消", "计划被取消"),
    EXPIRED("已过期", "计划超过有效期"),
    ARCHIVED("已归档", "计划已归档");

    private final String displayName;
    private final String description;

    PlanStatusEnum(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public boolean isFinal() {
        return this == COMPLETED ||
                this == FAILED ||
                this == CANCELLED ||
                this == EXPIRED ||
                this == ARCHIVED;
    }

    public boolean isActive() {
        return this == EXECUTING ||
                this == SCHEDULED ||
                this == VALIDATING;
    }
}
