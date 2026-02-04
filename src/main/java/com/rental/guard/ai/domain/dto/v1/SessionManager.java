/**
 * @author qkcao
 * @date 2026/1/28 15:22
 */
package com.rental.guard.ai.domain.dto.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.rental.guard.ai.domain.service.v1.SessionContext;
import lombok.Data;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理核心类 - 基于Redis的分布式会话管理
 */
@Data
@RedisHash("SmartAgentSession")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionManager implements Serializable {

    private String sessionId;
    private String userId;
    private String tenantId; // 租户ID，支持多租户
    private SessionState state;
    private Date createdAt;
    private Date lastActiveAt;
    private Map<String, Object> sessionAttributes;
    private List<Message> messageHistory;
    private SessionContext context;
    private Map<String, Integer> scenarioCounter;
    private String currentScenario;
    private String riskProfile; // 用户风险画像

    private Long ttl = 1800L; // 30分钟过期

    // 非持久化字段
    @JsonIgnore
    private transient Map<String, Object> tempAttributes = new ConcurrentHashMap<>();

    public SessionManager() {
        this.sessionAttributes = new HashMap<>();
        this.messageHistory = new LinkedList<>();
        this.context = new SessionContext();
        this.scenarioCounter = new HashMap<>();
        this.state = SessionState.ACTIVE;
        this.createdAt = new Date();
        this.lastActiveAt = new Date();
    }

    public SessionManager(String sessionId, String userId) {
        this();
        this.sessionId = sessionId;
        this.userId = userId;
        this.sessionId = generateSessionId(userId);
    }

    private String generateSessionId(String userId) {
        return "sess_" + userId + "_" +
                System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8);
    }

    public void addMessage(Message message) {
        this.messageHistory.add(message);
        this.lastActiveAt = new Date();

        // 限制历史消息数量，防止内存溢出
        if (messageHistory.size() > 1000) {
            messageHistory = messageHistory.subList(messageHistory.size() - 40, messageHistory.size());
        }
    }

    public List<Message> getRecentMessages(int count) {
        if (messageHistory.size() <= count) {
            return new ArrayList<>(messageHistory);
        }
        return new ArrayList<>(messageHistory.subList(
                messageHistory.size() - count, messageHistory.size()));
    }

    public void incrementScenarioCount(String scenario) {
        scenarioCounter.put(scenario, scenarioCounter.getOrDefault(scenario, 0) + 1);
        this.currentScenario = scenario;
    }

    public void updateRiskProfile(String riskLevel, String scenario) {
        // 根据风险等级更新用户风险画像
        if (riskProfile == null) {
            riskProfile = "初始用户";
        }

        switch (riskLevel) {
            case "极高":
                riskProfile = "高风险敏感用户";
                break;
            case "高":
                riskProfile = "风险警惕用户";
                break;
            case "中":
                riskProfile = "普通风险用户";
                break;
            case "低":
                riskProfile = "低风险用户";
                break;
        }
    }

    public boolean isExpired() {
        // Date -> Instant -> LocalDateTime（系统默认时区）
        LocalDateTime lastActive = lastActiveAt
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        return lastActive.isBefore(LocalDateTime.now().minusMinutes(30));
    }

    public String getSessionSummary() {
        return String.format(
                "Session[ID=%s, User=%s, Scenario=%s, Messages=%d, State=%s, RiskProfile=%s]",
                sessionId, userId, currentScenario, messageHistory.size(), state, riskProfile
        );
    }

    /**
     * 获取会话上下文摘要，用于大模型上下文
     */
    public String getContextSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("会话ID: ").append(sessionId).append("\n");
        sb.append("用户ID: ").append(userId).append("\n");
        sb.append("当前场景: ").append(currentScenario).append("\n");
        sb.append("风险画像: ").append(riskProfile).append("\n");
        sb.append("历史场景统计: ").append(scenarioCounter).append("\n");

        // 添加最近5条消息
        sb.append("最近对话:\n");
        List<Message> recent = getRecentMessages(5);
        for (Message msg : recent) {
            sb.append("  - ").append(msg.getSender()).append(": ")
                    .append(msg.getContent().toString().length() > 50 ?
                            msg.getContent().toString().substring(0, 50) + "..." :
                            msg.getContent().toString())
                    .append("\n");
        }

        return sb.toString();
    }
}
