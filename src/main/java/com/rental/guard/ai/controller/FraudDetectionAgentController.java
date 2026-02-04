/**
 * @author qkcao
 * @date 2026/1/22 17:31
 */
package com.rental.guard.ai.controller;

import com.alibaba.fastjson2.JSON;
import com.rental.guard.ai.domain.dto.*;
import com.rental.guard.ai.domain.service.*;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// FraudDetectionAgentController.java
@RestController
@RequestMapping("/ai/agent")
@Slf4j
public class FraudDetectionAgentController {

    @Autowired
    private IntentRecognitionModule intentRecognitionModule;
    @Autowired
    private TaskPlanner taskPlanner;

//    @Autowired
//    private TaskPlanner taskPlanner;
//
//    @Autowired
//    private ExecutionEngine executionEngine;

    /**
     * 新的智能体入口
     */
    @PostMapping("/process")
    public ResponseEntity<AgentResponse> processRequest(
            @RequestBody UserRequest request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        // 1. 意图识别
        IntentRecognitionModule.AgentIntent intent =
                intentRecognitionModule.recognizeIntent(request.getInput(), sessionId);

        log.info("识别到意图: {}, 置信度: {}",
                intent.getIntentType(), intent.getConfidence());

        // 2. 如果需要确认且置信度低，返回确认请求
        if (intent.getRequiresConfirmation() && intent.getConfidence() < 0.7) {
            return ResponseEntity.ok(AgentResponse.confirmationRequired(intent));
        }

//        // 3. 任务规划
//        TaskPlan taskPlan = taskPlanner.plan(intent);
//
//        // 4. 执行任务
//        ExecutionResult result = executionEngine.execute(taskPlan);

        // 5. 返回结果
        return ResponseEntity.ok(AgentResponse.success(intent, intent));
    }

    /**
     * 批量意图识别（用于历史数据分析）
     */
    @PostMapping("/batch-intent-recognition")
    public ResponseEntity<List<IntentRecognitionModule.AgentIntent>> batchRecognize(
            @RequestBody List<String> inputs) {

        List<IntentRecognitionModule.AgentIntent> intents = new ArrayList<>();

        for (String input : inputs) {
            IntentRecognitionModule.AgentIntent intent =
                    intentRecognitionModule.recognizeIntent(input, "batch-session");
            intents.add(intent);
        }

        return ResponseEntity.ok(intents);
    }

    /**
     * 获取意图统计
     */
    @GetMapping("/intent-stats")
    public ResponseEntity<Map<String, Object>> getIntentStatistics(
            @RequestParam(value = "days", defaultValue = "7") int days) {

        // 这里可以从数据库查询历史记录进行统计
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRequests", 1000);
        stats.put("intentDistribution", getDistributionData());
        stats.put("avgConfidence", 0.82);

        return ResponseEntity.ok(stats);
    }


    @PostMapping("/process-request")
    public AgentResponse processUserRequest(@RequestBody UserRequest request) {
        // 1. 识别意图
        IntentRecognitionModule.AgentIntent intent =
                intentRecognitionModule.recognizeIntent(request.getInput(), "");

        // 2. 任务规划
        TaskPlan plan = taskPlanner.plan(intent);

        // 3. 返回规划结果
        return AgentResponse.builder()
                .success(true)
                .message("任务规划完成")
                .data(Map.of(
                        "planId", plan.getPlanId(),
                        "taskCount", plan.getTasks().size(),
                        "estimatedDuration", plan.getMetadata().get("adjusted_duration"),
                        "strategy", plan.getStrategy().name()
                ))
                .build();
    }


    @PostMapping("/process/request/test")
    public ApiResponse processUserRequestTest() {
        // 模拟意图
        IntentRecognitionModule.AgentIntent intent = IntentRecognitionModule.AgentIntent.builder()
                .intentType(IntentTypeEnum.SINGLE_ANALYSIS)
                .entities(List.of("chat_123456"))
                .parameters(Map.of("content", "这是一条测试聊天记录"))
                .confidence(0.95)
                .priority(IntentRecognitionModule.Priority.MEDIUM)
                .build();

        // 执行规划
        TaskPlan plan = taskPlanner.plan(intent);

        // 验证结果
//        assertNotNull(plan);
//        assertEquals(PlanStatusEnum.READY, plan.getStatus());
//        assertEquals(1, plan.getTasks().size());
//
//        TaskPlanner.Task task = plan.getTasks().get(0);
//        assertEquals("ANALYZE_SINGLE_RECORD", task.getTaskType());
//        assertEquals(TaskPlanner.TaskStatus.PENDING, task.getStatus());

        log.info("单次分析计划创建成功: {}", JSON.toJSONString(plan));
        return ApiResponse.buildSuccess(plan);
    }

