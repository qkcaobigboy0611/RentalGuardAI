/**
 * @author qkcao
 * @date 2026/1/27 10:40
 */
package com.rental.guard.ai.domain.service.v1;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 风险特征
 */
@Data
public class RiskFeatures {
    private List<String> keywords = new ArrayList<>();
    private int urgencyScore;      // 紧急程度
    private int pressureScore;     // 施压程度
    private int conversationLength;
    private int repeatQuestionsCount;
}
