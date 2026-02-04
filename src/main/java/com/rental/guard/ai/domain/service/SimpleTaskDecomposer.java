/**
 * @author qkcao
 * @date 2026/1/26 18:17
 */
package com.rental.guard.ai.domain.service;

// SimpleTaskDecomposer.java
import com.rental.guard.ai.domain.dto.Task;
import com.rental.guard.ai.domain.dto.TaskStatusEnum;
import com.rental.guard.ai.domain.dto.TaskTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class SimpleTaskDecomposer {

    /**
     * 分解复杂任务为简单任务
     */
    public List<Task> decompose(List<Task> tasks) {
        List<Task> decomposedTasks = new ArrayList<>();

        for (Task task : tasks) {
            if (needDecomposition(task)) {
                decomposedTasks.addAll(decomposeTask(task));
            } else {
                decomposedTasks.add(task);
            }
        }

        return decomposedTasks;
    }

    /**
     * 判断是否需要分解
     */
    private boolean needDecomposition(Task task) {
        // 复合任务类型需要分解
        return task.getTaskType().isCompositeType() ||
                // 预估时间超过2分钟的任务
                (task.getEstimatedDuration() != null && task.getEstimatedDuration() > 120) ||
                // 需要多个工具的任务
                (task.getRequiredTools() != null && task.getRequiredTools().size() > 3);
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
            case REAL_TIME_MONITORING:
                subtasks = decomposeRealTimeMonitoring(parentTask);
                break;
            case COMPREHENSIVE_INVESTIGATION:
                subtasks = decomposeComprehensiveInvestigation(parentTask);
                break;
            default:
                // 通用分解逻辑
                subtasks = decomposeGenericTask(parentTask);
        }

        // 设置父子关系
        for (Task subtask : subtasks) {
            if (subtask.getMetadata() == null) {
                subtask.setMetadata(new HashMap<>());
            }
            subtask.getMetadata().put("parentTaskId", parentTask.getTaskId());
            subtask.getMetadata().put("originalTaskType", parentTask.getTaskType().name());
        }

        return subtasks;
    }

    /**
     * 分解用户调查任务
     */
    private List<Task> decomposeUserInvestigation(Task parentTask) {
        List<Task> subtasks = new ArrayList<>();
        String userId = extractUserId(parentTask);

        // 1. 基本信息查询
        subtasks.add(createSubtask(
                TaskTypeEnum.QUERY_USER_INFO,
                "查询用户基本信息",
                "获取用户注册信息和资料",
                Map.of("user_id", userId),
                parentTask
        ));

        // 2. 聊天记录查询
        subtasks.add(createSubtask(
                TaskTypeEnum.QUERY_CHAT_HISTORY,
                "查询用户聊天记录",
                "获取用户近期的聊天记录",
                Map.of(
                        "user_id", userId,
                        "time_range", parentTask.getParameters().getOrDefault("time_range", "最近30天")
                ),
                parentTask
        ));

        // 3. 交易记录查询
        subtasks.add(createSubtask(
                TaskTypeEnum.QUERY_TRANSACTION_HISTORY,
                "查询交易记录",
                "获取用户的支付和退款记录",
                Map.of("user_id", userId),
                parentTask
        ));

        // 4. 风险分析
        subtasks.add(createSubtask(
                TaskTypeEnum.RISK_ANALYSIS,
                "风险分析",
                "综合分析用户的风险等级",
                Map.of("user_id", userId),
                parentTask,
                List.of(
                        subtasks.get(0).getTaskId(),
                        subtasks.get(1).getTaskId(),
                        subtasks.get(2).getTaskId()
                )
        ));

        // 5. 生成报告
        subtasks.add(createSubtask(
                TaskTypeEnum.GENERATE_INVESTIGATION_REPORT,
                "生成调查报告",
                "生成详细的用户调查报告",
                Map.of("user_id", userId),
                parentTask,
                List.of(subtasks.get(3).getTaskId())
        ));

        return subtasks;
    }

    /**
     * 分解批量分析任务
     */
    private List<Task> decomposeBatchAnalysis(Task parentTask) {
        List<Task> subtasks = new ArrayList<>();

        // 1. 数据准备
        subtasks.add(createSubtask(
                TaskTypeEnum.DATA_PREPROCESSING,
                "准备批量数据",
                "准备需要分析的数据批次",
                parentTask.getParameters(),
                parentTask
        ));

        // 2. 创建子分析任务（示例：3个子任务）
        int batchCount = 3;
        List<String> batchTaskIds = new ArrayList<>();

        for (int i = 0; i < batchCount; i++) {
            Task batchTask = createSubtask(
                    TaskTypeEnum.BATCH_ANALYSIS_SUBTASK,
                    "批量分析子任务-" + (i + 1),
                    "分析第" + (i + 1) + "批数据",
                    Map.of(
                            "batch_index", i,
                            "batch_count", batchCount,
                            "batch_size", parentTask.getParameters().getOrDefault("batch_size", 100)
                    ),
                    parentTask,
                    List.of(subtasks.get(0).getTaskId())
            );

            batchTask.setMaxConcurrent(2); // 允许并行
            subtasks.add(batchTask);
            batchTaskIds.add(batchTask.getTaskId());
        }

        // 3. 结果汇总
        subtasks.add(createSubtask(
                TaskTypeEnum.DATA_VALIDATION,
                "汇总分析结果",
                "汇总所有批次的分析结果",
                parentTask.getParameters(),
                parentTask,
                batchTaskIds
        ));

        return subtasks;
    }

    /**
     * 分解实时监控任务
     */
    private List<Task> decomposeRealTimeMonitoring(Task parentTask) {
        List<Task> subtasks = new ArrayList<>();

        // 1. 监控启动
        subtasks.add(createSubtask(
                TaskTypeEnum.REAL_TIME_MONITORING,
                "启动实时监控",
                "启动实时监控组件",
                parentTask.getParameters(),
                parentTask
        ));

        // 2. 事件处理器
        subtasks.add(createSubtask(
                TaskTypeEnum.REAL_TIME_EVENT_PROCESSING,
                "实时事件处理",
                "处理监控到的事件",
                parentTask.getParameters(),
                parentTask,
                List.of(subtasks.get(0).getTaskId())
        ));

        // 3. 告警处理器
        subtasks.add(createSubtask(
                TaskTypeEnum.REAL_TIME_ALERT_PROCESSING,
                "实时告警处理",
                "处理实时告警通知",
                parentTask.getParameters(),
                parentTask,
                List.of(subtasks.get(1).getTaskId())
        ));

        return subtasks;
    }

    /**
     * 分解全面调查任务
     */
    private List<Task> decomposeComprehensiveInvestigation(Task parentTask) {
        List<Task> subtasks = new ArrayList<>();
        String userId = extractUserId(parentTask);

        // 信息收集阶段
        subtasks.add(createSubtask(TaskTypeEnum.QUERY_USER_INFO, "基本信息查询", "",
                Map.of("user_id", userId), parentTask));
        subtasks.add(createSubtask(TaskTypeEnum.QUERY_USER_BEHAVIOR, "行为分析", "",
                Map.of("user_id", userId), parentTask));
        subtasks.add(createSubtask(TaskTypeEnum.QUERY_USER_DEVICES, "设备查询", "",
                Map.of("user_id", userId), parentTask));
        subtasks.add(createSubtask(TaskTypeEnum.QUERY_USER_IP_HISTORY, "IP历史查询", "",
                Map.of("user_id", userId), parentTask));

        // 分析阶段（依赖信息收集）
        Task analysisTask = createSubtask(TaskTypeEnum.RISK_ANALYSIS, "综合风险分析", "",
                Map.of("user_id", userId), parentTask);

        List<String> analysisDeps = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            analysisDeps.add(subtasks.get(i).getTaskId());
        }
        analysisTask.setDependencies(analysisDeps);
        subtasks.add(analysisTask);

        // 报告阶段
        subtasks.add(createSubtask(TaskTypeEnum.GENERATE_INVESTIGATION_REPORT,
                "生成调查报告", "", Map.of("user_id", userId), parentTask,
                List.of(analysisTask.getTaskId())));

        return subtasks;
    }

    /**
     * 通用任务分解
     */
    private List<Task> decomposeGenericTask(Task parentTask) {
        List<Task> subtasks = new ArrayList<>();

        // 分解为：准备 -> 执行 -> 验证
        subtasks.add(createSubtask(
                TaskTypeEnum.DATA_PREPROCESSING,
                "任务准备",
                "准备任务所需的数据和资源",
                parentTask.getParameters(),
                parentTask
        ));

        subtasks.add(createSubtask(
                parentTask.getTaskType(),
                "任务执行",
                "执行主要任务逻辑",
                parentTask.getParameters(),
                parentTask,
                List.of(subtasks.get(0).getTaskId())
        ));

        subtasks.add(createSubtask(
                TaskTypeEnum.DATA_VALIDATION,
                "结果验证",
                "验证任务执行结果",
                parentTask.getParameters(),
                parentTask,
                List.of(subtasks.get(1).getTaskId())
        ));

        return subtasks;
    }

    /**
     * 创建子任务
     */
    private Task createSubtask(TaskTypeEnum taskType, String name, String description,
                               Map<String, Object> parameters, Task parentTask) {
        return createSubtask(taskType, name, description, parameters, parentTask, null);
    }

    private Task createSubtask(TaskTypeEnum taskType, String name, String description,
                               Map<String, Object> parameters, Task parentTask,
                               List<String> dependencies) {

        Map<String, Object> combinedParams = new HashMap<>(parentTask.getParameters());
        if (parameters != null) {
            combinedParams.putAll(parameters);
        }

        Task subtask = Task.builder()
                .taskId(generateSubtaskId(parentTask.getTaskId(), taskType))
                .taskType(taskType)
                .name(name)
                .description(description.isEmpty() ? taskType.getDescription() : description)
                .parameters(combinedParams)
                .priority(parentTask.getPriority())
                .estimatedDuration(taskType.getEstimatedDuration())
                .timeout(taskType.getDefaultTimeout())
                .maxRetries(parentTask.getMaxRetries())
                .requiredTools(new ArrayList<>(taskType.getRequiredTools()))
                .status(TaskStatusEnum.PENDING)
                .dependencies(dependencies != null ? new ArrayList<>(dependencies) : new ArrayList<>())
                .build();

        return subtask;
    }

    /**
     * 生成子任务ID
     */
    private String generateSubtaskId(String parentTaskId, TaskTypeEnum taskType) {
        return parentTaskId + "_" + taskType.name() + "_" +
                UUID.randomUUID().toString().substring(0, 6);
    }

    /**
     * 从任务参数中提取用户ID
     */
    private String extractUserId(Task task) {
        Map<String, Object> params = task.getParameters();
        if (params == null) return "unknown_user";

        if (params.containsKey("user_id")) {
            return params.get("user_id").toString();
        }
        if (params.containsKey("target")) {
            return params.get("target").toString();
        }

        return "unknown_user";
    }
}
