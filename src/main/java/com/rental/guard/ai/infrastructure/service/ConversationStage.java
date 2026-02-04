/**
 * @author qkcao
 * @date 2025/12/31 15:37
 */
package com.rental.guard.ai.infrastructure.service;

import lombok.Data;

import java.util.List;

@Data
public class ConversationStage {
    private String stageType;
    private List<String> lines;
    private String startLine;
    private int riskScore; // 0-10
    private List<String> riskIndicators;
}
