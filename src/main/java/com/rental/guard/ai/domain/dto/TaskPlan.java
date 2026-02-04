/**
 * @author qkcao
 * @date 2026/1/23 10:33
 */
package com.rental.guard.ai.domain.dto;

import com.rental.guard.ai.domain.service.IntentRecognitionModule;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 任务计划对象 - 表示完整的任务执行计划
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TaskPlan {

    // ==================== 基础信息 ====================
    /**
     * 计划唯一标识
     */
    @EqualsAndHashCode.Include
    @NonNull
    private String planId;

    /**
     * 计划名称
     */
    private String name;

    /**
     * 计划描述
     */
    private String description;

    /**
     * 计划分类标签
     */
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    // ==================== 意图关联 ====================
    /**
     * 原始用户意图
     */
    @NonNull
    private IntentRecognitionModule.AgentIntent originalIntent;

    /**
     * 意图置信度
     */
    private Double intentConfidence;

    // ==================== 计划状态 ====================

    /**
     * 当前计划状态
     */
    @Builder.Default
    private PlanStatusEnum status = PlanStatusEnum.DRAFT;

    /**
     * 状态历史记录
     */
    @Builder.Default
    private List<StatusTransition> statusHistory = new ArrayList<>();

    // ==================== 时间信息 ====================
    /**
     * 计划创建时间
     */
    @Builder.Default
    private LocalDateTime createTime = LocalDateTime.now();

    /**
     * 计划开始执行时间
     */
    private LocalDateTime startTime;

    /**
     * 计划完成时间
     */
    private LocalDateTime endTime;

    /**
     * 计划截止时间
     */
    private LocalDateTime deadline;

    /**
     * 预估开始时间
     */
    private LocalDateTime estimatedStartTime;

    /**
     * 预估完成时间
     */
    private LocalDateTime estimatedCompletionTime;

    /**
     * 实际耗时（毫秒）
     */
    private Long actualDuration;

    // ==================== 任务集合 ====================
    /**
     * 所有任务列表
     */
    @NonNull
    @Builder.Default
    private List<Task> tasks = new ArrayList<>();

    /**
     * 任务ID到任务的映射（缓存）
     */
    @ToString.Exclude
    private transient Map<String, Task> taskMap;

    // ==================== 执行策略 ====================

    /**
     * 执行策略
     */
    @Builder.Default
    private PlanningStrategyEnum strategy = PlanningStrategyEnum.LINEAR;

    /**
     * 策略参数
     */
    @Builder.Default
    private Map<String, Object> strategyParameters = new HashMap<>();

    // ==================== 优先级与约束 ====================
    /**
     * 计划优先级（1-10，10最高）
     */
    @Builder.Default
    private Integer priority = 5;

    /**
     * 最大并发任务数
     */
    private Integer maxConcurrentTasks;

    /**
     * 执行约束条件
     */
    @Builder.Default
    private Map<String, Object> constraints = new HashMap<>();

    // ==================== 统计信息 ====================

    /**
     * 任务统计
     */
    @Data
    @Builder
    public static class TaskStatistics {
        private int totalTasks;
        private int pendingTasks;
        private int readyTasks;
        private int runningTasks;
        private int completedTasks;
        private int failedTasks;
        private int cancelledTasks;
        private int skippedTasks;

        private int totalRetries;
        private int totalTimeouts;

        private long totalEstimatedDuration;
        private long totalActualDuration;

        private Map<TaskTypeEnum, Integer> tasksByType;
        private Map<String, Integer> tasksByStatus;

        public double getCompletionRate() {
            return totalTasks == 0 ? 0 : (double) completedTasks / totalTasks;
        }

        public double getSuccessRate() {
            int successful = completedTasks + skippedTasks;
            return totalTasks == 0 ? 0 : (double) successful / totalTasks;
        }
    }

    /**
     * 任务统计信息
     */
    private TaskStatistics statistics;

    // ==================== 检查点与恢复 ====================

    /**
     * 检查点信息
     */
    @Data
    @Builder
    public static class Checkpoint {
        private String checkpointId;
        private String name;
        private String description;
        private LocalDateTime timestamp;
        private PlanStatusEnum planStatus;
        private Map<String, Object> taskStates;  // 任务ID -> 任务状态快照
        private Map<String, Object> planState;   // 计划状态快照
        private boolean isMajor;  // 是否为主要检查点
        private String createdBy; // 创建者（系统/用户）

        public boolean isValid() {
            return taskStates != null && planState != null;
        }
    }

    /**
     * 检查点列表
     */
    @Builder.Default
    private List<Checkpoint> checkpoints = new ArrayList<>();

    /**
     * 当前活动检查点ID
     */
    private String activeCheckpointId;

    // ==================== 资源管理 ====================

    /**
     * 资源分配信息
     */
    @Data
    @Builder
    public static class ResourceAllocation {
        private Integer allocatedCpuCores;
        private Integer allocatedMemoryMB;
        private Integer allocatedDiskMB;
        private List<String> allocatedTools;
        private Map<String, Object> resourceConstraints;
        private LocalDateTime allocationTime;
        private LocalDateTime releaseTime;
    }

    /**
     * 资源分配记录
     */
    private ResourceAllocation resourceAllocation;

    // ==================== 执行历史 ====================

    /**
     * 执行历史记录
     */
    @Data
    @Builder
    public static class ExecutionRecord {
        private String recordId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private PlanStatusEnum resultStatus;
        private String executor;
        private String executionNode;
        private Map<String, Object> executionContext;
        private List<String> taskExecutionLogs;
        private Map<String, Object> performanceMetrics;
    }

    /**
     * 执行历史列表
     */
    @Builder.Default
    private List<ExecutionRecord> executionHistory = new ArrayList<>();

    // ==================== 结果与输出 ====================
    /**
     * 计划执行结果
     */
    private Object finalResult;

    /**
     * 结果数据类型
     */
    private String resultType;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 错误堆栈
     */
    private String errorStack;

    /**
     * 输出数据标识
     */
    @Builder.Default
    private List<String> outputs = new ArrayList<>();

    /**
     * 中间结果存储
     */
    @Builder.Default
    private Map<String, Object> intermediateResults = new HashMap<>();

    // ==================== 元数据 ====================
    /**
     * 自定义元数据
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 计划版本
     */
    @Builder.Default
    private String version = "1.0";

    /**
     * 创建者
     */
    private String createdBy;

    /**
     * 最后修改时间
     */
    private LocalDateTime lastModifiedTime;

    /**
     * 最后修改者
     */
    private String lastModifiedBy;

    // ==================== 业务扩展 ====================
    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 租户/组织ID
     */
    private String tenantId;

    /**
     * 项目/应用ID
     */
    private String projectId;

    /**
     * 关联的用户会话ID
     */
    private String sessionId;

    // ==================== 依赖关系 ====================
    /**
     * 依赖的其他计划ID
     */
    @Builder.Default
    private List<String> dependentPlanIds = new ArrayList<>();

    /**
     * 被哪些计划依赖
     */
    @Builder.Default
    private List<String> dependingPlanIds = new ArrayList<>();

    // ==================== 监控告警 ====================

    /**
     * 监控配置
     */
    @Data
    @Builder
    public static class MonitoringConfig {
        private boolean enableProgressMonitoring;
        private Integer progressCheckInterval;  // 秒
        private boolean enableTimeoutAlert;
        private boolean enableErrorAlert;
        private List<String> alertReceivers;
        private Map<String, Object> alertRules;
    }

    /**
     * 监控配置
     */
    private MonitoringConfig monitoringConfig;

    /**
     * 告警记录
     */
    @Builder.Default
    private List<AlertRecord> alerts = new ArrayList<>();

    // ==================== 方法 ====================

    /**
     * 获取任务映射（懒加载）
     */
    public Map<String, Task> getTaskMap() {
        if (taskMap == null) {
            synchronized (this) {
                if (taskMap == null) {
                    taskMap = new HashMap<>();
                    for (Task task : tasks) {
                        taskMap.put(task.getTaskId(), task);
                    }
                }
            }
        }
        return taskMap;
    }

    /**
     * 更新任务统计信息
     */
    public void updateStatistics() {
        TaskStatistics stats = TaskStatistics.builder()
                .totalTasks(tasks.size())
                .pendingTasks(0)
                .readyTasks(0)
                .runningTasks(0)
                .completedTasks(0)
                .failedTasks(0)
                .cancelledTasks(0)
                .skippedTasks(0)
                .totalRetries(0)
                .totalTimeouts(0)
                .totalEstimatedDuration(0)
                .totalActualDuration(0)
                .tasksByType(new HashMap<>())
                .tasksByStatus(new HashMap<>())
                .build();

        for (Task task : tasks) {
            // 统计任务状态
            switch (task.getStatus()) {
                case PENDING:
                    stats.pendingTasks++;
                    break;
                case READY:
                    stats.readyTasks++;
                    break;
                case RUNNING:
                    stats.runningTasks++;
                    break;
                case COMPLETED:
                    stats.completedTasks++;
                    break;
                case FAILED:
                    stats.failedTasks++;
                    break;
                case CANCELLED:
                    stats.cancelledTasks++;
                    break;
                case SKIPPED:
                    stats.skippedTasks++;
                    break;
                case TIMEOUT:
                    stats.totalTimeouts++;
                    break;
                case RETRYING:
                    stats.totalRetries++;
                    break;
            }

            // 统计重试次数
            if (task.getRetryCount() != null) {
                stats.totalRetries += task.getRetryCount();
            }

            // 统计预估和实际时间
            if (task.getEstimatedDuration() != null) {
                stats.totalEstimatedDuration += task.getEstimatedDuration();
            }
            if (task.getActualDuration() != null) {
                stats.totalActualDuration += task.getActualDuration();
            }

            // 按类型统计
            stats.tasksByType.merge(task.getTaskType(), 1, Integer::sum);

            // 按状态统计
            stats.tasksByStatus.merge(task.getStatus().name(), 1, Integer::sum);
        }

        this.statistics = stats;

        // 根据统计更新计划状态
        updatePlanStatusFromStatistics();
    }

    /**
     * 根据任务统计更新计划状态
     */
    private void updatePlanStatusFromStatistics() {
        if (statistics == null) return;

        if (statistics.completedTasks == statistics.totalTasks) {
            this.status = PlanStatusEnum.COMPLETED;
            if (this.endTime == null) {
                this.endTime = LocalDateTime.now();
            }
        } else if (statistics.failedTasks > 0 && statistics.runningTasks == 0) {
            this.status = PlanStatusEnum.FAILED;
        } else if (statistics.completedTasks + statistics.failedTasks + statistics.cancelledTasks
                == statistics.totalTasks) {
            this.status = PlanStatusEnum.PARTIALLY_COMPLETED;
        } else if (statistics.runningTasks > 0 && this.status != PlanStatusEnum.EXECUTING) {
            this.status = PlanStatusEnum.EXECUTING;
            if (this.startTime == null) {
                this.startTime = LocalDateTime.now();
            }
        }

        // 更新实际耗时
        if (this.startTime != null) {
            if (this.endTime != null) {
                this.actualDuration = java.time.Duration.between(startTime, endTime).toMillis();
            } else {
                this.actualDuration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            }
        }
    }

    /**
     * 获取下一个可执行的任务
     */
    public Optional<Task> getNextExecutableTask() {
        return tasks.stream()
                .filter(task -> task.getStatus() == TaskStatusEnum.READY ||
                        (task.getStatus() == TaskStatusEnum.PENDING &&
                                task.getDependencies().isEmpty()))
                .filter(task -> areDependenciesSatisfied(task))
                .min(Comparator.comparingInt(Task::getPriority).reversed()
                        .thenComparing(task -> task.getEstimatedDuration() != null ?
                                task.getEstimatedDuration() : 0));
    }

    /**
     * 检查任务的依赖是否都满足
     */
    private boolean areDependenciesSatisfied(Task task) {
        if (task.getDependencies().isEmpty()) {
            return true;
        }

        Map<String, Task> taskMap = getTaskMap();
        for (String depId : task.getDependencies()) {
            Task depTask = taskMap.get(depId);
            if (depTask == null || !depTask.isCompleted()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取关键路径上的任务
     */
    public List<Task> getCriticalPathTasks() {
        // 使用动态规划计算关键路径
        Map<String, Task> taskMap = getTaskMap();
        Map<String, Integer> earliestStart = new HashMap<>();
        Map<String, Integer> latestStart = new HashMap<>();

        // 拓扑排序
        List<String> sortedTaskIds = topologicalSort();

        // 计算最早开始时间
        for (String taskId : sortedTaskIds) {
            Task task = taskMap.get(taskId);
            int earliest = 0;
            for (String depId : task.getDependencies()) {
                Task depTask = taskMap.get(depId);
                int depEnd = earliestStart.get(depId) +
                        (depTask.getEstimatedDuration() != null ?
                                depTask.getEstimatedDuration() : 0);
                earliest = Math.max(earliest, depEnd);
            }
            earliestStart.put(taskId, earliest);
        }

        // 计算最晚开始时间
        int totalDuration = earliestStart.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        Collections.reverse(sortedTaskIds);
        for (String taskId : sortedTaskIds) {
            Task task = taskMap.get(taskId);
            int latest = totalDuration -
                    (task.getEstimatedDuration() != null ?
                            task.getEstimatedDuration() : 0);

            for (String depId : task.getDependents()) {
                Task depTask = taskMap.get(depId);
                if (latestStart.containsKey(depId)) {
                    latest = Math.min(latest, latestStart.get(depId) -
                            (task.getEstimatedDuration() != null ?
                                    task.getEstimatedDuration() : 0));
                }
            }
            latestStart.put(taskId, latest);
        }

        // 找出关键路径（浮动时间为0的任务）
        List<Task> criticalPath = new ArrayList<>();
        for (String taskId : sortedTaskIds) {
            int slack = latestStart.get(taskId) - earliestStart.get(taskId);
            if (slack == 0) {
                criticalPath.add(taskMap.get(taskId));
            }
        }

        return criticalPath;
    }

    /**
     * 拓扑排序
     */
    private List<String> topologicalSort() {
        Map<String, Task> taskMap = getTaskMap();
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        // 初始化
        for (Task task : tasks) {
            String taskId = task.getTaskId();
            adjacency.put(taskId, new ArrayList<>());
            inDegree.put(taskId, 0);
        }

        // 构建图
        for (Task task : tasks) {
            for (String depId : task.getDependencies()) {
                adjacency.get(depId).add(task.getTaskId());
                inDegree.put(task.getTaskId(),
                        inDegree.get(task.getTaskId()) + 1);
            }
        }

        // 拓扑排序
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String taskId = queue.poll();
            result.add(taskId);

            for (String neighbor : adjacency.get(taskId)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        if (result.size() != tasks.size()) {
            throw new IllegalStateException("存在循环依赖，无法进行拓扑排序");
        }

        return result;
    }

    /**
     * 创建检查点
     */
    public Checkpoint createCheckpoint(String name, String description, boolean isMajor) {
        Map<String, Task> taskMap = getTaskMap();
        Map<String, Object> taskStates = new HashMap<>();

        // 保存所有任务的状态
        for (Task task : tasks) {
            Map<String, Object> taskState = new HashMap<>();
            taskState.put("status", task.getStatus().name());
            taskState.put("progress", task.getProgress());
            taskState.put("result", task.getResult());
            taskState.put("errorMessage", task.getErrorMessage());
            taskState.put("startTime", task.getStartTime());
            taskState.put("endTime", task.getEndTime());

            taskStates.put(task.getTaskId(), taskState);
        }

        // 保存计划状态
        Map<String, Object> planState = new HashMap<>();
        planState.put("status", this.status.name());
        planState.put("statistics", this.statistics);
        planState.put("intermediateResults", new HashMap<>(this.intermediateResults));

        Checkpoint checkpoint = Checkpoint.builder()
                .checkpointId("CP_" + System.currentTimeMillis() + "_" +
                        UUID.randomUUID().toString().substring(0, 8))
                .name(name)
                .description(description)
                .timestamp(LocalDateTime.now())
                .planStatus(this.status)
                .taskStates(taskStates)
                .planState(planState)
                .isMajor(isMajor)
                .createdBy("system")
                .build();

        this.checkpoints.add(checkpoint);
        this.activeCheckpointId = checkpoint.getCheckpointId();

        return checkpoint;
    }

    /**
     * 从检查点恢复
     */
    public boolean restoreFromCheckpoint(String checkpointId) {
        Checkpoint checkpoint = checkpoints.stream()
                .filter(cp -> cp.getCheckpointId().equals(checkpointId))
                .findFirst()
                .orElse(null);

        if (checkpoint == null || !checkpoint.isValid()) {
            return false;
        }

        // 恢复任务状态
        Map<String, Task> taskMap = getTaskMap();
        for (Map.Entry<String, Object> entry : checkpoint.getTaskStates().entrySet()) {
            Task task = taskMap.get(entry.getKey());
            if (task != null) {
                Map<String, Object> taskState = (Map<String, Object>) entry.getValue();

                // 恢复任务状态
                task.setStatus(TaskStatusEnum.valueOf((String) taskState.get("status")));
                task.setProgress((Integer) taskState.get("progress"));
                task.setResult(taskState.get("result"));
                task.setErrorMessage((String) taskState.get("errorMessage"));
                task.setStartTime((LocalDateTime) taskState.get("startTime"));
                task.setEndTime((LocalDateTime) taskState.get("endTime"));
            }
        }

        // 恢复计划状态
        Map<String, Object> planState = checkpoint.getPlanState();
        this.status = PlanStatusEnum.valueOf((String) planState.get("status"));
        this.statistics = (TaskStatistics) planState.get("statistics");
        this.intermediateResults = (Map<String, Object>) planState.get("intermediateResults");

        this.activeCheckpointId = checkpointId;

        return true;
    }

    /**
     * 验证计划完整性
     */
    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 检查任务ID唯一性
        Set<String> taskIds = new HashSet<>();
        for (Task task : tasks) {
            if (taskIds.contains(task.getTaskId())) {
                errors.add("发现重复的任务ID: " + task.getTaskId());
            }
            taskIds.add(task.getTaskId());
        }

        // 检查依赖关系
        Map<String, Task> taskMap = getTaskMap();
        for (Task task : tasks) {
            for (String depId : task.getDependencies()) {
                if (!taskMap.containsKey(depId)) {
                    errors.add(String.format("任务%s依赖的任务%s不存在",
                            task.getTaskId(), depId));
                }
            }
        }

        // 检查循环依赖
        try {
            topologicalSort();
        } catch (IllegalStateException e) {
            errors.add("存在循环依赖: " + e.getMessage());
        }

        // 检查资源分配
        if (maxConcurrentTasks != null && maxConcurrentTasks <= 0) {
            warnings.add("最大并发任务数设置不合理: " + maxConcurrentTasks);
        }

        // 检查时间约束
        if (deadline != null && deadline.isBefore(LocalDateTime.now())) {
            warnings.add("计划截止时间已过");
        }

        boolean isValid = errors.isEmpty();
        return ValidationResult.builder()
                .valid(isValid)
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    /**
     * 获取计划进度（0-100）
     */
    public int getProgress() {
        if (statistics == null) {
            updateStatistics();
        }

        if (statistics.totalTasks == 0) {
            return 0;
        }

        // 加权计算进度（考虑任务预估时间）
        long totalWeight = 0;
        long completedWeight = 0;

        for (Task task : tasks) {
            int weight = task.getEstimatedDuration() != null ?
                    task.getEstimatedDuration() : 10;

            totalWeight += weight;

            if (task.isCompleted()) {
                completedWeight += weight;
            } else if (task.getProgress() != null) {
                completedWeight += weight * task.getProgress() / 100;
            }
        }

        return totalWeight == 0 ? 0 : (int) (completedWeight * 100 / totalWeight);
    }

    /**
     * 添加状态变更记录
     */
    public void addStatusTransition(PlanStatusEnum from, PlanStatusEnum to, String reason) {
        StatusTransition transition = StatusTransition.builder()
                .timestamp(LocalDateTime.now())
                .fromStatus(from)
                .toStatus(to)
                .reason(reason)
                .build();

        this.statusHistory.add(transition);
        this.status = to;

        // 记录状态变更到元数据
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put("last_status_change", transition);
    }

    // ==================== 内部类 ====================

    /**
     * 状态转移记录
     */
    @Data
    @Builder
    public static class StatusTransition {
        private LocalDateTime timestamp;
        private PlanStatusEnum fromStatus;
        private PlanStatusEnum toStatus;
        private String reason;
        private String changedBy;  // 变更者
    }

    /**
     * 告警记录
     */
    @Data
    @Builder
    public static class AlertRecord {
        private String alertId;
        private LocalDateTime timestamp;
        private String alertType;  // TIMEOUT, ERROR, RESOURCE, etc.
        private String severity;   // INFO, WARN, ERROR, CRITICAL
        private String message;
        private Map<String, Object> context;
        private boolean acknowledged;
        private LocalDateTime acknowledgedTime;
        private String acknowledgedBy;
    }

    /**
     * 验证结果
     */
    @Data
    @Builder
    public static class ValidationResult {
        private boolean valid;
        private List<String> errors;
        private List<String> warnings;
        private Map<String, Object> details;
    }
}
