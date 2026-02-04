/**
 * @author qkcao
 * @date 2026/1/27 10:23
 */
package com.rental.guard.ai.domain.service.v1;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.guard.ai.domain.service.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 防租房欺诈AI智能体
 */
@Slf4j
@Component
public class RentalFraudAIAgent {

    // 智能体核心模块
    private final MemoryModule memoryModule;
    private final KnowledgeBase knowledgeBase;
    private final DecisionModule decisionModule;
    private final ActionModule actionModule;
    private final LearningModule learningModule;
    @Autowired
    private LLMService llmService;

    public RentalFraudAIAgent() {
        this.memoryModule = new MemoryModule();
        this.knowledgeBase = new KnowledgeBase();
        this.decisionModule = new DecisionModule();
        this.actionModule = new ActionModule();
        this.learningModule = new LearningModule();
    }

    /**
     * 处理用户对话
     */
    public AgentResponseNo processConversation(String sessionId, String userMessage) {
        try {
            log.info("智能体处理会话[{}] - 消息: {}", sessionId, userMessage);

            // 1. 记忆管理：获取对话历史
            ConversationContext context = memoryModule.getOrCreateContext(sessionId);
            context.addMessage(userMessage, MessageType.USER);

            // 2. 风险特征提取
            RiskFeatures features = extractRiskFeatures(context);

            // 3. 知识库查询：获取相关案例和规则
            KnowledgeData knowledge = knowledgeBase.queryRelevantKnowledge(context, features);

            // 4. AI分析
            FraudAnalysisResult analysis = performAIAnalysis(context, features, knowledge);

            // 5. 决策制定
            AgentDecision decision = decisionModule.makeDecision(context, features, analysis);

            // 6. 执行动作
            AgentAction action = actionModule.executeAction(decision, context);

            // 7. 学习反馈
            learningModule.recordExperience(context, features, analysis, decision);

            // 8. 构建响应
            AgentResponseNo response = buildResponse(context, analysis, decision, action);
            context.addMessage(response.getResponse(), MessageType.AGENT);

            return response;

        } catch (Exception e) {
            log.error("智能体处理异常", e);
            return AgentResponseNo.failure("系统处理异常，请稍后重试");
        }
    }

    /**
     * 提取风险特征
     */
    private RiskFeatures extractRiskFeatures(ConversationContext context) {
        RiskFeatures features = new RiskFeatures();

        // 提取文本特征
        String latestMessage = context.getLatestMessage();
        features.setKeywords(extractKeywords(latestMessage));
        features.setUrgencyScore(calculateUrgencyScore(latestMessage));
        features.setPressureScore(calculatePressureScore(latestMessage));

        // 提取对话特征
        features.setConversationLength(context.getMessageCount());
        features.setRepeatQuestionsCount(countRepeatQuestions(context));

        return features;
    }

    /**
     * 执行AI分析
     */
    private FraudAnalysisResult performAIAnalysis(ConversationContext context,
                                                  RiskFeatures features,
                                                  KnowledgeData knowledge) {
        try {
            // 构建分析请求
            AIAnalysisRequest request = new AIAnalysisRequest();
            request.setConversation(context.getAllMessages());
            request.setRiskFeatures(features);
            request.setRelevantKnowledge(knowledge);
            request.setTimestamp(LocalDateTime.now());

            // 调用AI分析服务（可以是本地模型或远程API）
            String prompt = buildAnalysisPrompt(request);
            AIAnalysisResult aiResult = callAIService(prompt);

            if (!aiResult.isSuccess()) {
                return FraudAnalysisResult.failure(aiResult.getErrorMessage());
            }

            // 解析AI结果
            return parseAIResult(aiResult.getContent());

        } catch (Exception e) {
            log.error("AI分析异常", e);
            return FraudAnalysisResult.failure("AI分析失败");
        }
    }

    /**
     * 构建分析提示词
     */
    private String buildAnalysisPrompt(AIAnalysisRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的防租房欺诈AI助手。请分析以下对话并判断是否存在欺诈风险：\n\n");

        prompt.append("【对话历史】\n");
        for (String message : request.getConversation()) {
            prompt.append(message).append("\n");
        }

        prompt.append("\n【风险特征】\n");
        prompt.append("紧急程度: ").append(request.getRiskFeatures().getUrgencyScore()).append("\n");
        prompt.append("施压程度: ").append(request.getRiskFeatures().getPressureScore()).append("\n");
        prompt.append("关键词: ").append(String.join(", ", request.getRiskFeatures().getKeywords())).append("\n");

        prompt.append("\n【请按以下格式输出JSON】\n");
        prompt.append("{\n");
        prompt.append("  \"isFraud\": true/false,\n");
        prompt.append("  \"riskScore\": 0-100,\n");
        prompt.append("  \"fraudType\": \"欺诈类型\",\n");
        prompt.append("  \"confidence\": 0.0-1.0,\n");
        prompt.append("  \"reasons\": [\"原因1\", \"原因2\"],\n");
        prompt.append("  \"suggestions\": [\"建议1\", \"建议2\"]\n");
        prompt.append("}");

        return prompt.toString();
    }

