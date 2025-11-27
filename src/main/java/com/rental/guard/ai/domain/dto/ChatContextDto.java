/**
 * @author qkcao
 * @date 2025/9/16 18:33
 */
package com.rental.guard.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 聊天上下文DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatContextDto {

    private Long channelId;

    private List<ChatMessageDto> messages;

    private String formattedContext;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessageDto {
        private String senderId;
        private String senderType;
        private String content;
        private String messageType;
        private Date createTime;
    }
}
