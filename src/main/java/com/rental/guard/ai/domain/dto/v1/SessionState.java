/**
 * @author qkcao
 * @date 2026/1/28 15:32
 */
package com.rental.guard.ai.domain.dto.v1;

/**
 * 会话状态枚举
 */
public enum SessionState {
    ACTIVE("活跃"),
    PAUSED("暂停"),
    COMPLETED("已完成"),
    EXPIRED("已过期"),
    ERROR("错误");

    private final String description;

    SessionState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
