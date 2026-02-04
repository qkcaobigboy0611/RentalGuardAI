/**
 * @author qkcao
 * @date 2026/1/23 17:49
 */
package com.rental.guard.ai.domain.dto;

// OllamaException.java
public class OllamaException extends RuntimeException {
    public OllamaException(String message) {
        super(message);
    }

    public OllamaException(String message, Throwable cause) {
        super(message, cause);
    }
}
