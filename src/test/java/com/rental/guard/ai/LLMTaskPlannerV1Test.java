/**
 * @author qkcao
 * @date 2026/1/27 11:19
 */
package com.rental.guard.ai;

import com.rental.guard.ai.domain.service.v1.AgentResponseNo;
import com.rental.guard.ai.domain.service.v1.RentalFraudAIAgent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
public class LLMTaskPlannerV1Test {
    @Autowired
    private RentalFraudAIAgent rentalFraudAIAgent;

    @Test
    void testComplexInvestigationPlanning() {
        String sessionId = "session_123";
        String userMessage = "房子我很满意，今天能定下来吗？需要马上交5000元押金";

        AgentResponseNo response = rentalFraudAIAgent.processConversation(sessionId, userMessage);

        System.out.println("响应: " + response.getResponse());
        System.out.println("风险分数: " + response.getAnalysisResult().getRiskScore());
        System.out.println("建议: " + response.getDecision().getRecommendations());
    }

    @Test
    void testComplexInvestigationPlanning2() {
        String sessionId = "session_123";
        String userMessage = "房子我很满意，今天能定下来吗？需要马上交5000元押金";

        AgentResponseNo response = rentalFraudAIAgent.processConversation(sessionId, userMessage);

        System.out.println("响应: " + response.getResponse());
        System.out.println("风险分数: " + response.getAnalysisResult().getRiskScore());
        System.out.println("建议: " + response.getDecision().getRecommendations());
    }


}
