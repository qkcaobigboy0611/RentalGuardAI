/**
 * @author qkcao
 * @date 2025/9/16 18:33
 */
package com.rental.guard.ai.domain.service;

import com.rental.guard.ai.domain.dto.ChatContextDto;
import com.rental.guard.ai.domain.dto.FraudAnalysisResult;

public interface FraudDetectionService {
    /**
     * 获取聊天上下文
     *
     * @param channelId    频道ID
     * @param messageCount 获取消息数量，默认20条
     * @return 聊天上下文
     */
    ChatContextDto getChatContext(Long channelId, int messageCount);

    /**
     * 添加训练案例
     *
     * @param chatContent 聊天内容
     * @param isFraud     是否欺诈
     * @param fraudType   欺诈类型
     * @param description 描述
     */
    void addTrainingCase(String chatContent, boolean isFraud, String fraudType, String description);

    /**
     * AI分析聊天内容是否存在欺诈行为
     *
     * @param chatContext 聊天上下文
     * @param ip1         用户1IP
     * @param ip2         用户2IP
     * @return 分析结果
     */
    FraudAnalysisResult analyzeWithAI(String chatContext, String ip1, String ip2);


    /**
     * 异步触发AI反欺诈分析
     *
     * @param channelId      聊天频道ID
     * @param userId         用户ID
     * @param triggerMessage 触发消息
     * @param sensitiveWord  触发的敏感词
     * @param payload        触发的聊天内容
     * @param ip1            用户1ip
     * @param ip2            用户2ip
     */
    void triggerAIAnalysisAsync(Long channelId, String userId, String triggerMessage,
                                String sensitiveWord, String payload, String ip1, String ip2);


    /**
     * 处理欺诈检测结果
     *
     * @param analysisResult 分析结果，包含AI识别的问题用户ID
     * @param triggerUserId  触发检测的用户ID
     * @param channelId      频道ID
     * @param payload        触发的聊天内容
     */
    void handleFraudDetectionResult(FraudAnalysisResult analysisResult, String triggerUserId,
                                    Long channelId, String payload);

    /**
     * 记录检测历史
     *
     * @param triggerUserId  触发检测的用户ID
     * @param channelId      频道ID
     * @param sensitiveWord  触发敏感词
     * @param triggerMessage 触发消息
     * @param chatContext    聊天上下文
     * @param analysisResult 分析结果，包含AI识别的问题用户ID
     */
    void recordDetectionHistory(String triggerUserId, Long channelId, String sensitiveWord,
                                String triggerMessage, ChatContextDto chatContext, FraudAnalysisResult analysisResult);

}
