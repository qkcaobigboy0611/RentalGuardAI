/**
 * @author qkcao
 * @date 2026/1/27 10:29
 */
package com.rental.guard.ai.domain.service.v1;

import lombok.Data;

import java.util.List;

@Data
public class FraudRule {
    private String id;
    private String description;
    private double riskWeight;
    private List<String> keywords;

    public FraudRule(String id, String description, double riskWeight, List<String> keywords) {
        this.id = id;
        this.description = description;
        this.riskWeight = riskWeight;
        this.keywords = keywords;
    }
}
