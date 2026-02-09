/**
 * @author qkcao
 * @date 2025/9/16 18:34
 */
package com.rental.guard.ai.domain.service;

import com.alibaba.fastjson2.JSON;
import com.rental.guard.ai.config.AIConfig;
import com.rental.guard.ai.config.ArgConfig;
import com.rental.guard.ai.contants.FraudDetectionConstants;
import com.rental.guard.ai.domain.dto.*;
import com.rental.guard.ai.infrastructure.mapper.FraudDetectionRecordMapper;
import com.rental.guard.ai.infrastructure.mapper.FraudTrainingCaseMapper;
import com.rental.guard.ai.infrastructure.mapper.MessageMapper;
import com.rental.guard.ai.infrastructure.po.PoFraudDetectionRecord;
import com.rental.guard.ai.infrastructure.po.PoFraudTrainingCase;
import com.rental.guard.ai.infrastructure.po.PoMessage;
import com.rental.guard.ai.infrastructure.service.CaseRanker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI反欺诈检测服务实现
 */
@Slf4j
@Service
public class FraudDetectionServiceImpl implements FraudDetectionService {

    @Autowired
    private AIConfig aiConfig;
    @Autowired
    private AIAnalysisServiceSelector aiAnalysisServiceSelector;
    @Autowired
    private FraudDetectionRecordMapper fraudDetectionRecordMapper;
    @Autowired
    private FraudTrainingCaseMapper fraudTrainingCaseMapper;
    @Autowired
    private MessageMapper messageMapper;
    @Autowired
    private ArgConfig argConfig;


    @Override
    public ChatContextDto getChatContext(Long channelId, int messageCount) {
        log.debug("获取聊天上下文 - channelId: {}, messageCount: {}", channelId, messageCount);
        try {
            // 获取最近的消息列表
            List<MessageInfo> messages = getMessages(channelId, 0L, (long) messageCount);
            // 转换为ChatMessageDto列表
            List<ChatContextDto.ChatMessageDto> chatMessages = new ArrayList<>();
            for (MessageInfo message : messages) {
                ChatContextDto.ChatMessageDto chatMessage = ChatContextDto.ChatMessageDto.builder()
                        .senderId(message.getCreatorId()).senderType("user") // 可以根据需要区分用户类型
                        .content(message.getPayload())
                        .messageType(getMessageTypeDescription((int) message.getType()))
                        .createTime(message.getCreateTime()).build();
                chatMessages.add(chatMessage);
            }
            // 格式化上下文
            String formattedContext = formatChatContext(chatMessages);

            return ChatContextDto.builder()
                    .channelId(channelId)
                    .messages(chatMessages)
                    .formattedContext(formattedContext).build();

        } catch (Exception e) {
            log.error("获取聊天上下文异常 - channelId: {}", channelId, e);
            return null;
        }
    }

    @Override
    public void addTrainingCase(String chatContent, boolean isFraud, String fraudType, String description) {
        log.info("添加训练案例 - isFraud: {}, fraudType: {}", isFraud, fraudType);
        try {
            Date now = new Date();
            PoFraudTrainingCase trainingCase = PoFraudTrainingCase.builder().chatContent(chatContent)
                    .isFraud(isFraud ? 1 : 0).fraudType(fraudType).source("manual") // 手工添加的训练案例
                    .confidenceScore(new BigDecimal("1.0")) // 人工标注的置信度为1.0
                    .description(description).createTime(now).updateTime(now)
                    .build();

            fraudTrainingCaseMapper.insert(trainingCase);

            log.info("训练案例添加成功 - caseId: {}, isFraud: {}, fraudType: {}", trainingCase.getId(), isFraud, fraudType);
        } catch (Exception e) {
            log.error("添加训练案例失败 - isFraud: {}, fraudType: {}", isFraud, fraudType, e);
        }
    }

