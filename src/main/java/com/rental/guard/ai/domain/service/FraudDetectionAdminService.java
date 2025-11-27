/**
 * @author qkcao
 * @date 2025/9/16 18:15
 */
package com.rental.guard.ai.domain.service;

import com.rental.guard.ai.domain.dto.AddTrainingCaseRequest;
import com.rental.guard.ai.domain.dto.FraudDetectionStatistics;
import com.rental.guard.ai.domain.dto.TestAIAnalysisRequest;
import com.rental.guard.ai.domain.dto.TestAIAnalysisResponse;
import com.rental.guard.ai.infrastructure.po.PoFraudDetectionRecord;
import com.rental.guard.ai.infrastructure.po.PoFraudTrainingCase;

import java.math.BigDecimal;
import java.util.List;

public interface FraudDetectionAdminService {

    /**
     * 分页查询欺诈检测记录
     */
    List<PoFraudDetectionRecord> getFraudDetectionRecords(String userId, Long channelId,
                                                          Integer isFraud, String fraudType, BigDecimal minRiskScore, Integer offset, Integer limit);

    /**
     * 获取欺诈检测统计信息
     */
    FraudDetectionStatistics getFraudDetectionStatistics(Integer days);

    /**
     * 分页查询训练案例
     */
    List<PoFraudTrainingCase> getTrainingCases(Integer isFraud, String fraudType,
                                               String source, Integer offset, Integer limit);

    /**
     * 添加训练案例
     */
    void addTrainingCase(AddTrainingCaseRequest request);


    /**
     * 测试AI分析
     */
    TestAIAnalysisResponse testAIAnalysis(TestAIAnalysisRequest request, String ip1, String ip2);
}
