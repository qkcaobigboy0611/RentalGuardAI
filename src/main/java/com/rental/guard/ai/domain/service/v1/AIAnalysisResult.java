/**
 * @author qkcao
 * @date 2026/1/27 10:41
 */
package com.rental.guard.ai.domain.service.v1;

import lombok.Data;

/**
 * AI分析结果
 */
@Data
public class AIAnalysisResult {
    private boolean success;
    private String content;
    private String errorMessage;
}
