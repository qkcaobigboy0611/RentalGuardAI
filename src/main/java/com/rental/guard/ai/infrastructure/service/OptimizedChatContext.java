/**
 * @author qkcao
 * @date 2025/12/31 15:36
 */
package com.rental.guard.ai.infrastructure.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OptimizedChatContext {
    private String originalContent;
    private String processedContent;
    private List<TextChunk> chunks;
    private List<ConversationStage> conversationStages;
    private Map<String, Object> keyInformation;
    private double compressionRatio;
}
