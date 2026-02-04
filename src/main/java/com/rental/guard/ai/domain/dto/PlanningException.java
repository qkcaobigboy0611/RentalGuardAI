/**
 * @author qkcao
 * @date 2026/1/22 19:05
 */
package com.rental.guard.ai.domain.dto;

public class PlanningException extends RuntimeException{
    public PlanningException(String message) {
        super(message);
    }

    public PlanningException(String message, Throwable cause) {
        super(message, cause);
    }
}
