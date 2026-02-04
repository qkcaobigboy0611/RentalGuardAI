/**
 * @author qkcao
 * @date 2026/1/23 18:23
 */
package com.rental.guard.ai;

import com.rental.guard.ai.domain.dto.IntentTypeEnum;
import com.rental.guard.ai.domain.dto.Task;
import com.rental.guard.ai.domain.dto.TaskPlan;
import com.rental.guard.ai.domain.dto.TaskTypeEnum;
import com.rental.guard.ai.domain.service.IntentRecognitionModule;
import com.rental.guard.ai.domain.service.LLMExceptionHandler;
import com.rental.guard.ai.domain.service.LLMTaskPlanner;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@SpringBootTest
public class LLMTaskPlannerTest {

    @Autowired
    private LLMTaskPlanner llmTaskPlanner;
    @Autowired
    private LLMExceptionHandler llmExceptionHandler;

    @Test
    void testComplexInvestigationPlanning() {
        IntentRecognitionModule.AgentIntent intent = IntentRecognitionModule.AgentIntent.builder()
                .intentType(IntentTypeEnum.USER_INVESTIGATION)
                .entities(List.of("user_12345", "13800138000"))
                .parameters(Map.of(
                        "time_range", "最近30天",
                        "investigation_depth", "deep"
                ))
                .confidence(0.92)
                .priority(IntentRecognitionModule.Priority.HIGH)
                .build();

        TaskPlan plan = llmTaskPlanner.planWithLLM(intent);

        log.info("LLM规划测试通过，生成{}个任务", plan.getTasks().size());
    }

    @Test
    void testLLMExceptionHandling() {
        Task task = Task.builder()
                .taskId("TEST_TASK")
                .taskType(TaskTypeEnum.QUERY_USER_INFO)
                .name("测试任务")
                .estimatedDuration(10)
                .timeout(5)
                .maxRetries(3)
                .build();

        TaskPlan plan = TaskPlan.builder()
                .planId("TEST_PLAN")
                .tasks(new ArrayList<>())
                .build();

        Exception exception = new TimeoutException("任务执行超时");

        LLMExceptionHandler.ExceptionHandlingResult result =
                llmExceptionHandler.handleTaskException(task, exception, plan);

        log.info("异常处理测试通过，建议行动: {}", result.getActionTaken());
    }
}


