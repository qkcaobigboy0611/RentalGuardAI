/**
 * @author qkcao
 * @date 2026/1/27 10:34
 */
package com.rental.guard.ai.domain.service.v1;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentDecision {
    private double riskScore;
    private RiskLevel riskLevel;
    private DecisionStrategy strategy;
    private List<String> recommendations;
    private LocalDateTime timestamp;
}
