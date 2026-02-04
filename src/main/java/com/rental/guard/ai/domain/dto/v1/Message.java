/**
 * @author qkcao
 * @date 2026/1/28 15:22
 */
package com.rental.guard.ai.domain.dto.v1;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.springframework.data.annotation.Transient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 消息实体 - 支持复杂消息类型
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {

    public enum MessageType {
        USER_INPUT,           // 用户输入
        AGENT_RESPONSE,       // 智能体响应
        SYSTEM_NOTIFICATION,  // 系统通知
        FILE_UPLOAD,          // 文件上传
        ACTION_REQUIRED,      // 需要操作
        ERROR                 // 错误消息
    }

    public enum ContentType {
        TEXT,                 // 文本
        JSON,                 // JSON数据
        RICH_TEXT,            // 富文本（HTML/Markdown）
        STRUCTURED_DATA       // 结构化数据
    }

    private String messageId;
    private String sessionId;
    private String sender;            // 发送者：USER, AGENT, SYSTEM
    private MessageType messageType;
    private ContentType contentType;
    private Object content;           // 消息内容
    private Map<String, Object> metadata = new HashMap<>();
    private LocalDateTime timestamp;
    private String parentMessageId;   // 父消息ID，用于消息线程
    private String threadId;          // 消息线程ID

    // 非持久化字段
    @Transient
    private Map<String, Object> processingContext = new HashMap<>();

    public Message() {
        this.messageId = "msg_" + UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
    }

    public Message(String sessionId, String sender, MessageType messageType, Object content) {
        this();
        this.sessionId = sessionId;
        this.sender = sender;
        this.messageType = messageType;
        this.content = content;
        this.contentType = ContentType.TEXT;

        // 自动设置元数据
        this.metadata.put("createdAt", timestamp.toString());
        this.metadata.put("messageType", messageType.name());
    }

    public static Message createUserMessage(String sessionId, String userInput) {
        Message message = new Message(sessionId, "USER", MessageType.USER_INPUT, userInput);
        message.metadata.put("inputType", "text");
        message.metadata.put("language", "zh-CN");
        return message;
    }

    public static Message createAgentMessage(String sessionId, AgentResponse agentResponse) {
        Message message = new Message(sessionId, "AGENT", MessageType.AGENT_RESPONSE, agentResponse);
        message.metadata.put("riskLevel", agentResponse.getRiskLevel());
        message.metadata.put("confidence", agentResponse.getConfidence());
        message.contentType = ContentType.STRUCTURED_DATA;
        return message;
    }

    public static Message createFileUploadMessage(String sessionId, String fileName, String fileType, String fileUrl) {
        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("fileName", fileName);
        fileInfo.put("fileType", fileType);
        fileInfo.put("fileUrl", fileUrl);
        fileInfo.put("uploadedAt", LocalDateTime.now().toString());

        Message message = new Message(sessionId, "USER", MessageType.FILE_UPLOAD, fileInfo);
        message.contentType = ContentType.JSON;
        return message;
    }

    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }

    public Object getMetadata(String key) {
        return this.metadata.get(key);
    }

    public String getContentAsString() {
        if (content instanceof String) {
            return (String) content;
        } else if (content instanceof AgentResponse) {
            return ((AgentResponse) content).getFormattedResponse();
        } else {
            return content != null ? content.toString() : "";
        }
    }

    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }

    @Override
    public String toString() {
        return String.format("%s [%s]: %s",
                sender,
                timestamp.toLocalTime(),
                getContentAsString().length() > 100 ?
                        getContentAsString().substring(0, 100) + "..." :
                        getContentAsString()
        );
    }
}
