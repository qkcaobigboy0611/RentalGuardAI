/**
 * @author qkcao
 * @date 2025/12/31 15:45
 */
package com.rental.guard.ai.infrastructure.service;

import com.rental.guard.ai.infrastructure.po.PoFraudTrainingCase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量文档对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorDocument {
    private String id;                  // 文档ID
    private String text;                // 原始文本
    private List<Float> vector;         // 向量表示
    private Map<String, Object> metadata; // 元数据
    private LocalDateTime createTime;

    /**
     * 从PO对象创建
     */
    public static VectorDocument fromPo(PoFraudTrainingCase poCase, List<Float> vector) {
        return VectorDocument.builder()
                .id("case_" + poCase.getId())
                .text(poCase.getChatContent())
                .vector(vector)
                .metadata(new HashMap<String, Object>() {{
                    put("case_id", poCase.getId());
                    put("is_fraud", poCase.getIsFraud());
                    put("fraud_type", poCase.getFraudType());
                    put("confidence_score", poCase.getConfidenceScore());
                    put("source", poCase.getSource());
                }})
                .createTime(LocalDateTime.now())
                .build();
    }
}
