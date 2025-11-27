/**
 * @author qkcao
 * @date 2025/9/16 18:16
 */
package com.rental.guard.ai.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rental.guard.ai.domain.dto.*;
import com.rental.guard.ai.infrastructure.mapper.ChannelMapper;
import com.rental.guard.ai.infrastructure.mapper.FraudDetectionRecordMapper;
import com.rental.guard.ai.infrastructure.mapper.FraudTrainingCaseMapper;
import com.rental.guard.ai.infrastructure.mapper.UserMapper;
import com.rental.guard.ai.infrastructure.po.PoFraudDetectionRecord;
import com.rental.guard.ai.infrastructure.po.PoFraudTrainingCase;
import com.rental.guard.ai.infrastructure.po.PoUser;
import com.rental.guard.ai.utils.OffsetLimit;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 反欺诈管理服务实现
 */
@Slf4j
@Service
public class FraudDetectionAdminServiceImpl implements FraudDetectionAdminService {
    @Autowired
    private FraudDetectionRecordMapper fraudDetectionRecordMapper;
    @Autowired
    private FraudTrainingCaseMapper fraudTrainingCaseMapper;
    @Autowired
    @Lazy
    private FraudDetectionService fraudDetectionService;
    @Resource
    private UserMapper userMapper;
    @Autowired
    private ChannelMapper channelMapper;


    @Override
    public List<PoFraudDetectionRecord> getFraudDetectionRecords(String userId, Long channelId, Integer isFraud, String fraudType, BigDecimal minRiskScore, Integer offset, Integer limit) {

        Page<PoFraudDetectionRecord> page = new Page<>(OffsetLimit.pageSize(offset, limit), limit);
        QueryWrapper<PoFraudDetectionRecord> queryWrapper = new QueryWrapper<>();

        if (StringUtils.isNotEmpty(userId)) {
            queryWrapper.eq("user_id", userId);
        }
        if (channelId != null) {
            queryWrapper.eq("channel_id", channelId);
        }
        if (isFraud != null) {
            queryWrapper.eq("is_fraud", isFraud);
        }
        if (StringUtils.isNotEmpty(fraudType)) {
            queryWrapper.eq("fraud_type", fraudType);
        }
        if (minRiskScore != null) {
            queryWrapper.ge("risk_score", minRiskScore);
        }

        queryWrapper.orderByDesc("create_time");

        IPage<PoFraudDetectionRecord> pageResult =
                fraudDetectionRecordMapper.selectPage(page, queryWrapper);

        return pageResult.getRecords();
    }

    @Override
    public FraudDetectionStatistics getFraudDetectionStatistics(Integer days) {
        if (days == null) {
            days = 7; // 默认7天
        }

        // 统计查询
        QueryWrapper<PoFraudDetectionRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("create_time", String.format("DATE_SUB(NOW(), INTERVAL %d DAY)", days));

        List<PoFraudDetectionRecord> records = fraudDetectionRecordMapper.getPoFraudDetectionRecords(days);

        // 统计计算
        int totalDetections = records.size();
        int fraudDetections =
                (int) records.stream().filter(r -> r.getIsFraud() != null && r.getIsFraud() == 1).count();
        int highRiskDetections = (int) records.stream()
                .filter(
                        r -> r.getRiskScore() != null && r.getRiskScore().compareTo(new BigDecimal("0.8")) >= 0)
                .count();

        double avgCostTime = records.stream().filter(r -> r.getAiCostTime() != null)
                .mapToInt(r -> r.getAiCostTime()).average().orElse(0.0);

        return FraudDetectionStatistics.builder().totalDetections(totalDetections)
                .fraudDetections(fraudDetections).highRiskDetections(highRiskDetections)
                .averageCostTimeMs(Math.round(avgCostTime))
                .fraudRate(totalDetections > 0 ? (double) fraudDetections / totalDetections : 0.0)
                .days(days).build();
    }

    @Override
    public List<PoFraudTrainingCase> getTrainingCases(Integer isFraud, String fraudType, String source, Integer offset, Integer limit) {
        OffsetLimit offsetLimit = OffsetLimit.normalize(offset, limit);
        offset = offsetLimit.offset;
        limit = offsetLimit.limit;

        Page<PoFraudTrainingCase> page = new Page<>(offset / limit + 1, limit);
        QueryWrapper<PoFraudTrainingCase> queryWrapper = new QueryWrapper<>();

        if (isFraud != null) {
            queryWrapper.eq("is_fraud", isFraud);
        }
        if (StringUtils.isNotEmpty(fraudType)) {
            queryWrapper.eq("fraud_type", fraudType);
        }
        if (StringUtils.isNotEmpty(source)) {
            queryWrapper.eq("source", source);
        }

        queryWrapper.orderByDesc("create_time");

        IPage<PoFraudTrainingCase> pageResult = fraudTrainingCaseMapper.selectPage(page, queryWrapper);

        return pageResult.getRecords();
    }