    /**
     * 优化点：
     * 1.向量检索优化：预先计算所有训练案例并存储到数据库中，避免每次实时计算；在数据库层面进行相似度的搜索，利用索引加速
     *
     * @param chatContext 聊天上下文
     * @param ip1         用户1IP
     * @param ip2         用户2IP
     * @return
     */
    @Override
    public FraudAnalysisResult analyzeWithAI(String chatContext, String ip1, String ip2) {
        log.debug("执行AI分析 - chatContext: {}", chatContext);
        try {
            // 获取相关训练案例
            List<PoFraudTrainingCase> trainingCases = fraudTrainingCaseMapper.getALlPoFraudTrainingCase();

            // 集成向量模型获取相关案例
            List<PoFraudTrainingCase> relevantTrainingCases = getRelevantTrainingCases(chatContext, trainingCases, 10);

            // 构建提示词
            String prompt = RentalFraudRequestBuilder.buildEnhancedPromptWithTrainingCases(chatContext, relevantTrainingCases, "fraud_detection");

            AIAnalysisRequest request = AIAnalysisRequest.fraudDetection(chatContext);
            request.setPrompt(prompt);
            request.setIp1(ip1);
            request.setIp2(ip2);

            // 调用AI服务
            AIAnalysisResult aiResult = aiAnalysisServiceSelector.getAIAnalysisService().analyze(request);

            if (!aiResult.getSuccess()) {
                return FraudAnalysisResult.failure(aiResult.getErrorMessage(), aiResult.getCostTimeMs());
            }

            // 解析AI返回的JSON结果
            String aiResponse = aiResult.getContent();
            log.info("AI分析原始结果: {}", aiResponse);

            try {
                // 解析JSON响应
                FraudAnalysisResult result = JSON.parseObject(aiResponse, FraudAnalysisResult.class);
                result.setAiCostTime(aiResult.getCostTimeMs());
                result.setAnalysisSuccess(true);

                log.info("AI分析完成 - isFraud: {}, riskScore: {}, fraudType: {}, costTime: {}ms",
                        result.getIsFraud(), result.getRiskScore(), result.getFraudType(),
                        result.getAiCostTime());

                return result;
            } catch (Exception parseEx) {
                log.error("解析AI分析结果失败，原始结果: {}", aiResponse, parseEx);
            }
        } catch (Exception e) {
            log.error("AI分析异常", e);
            return FraudAnalysisResult.failure("AI分析异常: " + e.getMessage(), 0L);
        }
        return null;
    }

