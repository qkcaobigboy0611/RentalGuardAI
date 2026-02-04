/**
 * @author qkcao
 * @date 2026/1/22 11:19
 */
package com.rental.guard.ai.domain.dto;

public class AdaptiveParamAdjuster {

    // 根据输入文本长度动态调整参数
    public static OllamaRequest.Options adjustByInputLength(String input, String taskType) {
        int inputLength = input.length();
        OllamaRequest.Options.OptionsBuilder builder = OllamaRequest.Options.builder();

        switch (taskType) {
            case "fraud_detection":
                // 长文本需要更多tokens进行深度分析
                builder.num_predict(Math.min(2500, 500 + inputLength / 2));
                builder.temperature(inputLength > 1000 ? 0.12 : 0.1);
                break;

            case "risk_scoring":
                builder.num_predict(400 + inputLength / 3);
                builder.temperature(0.15);
                break;

            default:
                builder.num_predict(1000);
                builder.temperature(0.2);
        }

        return builder
                .top_p(0.85)
                .top_k(30)
                .think(taskType.equals("fraud_detection"))
                .build();
    }

    // 根据用户风险级别调整
    public static OllamaRequest.Options adjustByRiskLevel(String riskLevel) {
        OllamaRequest.Options.OptionsBuilder builder = OllamaRequest.Options.builder();

        switch (riskLevel) {
            case "HIGH":
                // 高风险需要更严格、更详细的检查
                return builder
                        .temperature(0.05)
                        .num_predict(2000)
                        .top_p(0.7)
                        .top_k(15)
                        .think(true)
                        .build();

            case "MEDIUM":
                return builder
                        .temperature(0.1)
                        .num_predict(1200)
                        .top_p(0.8)
                        .top_k(25)
                        .think(true)
                        .build();

            case "LOW":
            default:
                return builder
                        .temperature(0.2)
                        .num_predict(800)
                        .top_p(0.9)
                        .top_k(40)
                        .think(false)
                        .build();
        }
    }
}
