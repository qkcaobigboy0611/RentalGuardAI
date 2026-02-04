/**
 * @author qkcao
 * @date 2026/1/22 19:03
 */
package com.rental.guard.ai.domain.service;

import com.rental.guard.ai.domain.dto.IntentTypeEnum;
import com.rental.guard.ai.domain.dto.Task;
import com.rental.guard.ai.domain.dto.TaskTypeEnum;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 任务分解器实现
 */
@Component
public class TaskDecomposer {

    // 任务模板库
    private final Map<IntentTypeEnum, TaskTemplate> taskTemplates;

    public TaskDecomposer() {
        this.taskTemplates = initializeTaskTemplates();
    }

    /**
     * 任务分解
     */
    public List<Task> decompose(List<Task> tasks) {
        List<Task> decomposedTasks = new ArrayList<>();

        for (Task task : tasks) {
            // 检查是否需要分解
            if (shouldDecompose(task)) {
                decomposedTasks.addAll(decomposeTask(task));
            } else {
                decomposedTasks.add(task);
            }
        }

        return decomposedTasks;
    }

    /**
     * 分解单个任务
     */
    private List<Task> decomposeTask(Task parentTask) {
        List<Task> subtasks = new ArrayList<>();

        switch (parentTask.getTaskType()) {
            case USER_INVESTIGATION:
                subtasks = decomposeUserInvestigation(parentTask);
                break;
            case BATCH_ANALYSIS:
                subtasks = decomposeBatchAnalysis(parentTask);
                break;
            case GENERATE_REPORT_CONTENT:
                subtasks = decomposeReportGeneration(parentTask);
                break;
            default:
                subtasks.add(parentTask);
        }

        // 设置父子关系
        for (Task subtask : subtasks) {
            if (subtask.getMetadata() == null) {
                subtask.setMetadata(new HashMap<>());
            }
            subtask.getMetadata().put("parentTaskId", parentTask.getTaskId());
        }

        return subtasks;
    }

    /**
     * 分解用户调查任务
     */
    private List<Task> decomposeUserInvestigation(Task parentTask) {
        List<Task> subtasks = new ArrayList<>();
        String userId = (String) parentTask.getParameters().get("user_id");

        // 1. 获取用户基本信息
        subtasks.add(Task.builder()
                .taskId(generateTaskId())
                .taskType(TaskTypeEnum.QUERY_USER_INFO)
                .name("查询用户基本信息")
                .description("获取用户注册信息、认证状态等")
                .parameters(Map.of("user_id", userId))
                .priority(10)
                .estimatedDuration(5)
                .requiredTools(Arrays.asList("UserQueryService"))
                .build());

        // 2. 查询聊天记录
        subtasks.add(Task.builder()
                .taskId(generateTaskId())
                .taskType(TaskTypeEnum.QUERY_CHAT_HISTORY)
                .name("查询聊天记录")
                .description("获取用户近期的聊天记录")
                .parameters(Map.of(
                        "user_id", userId,
                        "time_range", parentTask.getParameters().getOrDefault("time_range", "最近7天")
                ))
                .dependencies(List.of(subtasks.get(0).getTaskId())) // 依赖第一个任务
                .priority(9)
                .estimatedDuration(10)
                .requiredTools(Arrays.asList("ChatQueryService"))
                .build());

        // 3. 查询交易记录
        subtasks.add(Task.builder()
                .taskId(generateTaskId())
                .taskType(TaskTypeEnum.QUERY_TRANSACTION_HISTORY)
                .name("查询交易记录")
                .description("获取用户的支付和退款记录")
                .parameters(Map.of("user_id", userId))
                .dependencies(List.of(subtasks.get(0).getTaskId()))
                .priority(9)
                .estimatedDuration(8)
                .requiredTools(Arrays.asList("TransactionService"))
                .build());

        // 4. 风险分析
        subtasks.add(Task.builder()
                .taskId(generateTaskId())
                .taskType(TaskTypeEnum.RISK_ANALYSIS)
                .name("综合风险分析")
                .description("综合分析用户的所有行为数据")
                .parameters(Map.of("user_id", userId))
                .dependencies(List.of(
                        subtasks.get(1).getTaskId(),
                        subtasks.get(2).getTaskId()
                ))
                .priority(8)
                .estimatedDuration(15)
                .requiredTools(Arrays.asList("RiskAnalyzer", "MLModel"))
                .build());

        // 5. 生成调查报告
        subtasks.add(Task.builder()
                .taskId(generateTaskId())
                .taskType(TaskTypeEnum.GENERATE_INVESTIGATION_REPORT)
                .name("生成调查报告")
                .description("生成详细的用户调查报告")
                .parameters(Map.of("user_id", userId))
                .dependencies(List.of(subtasks.get(3).getTaskId()))
                .priority(7)
                .estimatedDuration(12)
                .requiredTools(Arrays.asList("ReportGenerator"))
                .build());

        return subtasks;
    }

