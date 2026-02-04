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

import java.util.Map;

/**
 * 搜索结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    private String documentId;          // 文档ID
    private String text;                // 文本内容
    private Float similarity;           // 相似度得分
    private Map<String, Object> metadata; // 元数据
    private PoFraudTrainingCase caseData; // 完整的案例数据（延迟加载）
}