    @Override
    public void triggerAIAnalysisAsync(Long channelId, String userId, String triggerMessage, String sensitiveWord, String payload, String ip1, String ip2) {
        if (!aiConfig.getEnabled() || !aiConfig.getFraudDetection().getEnabled()) {
            log.debug("AI反欺诈检测已禁用");
            return;
        }

        log.info("触发AI反欺诈分析 - channelId: {}, userId: {}, sensitiveWord: {}", channelId, userId, sensitiveWord);
        try {
            // 获取聊天上下文
            ChatContextDto chatContext = getChatContext(channelId, aiConfig.getFraudDetection().getContextMessageCount());

            // AI分析
            FraudAnalysisResult analysisResult = analyzeWithAI(chatContext.getFormattedContext(), ip1, ip2);

            if (analysisResult.getAnalysisSuccess()) {
                // 处理分析结果
                handleFraudDetectionResult(analysisResult, userId, channelId, payload);

                // 记录检测历史
                recordDetectionHistory(userId, channelId, sensitiveWord, triggerMessage, chatContext, analysisResult);
            } else {
                log.error("AI分析失败: {}", analysisResult.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("AI反欺诈分析异常 - channelId: {}, userId: {}", channelId, userId, e);
        }
    }

    private String formatChatContext(List<ChatContextDto.ChatMessageDto> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatContextDto.ChatMessageDto message : messages) {
            sb.append(String.format("[%s] %s (%s): %s\n", message.getCreateTime(), message.getSenderId(),
                    message.getMessageType(), message.getContent()));
        }
        return sb.toString();
    }

    @Override
    public void handleFraudDetectionResult(FraudAnalysisResult analysisResult, String triggerUserId, Long channelId, String payload) {
        log.info("处理欺诈检测结果 - triggerUserId: {}, channelId: {}, isFraud: {}, riskScore: {}, suspiciousUserId: {}",
                triggerUserId, channelId, analysisResult.getIsFraud(), analysisResult.getRiskScore(),
                analysisResult.getSuspiciousUserId());

        if (analysisResult.getIsFraud() == null || !analysisResult.getIsFraud()) {
            return;
        }

        BigDecimal riskScore = analysisResult.getRiskScore();
        if (riskScore == null) {
            return;
        }

        // 确定要处罚的用户ID
        String targetUserId = analysisResult.getSuspiciousUserId();
        if (targetUserId == null || targetUserId.trim().isEmpty()) {
            // 如果AI没有识别出具体用户，则使用触发用户ID作为后备
            targetUserId = triggerUserId;
            log.warn("AI未能识别出具体问题用户，使用触发用户作为后备 - triggerUserId: {}", triggerUserId);
        } else {
            log.info("AI识别出问题用户 - suspiciousUserId: {}, triggerUserId: {}", targetUserId, triggerUserId);
        }
        try {
            if (riskScore.compareTo(FraudDetectionConstants.HIGH_RISK_THRESHOLD) >= 0) {
                // todo 高风险：立即封禁用户
                System.out.println("todo 高风险：立即封禁用户");

                // todo 报警提醒
                System.out.println("todo 报警提醒");
                log.warn("高风险欺诈行为，已封禁用户 - targetUserId: {}, riskScore: {}", targetUserId, riskScore);

            } else if (riskScore.compareTo(FraudDetectionConstants.MEDIUM_RISK_THRESHOLD) >= 0) {
                // 中风险：限制聊天功能（这里需要具体的限制逻辑，暂时记录警告）
                // todo 中风险报警提醒
                System.out.println("todo 中风险报警提醒");
                log.warn("中风险欺诈行为，需要人工审核 - targetUserId: {}, riskScore: {}", targetUserId, riskScore);

            } else if (riskScore.compareTo(FraudDetectionConstants.LOW_RISK_THRESHOLD) >= 0) {
                // 低风险：记录预警，加强监控
                // todo 低风险报警提醒
                System.out.println("todo 低风险报警提醒");
                log.info("低风险欺诈行为，记录预警 - targetUserId: {}, riskScore: {}", targetUserId, riskScore);
            }
        } catch (Exception e) {
            log.error("处理欺诈检测结果异常 - targetUserId: {}, triggerUserId: {}, channelId: {}", targetUserId, triggerUserId, channelId, e);
        }
    }

    @Override
    public void recordDetectionHistory(String triggerUserId, Long channelId, String sensitiveWord,
                                       String triggerMessage, ChatContextDto chatContext, FraudAnalysisResult analysisResult) {
        log.info("记录检测历史 - triggerUserId: {}, channelId: {}, suspiciousUserId: {}", triggerUserId,
                channelId, analysisResult.getSuspiciousUserId());
        try {
            // 确定处理动作
            String actionTaken = determineActionTaken(analysisResult);

            // 构建检测记录
            PoFraudDetectionRecord record = PoFraudDetectionRecord.builder().userId(triggerUserId) // 触发检测的用户ID
                    .channelId(channelId).triggerSensitiveWord(sensitiveWord).triggerMessage(triggerMessage)
                    .chatContext(JSON.toJSONString(chatContext))
                    .aiAnalysisResult(JSON.toJSONString(analysisResult))
                    .riskScore(analysisResult.getRiskScore())
                    .isFraud(analysisResult.getIsFraud() != null && analysisResult.getIsFraud() ? 1 : 0)
                    .fraudType(analysisResult.getFraudType()).actionTaken(actionTaken)
                    .aiCostTime(
                            analysisResult.getAiCostTime() != null ? analysisResult.getAiCostTime().intValue()
                                    : null)
                    .createTime(LocalDateTime.now()).updateTime(LocalDateTime.now()).build();

            // 保存到数据库
            fraudDetectionRecordMapper.insert(record);

            String suspiciousUserId = analysisResult.getSuspiciousUserId();
            log.info("检测历史记录成功 - recordId: {}, triggerUserId: {}, suspiciousUserId: {}, isFraud: {}",
                    record.getId(), triggerUserId, suspiciousUserId, record.getIsFraud());

        } catch (Exception e) {
            log.error("记录检测历史异常 - triggerUserId: {}, channelId: {}", triggerUserId, channelId, e);
        }
    }

    private String determineActionTaken(FraudAnalysisResult analysisResult) {
        if (analysisResult.getIsFraud() == null || !analysisResult.getIsFraud()) {
            return FraudDetectionConstants.ACTION_MONITOR;
        }
        BigDecimal riskScore = analysisResult.getRiskScore();
        if (riskScore == null) {
            return FraudDetectionConstants.ACTION_MONITOR;
        }
        if (riskScore.compareTo(FraudDetectionConstants.HIGH_RISK_THRESHOLD) >= 0) {
            return FraudDetectionConstants.ACTION_BAN_USER;
        } else if (riskScore.compareTo(FraudDetectionConstants.MEDIUM_RISK_THRESHOLD) >= 0) {
            return FraudDetectionConstants.ACTION_LIMIT_CHAT;
        } else if (riskScore.compareTo(FraudDetectionConstants.LOW_RISK_THRESHOLD) >= 0) {
            return FraudDetectionConstants.ACTION_WARNING;
        } else {
            return FraudDetectionConstants.ACTION_MONITOR;
        }
    }

    public List<MessageInfo> getMessages(long channelId, Long from, Long to) {
        List<PoMessage> poMessages = messageMapper.selectByChannelAndOffset(from, to, channelId);
        return poMessages.stream().map(MessageInfo::fromPO).collect(Collectors.toList());
    }

    private String getMessageTypeDescription(Integer type) {
        // 根据消息类型返回描述
        if (type == null)
            return "未知";
        switch (type) {
            case 1: // IMTypes.MSG_TEXT
                return "文本";
            case 2: // IMTypes.MSG_IMAGE
                return "图片";
            case 3: // IMTypes.MSG_LOCATION
                return "地理位置";
            case 4: // IMTypes.MSG_VIDEO
                return "视频";
            case 5: // IMTypes.MSG_HOUSE
                return "房源";
            case 6: // IMTypes.MSG_CONTACT
                return "联系方式";
            default:
                return "其他";
        }
    }

    // 获取相关训练案例的方法
    private List<PoFraudTrainingCase> getRelevantTrainingCases(String query, List<PoFraudTrainingCase> allCases, int topK) {
        try {
            // 1. 获取查询文本的向量
            List<Float> queryVector = getEmbedding(query);

            // 2. 批量获取所有案例的向量（可以预先计算并存储）
            Map<Integer, List<Float>> caseVectors = new HashMap<>();
            for (PoFraudTrainingCase trainingCase : allCases) {
                // 可以先预存向量，实时计算向量的话，性能比较差
                // 实时计算向量（性能较差，建议预先计算）
                List<Float> vector = getEmbedding(trainingCase.getChatContent());
                caseVectors.put(trainingCase.getId(), vector);
            }

            // 3. 计算相似度并排序
            List<CaseSimilarity> similarities = new ArrayList<>();
            for (PoFraudTrainingCase trainingCase : allCases) {
                List<Float> caseVector = caseVectors.get(trainingCase.getId());
                float similarity = cosineSimilarity(queryVector, caseVector);
                similarities.add(new CaseSimilarity(trainingCase, similarity));
            }
            // 4. 按相似度排序并返回topK
            similarities.sort((a, b) -> Float.compare(b.similarity, a.similarity));

            List<PoFraudTrainingCase> result = new ArrayList<>();
            for (int i = 0; i < Math.min(topK, similarities.size()); i++) {
                result.add(similarities.get(i).caseItem);
            }

            return result;
        } catch (Exception e) {
            log.error("获取相关训练案例失败，使用全部案例", e);
            return allCases; // 失败时返回全部案例
        }
    }

    // 解析存储的嵌入向量字符串
    private List<Float> parseEmbedding(String embeddingStr) {
        if (embeddingStr == null || embeddingStr.isEmpty()) {
            return null;
        }
        try {
            String[] parts = embeddingStr.split(",");
            List<Float> result = new ArrayList<>();
            for (String part : parts) {
                result.add(Float.parseFloat(part.trim()));
            }
            return result;
        } catch (Exception e) {
            log.error("解析嵌入向量失败", e);
            return null;
        }
    }


    // 调用阿里云百炼向量模型API
    @Override
    public List<Float> getEmbedding(String text) {
        try {
            // 构建请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + argConfig.getApiKey());

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "text-embedding-v4");
            requestBody.put("input", text);
            requestBody.put("dimensions", 1024);
            requestBody.put("encoding_format", "float");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 发送请求
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    argConfig.getEndpoint(),
                    entity,
                    Map.class
            );

            // 解析响应
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
                if (data != null && !data.isEmpty()) {
                    List<Double> embeddingDoubles = (List<Double>) data.get(0).get("embedding");
                    // 转换为Float列表
                    List<Float> embeddingFloats = new ArrayList<>();
                    for (Double d : embeddingDoubles) {
                        embeddingFloats.add(d.floatValue());
                    }
                    return embeddingFloats;
                }
            }

            throw new RuntimeException("获取向量失败: " + response.getStatusCode());
        } catch (Exception e) {
            log.error("调用向量模型API失败", e);
            throw new RuntimeException("获取文本向量失败", e);
        }
    }

    // 计算余弦相似度
    private float cosineSimilarity(List<Float> vectorA, List<Float> vectorB) {
        if (vectorA == null || vectorB == null || vectorA.size() != vectorB.size()) {
            return 0;
        }

        float dotProduct = 0;
        float normA = 0;
        float normB = 0;

        for (int i = 0; i < vectorA.size(); i++) {
            dotProduct += vectorA.get(i) * vectorB.get(i);
            normA += vectorA.get(i) * vectorA.get(i);
            normB += vectorB.get(i) * vectorB.get(i);
        }

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    /**
     * 结果融合策略
     */
    private List<PoFraudTrainingCase> mergeAndRankCases(
            List<PoFraudTrainingCase> vectorCases,
            List<PoFraudTrainingCase> keywordCases,
            int topK) {

        // 使用优先队列进行融合排序
        return CaseRanker.mergeAndRank(vectorCases, keywordCases, topK);
    }
}
