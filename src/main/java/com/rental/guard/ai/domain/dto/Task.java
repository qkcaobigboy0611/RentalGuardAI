/**
 * @author qkcao
 * @date 2026/1/23 10:32
 */
package com.rental.guard.ai.domain.dto;


import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 任务对象 - 表示可执行的最小工作单元
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Task {

    // ==================== 基础信息 ====================
    /**
     * 任务唯一标识
     */
    private String taskId;

    /**
     * 任务类型
     */
    private TaskTypeEnum taskType;

    /**
     * 任务名称（可读）
     */
    private String name;

    /**
     * 任务描述
     */
    private String description;

    /**
     * 任务分类标签（便于分组和过滤）
     */
    private List<String> tags;

    // ==================== 状态信息 ====================

    /**
     * 当前任务状态
     */
    private TaskStatusEnum status = TaskStatusEnum.CREATED;

    /**
     * 任务创建时间
     */
    @Builder.Default
    private LocalDateTime createTime = LocalDateTime.now();

    /**
     * 任务开始执行时间
     */
    private LocalDateTime startTime;

    /**
     * 任务结束时间
     */
    private LocalDateTime endTime;

    /**
     * 任务实际耗时（毫秒）
     */
    private Long actualDuration;

    /**
     * 任务进度（0-100）
     */
    @Builder.Default
    private Integer progress = 0;

    /**
     * 进度描述
     */
    private String progressMessage;

    // ==================== 执行信息 ====================
    /**
     * 任务参数（键值对）
     */
    private Map<String, Object> parameters = new HashMap<>();

    /**
     * 任务需要的工具/服务
     */
    @Builder.Default
    private List<String> requiredTools = new ArrayList<>();

    /**
     * 任务需要的资源
     */
    @Builder.Default
    private List<String> requiredResources = new ArrayList<>();

    /**
     * 任务输出标识（用于其他任务引用）
     */
    @Builder.Default
    private List<String> outputs = new ArrayList<>();

    /**
     * 任务输入标识（引用其他任务的输出）
     */
    @Builder.Default
    private List<String> inputs = new ArrayList<>();

    /**
     * 任务执行器（负责执行该任务的服务）
     */
    private String executor;

    /**
     * 执行上下文（传递给执行器的额外信息）
     */
    @Builder.Default
    private Map<String, Object> executionContext = new HashMap<>();

    // ==================== 依赖关系 ====================
    /**
     * 依赖的任务ID列表（这些任务完成后才能执行本任务）
     */
    @Builder.Default
    private List<String> dependencies = new ArrayList<>();

    /**
     * 后续任务ID列表（依赖本任务的任务）
     */
    @Builder.Default
    private List<String> dependents = new ArrayList<>();

    // ==================== 约束条件 ====================
    /**
     * 预估执行时间（秒）
     */
    private Integer estimatedDuration;

    /**
     * 超时时间（秒）
     */
    @Builder.Default
    private Integer timeout = 300;

    /**
     * 重试次数
     */
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 最大重试次数
     */
    @Builder.Default
    private Integer maxRetries = 3;

    /**
     * 重试间隔（秒）
     */
    @Builder.Default
    private Integer retryInterval = 10;

    /**
     * 任务优先级（1-10，10最高）
     */
    @Builder.Default
    private Integer priority = 5;

    /**
     * 最大并发数（同一类型任务同时执行的最大数量）
     */
    private Integer maxConcurrent;

    /**
     * 任务执行约束（时间窗口、资源限制等）
     */
    @Builder.Default
    private Map<String, Object> constraints = new HashMap<>();

    /**
     * 任务执行环境要求
     */
    private ExecutionEnvironment environment;

    // ==================== 结果信息 ====================
    /**
     * 任务执行结果
     */
    private Object result;

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
     * 错误码
     */
    private String errorCode;

    /**
     * 执行日志
     */
    @Builder.Default
    private List<TaskLog> logs = new ArrayList<>();

    /**
     * 执行统计信息
     */
    private ExecutionStats executionStats;

    // ==================== 元数据 ====================
    /**
     * 自定义元数据
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 任务版本
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

    // ==================== 父子关系 ====================
    /**
     * 父任务ID（如果是子任务）
     */
    private String parentTaskId;

    /**
     * 子任务ID列表
     */
    @Builder.Default
    private List<String> childTaskIds = new ArrayList<>();

    /**
     * 是否是原子任务（不可再分解）
     */
    @Builder.Default
    private boolean atomic = false;

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

    // ==================== 方法 ====================

    /**
     * 检查任务是否就绪（依赖已满足）
     */
    public boolean isReady() {
        return status == TaskStatusEnum.READY ||
                (status == TaskStatusEnum.PENDING && dependencies.isEmpty());
    }

    /**
     * 检查任务是否完成
     */
    public boolean isCompleted() {
        return status == TaskStatusEnum.COMPLETED;
    }

    /**
     * 检查任务是否失败
     */
    public boolean isFailed() {
        return status == TaskStatusEnum.FAILED ||
                status == TaskStatusEnum.TIMEOUT;
    }

    /**
     * 检查任务是否可以重试
     */
    public boolean canRetry() {
        return status == TaskStatusEnum.FAILED &&
                retryCount < maxRetries;
    }

    /**
     * 开始执行任务
     */
    public void start() {
        if (!status.canTransitionTo(TaskStatusEnum.RUNNING)) {
            throw new IllegalStateException(
                    String.format("任务%s无法从状态%s转换到RUNNING", taskId, status));
        }
        this.status = TaskStatusEnum.RUNNING;
        this.startTime = LocalDateTime.now();
        this.progress = 0;
    }

    /**
     * 完成任务
     */
    public void complete(Object result, String resultType) {
        if (!status.canTransitionTo(TaskStatusEnum.COMPLETED)) {
            throw new IllegalStateException(
                    String.format("任务%s无法从状态%s转换到COMPLETED", taskId, status));
        }
        this.status = TaskStatusEnum.COMPLETED;
        this.endTime = LocalDateTime.now();
        this.result = result;
        this.resultType = resultType;
        this.progress = 100;

        if (startTime != null && endTime != null) {
            this.actualDuration = java.time.Duration.between(startTime, endTime).toMillis();
        }
    }

    /**
     * 任务失败
     */
    public void fail(String errorMessage, String errorCode) {
        if (!status.canTransitionTo(TaskStatusEnum.FAILED)) {
            throw new IllegalStateException(
                    String.format("任务%s无法从状态%s转换到FAILED", taskId, status));
        }
        this.status = TaskStatusEnum.FAILED;
        this.endTime = LocalDateTime.now();
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;

        if (startTime != null && endTime != null) {
            this.actualDuration = java.time.Duration.between(startTime, endTime).toMillis();
        }
    }

    /**
     * 添加执行日志
     */
    public void addLog(TaskLog.Level level, String message) {
        this.logs.add(TaskLog.builder()
                .timestamp(LocalDateTime.now())
                .level(level)
                .message(message)
                .taskId(taskId)
                .build());
    }

    /**
     * 更新进度
     */
    public void updateProgress(int progress, String message) {
        this.progress = Math.max(0, Math.min(100, progress));
        this.progressMessage = message;
        addLog(TaskLog.Level.INFO,
                String.format("进度更新: %d%%, %s", progress, message));
    }

    /**
     * 获取任务权重（用于调度）
     */
    public double getWeight() {
        double weight = priority / 10.0;

        // 预估时间越长，权重越低（避免大任务阻塞）
        if (estimatedDuration != null && estimatedDuration > 60) {
            weight *= 0.8;
        }

        // 重试次数越多，权重越低
        if (retryCount > 0) {
            weight *= Math.pow(0.9, retryCount);
        }

        return weight;
    }

    /**
     * 验证任务参数是否有效
     */
    public boolean validateParameters() {
        // 检查必填参数
        Set<String> requiredParams = getRequiredParameters();
        if (!parameters.keySet().containsAll(requiredParams)) {
            return false;
        }

        // 参数类型检查
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (!isParameterValid(entry.getKey(), entry.getValue())) {
                return false;
            }
        }

        return true;
    }

    private Set<String> getRequiredParameters() {
        // 根据任务类型返回必填参数
        switch (taskType) {
            case QUERY_USER_INFO:
                return Set.of("user_id");
            case QUERY_CHAT_HISTORY:
                return Set.of("user_id", "time_range");
            case RISK_ANALYSIS:
                return Set.of("user_id");
            default:
                return Collections.emptySet();
        }
    }

    private boolean isParameterValid(String key, Object value) {
        // 参数验证逻辑
        if (value == null) return false;

        switch (key) {
            case "user_id":
                return value instanceof String && ((String) value).length() >= 6;
            case "time_range":
                return value instanceof String && !((String) value).isEmpty();
            default:
                return true;
        }
    }

    // ==================== 内部类 ====================

    /**
     * 任务日志
     */
    @Data
    @Builder
    public static class TaskLog {
        public enum Level {
            DEBUG, INFO, WARN, ERROR
        }

        private LocalDateTime timestamp;
        private Level level;
        private String message;
        private String taskId;
        private Map<String, Object> context;
    }

    /**
     * 执行环境要求
     */
    @Data
    @Builder
    public static class ExecutionEnvironment {
        private String runtime;  // 如：JAVA_11, PYTHON_3_8
        private List<String> dependencies;
        private Map<String, String> environmentVariables;
        private ResourceRequirements resources;
    }

    /**
     * 资源要求
     */
    @Data
    @Builder
    public static class ResourceRequirements {
        private Integer cpuCores;
        private Integer memoryMB;
        private Integer diskMB;
        private Boolean gpuRequired;
        private Integer gpuMemoryMB;
    }

    /**
     * 执行统计
     */
    @Data
    @Builder
    public static class ExecutionStats {
        private Long cpuTimeMillis;
        private Long memoryPeakMB;
        private Integer ioOperations;
        private Integer networkRequests;
        private Map<String, Long> customMetrics;
    }
}
