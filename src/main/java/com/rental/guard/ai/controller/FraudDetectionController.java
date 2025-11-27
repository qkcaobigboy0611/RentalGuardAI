/**
 * @author qkcao
 * @date 2025/9/16 18:11
 */
package com.rental.guard.ai.controller;

import com.rental.guard.ai.domain.dto.AddTrainingCaseRequest;
import com.rental.guard.ai.domain.dto.FraudDetectionStatistics;
import com.rental.guard.ai.domain.dto.TestAIAnalysisRequest;
import com.rental.guard.ai.domain.dto.TestAIAnalysisResponse;
import com.rental.guard.ai.domain.service.FraudDetectionAdminService;
import com.rental.guard.ai.infrastructure.po.PoFraudDetectionRecord;
import com.rental.guard.ai.infrastructure.po.PoFraudTrainingCase;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI反欺诈管理后台控制器
 */
@RestController
@RequestMapping("/ai/fraud")
@Slf4j
public class FraudDetectionController {

    @Autowired
    private FraudDetectionAdminService fraudDetectionAdminService;

    /**
     * 获取欺诈检测记录列表
     */
    @GetMapping("/records")
    public List<PoFraudDetectionRecord> getFraudDetectionRecords(
            @RequestParam("userId") @Nullable String userId,
            @RequestParam("channelId") @Nullable Long channelId,
            @RequestParam("isFraud") @Nullable Integer isFraud,
            @RequestParam("fraudType") @Nullable String fraudType,
            @RequestParam("minRiskScore") @Nullable BigDecimal minRiskScore,
            @RequestParam("offset") @Nullable Integer offset,
            @RequestParam("limit") @Nullable Integer limit) {

        List<PoFraudDetectionRecord> result = fraudDetectionAdminService
                .getFraudDetectionRecords(userId, channelId, isFraud, fraudType, minRiskScore, offset, limit);
        return result;
    }

    /**
     * 获取欺诈检测统计信息
     */
    @GetMapping("/statistics")
    public FraudDetectionStatistics getFraudDetectionStatistics(
            @RequestParam("days") @Nullable Integer days) {

        FraudDetectionStatistics statistics = fraudDetectionAdminService.getFraudDetectionStatistics(days);
        return statistics;
    }

    /**
     * 获取训练案例列表
     */
    @GetMapping("/training-cases")
    public List<PoFraudTrainingCase> getTrainingCases(
            @RequestParam("isFraud") @Nullable Integer isFraud,
            @RequestParam("fraudType") @Nullable String fraudType,
            @RequestParam("source") @Nullable String source,
            @RequestParam("offset") @Nullable Integer offset,
            @RequestParam("limit") @Nullable Integer limit) {

        List<PoFraudTrainingCase> result = fraudDetectionAdminService
                .getTrainingCases(isFraud, fraudType, source, offset, limit);
        return result;
    }

    /**
     * 添加训练案例
     */
    @PostMapping("/training-cases")
    public Long addTrainingCase(@RequestBody AddTrainingCaseRequest request) throws Exception {
        // 检查是否提供了聊天内容或用户手机号
        if (StringUtils.isEmpty(request.getChatContent()) &&
                StringUtils.isEmpty(request.getUserPhone1()) &&
                StringUtils.isEmpty(request.getUserPhone2())) {
            throw new Exception("请提供聊天内容或用户手机号");
        }

        if (request.getIsFraud() == null) {
            throw new Exception("是否欺诈不能为空");
        }
        fraudDetectionAdminService.addTrainingCase(request);
        return 0l;
    }


    /**
     * 测试AI分析
     */
    @PostMapping("/test")
    public TestAIAnalysisResponse testAIAnalysis(@RequestBody TestAIAnalysisRequest request) throws Exception {
        // 参数校验
        if ((StringUtils.isEmpty(request.getUserId1()) && StringUtils.isEmpty(request.getPhone1())) &&
                (StringUtils.isEmpty(request.getUserId2()) && StringUtils.isEmpty(request.getPhone2()))) {
            throw new Exception("请提供聊天内容或用户信息（userId/手机号）");
        }
        try {
            String ip1 = "61.135.152.140";
            String ip2 = "61.135.152.158";
            TestAIAnalysisResponse result = fraudDetectionAdminService.testAIAnalysis(request, ip1, ip2);
            return result;
        } catch (Exception e) {
            log.error("测试AI分析失败", e);
        }
        return null;
    }

}
