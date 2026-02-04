/**
 * @author qkcao
 * @date 2026/1/27 10:42
 */
package com.rental.guard.ai.domain.service.v1;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 欺诈分析结果
 */
@Data
public class FraudAnalysisResult {
    private boolean isFraud;
    private double riskScore;
    private String fraudType;
    private double confidence;
    private List<String> reasons = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();
    private boolean analysisSuccess;

    public static FraudAnalysisResult failure(String errorMessage) {
        FraudAnalysisResult result = new FraudAnalysisResult();
        result.setAnalysisSuccess(false);
        return result;
    }
}
