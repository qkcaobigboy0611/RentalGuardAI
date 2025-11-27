/**
 * @author qkcao
 * @date 2025/9/16 18:23
 */
package com.rental.guard.ai.domain.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 欺诈检测统计DTO
 */
@Data
@Builder
public class FraudDetectionStatistics {

    private Integer totalDetections;

    private Integer fraudDetections;

    private Integer highRiskDetections;

    private Long averageCostTimeMs;

    private Double fraudRate;

    private Integer days;
}
