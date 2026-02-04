/**
 * @author qkcao
 * @date 2026/1/22 11:23
 */
package com.rental.guard.ai.domain.dto;

import com.rental.guard.ai.infrastructure.po.PoFraudTrainingCase;

import java.util.List;

public class RentalFraudRequestBuilder {

    private String model = "llama3:8b";  // 推荐使用中等规模的推理模型
    private String prompt;
    private String taskType = "fraud_detection";
    private String riskLevel = "UNKNOWN";
    private boolean includeThinking = true;
    private boolean streaming = false;

    public static RentalFraudRequestBuilder newBuilder() {
        return new RentalFraudRequestBuilder();
    }

    public RentalFraudRequestBuilder model(String model) {
        this.model = model;
        return this;
    }

    public RentalFraudRequestBuilder prompt(String prompt) {
        this.prompt = prompt;
        return this;
    }

    public RentalFraudRequestBuilder taskType(String taskType) {
        this.taskType = taskType;
        return this;
    }

    public RentalFraudRequestBuilder riskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
        return this;
    }

    public RentalFraudRequestBuilder includeThinking(boolean includeThinking) {
        this.includeThinking = includeThinking;
        return this;
    }

    public RentalFraudRequestBuilder streaming(boolean streaming) {
        this.streaming = streaming;
        return this;
    }

    public OllamaRequest build() {
        // 获取基础参数配置
        OllamaRequest.Options options;

        if ("fraud_detection".equals(taskType)) {
            options = AdaptiveParamAdjuster.adjustByRiskLevel(riskLevel);
        } else {
            options = AdaptiveParamAdjuster.adjustByInputLength(prompt, taskType);
        }

        // 覆盖think设置
        OllamaRequest.Options finalOptions = OllamaRequest.Options.builder()
                .temperature(options.getTemperature())
                .num_predict(options.getNum_predict())
                .top_p(options.getTop_p())
                .top_k(options.getTop_k())
                .think(includeThinking && options.getThink())
                .build();

        return OllamaRequest.builder()
                .model(model)
                .prompt(prompt)
                .stream(streaming)
                .options(finalOptions)
                .build();
    }

    /**
     * 构建包含训练案例的增强版提示词
     *
     * @param chatContext 聊天上下文
     * @param trainingCases 训练案例列表
     * @param taskType 任务类型：fraud_detection/risk_scoring/advice_generation
     * @return 完整的增强提示词
     */
    public static String buildEnhancedPromptWithTrainingCases(
            String chatContext,
            List<PoFraudTrainingCase> trainingCases,
            String taskType) {

        StringBuilder promptBuilder = new StringBuilder();

        // ========== 系统指令 ==========
        promptBuilder.append("你是一个专业的防租房欺诈AI助手，专门识别租房场景中的诈骗和杀猪盘行为。\n");
        promptBuilder.append("请按照以下步骤分析用户提供的租房信息：\n");
        promptBuilder.append("1. 识别潜在的欺诈模式\n");
        promptBuilder.append("2. 评估风险等级\n");
        promptBuilder.append("3. 提供具体的防范建议\n");
        promptBuilder.append("4. 引用相关法律依据\n\n");

        // ========== 任务类型特定指令 ==========
        promptBuilder.append("【任务指令】");
        switch (taskType) {
            case "fraud_detection":
                promptBuilder.append("请进行深度欺诈分析：\n");
                break;
            case "risk_scoring":
                promptBuilder.append("请输出量化的风险评估：\n");
                break;
            case "advice_generation":
                promptBuilder.append("请提供可操作的安全建议：\n");
                break;
            default:
                promptBuilder.append("请进行综合欺诈分析：\n");
        }

        // ========== 训练案例部分 ==========
        if (trainingCases != null && !trainingCases.isEmpty()) {
            promptBuilder.append("\n【参考训练案例】\n");
            promptBuilder.append("以下是已标注的训练案例，请学习识别模式：\n\n");

            for (int i = 0; i < trainingCases.size(); i++) {
                PoFraudTrainingCase trainingCase = trainingCases.get(i);
                promptBuilder.append("案例").append(i + 1).append(":\n");
                promptBuilder.append("聊天内容：").append(trainingCase.getChatContent()).append("\n");
                promptBuilder.append("是否欺诈：").append(trainingCase.getIsFraud() == 1 ? "是" : "否")
                        .append("\n");

                if (trainingCase.getIsFraud() == 1 && trainingCase.getFraudType() != null) {
                    promptBuilder.append("欺诈类型：").append(trainingCase.getFraudType()).append("\n");
                }

                if (trainingCase.getDescription() != null
                        && !trainingCase.getDescription().trim().isEmpty()) {
                    promptBuilder.append("分析说明：").append(trainingCase.getDescription()).append("\n");
                }

                promptBuilder.append("\n");
            }

            promptBuilder.append("基于以上训练案例的模式，");
        }

        // ========== 分析要求 ==========
        promptBuilder.append("\n【分析要求】\n");
        promptBuilder.append("请分析以下聊天记录，重点关注以下欺诈特征：\n");
        promptBuilder.append("1. 是否存在诱导添加其他平台如QQ、百度网盘等行为\n");
        promptBuilder.append("2. 是否快速建立信任关系、给老板租房、长租2-3等，不符合正常租房交流\n");
        promptBuilder.append("3. 是否存在虚假承诺或过度热情\n");
        promptBuilder.append("4. 语言表达是否符合正常房屋租赁场景\n");
        promptBuilder.append("5. 是否与训练案例中的欺诈模式相似\n\n");

        // ========== 重要提醒 ==========
        promptBuilder.append("【重要提醒】\n");
        promptBuilder.append("1. 平台允许用户指导对方加微信、打电话，需综合判断为正常用户\n");
        promptBuilder.append("2. 平台有自动回复，回复内容一般是引导添加微信联系，所以有重复消息引导添加微信基本可视为正常用户\n");
        promptBuilder.append("3. 聊天记录格式为 [时间] 用户ID (消息类型): 消息内容\n");
        promptBuilder.append("4. 请仔细分析每个用户的发言内容，识别出具体是哪个用户ID存在欺诈行为\n\n");

        // ========== 聊天记录 ==========
        promptBuilder.append("【当前聊天记录】\n");
        promptBuilder.append(chatContext).append("\n\n");

        // ========== 输出格式 ==========
        promptBuilder.append("【输出格式】\n");
        promptBuilder.append("请返回JSON格式分析结果，包含以下字段：\n");
        promptBuilder.append("{\n");

        // 根据任务类型调整输出字段
        if ("fraud_detection".equals(taskType)) {
            promptBuilder.append("  \"is_fraud\": true/false,\n");
            promptBuilder.append("  \"fraud_type\": \"杀猪盘/投资诈骗/租房诈骗/其他\",\n");
        }

        promptBuilder.append("  \"risk_score\": 0.0-1.0,\n");
        promptBuilder.append("  \"confidence\": 0.0-1.0,\n");
        promptBuilder.append("  \"reason\": \"详细分析原因，包括欺诈模式识别、风险评估依据、防范建议和法律依据\",\n");
        promptBuilder.append("  \"keywords\": [\"触发的关键词或短语\"],\n");
        promptBuilder.append("  \"suspicious_user_id\": \"存在可疑行为的具体用户ID，如果没有则为null\",\n");

        if ("advice_generation".equals(taskType)) {
            promptBuilder.append("  \"prevention_advice\": [\"具体防范建议1\", \"具体防范建议2\"],\n");
        }

        promptBuilder.append("  \"analysis_steps\": [\n");
        promptBuilder.append("    \"已完成的步骤1\",\n");
        promptBuilder.append("    \"已完成的步骤2\",\n");
        promptBuilder.append("    \"已完成的步骤3\"\n");
        promptBuilder.append("  ]\n");
        promptBuilder.append("}");

        // 添加严格的格式要求
        promptBuilder.append("**格式要求**：\n");
        promptBuilder.append("1. 必须直接返回JSON对象，不要用```json包装\n");
        promptBuilder.append("2. 不要添加任何说明文字\n");
        promptBuilder.append("3. 确保JSON格式正确，可以被直接解析\n");

        return promptBuilder.toString();
    }
}
