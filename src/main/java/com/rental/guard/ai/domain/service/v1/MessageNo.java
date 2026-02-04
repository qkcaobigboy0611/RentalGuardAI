/**
 * @author qkcao
 * @date 2026/1/27 10:31
 */
package com.rental.guard.ai.domain.service.v1;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageNo {
    private String content;
    private MessageType type;
    private LocalDateTime timestamp;
}
