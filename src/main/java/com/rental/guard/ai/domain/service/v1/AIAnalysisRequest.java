/**
 * @author qkcao
 * @date 2026/1/27 10:41
 */
package com.rental.guard.ai.domain.service.v1;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI分析请求
 */
@Data
public class AIAnalysisRequest {
    private List<String> conversation;
    private RiskFeatures riskFeatures;
    private KnowledgeData relevantKnowledge;
    private LocalDateTime timestamp;
}
