/**
 * @author qkcao
 * @date 2025/12/31 16:18
 */
package com.rental.guard.ai.infrastructure.service;

import com.rental.guard.ai.infrastructure.po.PoFraudTrainingCase;

import java.math.BigDecimal;
import java.util.List;

/**
 * 训练案例检索器接口
 */
public interface TrainingCaseRetriever {

    /**
     * 根据查询文本检索相关案例
     */
    List<PoFraudTrainingCase> retrieve(String query, int topK);

    /**
     * 根据关键词检索案例
     */
    List<PoFraudTrainingCase> retrieveByKeywords(String query, int topK);

    /**
     * 混合检索：向量 + 关键词
     */
    List<PoFraudTrainingCase> hybridRetrieve(String query, int topK);

    /**
     * 根据欺诈类型检索案例
     */
    List<PoFraudTrainingCase> retrieveByFraudType(String fraudType, int topK);

    /**
     * 获取高置信度案例
     */
    List<PoFraudTrainingCase> retrieveHighConfidenceCases(BigDecimal minConfidence, int topK);
}
