/**
 * @author qkcao
 * @date 2025/9/16 18:41
 */
package com.rental.guard.ai.contants;

import com.rental.guard.ai.infrastructure.po.PoFraudTrainingCase;

import java.math.BigDecimal;
import java.util.List;

/**
 * 反欺诈检测相关常量
 */
public class FraudDetectionConstants {

    /**
     * 风险等级阈值
     */
    public static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("0.8");
    public static final BigDecimal MEDIUM_RISK_THRESHOLD = new BigDecimal("0.5");
    public static final BigDecimal LOW_RISK_THRESHOLD = new BigDecimal("0.2");

    /**
     * 欺诈类型
     */
    public static final String FRAUD_TYPE_PIG_BUTCHERING = "杀猪盘";
    public static final String FRAUD_TYPE_INVESTMENT = "投资诈骗";
    public static final String FRAUD_TYPE_FAKE_HOUSE = "虚假房源";
    public static final String FRAUD_TYPE_OTHER = "其他";

    /**
     * 处理动作
     */
    public static final String ACTION_BAN_USER = "ban_user";
    public static final String ACTION_LIMIT_CHAT = "limit_chat";
    public static final String ACTION_WARNING = "warning";
    public static final String ACTION_MONITOR = "monitor";


    /**
     * 构建包含训练案例的提示词
     *
     * @param chatContext 聊天上下文
     * @param trainingCases 训练案例列表
     * @return 完整的提示词
     */
    public static String buildPromptWithTrainingCases(String chatContext,
                                                      List<PoFraudTrainingCase> trainingCases) {
        StringBuilder promptBuilder = new StringBuilder();

        promptBuilder.append("你是一个专业的反欺诈分析专家，专门识别租房场景中的诈骗和杀猪盘行为。\n\n");

        // 添加训练案例作为参考
        if (trainingCases != null && !trainingCases.isEmpty()) {
            promptBuilder.append("参考以下已标注的训练案例，学习识别模式：\n\n");

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

        promptBuilder.append("分析以下聊天记录，重点关注：\n");
        promptBuilder.append("1. 是否存在诱导添加其他平台如QQ、百度网盘等行为\n");
        promptBuilder.append("2. 是否快速建立信任关系、给老板租房、长租2-3等，不符合正常租房交流\n");
        promptBuilder.append("4. 是否存在虚假承诺或过度热情\n");
        promptBuilder.append("5. 语言表达是否符合正常房屋租赁场景\n");
        promptBuilder.append("6. 是否与训练案例中的欺诈模式相似\n\n");

        promptBuilder.append("**重要提醒 1：**：平台允许用户指导对方加微信、打电话，需综合判断为正常用户，并且平台有自动回复，回复内容一般是引导添加微信联系，所以有重复消息引导添加微信基本可视为正常用户。\n");
        promptBuilder.append("**重要提醒 2：**：聊天记录格式为 [时间] 用户ID (消息类型): 消息内容。\n");
        promptBuilder.append("请仔细分析每个用户的发言内容，识别出具体是哪个用户ID存在欺诈行为。\n\n");

        promptBuilder.append("当前聊天记录：\n").append(chatContext).append("\n\n");

        promptBuilder.append("请返回JSON格式分析结果：\n");
        promptBuilder.append("{\n");
        promptBuilder.append("  \"is_fraud\": true/false,\n");
        promptBuilder.append("  \"risk_score\": 0.0-1.0,\n");
        promptBuilder.append("  \"fraud_type\": \"杀猪盘/投资诈骗/其他\",\n");
        promptBuilder.append("  \"confidence\": 0.0-1.0,\n");
        promptBuilder.append("  \"reason\": \"详细分析原因，如果是欺诈请说明与哪个训练案例相似\",\n");
        promptBuilder.append("  \"keywords\": [\"触发的关键词\"],\n");
        promptBuilder.append("  \"suspicious_user_id\": \"存在欺诈行为的具体用户ID，如果没有欺诈则为null\"\n");
        promptBuilder.append("}");

        return promptBuilder.toString();
    }
}
