/**
 * @author qkcao
 * @date 2025/9/16 18:47
 */
package com.rental.guard.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Ollama响应DTO
 * 调试和监控 成本控制和分析 对话状态管理
 * 大模型返回的数据
 * （1）生成的文本内容（response）
 * （2）模型信息(model)
 * （3）上下文信息（多次对话 context）
 * （4）性能指标（包括时间消耗和token数量）
 * 时间指标：总消耗，加载消耗，评估消耗
 * token数量：输入提示词消耗的token和生成文本消耗的token
 * （5）其他元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaResponse {
    // ========== 基础信息 ==========

    /**
     * 请求唯一标识
     */
    private String requestId;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 生成的响应文本
     */
    private String response;

    /**
     * 是否完成
     */
    private Boolean done;

    // ========== 性能统计 ==========

    /**
     * 总耗时（纳秒）
     */
    private Long totalDuration;

    /**
     * 加载时间（纳秒）
     */
    private Long loadDuration;

    /**
     * 提示词评估时间（纳秒）
     */
    private Long promptEvalDuration;

    /**
     * 评估时间（纳秒）
     */
    private Long evalDuration;

    /**
     * 首个token到达时间（纳秒）
     */
    private Long timeToFirstToken;

    // ========== Token统计 ==========

    /**
     * 提示词token数
     */
    private Integer promptEvalCount;

    /**
     * 生成token数
     */
    private Integer evalCount;

    /**
     * 总token数
     */
    public Integer getTotalTokens() {
        return (promptEvalCount != null ? promptEvalCount : 0) +
                (evalCount != null ? evalCount : 0);
    }

    // ========== 上下文与状态 ==========

    /**
     * 上下文向量
     */
    private int[] context;

    /**
     * 停止原因
     */
    private String finishReason;

    /**
     * 随机种子
     */
    private Integer seed;

    // ========== 业务相关 ==========

    /**
     * 结构化响应
     */
    private Map<String, Object> structuredResponse;

    /**
     * 置信度分数
     */
    private Double confidence;

    // ========== 质量评估 ==========

    /**
     * 质量评分
     */
    private Double qualityScore;

    // ========== 成本计算 ==========

    /**
     * 估算成本
     */
    private Double estimatedCost;


    // ========== 便捷方法 ==========

    /**
     * 获取平均生成速度（tokens/秒）
     */
    public Double getTokensPerSecond() {
        if (evalCount != null && evalDuration != null && evalDuration > 0) {
            return evalCount / (evalDuration / 1_000_000_000.0);
        }
        return 0.0;
    }

    /**
     * 获取生成的文本内容
     */
    public String getText() {
        return response;
    }


    /**
     * 获取总时间（毫秒）
     */
    public Long getTotalTimeMs() {
        return totalDuration != null ? totalDuration / 1_000_000 : 0L;
    }

    /**
     * 获取估算的token数量
     */
    public Integer getEstimatedTokens() {
        if (promptEvalCount != null && evalCount != null) {
            return promptEvalCount + evalCount;
        }
        return response != null ? response.length() / 4 : 0; // 粗略估算
    }

}
