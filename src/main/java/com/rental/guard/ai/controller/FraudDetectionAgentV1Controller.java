/**
 * @author qkcao
 * @date 2026/1/27 11:15
 */
package com.rental.guard.ai.controller;

import com.rental.guard.ai.domain.service.FraudDetectionService;
import com.rental.guard.ai.domain.service.v1.AgentResponseNo;
import com.rental.guard.ai.domain.service.v1.RentalFraudAIAgent;
import com.rental.guard.ai.infrastructure.mapper.FraudTrainingCaseMapper;
import com.rental.guard.ai.infrastructure.po.PoFraudTrainingCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fraud-agent")
@Slf4j
public class FraudDetectionAgentV1Controller {
    @Autowired
    private RentalFraudAIAgent fraudAgent;
    @Autowired
    private FraudDetectionService fraudDetectionService;
    @Autowired
    private FraudTrainingCaseMapper fraudTrainingCaseMapper;

    /**
     * 处理对话消息
     */
    @PostMapping("/conversation/{sessionId}")
    public ResponseEntity<AgentResponseNo> processMessage() {

        // 处理对话
        String sessionId = "session_123";
        String userMessage = "房子我很满意，今天能定下来吗？需要马上交5000元押金";

        AgentResponseNo response = fraudAgent.processConversation(
                sessionId,
                userMessage);

        return ResponseEntity.ok(response);
    }

    /**
     * 处理对话消息
     */
    @PostMapping("/conversation/v2")
    public void processMessage2() {

// 获取相关训练案例
        List<PoFraudTrainingCase> trainingCases = fraudTrainingCaseMapper.getALlPoFraudTrainingCase();
        for (PoFraudTrainingCase trainingCase : trainingCases) {
            List<Float> embedding = fraudDetectionService.getEmbedding(trainingCase.getChatContent());

        }
    }
}