    @PostMapping("/process/request/test2")
    public ApiResponse processUserRequestTest2() {
        IntentRecognitionModule.AgentIntent intent = IntentRecognitionModule.AgentIntent.builder()
                .intentType(IntentTypeEnum.USER_INVESTIGATION)
                .entities(List.of("user_12345", "13800138000"))
                .parameters(Map.of("time_range", "最近30天"))
                .confidence(0.88)
                .priority(IntentRecognitionModule.Priority.HIGH)
                .build();

        TaskPlan plan = taskPlanner.plan(intent);

        // 验证任务依赖关系
        for (Task task : plan.getTasks()) {
            if (TaskTypeEnum.RISK_ANALYSIS.equals(task.getTaskType())) {
                log.info(JSON.toJSONString(task.getDependencies()));
            }
        }

        log.info("用户调查计划创建成功，任务数: {}", plan.getTasks().size());
        return ApiResponse.buildSuccess(plan);
    }


    @Autowired
    private LLMTaskPlanner llmTaskPlanner;

    @Autowired
    private LLMExceptionHandler llmExceptionHandler;

    @Autowired
    private LLMStrategySelector llmStrategySelector;

    /**
     * 智能任务规划接口
     */
    @PostMapping("/plan")
    public ResponseEntity<LLMPlanningResponse> planWithLLM(@RequestBody UserRequest request) {
        try {
            // 1. 识别意图
            IntentRecognitionModule.AgentIntent intent =
                    intentRecognitionModule.recognizeIntent(request.getInput(), null);

            // 2. LLM增强的任务规划
            TaskPlan plan = llmTaskPlanner.planWithLLM(intent);

            // 3. 构建响应
            LLMPlanningResponse response = LLMPlanningResponse.builder()
                    .success(true)
                    .planId(plan.getPlanId())
                    .plan(plan)
                    .explanation((String) plan.getMetadata().get("llm_explanation"))
                    .message("LLM规划完成")
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("LLM规划失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(LLMPlanningResponse.error("LLM规划失败: " + e.getMessage()));
        }
    }

    /**
     * 处理任务异常
     */
    @PostMapping("/handle-exception")
    public ResponseEntity<ExceptionHandlingResponse> handleTaskException(
            @RequestBody TaskExceptionRequest request) {

        try {
            // 获取任务和计划信息
//            Task task = getTaskById(request.getTaskId());
//            TaskPlan plan = getPlanById(request.getPlanId());

            Task task = null;
            TaskPlan plan = null;

            // 处理异常
            LLMExceptionHandler.ExceptionHandlingResult result =
                    llmExceptionHandler.handleTaskException(task, request.getException(), plan);

            // 更新策略选择器的历史记录
            if (result.getSuccess() != null) {
                llmStrategySelector.updateStrategyResult(
                        plan.getPlanId(), result.getSuccess(), plan.getStrategy());
            }

            ExceptionHandlingResponse response = ExceptionHandlingResponse.builder()
                    .success(true)
                    .result(result)
                    .message("异常处理完成")
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("异常处理失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ExceptionHandlingResponse.error("异常处理失败: " + e.getMessage()));
        }
    }

    /**
     * 获取策略建议报告
     */
    @GetMapping("/strategy-report")
    public ResponseEntity<String> getStrategyReport() {
        try {
            String report = llmStrategySelector.getStrategyAdviceReport();
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("获取策略报告失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("获取策略报告失败: " + e.getMessage());
        }
    }


    // 请求响应数据结构
    @Data
    @Builder
    static class LLMPlanningResponse {
        private boolean success;
        private String planId;
        private TaskPlan plan;
        private String explanation;
        private String message;

        public static LLMPlanningResponse error(String message) {
            return LLMPlanningResponse.builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }

    @Data
    static class TaskExceptionRequest {
        private String taskId;
        private String planId;
        private Exception exception;
    }

    @Data
    @Builder
    static class ExceptionHandlingResponse {
        private boolean success;
        private LLMExceptionHandler.ExceptionHandlingResult result;
        private String message;

        public static ExceptionHandlingResponse error(String message) {
            return ExceptionHandlingResponse.builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }


    private Map<String, Integer> getDistributionData() {
        // 模拟数据
        Map<String, Integer> distribution = new HashMap<>();
        distribution.put("SINGLE_ANALYSIS", 450);
        distribution.put("USER_INVESTIGATION", 300);
        distribution.put("REPORT_GENERATION", 150);
        distribution.put("REAL_TIME_MONITORING", 50);
        distribution.put("OTHERS", 50);
        return distribution;
    }
}
