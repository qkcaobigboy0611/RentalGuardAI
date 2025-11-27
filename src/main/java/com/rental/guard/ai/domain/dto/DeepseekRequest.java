/**
 * @author qkcao
 * @date 2025/9/16 18:48
 */
package com.rental.guard.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 阿里云千问请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepseekRequest {

    private String model;

    private Input input;

    private Parameters parameters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Input {
        private String prompt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Parameters {
        private Integer max_tokens;
        private Double temperature;
        private String result_format = "message";
    }

    public static DeepseekRequest create(String model, String prompt, Integer maxTokens,
                                         Double temperature) {
        return DeepseekRequest.builder().model(model).input(Input.builder().prompt(prompt).build())
                .parameters(Parameters.builder().max_tokens(maxTokens).temperature(temperature)
                        .result_format("message").build())
                .build();
    }
}

