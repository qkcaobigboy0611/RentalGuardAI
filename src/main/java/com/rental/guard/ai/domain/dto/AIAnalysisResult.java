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
 * AI分析结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIAnalysisResult {

    private Boolean success;

    private String content;

    private Integer totalTokens;

    private Long costTimeMs;

    private String errorMessage;

    public static AIAnalysisResult success(String content, Integer totalTokens, Long costTimeMs) {
        return AIAnalysisResult.builder().success(true).content(content).totalTokens(totalTokens)
                .costTimeMs(costTimeMs).build();
    }

    public static AIAnalysisResult failure(String errorMessage, Long costTimeMs) {
        return AIAnalysisResult.builder().success(false).errorMessage(errorMessage)
                .costTimeMs(costTimeMs).build();
    }
}
