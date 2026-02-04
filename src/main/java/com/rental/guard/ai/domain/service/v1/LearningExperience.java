/**
 * @author qkcao
 * @date 2026/1/27 10:39
 */
package com.rental.guard.ai.domain.service.v1;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningExperience {
    private String sessionId;
    private RiskFeatures features;
    private FraudAnalysisResult analysis;
    private AgentDecision decision;
    private LocalDateTime timestamp;
}
