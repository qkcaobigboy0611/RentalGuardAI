/**
 * @author qkcao
 * @date 2026/1/27 10:42
 */
package com.rental.guard.ai.domain.service.v1;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体响应
 */
@Data
public class AgentResponseNo {
    private String sessionId;
    private String response;
    private FraudAnalysisResult analysisResult;
    private AgentDecision decision;
    private AgentAction action;
    private LocalDateTime timestamp;

    public static AgentResponseNo failure(String errorMessage) {
        AgentResponseNo response = new AgentResponseNo();
        response.setResponse("系统错误: " + errorMessage);
        return response;
    }
}
