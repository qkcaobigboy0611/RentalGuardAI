/**
 * @author qkcao
 * @date 2026/1/27 10:39
 */
package com.rental.guard.ai.domain.service.v1;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 学习模块：记录经验并更新知识
 */
@Slf4j
public class LearningModule {
    private final List<LearningExperience> experiences = new ArrayList<>();

    public void recordExperience(ConversationContext context,
                                 RiskFeatures features,
                                 FraudAnalysisResult analysis,
                                 AgentDecision decision) {
        LearningExperience experience = new LearningExperience();
        experience.setSessionId(context.getSessionId());
        experience.setFeatures(features);
        experience.setAnalysis(analysis);
        experience.setDecision(decision);
        experience.setTimestamp(LocalDateTime.now());

        experiences.add(experience);

        // 简单的学习逻辑：如果AI分析错误较多，可以调整权重
        adjustLearningWeights();
    }

    private void adjustLearningWeights() {
        // 简化的学习逻辑
        // 在实际应用中，这里可以集成机器学习模型
        if (experiences.size() % 100 == 0) {
            log.info("已积累 {} 条学习经验，可进行模型更新", experiences.size());
        }
    }
}
