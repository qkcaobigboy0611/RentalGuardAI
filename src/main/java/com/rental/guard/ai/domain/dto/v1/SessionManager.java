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
    private double userExperienceScore = 0.0; // 用户经验分
    private RiskStatus riskStatus = RiskStatus.SAFE;

    private Long ttl = 1800L; // 30分钟过期

    // 优化：使用更科学的风险状态枚举
    public enum RiskStatus {
        SAFE, ALERT, DANGER, BLACKLIST
    }

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

    /**
     * 优化点 1：消息列表优化
     * 建议将消息历史限制在最近的 20-30 条，历史消息应归档至持久化数据库
     */
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

    /**
     * 优化点 2：内聚风险校准逻辑 (针对你提供的 calibrateRiskLevel)
     * 将风险评估逻辑转化为一种“信任分”累加
     */
    public void calibrateRiskLevel(AgentResponse response) {
        int totalInteractions = scenarioCounter.values().stream().mapToInt(Integer::intValue).sum();

        // 动态计算用户信任权重
        double trustFactor = Math.min(1.0, totalInteractions / 50.0); // 互动越多，信任度越高

        String currentLevel = response.getRiskLevel();

        if (trustFactor > 0.2 && "高风险".equals(currentLevel)) {
            // 对资深用户（互动 > 10次）进行风险平滑处理
            response.setRiskLevel("中高风险");
            response.appendDetailedAnalysis("\n💡 提示：基于您的历史使用习惯，系统已为您过滤掉部分常规警示。");
        }

        // 更新用户经验分
        this.userExperienceScore = trustFactor * 100;
    }

    /**
     * 优化点 3：重构 RiskProfile 更新逻辑
     */
    public void updateRiskProfile(AgentResponse response) {
        double currentRiskScore = response.getRiskScore();

        // 自动演进用户画像
        if (currentRiskScore > 0.8) {
            this.riskStatus = RiskStatus.DANGER;
            this.riskProfile = "易受损用户 - 频繁遭遇霸王条款";
        } else if (this.userExperienceScore > 50) {
            this.riskProfile = "资深租客 - 具备基础法律辨析力";
        }
    }
}
