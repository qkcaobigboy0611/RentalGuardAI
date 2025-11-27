/**
 * @author qkcao
 * @date 2025/9/16 18:49
 */
package com.rental.guard.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Deepseek响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepseekResponse {

    private Output output;

    private Usage usage;

    private String request_id;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Output {
        private String text;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        private Integer output_tokens;
        private Integer input_tokens;
        private Integer total_tokens;
    }

    public String getText() {
        return output != null ? output.getText() : null;
    }

    public Integer getTotalTokens() {
        return usage != null ? usage.getTotal_tokens() : null;
    }
}
