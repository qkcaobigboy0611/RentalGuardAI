/**
 * @author qkcao
 * @date 2025/9/16 18:46
 */
package com.rental.guard.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * Ollama请求DTO
 *
 * 温度与采样的交互
 * （1）低温度 + top_p = 0.9 : 聚焦高质量输出
 * （2）高温度 + top_k = 50 : 鼓励探索但限制范围
 *
 * 设置推荐
 * （1）温度系数设置为0.3,较低，以确保确定性，允许轻微的变化
 * （2）最大生成token数：1024 （如果输入文本较长的话，也要控制成本）
 * （3）采样方法：top_p和top_k结合，但以top_p为主，top_k辅助，限制在合理范围（top为0.9 保存90%的token）
 * （4）思考模型：启用（可能会增加生成时间，消耗更多的token）
 * （5）考虑重复惩罚和频率惩罚
 *
 *
 * 防租房欺诈AI助手，其场景要求的特殊性
 * （1）高确定性：欺诈检测需要准确，可靠的输出
 * （2）推理过程显示化：需要展示完整的推理链条，便于审核和解释
 * （3）响应完整性：确保完整的分析和建议
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
     * 提示词 输入给模型的文本指令和上下文（包含系统指令，上下文示例，用户查询三部分）
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
         * 温度参数 控制输出的随机性和创造性
         *  0：确定性输出（贪婪解码），选择概率最高的token
         *  1：标准softmax分布
         *  >1 : 增加多样性，但可能降低连贯性
         *  风险：过高温度可能导致不连贯性和无意义的输出
         *  任务类型推荐值：
         *     代码生成 ：0.1-0.3 高稳定性
         *     创意写作 ：0.7-0.9 高创造性
         *     对话系统 ：0.5-0.7 平衡
         */
        private Double temperature; //0.1

        /**
         * 最大生成token数 (目前设置1000)
         */
        private Integer num_predict;

        /**
         * top_p参数
         */
        private Double top_p; //0.9

        /**
         * top_k参数
         * 仅仅从概率最高的前k个token中取样，避免生成低质量或者无关的内容
         */
        private Integer top_k; //50

        /**
         * 模型先生成内部推理步骤-》然后生成最终答案
         */
        private Boolean think;

        // 1. 重复惩罚 - 防止模型重复相同内容
        private Double repeat_penalty;

        // 2. 停止序列 - 控制生成终止
        private List<String> stop;

        // 3. 频率惩罚 - 降低常见词的频率
        private Double frequency_penalty;
    }

    public static OllamaRequest create(String model, String prompt, Integer maxTokens, Double temperature) {
        Options options = Options.builder()
                .temperature(temperature != null ? Math.max(0.1, Math.min(temperature, 0.3)) : 0.15) //0.1
                .num_predict(maxTokens != null ? Math.min(maxTokens, 2048) : 1024) // 1000
                .top_p(0.9)
                .top_k(50)
                .think(true)
                .repeat_penalty(1.2)
                .stop(Arrays.asList("\n\n", "---", "结论:"))
                .frequency_penalty(0.1)  // 轻微惩罚高频词
                .build();

        return OllamaRequest.builder()
                .model(model)
                .prompt(prompt)
                .stream(true)
                .options(options)
                .build();
    }


}
