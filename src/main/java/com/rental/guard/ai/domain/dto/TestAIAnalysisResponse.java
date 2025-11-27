/**
 * @author qkcao
 * @date 2025/9/16 18:37
 */
package com.rental.guard.ai.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TestAIAnalysisResponse {

    /**
     * 找到的聊天频道ID
     */
    private Long channelId;

    /**
     * 用户1信息
     */
    private String user1Info;

    /**
     * 用户2信息
     */
    private String user2Info;

    /**
     * 格式化的聊天内容
     */
    private String formattedChatContent;

    /**
     * AI分析结果
     */
    private FraudAnalysisResult analysisResult;
}
