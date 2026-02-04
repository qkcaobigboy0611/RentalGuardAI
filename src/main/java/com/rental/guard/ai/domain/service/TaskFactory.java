/**
 * @author qkcao
 * @date 2026/1/22 19:08
 */
package com.rental.guard.ai.domain.service;

import com.rental.guard.ai.domain.dto.PlanningStrategyEnum;
import com.rental.guard.ai.domain.dto.Task;
import com.rental.guard.ai.domain.dto.TaskStatusEnum;
import com.rental.guard.ai.domain.dto.TaskTypeEnum;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class TaskFactory {

    /**
     * 根据意图生成初始任务
     */
    public List<Task> createTasks(IntentRecognitionModule.AgentIntent intent,
                                  PlanningStrategyEnum strategy) {
        List<Task> tasks = new ArrayList<>();

        switch (intent.getIntentType()) {
            case SINGLE_ANALYSIS:
                tasks.add(createSingleAnalysisTask(intent));
                break;
            case USER_INVESTIGATION:
                tasks.add(createUserInvestigationTask(intent));
                break;
            case REAL_TIME_MONITORING:
                tasks.add(createRealTimeMonitoringTask(intent));
                break;
            case REPORT_GENERATION:
                tasks.add(createReportGenerationTask(intent));
                break;
            case RULE_CONFIGURATION:
                tasks.add(createRuleConfigurationTask(intent));
                break;
            case BATCH_PROCESSING:
                tasks.add(createBatchProcessingTask(intent));
                break;
            default:
                tasks.add(createGenericTask(intent));
        }

        // 根据策略调整任务
        adjustTasksForStrategy(tasks, strategy);

        return tasks;
    }

    /**
     * 创建单次分析任务
     */
    private Task createSingleAnalysisTask(IntentRecognitionModule.AgentIntent intent) {
        return Task.builder()
                .taskId(generateTaskId("ANALYSIS"))
                .taskType(TaskTypeEnum.RISK_ANALYSIS)
                .name("单次风险分析")
                .description("分析单条聊天记录或文本的风险")
                .parameters(extractAnalysisParameters(intent))
                .priority(intent.getPriority() == IntentRecognitionModule.Priority.HIGH ? 10 : 5)
                .estimatedDuration(10)
                .timeout(30)
                .maxRetries(3)
                .requiredTools(Arrays.asList("RiskAnalyzer", "TextProcessor"))
                .status(TaskStatusEnum.PENDING)
                .createTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建用户调查任务
     */
    private Task createUserInvestigationTask(IntentRecognitionModule.AgentIntent intent) {
        return Task.builder()
                .taskId(generateTaskId("INVESTIGATION"))
                .taskType(TaskTypeEnum.USER_INVESTIGATION)
                .name("用户深度调查")
                .description("全面调查用户的风险行为")
                .parameters(extractUserInvestigationParameters(intent))
                .priority(intent.getPriority() == IntentRecognitionModule.Priority.HIGH ? 9 : 6)
                .estimatedDuration(60)
                .timeout(300)
                .maxRetries(2)
                .requiredTools(Arrays.asList(
                        "UserQueryService",
                        "ChatQueryService",
                        "TransactionService",
                        "RiskAnalyzer"
                ))
                .status(TaskStatusEnum.PENDING)
                .createTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建实时监控任务
     */
    private Task createRealTimeMonitoringTask(IntentRecognitionModule.AgentIntent intent) {
        return Task.builder()
                .taskId(generateTaskId("MONITOR"))
                .taskType(TaskTypeEnum.REAL_TIME_MONITORING)
                .name("实时风险监控")
                .description("持续监控指定目标的实时行为")
                .parameters(extractMonitoringParameters(intent))
                .priority(10)  // 实时监控总是高优先级
                .estimatedDuration(3600)  // 默认监控1小时
                .timeout(86400)  // 24小时超时
                .maxRetries(1)
                .requiredTools(Arrays.asList("StreamProcessor", "AlertManager"))
                .status(TaskStatusEnum.PENDING)
                .createTime(LocalDateTime.now())
                .constraints(Map.of(
                        "real_time", true,
                        "persistent", true
                ))
                .build();
    }

    /**
     * 创建报告生成任务
     */
    private Task createReportGenerationTask(IntentRecognitionModule.AgentIntent intent) {
        return Task.builder()
                .taskId(generateTaskId("REPORT"))
                .taskType(TaskTypeEnum.GENERATE_RISK_REPORT)
                .name("风险报告生成")
                .description("生成指定范围的风险报告")
                .parameters(extractReportParameters(intent))
                .priority(intent.getPriority() == IntentRecognitionModule.Priority.HIGH ? 8 : 4)
                .estimatedDuration(120)
                .timeout(600)
                .maxRetries(2)
                .requiredTools(Arrays.asList("DataCollector", "ReportGenerator"))
                .outputs(List.of("report_output"))
                .status(TaskStatusEnum.PENDING)
                .createTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建规则配置任务
     */
    private Task createRuleConfigurationTask(IntentRecognitionModule.AgentIntent intent) {
        return Task.builder()
                .taskId(generateTaskId("RULE"))
                .taskType(TaskTypeEnum.RULE_CONFIGURATION)
                .name("风险规则配置")
                .description("配置或更新风险检测规则")
                .parameters(extractRuleParameters(intent))
                .priority(intent.getPriority() == IntentRecognitionModule.Priority.HIGH ? 7 : 5)
                .estimatedDuration(30)
                .timeout(180)
                .maxRetries(3)
                .requiredTools(Arrays.asList("RuleEngine", "ValidationService"))
                .status(TaskStatusEnum.PENDING)
                .createTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建批量处理任务
     */
    private Task createBatchProcessingTask(IntentRecognitionModule.AgentIntent intent) {
        return Task.builder()
                .taskId(generateTaskId("BATCH"))
                .taskType(TaskTypeEnum.BATCH_ANALYSIS)
                .name("批量风险分析")
                .description("批量分析多条记录的风险")
                .parameters(extractBatchParameters(intent))
                .priority(intent.getPriority() == IntentRecognitionModule.Priority.HIGH ? 7 : 5)
                .estimatedDuration(300)
                .timeout(1800)
                .maxRetries(1)
                .requiredTools(Arrays.asList("BatchProcessor", "RiskAnalyzer"))
                .maxConcurrent(5)
                .status(TaskStatusEnum.PENDING)
                .createTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建通用任务
     */
    private Task createGenericTask(IntentRecognitionModule.AgentIntent intent) {
        return Task.builder()
                .taskId(generateTaskId("GENERIC"))
                .taskType(TaskTypeEnum.LOOP_TASK)
                .name("通用处理任务")
                .description("处理通用请求")
                .parameters(intent.getParameters() != null ?
                        new HashMap<>(intent.getParameters()) : new HashMap<>())
                .priority(5)
                .estimatedDuration(30)
                .timeout(120)
                .maxRetries(2)
                .requiredTools(Collections.singletonList("GenericProcessor"))
                .status(TaskStatusEnum.PENDING)
                .createTime(LocalDateTime.now())
                .build();
    }

    /**
     * 根据策略调整任务
     */
    private void adjustTasksForStrategy(List<Task> tasks, PlanningStrategyEnum strategy) {
        for (Task task : tasks) {
            switch (strategy) {
                case PARALLEL:
                    task.setMaxConcurrent(task.getMaxConcurrent() != null ?
                            task.getMaxConcurrent() * 2 : 2);
                    break;
                case CONDITIONAL:
                    if (task.getMetadata() == null) {
                        task.setMetadata(new HashMap<>());
                    }
                    task.getMetadata().put("conditional_execution", true);
                    break;
                case ITERATIVE:
                    if (task.getMetadata() == null) {
                        task.setMetadata(new HashMap<>());
                    }
                    task.getMetadata().put("iterative", true);
                    task.getMetadata().put("max_iterations", 10);
                    break;
                case MONITORING:
                    task.setTimeout(86400);  // 监控任务24小时超时
                    break;
            }
        }
    }

    /**
     * 提取分析参数
     */
    private Map<String, Object> extractAnalysisParameters(IntentRecognitionModule.AgentIntent intent) {
        Map<String, Object> params = new HashMap<>();

        if (intent.getEntities() != null && !intent.getEntities().isEmpty()) {
            params.put("target", intent.getEntities().get(0));
        }

        if (intent.getParameters() != null) {
            params.putAll(intent.getParameters());
        }

        params.put("analysis_type", "single");
        params.put("timestamp", System.currentTimeMillis());

        return params;
    }

    /**
     * 提取用户调查参数
     */
    private Map<String, Object> extractUserInvestigationParameters(IntentRecognitionModule.AgentIntent intent) {
        Map<String, Object> params = new HashMap<>();

        // 提取用户ID
        if (intent.getEntities() != null) {
            for (String entity : intent.getEntities()) {
                if (entity.matches("\\w{6,20}")) {  // 简单用户ID匹配
                    params.put("user_id", entity);
                    break;
                }
            }
        }

        // 提取时间范围
        if (intent.getParameters() != null) {
            params.putAll(intent.getParameters());
        }

        if (!params.containsKey("time_range")) {
            params.put("time_range", "最近30天");
        }

        params.put("investigation_depth", "deep");
        params.put("include_related", true);

        return params;
    }

    /**
     * 提取监控参数
     */
    private Map<String, Object> extractMonitoringParameters(IntentRecognitionModule.AgentIntent intent) {
        Map<String, Object> params = new HashMap<>();

        if (intent.getEntities() != null && !intent.getEntities().isEmpty()) {
            params.put("monitor_targets", intent.getEntities());
        }

        if (intent.getParameters() != null) {
            params.putAll(intent.getParameters());
        }

        params.put("monitor_type", "real_time");
        params.put("alert_threshold", "medium");
        params.put("duration_minutes", 60);

        return params;
    }

    /**
     * 提取报告参数
     */
    private Map<String, Object> extractReportParameters(IntentRecognitionModule.AgentIntent intent) {
        Map<String, Object> params = new HashMap<>();

        if (intent.getParameters() != null) {
            params.putAll(intent.getParameters());
        }

        // 设置默认值
        params.putIfAbsent("report_type", "daily_risk_report");
        params.putIfAbsent("format", "pdf");
        params.putIfAbsent("include_charts", true);

        // 提取时间范围
        if (intent.getTimeRange() != null && intent.getTimeRange().getTimeExpression() != null) {
            params.put("time_range", intent.getTimeRange().getTimeExpression());
        } else {
            params.put("time_range", "最近7天");
        }

        return params;
    }

    /**
     * 提取规则参数
     */
    private Map<String, Object> extractRuleParameters(IntentRecognitionModule.AgentIntent intent) {
        Map<String, Object> params = new HashMap<>();

        if (intent.getParameters() != null) {
            params.putAll(intent.getParameters());
        }

        params.put("rule_scope", "global");
        params.put("validation_required", true);

        return params;
    }

    /**
     * 提取批量参数
     */
    private Map<String, Object> extractBatchParameters(IntentRecognitionModule.AgentIntent intent) {
        Map<String, Object> params = new HashMap<>();

        if (intent.getParameters() != null) {
            params.putAll(intent.getParameters());
        }

        params.putIfAbsent("batch_size", 100);
        params.putIfAbsent("batch_count", 1);
        params.put("processing_mode", "parallel");

        return params;
    }

    /**
     * 生成任务ID
     */
    private String generateTaskId(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" +
                Thread.currentThread().getId() + "_" +
                (int)(Math.random() * 10000);
    }
}
