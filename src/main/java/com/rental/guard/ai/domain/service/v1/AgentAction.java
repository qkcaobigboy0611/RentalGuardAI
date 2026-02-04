/**
 * @author qkcao
 * @date 2026/1/27 10:36
 */
package com.rental.guard.ai.domain.service.v1;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentAction {
    private ActionType type;
    private String message;
    private AgentDecision decision;
    private LogLevel logLevel;
    private LocalDateTime timestamp;
}
