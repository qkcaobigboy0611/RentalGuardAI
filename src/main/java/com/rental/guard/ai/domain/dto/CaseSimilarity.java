/**
 * @author qkcao
 * @date 2025/9/18 18:34
 */
package com.rental.guard.ai.domain.dto;

import com.rental.guard.ai.infrastructure.po.PoFraudTrainingCase;

public class CaseSimilarity {
    public PoFraudTrainingCase caseItem;
    public float similarity;

    public CaseSimilarity(PoFraudTrainingCase caseItem, float similarity) {
        this.caseItem = caseItem;
        this.similarity = similarity;
    }
}
