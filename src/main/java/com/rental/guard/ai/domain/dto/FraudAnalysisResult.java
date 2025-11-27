/**
 * @author qkcao
 * @date 2025/9/16 18:37
 */
package com.rental.guard.ai.domain.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 欺诈分析结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAnalysisResult {

    @JSONField(name = "is_fraud")
    private Boolean isFraud;

    @JSONField(name = "risk_score")
    private BigDecimal riskScore;

    @JSONField(name = "fraud_type")
    private String fraudType;

    @JSONField(name = "confidence")
    private BigDecimal confidence;

    @JSONField(name = "reason")
    private String reason;

    @JSONField(name = "keywords")
    private List<String> keywords;

    @JSONField(name = "suspicious_user_id")
    private String suspiciousUserId;

    @JSONField(name = "ip1_address")
    private String ip1Address;

    @JSONField(name = "ip2_address")
    private String ip2Address;



    private Long aiCostTime;

    private Boolean analysisSuccess;

    private String errorMessage;

    public static FraudAnalysisResult failure(String errorMessage, Long costTime) {
        return FraudAnalysisResult.builder().analysisSuccess(false).errorMessage(errorMessage)
                .aiCostTime(costTime).build();
    }

    public static FraudAnalysisResult success(Boolean isFraud, BigDecimal riskScore, String fraudType,
                                              BigDecimal confidence, String reason, List<String> keywords, String suspiciousUserId, Long costTime) {
        return FraudAnalysisResult.builder().analysisSuccess(true).isFraud(isFraud).riskScore(riskScore)
                .fraudType(fraudType).confidence(confidence).reason(reason).keywords(keywords)
                .suspiciousUserId(suspiciousUserId).aiCostTime(costTime).build();
    }
}

