/**
 * @author qkcao
 * @date 2025/9/16 18:43
 */
package com.rental.guard.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI分析请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIAnalysisRequest {

    private String analysisType;

    private String content;

    private String prompt;

    private Integer maxTokens;

    private Double temperature;

    private String ip1;

    private String ip2;

    public static AIAnalysisRequest fraudDetection(String chatContext) {
        return AIAnalysisRequest.builder().analysisType("fraud_detection").content(chatContext)
                .maxTokens(1000).temperature(0.1).build();
    }
}
