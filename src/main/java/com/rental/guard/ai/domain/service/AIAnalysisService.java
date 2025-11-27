/**
 * @author qkcao
 * @date 2025/9/16 18:44
 */
package com.rental.guard.ai.domain.service;

import com.rental.guard.ai.domain.dto.AIAnalysisRequest;
import com.rental.guard.ai.domain.dto.AIAnalysisResult;

/**
 * AI分析服务接口
 */
public interface AIAnalysisService {
    /**
     * 执行AI分析
     *
     * @param request AI分析请求
     * @return AI分析结果
     */
    AIAnalysisResult analyze(AIAnalysisRequest request);

    /**
     * 检查AI服务是否可用
     *
     * @return 是否可用
     */
    boolean isAvailable();

    /**
     * 获取AI服务类型
     *
     * @return 服务类型
     */
    String getServiceType();
}