    @Override
    public void addTrainingCase(AddTrainingCaseRequest request) {

        String chatContent = request.getChatContent();

        // 如果没有直接提供聊天内容，但提供了手机号，则查询聊天记录
        if (StringUtils.isEmpty(chatContent) && (StringUtils.isNotEmpty(request.getUserPhone1())
                || StringUtils.isNotEmpty(request.getUserPhone2()))) {
            // 根据两个用户手机号获取聊天内容
            chatContent = getChatContentFromPhones(request.getUserPhone1(), request.getUserPhone2());
        }

        fraudDetectionService.addTrainingCase(chatContent, request.getIsFraud(), request.getFraudType(),
                request.getDescription());

        log.info("管理员添加训练案例 - isFraud: {}, fraudType: {}", request.getIsFraud(),
                request.getFraudType());
    }

    @Override
    public TestAIAnalysisResponse testAIAnalysis(TestAIAnalysisRequest request, String ip1, String ip2) {
        // 如果直接提供了聊天内容，直接分析
        if (StringUtils.isNotEmpty(request.getChatContent())) {
            FraudAnalysisResult analysisResult =
                    fraudDetectionService.analyzeWithAI(request.getChatContent(), ip1, ip2);
            return TestAIAnalysisResponse.builder().formattedChatContent(request.getChatContent())
                    .analysisResult(analysisResult).build();
        }

        // 通过用户信息查找聊天记录
        return analyzeByUserInfo(request);
    }


    private TestAIAnalysisResponse analyzeByUserInfo(TestAIAnalysisRequest request) {
        try {
            // 1. 查找用户信息
            PoUser user1 = findUser(request.getUserId1(), request.getPhone1());
            PoUser user2 = findUser(request.getUserId2(), request.getPhone2());

            // 2. 查找聊天频道
            Long channelId = getChannelId(user1.getUserId(), user2.getUserId());

            // 3. 获取聊天上下文
            int messageCount = request.getMessageCount() != null ? request.getMessageCount() : 20;
            ChatContextDto chatContext = fraudDetectionService.getChatContext(channelId, messageCount);

            // 4. AI分析
            FraudAnalysisResult analysisResult =
                    fraudDetectionService.analyzeWithAI(chatContext.getFormattedContext(), user1.getIp(), user2.getIp());

            // 5. 构建响应
            return TestAIAnalysisResponse.builder().channelId(channelId)
                    .user1Info(getUserDisplayInfo(user1)).user2Info(getUserDisplayInfo(user2))
                    .formattedChatContent(chatContext.getFormattedContext()).analysisResult(analysisResult)
                    .build();

        } catch (Exception e) {
            log.error("通过用户信息分析聊天记录失败", e);
            throw new RuntimeException("分析失败: " + e.getMessage());
        }
    }

    /**
     * 根据两个用户手机号获取聊天内容
     */
    private String getChatContentFromPhones(String phone1, String phone2) {
        try {
            // 根据手机号查找用户ID
            String userId1 = userMapper.selectByPhone(phone1).getUserId();
            String userId2 = userMapper.selectByPhone(phone2).getUserId();

            // 查找两个用户的聊天频道
            Long channelId = getChannelId(userId1, userId2);

            // 获取聊天上下文
            ChatContextDto chatContext = fraudDetectionService.getChatContext(channelId, 100); // 获取最近50条消息

            return chatContext != null ? chatContext.getFormattedContext() : null;

        } catch (Exception e) {
            log.error("根据手机号获取聊天内容失败 - phone1: {}, phone2: {}", phone1, phone2, e);
            return null;
        }
    }

    public Long getChannelId(String creator, String peerId) {
        return channelMapper.selectChannelId(creator, peerId);
    }

    private String getUserDisplayInfo(PoUser user) {
        return String.format("%s (ID: %s, 手机: %s)",
                StringUtils.isNotEmpty(user.getNickName()) ? user.getNickName() : "未设置昵称", user.getUserId(),
                StringUtils.isNotEmpty(user.getPhone()) ? user.getPhone() : "未绑定");
    }

    private PoUser findUser(String userId, String phone) {
        if (StringUtils.isNotEmpty(userId)) {
            return userMapper.selectByUserId(userId);
        }
        if (StringUtils.isNotEmpty(phone)) {
            return userMapper.selectByPhone(phone);
        }
        return null;
    }
}
