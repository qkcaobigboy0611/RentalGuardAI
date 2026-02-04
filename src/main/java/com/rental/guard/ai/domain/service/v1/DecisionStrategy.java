/**
 * @author qkcao
 * @date 2026/1/27 10:34
 */
package com.rental.guard.ai.domain.service.v1;

import lombok.Getter;

@Getter
public enum DecisionStrategy {
    CONTINUE,          // 继续对话
    WARN_AND_MONITOR,  // 警告并监控
    BLOCK_AND_WARN     // 阻断并警告
}
