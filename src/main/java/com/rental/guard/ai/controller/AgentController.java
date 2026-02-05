/**
 * @author qkcao
 * @date 2026/1/28 15:30
 */
package com.rental.guard.ai.controller;

import com.rental.guard.ai.domain.dto.v1.AgentResponse;
import com.rental.guard.ai.domain.service.v1.AgentOrchestrator;
import com.rental.guard.ai.domain.service.v1.OptimizedAgentOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST API控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    @Autowired
    private AgentOrchestrator agentOrchestrator;

    @Autowired
    private OptimizedAgentOrchestrator optimizedAgentOrchestrator;

    /**
     * 处理用户消息
     * @param sessionId 会话ID
     * @param userInput 用户输入内容
     * @param type 1:文本，2:图片
     * @param localPath 文件路径
     * @return
     */
    @GetMapping("/chat")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> chat(
            @RequestParam String sessionId,
            @RequestParam String userInput,
            @RequestParam Integer type,
            @RequestParam String localPath) {

        if (userInput == null || userInput.trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                            .body(Map.of("error", "消息内容不能为空")));
        }

        return optimizedAgentOrchestrator.processRequestWithReAct(sessionId, userInput, localPath)
                .thenApply(response -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("sessionId", sessionId);
                    result.put("response", response);
                    result.put("formattedResponse", response.getFormattedResponse());

                    return ResponseEntity.ok(result);
                })
                .exceptionally(e -> {
                    log.error("聊天处理失败", e);
                    return ResponseEntity.internalServerError()
                            .body(Map.of(
                                    "error", "处理请求时发生错误",
                                    "message", e.getMessage()
                            ));
                });
    }

    /**
     * 批量处理消息
     */
    @PostMapping("/batch-chat")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> batchChat(
            @RequestBody Map<String, Object> request) {

        try {
            @SuppressWarnings("unchecked")
            Map<String, String> sessionMessages = (Map<String, String>) request.get("messages");

            if (sessionMessages == null || sessionMessages.isEmpty()) {
                return CompletableFuture.completedFuture(
                        ResponseEntity.badRequest()
                                .body(Map.of("error", "消息不能为空")));
            }

            return agentOrchestrator.batchProcessRequests(
                            new ArrayList<>(sessionMessages.keySet()),
                            new ArrayList<>(sessionMessages.values()))
                    .thenApply(responses -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("success", true);
                        result.put("count", responses.size());
                        result.put("responses", responses);

                        return ResponseEntity.ok(result);
                    })
                    .exceptionally(e -> {
                        log.error("批量聊天处理失败", e);
                        return ResponseEntity.internalServerError()
                                .body(Map.of(
                                        "error", "批量处理请求时发生错误",
                                        "message", e.getMessage()
                                ));
                    });

        } catch (Exception e) {
            log.error("批量聊天请求解析失败", e);
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                            .body(Map.of("error", "请求格式错误")));
        }
    }

    /**
     * 获取系统状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            Map<String, Object> status = agentOrchestrator.getSystemStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("获取系统状态失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("service", "SmartAgent");
        health.put("version", "1.0.0");

        return ResponseEntity.ok(health);
    }

    /**
     * 模拟表格中的测试用例
     */
    @PostMapping("/test-scenarios")
    public ResponseEntity<Map<String, Object>> testScenarios() {
        try {
            Map<String, String> testCases = new HashMap<>();
            testCases.put("合同审核", "上传合同，询问押金条款");
            testCases.put("距离欺诈", "房子离地铁真的只要5分钟吗？");
            testCases.put("租金欺诈", "房东报价5000，市场价多少？");
            testCases.put("霸王条款", "合同里说断租不退押金");

            Map<String, Object> results = new HashMap<>();

            for (Map.Entry<String, String> entry : testCases.entrySet()) {
                String sessionId = "test_" + entry.getKey() + "_" + System.currentTimeMillis();
                CompletableFuture<AgentResponse> future =
                        agentOrchestrator.processRequestV2(sessionId, entry.getValue(), null);

                try {
                    AgentResponse response = future.get(); // 等待完成
                    results.put(entry.getKey(), Map.of(
                            "input", entry.getValue(),
                            "riskLevel", response.getRiskLevel(),
                            "coreLogic", response.getCoreLogic(),
                            "recommendations", response.getRecommendations()
                    ));
                } catch (Exception e) {
                    results.put(entry.getKey(), Map.of("error", e.getMessage()));
                }
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "testResults", results
            ));

        } catch (Exception e) {
            log.error("测试场景失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 处理用户消息
     */
    @GetMapping("/chat")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> chat11(
            @RequestParam String sessionId,
            @RequestParam String userInput) {

        if (userInput == null || userInput.trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                            .body(Map.of("error", "消息内容不能为空")));
        }

        return agentOrchestrator.processRequestV2(sessionId, userInput, null)
                .thenApply(response -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("sessionId", sessionId);
                    result.put("response", response);
                    result.put("formattedResponse", response.getFormattedResponse());

                    return ResponseEntity.ok(result);
                })
                .exceptionally(e -> {
                    log.error("聊天处理失败", e);
                    return ResponseEntity.internalServerError()
                            .body(Map.of(
                                    "error", "处理请求时发生错误",
                                    "message", e.getMessage()
                            ));
                });
    }
}
