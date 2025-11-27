/**
 * @author qkcao
 * @date 2025/9/16 18:46
 */
package com.rental.guard.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ollama请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaRequest {

    /**
     * 模型名称
     */
    private String model;

    /**
     * 提示词
     */
    private String prompt;

    /**
     * 是否流式输出
     */
    private Boolean stream = false;

    /**
     * 生成参数
     */
    private Options options;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Options {
        /**
         * 温度参数
         */
        private Double temperature;

        /**
         * 最大生成token数
         */
        private Integer num_predict;

        /**
         * top_p参数
         */
        private Double top_p;

        /**
         * top_k参数
         */
        private Integer top_k;

        private Boolean think;
    }

    public static OllamaRequest create(String model, String prompt, Integer maxTokens, Double temperature) {
        Options options = Options.builder()
                .temperature(temperature)
                .num_predict(maxTokens)
                .top_p(0.9)
                .top_k(50)
                .think(false)
                .build();

        return OllamaRequest.builder()
                .model(model)
                .prompt(prompt)
                .stream(false)
                .options(options)
                .build();
    }
}
