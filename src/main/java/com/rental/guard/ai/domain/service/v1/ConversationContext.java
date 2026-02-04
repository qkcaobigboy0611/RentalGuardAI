/**
 * @author qkcao
 * @date 2026/1/27 10:30
 */
package com.rental.guard.ai.domain.service.v1;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ConversationContext {
    private String sessionId;
    private List<MessageNo> messageNos;
    private LocalDateTime createdTime;
    private LocalDateTime lastUpdated;

    public ConversationContext(String sessionId) {
        this.sessionId = sessionId;
        this.messageNos = new ArrayList<>();
        this.createdTime = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
    }

    public void addMessage(String content, MessageType type) {
        MessageNo messageNo = new MessageNo();
        messageNo.setContent(content);
        messageNo.setType(type);
        messageNo.setTimestamp(LocalDateTime.now());
        messageNos.add(messageNo);
        lastUpdated = LocalDateTime.now();
    }

    public List<String> getAllMessages() {
        List<String> result = new ArrayList<>();
        for (MessageNo msg : messageNos) {
            result.add("[" + msg.getType() + "] " + msg.getContent());
        }
        return result;
    }

    public String getLatestMessage() {
        if (messageNos.isEmpty()) return "";
        return messageNos.get(messageNos.size() - 1).getContent();
    }

    public int getMessageCount() {
        return messageNos.size();
    }

    public List<String> getMessageNos() {
        List<String> result = new ArrayList<>();
        for (MessageNo msg : messageNos) {
            result.add(msg.getContent());
        }
        return result;
    }
}
