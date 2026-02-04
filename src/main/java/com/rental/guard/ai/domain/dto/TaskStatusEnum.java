/**
 * @author qkcao
 * @date 2026/1/23 10:41
 */
package com.rental.guard.ai.domain.dto;

import com.azure.core.annotation.Get;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 任务状态枚举
 */
@Getter
public enum TaskStatusEnum {
    CREATED("已创建", "任务已创建但未进入执行队列"),
    PENDING("等待执行", "任务在队列中等待执行"),
    READY("就绪", "所有依赖已满足，等待资源分配"),
    SCHEDULED("已调度", "已分配到执行器"),
    RUNNING("执行中", "正在执行"),
    PAUSED("已暂停", "任务被暂停"),
    COMPLETED("已完成", "任务成功完成"),
    FAILED("失败", "任务执行失败"),
    CANCELLED("已取消", "任务被取消"),
    SKIPPED("已跳过", "任务被跳过"),
    TIMEOUT("超时", "任务执行超时"),
    RETRYING("重试中", "任务正在重试");

    private final String displayName;
    private final String description;

    TaskStatusEnum(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public boolean isFinal() {
        return this == COMPLETED ||
                this == FAILED ||
                this == CANCELLED ||
                this == SKIPPED ||
                this == TIMEOUT;
    }

    public boolean isActive() {
        return this == RUNNING ||
                this == RETRYING ||
                this == SCHEDULED;
    }

    public boolean canTransitionTo(TaskStatusEnum newStatus) {
        // 定义状态转移规则
        Map<TaskStatusEnum, Set<TaskStatusEnum>> allowedTransitions = Map.of(
                CREATED, Set.of(PENDING, CANCELLED),
                PENDING, Set.of(READY, CANCELLED),
                READY, Set.of(SCHEDULED, CANCELLED),
                SCHEDULED, Set.of(RUNNING, CANCELLED),
                RUNNING, Set.of(COMPLETED, FAILED, PAUSED, TIMEOUT),
                PAUSED, Set.of(RUNNING, CANCELLED),
                FAILED, Set.of(RETRYING, CANCELLED),
                RETRYING, Set.of(RUNNING, CANCELLED)
        );

        return allowedTransitions.getOrDefault(this, Collections.emptySet())
                .contains(newStatus);
    }
}