    /**
     * 分解批量分析任务
     */
    private List<Task> decomposeBatchAnalysis(Task parentTask) {
        List<Task> subtasks = new ArrayList<>();
        int batchSize = (int) parentTask.getParameters().getOrDefault("batch_size", 100);
        int batchCount = (int) parentTask.getParameters().getOrDefault("batch_count", 1);

        // 1. 准备数据
        subtasks.add(Task.builder()
                .taskId(generateTaskId())
                .taskType(TaskTypeEnum.BATCH_ANALYSIS)
                .name("准备批量数据")
                .description("准备需要分析的数据批次")
                .parameters(parentTask.getParameters())
                .priority(10)
                .estimatedDuration(30)
                .requiredTools(Arrays.asList("DataPreprocessor"))
                .build());

        // 创建并行分析任务
        for (int i = 0; i < batchCount; i++) {
            subtasks.add(Task.builder()
                    .taskId(generateTaskId())
                    .taskType(TaskTypeEnum.BATCH_ANALYSIS_SUBTASK)
                    .name(String.format("批次分析任务-%d", i + 1))
                    .description(String.format("分析第%d批次数据", i + 1))
                    .parameters(Map.of(
                            "batch_index", i,
                            "batch_size", batchSize
                    ))
                    .dependencies(List.of(subtasks.get(0).getTaskId()))
                    .priority(9)
                    .estimatedDuration(20)
                    .maxConcurrent(5)  // 最多5个并行
                    .requiredTools(Arrays.asList("RiskAnalyzer"))
                    .build());
        }

        // 汇总结果
        int lastSubtaskIndex = subtasks.size() - 1;
        List<String> analysisTaskIds = new ArrayList<>();
        for (int i = 1; i <= batchCount; i++) {
            analysisTaskIds.add(subtasks.get(i).getTaskId());
        }

        subtasks.add(Task.builder()
                .taskId(generateTaskId())
                .taskType(TaskTypeEnum.AGGREGATE_RESULTS)
                .name("汇总分析结果")
                .description("汇总所有批次的分析结果")
                .parameters(parentTask.getParameters())
                .dependencies(analysisTaskIds)
                .priority(8)
                .estimatedDuration(15)
                .requiredTools(Arrays.asList("ResultAggregator"))
                .build());

        return subtasks;
    }

