/**
 * @author qkcao
 * @date 2026/2/11 11:43
 */
package com.rental.guard.ai.domain.service.Scenario;

import com.rental.guard.ai.domain.dto.v1.AgentResponse;
import com.rental.guard.ai.domain.dto.v1.Message;
import com.rental.guard.ai.domain.dto.v1.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 默认场景处理器
 */
@Slf4j
@Component
public class DefaultScenarioHandler implements ScenarioHandler {

    @Override
    public void process(AgentResponse response, SessionManager session,
                        List<AgentResponse.RetrievedDocument> docs) {
        try {
            log.info("执行默认场景后处理 - 会话: {}", session.getSessionId());

            // 通用后处理逻辑

            // 1. 风险等级校准
            calibrateRiskLevel(response, session);

            // 2. 置信度调整
            adjustConfidence(response, docs);

            // 3. 会话历史分析
            analyzeSessionHistory(response, session);

            // 4. 建议补充
            supplementGeneralAdvice(response);

            log.info("默认场景后处理完成");

        } catch (Exception e) {
            log.error("默认场景处理异常", e);
        }


    }

    private void calibrateRiskLevel(AgentResponse response, SessionManager session) {
        // 1. 快速计算总互动次数 (使用流的简写或 Map 聚合)
        int totalInteractions = session.getScenarioCounter().values().stream()
                .mapToInt(Integer::intValue).sum();

        // 2. 引入权重阈值（建议放入配置类或常量）
        final int VETERAN_THRESHOLD = 15;

        // 3. 计算用户信任/经验指数 (0.0 - 1.0)
        double experienceFactor = Math.min(1.0, (double) totalInteractions / VETERAN_THRESHOLD);

        // 4. 核心逻辑优化：只针对“中低风险”进行降噪，对“高/极高”风险保持警惕
        Double currentScore = response.getRiskScore(); // 假设你已按照之前的建议实现了分数制
        if (currentScore == null) return;

        if (experienceFactor > 0.7) { // 经验丰富的用户
            // 策略：如果客观评分只是略高（如0.75），则针对资深用户平滑处理
            // 但如果评分极高（>0.9），说明是致命霸王条款，坚决不降级
            if (currentScore > 0.7 && currentScore < 0.9) {
                response.setRiskLevel("中高风险");
                response.appendDetailedAnalysis("\n💡 [提示] 基于您的历史使用经验，系统已为您精简了基础合规性描述。");
            }

            // 存入元数据，前端可以展示“老用户专属精简版分析”
            response.putMetadata("userCalibrated", true);
        }
    }

    private void adjustConfidence(AgentResponse response, List<AgentResponse.RetrievedDocument> docs) {
        // 根据支持文档数量调整置信度
        int relevantDocCount = (int) docs.stream()
                .filter(doc -> doc.getRelevanceScore() > 0.7)
                .count();

        if (relevantDocCount >= 3) {
            response.setConfidence(Math.min(response.getConfidence() * 1.2, 1.0));
        } else if (relevantDocCount == 0) {
            response.setConfidence(Math.max(response.getConfidence() * 0.8, 0.5));
        }
    }

    private void analyzeSessionHistory(AgentResponse response, SessionManager session) {
        // 1. 从消息记录中获取最新的用户输入
        List<Message> messages = session.getMessageHistory();
        if (messages.size() < 2) return;

        Message latestUserMsg = messages.stream()
                .filter(m -> "USER".equals(m.getSender()))
                .reduce((first, second) -> second) // 获取最后一条
                .orElse(null);

        if (latestUserMsg != null) {
            boolean isRepeated = detectRepeatedQuestion(latestUserMsg.getContentAsString(), messages);
            if (isRepeated) {
                // 2. 标记响应类型为引用
                response.setResponseType(AgentResponse.ResponseType.ANALYSIS);
                response.appendDetailedAnalysis("\n🔄 **温馨提示**：您刚才询问过类似的问题。");
                response.appendDetailedAnalysis("\n您可以直接查看上方已生成的【" + session.getCurrentScenario() + "】报告，如需深度解析请告诉我。");

                // 3. 可以在元数据中记录，让前端展示“历史引用”的小标签
                response.putMetadata("is_repeated_query", true);
            }
        }
    }

    private void supplementGeneralAdvice(AgentResponse response) {
        // 添加通用建议
        response.appendDetailedAnalysis("\n💎 租房通用建议:");
        response.appendDetailedAnalysis("\n  - 仔细阅读合同所有条款");
        response.appendDetailedAnalysis("\n  - 实地查看房屋状况");
        response.appendDetailedAnalysis("\n  - 核实房东身份和产权证明");
        response.appendDetailedAnalysis("\n  - 保留所有付款凭证和沟通记录");
    }

    private boolean detectRepeatedQuestion(String currentUserInput, List<Message> history) {
        if (currentUserInput == null || history == null || history.isEmpty()) return false;

        // 1. 获取最近 10 分钟内的用户提问（时间维度预处理）
        LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);

        List<String> lastUserQueries = history.stream()
                .filter(msg -> "USER".equals(msg.getSender()))
                .filter(msg -> msg.getTimestamp().isAfter(tenMinutesAgo)) // 增加时间窗口
                .map(Message::getContentAsString)
                .filter(content -> !content.equals(currentUserInput))
                .limit(5)
                .collect(Collectors.toList());

        for (String oldQuery : lastUserQueries) {
            // 长度过滤：如果长度差距超过一倍，基本不可能是重复问题，快速失败
            if (Math.abs(oldQuery.length() - currentUserInput.length()) > Math.max(oldQuery.length(), currentUserInput.length()) * 0.5) {
                continue;
            }

            if (calculateSimilarity(currentUserInput, oldQuery) > 0.85) {
                return true;
            }
        }
        return false;
    }


    private double calculateSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) return 0.0;

        // 1. 预处理：转小写、去除空格及特殊符号
        String s1 = text1.toLowerCase().replaceAll("[\\s\\p{Punct}]+", "");
        String s2 = text2.toLowerCase().replaceAll("[\\s\\p{Punct}]+", "");

        if (s1.equals(s2)) return 1.0;
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;

        // 2. 使用编辑距离算法 (可以使用 Apache Commons Text 库，或手动实现)
        int editDistance = getLevenshteinDistance(s1, s2);

        // 3. 转化为相似度：1 - (距离 / 最大长度)
        return 1.0 - ((double) editDistance / Math.max(s1.length(), s2.length()));
    }

    private int getLevenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) costs[j] = j;
                else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1))
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }
}