    /**
     * 构建响应
     */
    private AgentResponseNo buildResponse(ConversationContext context,
                                          FraudAnalysisResult analysis,
                                          AgentDecision decision,
                                          AgentAction action) {
        AgentResponseNo response = new AgentResponseNo();
        response.setSessionId(context.getSessionId());
        response.setAnalysisResult(analysis);
        response.setDecision(decision);
        response.setAction(action);
        response.setResponse(generateResponseText(analysis, decision));
        response.setTimestamp(LocalDateTime.now());
        return response;
    }

    /**
     * 生成响应文本
     */
    private String generateResponseText(FraudAnalysisResult analysis, AgentDecision decision) {
        if (analysis.isFraud()) {
            return String.format("⚠️ 检测到高风险行为（风险分: %.1f）\n\n%s\n\n建议：%s",
                    analysis.getRiskScore(),
                    String.join("\n", analysis.getReasons()),
                    String.join("；", analysis.getSuggestions()));
        } else {
            return "✅ 当前对话未发现明显欺诈风险。请继续保持警惕，注意保护个人信息和财产安全。";
        }
    }

    // 辅助方法
    private List<String> extractKeywords(String text) {
        List<String> keywords = Arrays.asList("押金", "定金", "转账", "现金", "紧急", "今天", "马上");
        List<String> found = new ArrayList<>();
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                found.add(keyword);
            }
        }
        return found;
    }

    private int calculateUrgencyScore(String text) {
        String[] urgencyWords = {"马上", "立即", "今天", "立刻", "赶紧", "快点"};
        int score = 0;
        for (String word : urgencyWords) {
            if (text.contains(word)) score += 10;
        }
        return Math.min(score, 100);
    }

    private int calculatePressureScore(String text) {
        String[] pressureWords = {"最后", "唯一", "错过", "没了", "不租了"};
        int score = 0;
        for (String word : pressureWords) {
            if (text.contains(word)) score += 15;
        }
        return Math.min(score, 100);
    }

    private int countRepeatQuestions(ConversationContext context) {
        // 简化的重复问题检测
        Set<String> uniqueQuestions = new HashSet<>();
        int repeats = 0;
        for (String msg : context.getMessageNos()) {
            if (msg.length() > 10) {
                if (!uniqueQuestions.add(msg)) {
                    repeats++;
                }
            }
        }
        return repeats;
    }

    private AIAnalysisResult callAIService(String prompt) {
        // 这里可以接入实际的AI服务
        // 简化实现：使用规则引擎
        String generate = llmService.generate(prompt);
        return simulateAIAnalysis(generate);
    }

    private AIAnalysisResult simulateAIAnalysis(String simulatedResponse) {
        // 简化模拟
//        String simulatedResponse = "{\n" +
//                "  \"isFraud\": false,\n" +
//                "  \"riskScore\": 35.5,\n" +
//                "  \"fraudType\": \"\",\n" +
//                "  \"confidence\": 0.75,\n" +
//                "  \"reasons\": [\"对话内容正常\", \"未发现紧急催促\"],\n" +
//                "  \"suggestions\": [\"建议要求正规合同\", \"避免现金交易\"]\n" +
//                "}";

        AIAnalysisResult result = new AIAnalysisResult();
        result.setSuccess(true);
        result.setContent(simulatedResponse);
        return result;
    }

    private FraudAnalysisResult parseAIResult(String response) {
        FraudAnalysisResult result = new FraudAnalysisResult();

        try {
            // 清理响应，提取JSON部分
            String jsonStr = extractJsonFromResponse(response);

            // 使用ObjectMapper解析
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonStr);

            // 设置boolean类型字段
            if (root.has("isFraud")) {
                result.setFraud(root.get("isFraud").asBoolean());
            }

            // 设置double类型字段
            if (root.has("riskScore")) {
                result.setRiskScore(root.get("riskScore").asDouble());
            }

            if (root.has("confidence")) {
                result.setConfidence(root.get("confidence").asDouble());
            }

            // 设置字符串类型字段
            if (root.has("fraudType")) {
                result.setFraudType(root.get("fraudType").asText());
            }

            // 设置数组类型字段
            if (root.has("reasons")) {
                List<String> reasons = mapper.convertValue(
                        root.get("reasons"),
                        new TypeReference<List<String>>() {}
                );
                result.setReasons(reasons);
            }

            if (root.has("suggestions")) {
                List<String> suggestions = mapper.convertValue(
                        root.get("suggestions"),
                        new TypeReference<List<String>>() {}
                );
                result.setSuggestions(suggestions);
            }

            // 设置分析成功标志
            result.setAnalysisSuccess(true);

        } catch (Exception e) {
            log.warn("解析AI响应失败，返回默认结果", e);
            // 解析失败时返回一个默认的失败结果
            return FraudAnalysisResult.failure("解析AI响应失败: " + e.getMessage());
        }

        return result;
    }

    // 从响应中提取JSON字符串
    private String extractJsonFromResponse(String response) {
        // 方法1：直接查找 { 和 } 之间的内容
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');

        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }

        // 方法2：使用正则表达式提取JSON
        Pattern pattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group();
        }

        // 如果都失败，返回原始响应
        return response.trim();
    }
}
