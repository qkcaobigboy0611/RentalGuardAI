/**
 * @author qkcao
 * @date 2026/1/27 10:28
 */
package com.rental.guard.ai.domain.service.v1;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class KnowledgeData {
    private List<FraudRule> relevantRules = new ArrayList<>();
    private List<FraudPattern> relevantPatterns = new ArrayList<>();

    public void addRelevantRule(FraudRule rule) {
        relevantRules.add(rule);
    }

    public void addRelevantPattern(FraudPattern pattern) {
        relevantPatterns.add(pattern);
    }
}
