/**
 * @author qkcao
 * @date 2025/12/31 15:37
 */
package com.rental.guard.ai.infrastructure.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TextChunk {
    private String content;
    private int startIndex;
    private int endIndex;
    private int riskLevel; // 1-5, 5为最高风险
    private boolean hasOverlap;

    public TextChunk withOffset(int offset) {
        return new TextChunk(
                content,
                startIndex + offset,
                endIndex + offset,
                riskLevel,
                hasOverlap
        );
    }
}