    /**
     * 分解报告生成任务
     */
    private List<Task> decomposeReportGeneration(Task parentTask) {
        List<Task> subtasks = new ArrayList<>();

        // 1. 收集报告数据
        subtasks.add(Task.builder()
                .taskId(generateTaskId())
                .taskType(TaskTypeEnum.COLLECT_REPORT_DATA)
                .name("收集报告数据")
                .description("收集报告所需的所有数据")
                .parameters(parentTask.getParameters())
                .priority(10)
                .estimatedDuration(20)
                .requiredTools(Arrays.asList("DataCollector"))
                .build());

        // 2. 分析数据
        subtasks.add(Task.builder()
                .taskId(generateTaskId())
                .taskType(TaskTypeEnum.ANALYZE_REPORT_DATA)
                .name("分析报告数据")
                .description("对收集的数据进行分析处理")
                .parameters(parentTask.getParameters())
                .dependencies(List.of(subtasks.get(0).getTaskId()))
                .priority(9)
                .estimatedDuration(25)
                .requiredTools(Arrays.asList("DataAnalyzer"))
                .build());

        // 3. 生成报告内容
        subtasks.add(Task.builder()
                .taskId(generateTaskId())
                .taskType(TaskTypeEnum.GENERATE_REPORT_CONTENT)
                .name("生成报告内容")
                .description("生成报告的详细内容")
                .parameters(parentTask.getParameters())
                .dependencies(List.of(subtasks.get(1).getTaskId()))
                .priority(8)
                .estimatedDuration(30)
                .requiredTools(Arrays.asList("ContentGenerator"))
                .build());

        // 4. 格式化报告
        subtasks.add(Task.builder()
                .taskId(generateTaskId())
                .taskType(TaskTypeEnum.FORMAT_REPORT)
                .name("格式化报告")
                .description("格式化报告，添加样式和排版")
                .parameters(parentTask.getParameters())
                .dependencies(List.of(subtasks.get(2).getTaskId()))
                .priority(7)
                .estimatedDuration(15)
                .requiredTools(Arrays.asList("ReportFormatter"))
                .build());

        // 5. 导出报告
        subtasks.add(Task.builder()
                .taskId(generateTaskId())
                .taskType(TaskTypeEnum.EXPORT_REPORT)
                .name("导出报告")
                .description("将报告导出为指定格式")
                .parameters(parentTask.getParameters())
                .dependencies(List.of(subtasks.get(3).getTaskId()))
                .priority(6)
                .estimatedDuration(10)
                .requiredTools(Arrays.asList("ReportExporter"))
                .build());

        return subtasks;
    }

    private String generateTaskId() {
        return "TASK_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    private boolean shouldDecompose(Task task) {
        // 根据任务类型判断是否需要分解
        Set<String> decomposableTypes = Set.of(
                "USER_INVESTIGATION",
                "BATCH_ANALYSIS",
                "REPORT_GENERATION",
                "REAL_TIME_MONITORING"
        );

        return decomposableTypes.contains(task.getTaskType())
                || (task.getEstimatedDuration() != null && task.getEstimatedDuration() > 30);
    }

    private Map<IntentTypeEnum, TaskTemplate> initializeTaskTemplates() {
        Map<IntentTypeEnum, TaskTemplate> templates = new HashMap<>();

        // 单次分析模板
        templates.put(IntentTypeEnum.SINGLE_ANALYSIS,
                TaskTemplate.builder()
                        .templateName("single_analysis")
                        .tasks(List.of(
                                TaskTemplateItem.builder()
                                        .taskType("ANALYZE_SINGLE_RECORD")
                                        .description("分析单条记录")
                                        .build()
                        ))
                        .build()
        );

        // 用户调查模板
        templates.put(IntentTypeEnum.USER_INVESTIGATION,
                TaskTemplate.builder()
                        .templateName("user_investigation")
                        .tasks(List.of(
                                TaskTemplateItem.builder()
                                        .taskType("QUERY_USER_INFO")
                                        .description("查询用户基本信息")
                                        .build(),
                                TaskTemplateItem.builder()
                                        .taskType("QUERY_CHAT_HISTORY")
                                        .description("查询聊天记录")
                                        .build(),
                                TaskTemplateItem.builder()
                                        .taskType("QUERY_TRANSACTION_HISTORY")
                                        .description("查询交易记录")
                                        .build(),
                                TaskTemplateItem.builder()
                                        .taskType("RISK_ANALYSIS")
                                        .description("风险分析")
                                        .build()
                        ))
                        .build()
        );

        return templates;
    }

    /**
     * 任务模板
     */
    @Data
    @Builder
    static class TaskTemplate {
        private String templateName;
        private List<TaskTemplateItem> tasks;
    }

    @Data
    @Builder
    static class TaskTemplateItem {
        private String taskType;
        private String description;
        private Map<String, Object> defaultParameters;
    }
}
