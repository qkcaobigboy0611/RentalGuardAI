/**
 * @author qkcao
 * @date 2026/1/22 19:10
 */
package com.rental.guard.ai.domain.dto;

import lombok.Data;

@Data
public class PlanningConstraints {
    private Boolean timeCritical = false;       // 是否时间紧迫
    private Boolean resourceConstrained = false; // 是否资源受限
    private Boolean requiresHighAvailability = false; // 是否需要高可用
    private int maxConcurrentTasks = 10;        // 最大并发任务数
    private int timeoutMinutes = 30;           // 超时时间
}
