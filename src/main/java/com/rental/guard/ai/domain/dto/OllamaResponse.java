/**
 * @author qkcao
 * @date 2025/9/16 18:47
 */
package com.rental.guard.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ollama响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaResponse {

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

    /**
     * 上下文信息
     */
    private int[] context;

    /**
     * 总生成时间（纳秒）
     */
    private Long total_duration;

    /**
     * 加载时间（纳秒）
     */
    private Long load_duration;

    /**
     * 提示词评估次数
     */
    private Integer prompt_eval_count;

    /**
     * 提示词评估时间（纳秒）
     */
    private Long prompt_eval_duration;

    /**
     * 评估次数
     */
    private Integer eval_count;

    /**
     * 评估时间（纳秒）
     */
    private Long eval_duration;

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
        return total_duration != null ? total_duration / 1_000_000 : 0L;
    }

    /**
     * 获取估算的token数量
     */
    public Integer getEstimatedTokens() {
        if (prompt_eval_count != null && eval_count != null) {
            return prompt_eval_count + eval_count;
        }
        return response != null ? response.length() / 4 : 0; // 粗略估算
    }
}
