/**
 * @author qkcao
 * @date 2026/1/22 18:59
 */
package com.rental.guard.ai.domain.service;

import com.rental.guard.ai.domain.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 任务规划模块
 */
@Slf4j
@Component
public class TaskPlanner {

    // ... [前面定义的数据结构部分]

    private final PlanningStrategySelector strategySelector;
    private final TaskFactory taskFactory;
    private final TaskDecomposer taskDecomposer;
    private final DependencyResolver dependencyResolver;
    private final ResourceAllocator resourceAllocator;
    private final Optimizer optimizer;

    public TaskPlanner(PlanningStrategySelector strategySelector,
                       TaskFactory taskFactory,
                       TaskDecomposer taskDecomposer,
                       DependencyResolver dependencyResolver,
                       ResourceAllocator resourceAllocator,
                       Optimizer optimizer) {
        this.strategySelector = strategySelector;
        this.taskFactory = taskFactory;
        this.taskDecomposer = taskDecomposer;
        this.dependencyResolver = dependencyResolver;
        this.resourceAllocator = resourceAllocator;
        this.optimizer = optimizer;
    }

    /**
     * 任务规划主入口（完整实现）
     */
    public TaskPlan plan(IntentRecognitionModule.AgentIntent intent) {
        log.info("开始任务规划，意图: {}", intent.getIntentType());

        long startTime = System.currentTimeMillis();

        try {
            // 1. 验证意图
            validateIntent(intent);

            // 2. 选择规划策略
            PlanningConstraints constraints =
                    extractPlanningConstraints(intent);
            PlanningStrategyEnum strategy = strategySelector.selectStrategyWithConstraints(intent, constraints);

            // 3. 生成初始任务序列
            List<Task> initialTasks = taskFactory.createTasks(intent, strategy);
            log.debug("生成初始任务: {}个", initialTasks.size());

            // 4. 任务分解
            List<Task> decomposedTasks = taskDecomposer.decompose(initialTasks);
            log.debug("分解后任务: {}个", decomposedTasks.size());

            // 5. 解析任务依赖
            List<Task> tasksWithDependencies = dependencyResolver.resolve(decomposedTasks);

            // 6. 优化任务顺序
            List<Task> optimizedTasks = optimizer.optimize(tasksWithDependencies);

            // 7. 分配资源
            List<Task> tasksWithResources = resourceAllocator.allocate(optimizedTasks);

            // 8. 创建任务计划
            TaskPlan plan = buildTaskPlan(intent, tasksWithResources, strategy);

            // 9. 计算预估完成时间
            estimateCompletionTime(plan);

            // 10. 验证计划可行性
            validatePlan(plan);

            long endTime = System.currentTimeMillis();
            log.info("任务规划完成，计划ID: {}, 任务数: {}, 耗时: {}ms",
                    plan.getPlanId(), plan.getTasks().size(), endTime - startTime);

            return plan;

        } catch (PlanningException e) {
            log.error("任务规划失败", e);
            throw new PlanningException("任务规划失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证意图
     */
    private void validateIntent(IntentRecognitionModule.AgentIntent intent) {
        if (intent == null) {
            throw new PlanningException("意图不能为空");
        }

        if (intent.getIntentType() == null) {
            throw new PlanningException("意图类型不能为空");
        }

        if (intent.getConfidence() < 0.3) {
            log.warn("意图置信度过低: {}", intent.getConfidence());
        }
    }

    /**
     * 提取规划约束
     */
    private PlanningConstraints extractPlanningConstraints(
            IntentRecognitionModule.AgentIntent intent) {

        PlanningConstraints constraints = new PlanningConstraints();

        // 基于意图优先级设置约束
        switch (intent.getPriority()) {
            case HIGH:
                constraints.setTimeCritical(true);
                constraints.setRequiresHighAvailability(true);
                break;
            case MEDIUM:
                constraints.setTimeCritical(false);
                constraints.setRequiresHighAvailability(true);
                break;
            case LOW:
                constraints.setResourceConstrained(true);
                break;
        }

        // 从参数中提取约束
        if (intent.getParameters() != null) {
            if (intent.getParameters().containsKey("timeout_minutes")) {
                constraints.setTimeoutMinutes(
                        Integer.parseInt(intent.getParameters().get("timeout_minutes").toString()));
            }

            if (intent.getParameters().containsKey("max_concurrent")) {
                constraints.setMaxConcurrentTasks(
                        Integer.parseInt(intent.getParameters().get("max_concurrent").toString()));
            }
        }

        return constraints;
    }

    /**
     * 构建任务计划
     */
    private TaskPlan buildTaskPlan(IntentRecognitionModule.AgentIntent intent,
                                   List<Task> tasks,
                                   PlanningStrategyEnum strategy) {

        String planId = "PLAN_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8);

        // 初始化所有任务状态
        for (Task task : tasks) {
            task.setStatus(TaskStatusEnum.PENDING);
            task.setCreateTime(LocalDateTime.now());
        }

        TaskPlan plan = TaskPlan.builder()
                .planId(planId)
                .originalIntent(intent)
                .tasks(tasks)
                .strategy(strategy)
                .status(PlanStatusEnum.READY)
                .createTime(LocalDateTime.now())
                .priority(intent.getPriority().ordinal())
                .metadata(buildPlanMetadata(intent, tasks))
                .checkpoints(new ArrayList<>())
                .build();

        plan.updateStatistics();

        // 添加初始检查点
        plan.getCheckpoints().add(TaskPlan.Checkpoint.builder()
                .checkpointId("INITIAL")
                .timestamp(LocalDateTime.now())
                .isMajor(true)
                .planState(Map.of(
                        "plan_status", plan.getStatus().name(),
                        "task_count", plan.getTasks().size()
                ))
                .build());

        return plan;
    }

    /**
     * 构建计划元数据
     */
    private Map<String, Object> buildPlanMetadata(IntentRecognitionModule.AgentIntent intent,
                                                  List<Task> tasks) {
        Map<String, Object> metadata = new HashMap<>();

        metadata.put("intent_confidence", intent.getConfidence());
        metadata.put("planning_timestamp", System.currentTimeMillis());
        metadata.put("estimated_total_duration",
                tasks.stream().mapToInt(Task::getEstimatedDuration).sum());

        // 统计任务类型分布
        Map<String, Integer> taskTypeDistribution = new HashMap<>();
        for (Task task : tasks) {
            taskTypeDistribution.merge(task.getTaskType().getDisplayName(), 1, Integer::sum);
        }
        metadata.put("task_type_distribution", taskTypeDistribution);

        // 依赖关系深度
        int maxDependencyDepth = calculateMaxDependencyDepth(tasks);
        metadata.put("max_dependency_depth", maxDependencyDepth);

        return metadata;
    }

    /**
     * 计算最大依赖深度
     */
    private int calculateMaxDependencyDepth(List<Task> tasks) {
        Map<String, Task> taskMap = new HashMap<>();
        Map<String, List<String>> dependencies = new HashMap<>();

        for (Task task : tasks) {
            taskMap.put(task.getTaskId(), task);
            dependencies.put(task.getTaskId(),
                    task.getDependencies() != null ? task.getDependencies() : new ArrayList<>());
        }

        int maxDepth = 0;
        for (Task task : tasks) {
            int depth = calculateTaskDepth(task.getTaskId(), dependencies, new HashMap<>());
            if (depth > maxDepth) {
                maxDepth = depth;
            }
        }

        return maxDepth;
    }

    private int calculateTaskDepth(String taskId,
                                   Map<String, List<String>> dependencies,
                                   Map<String, Integer> depthCache) {
        if (depthCache.containsKey(taskId)) {
            return depthCache.get(taskId);
        }

        List<String> deps = dependencies.get(taskId);
        if (deps == null || deps.isEmpty()) {
            depthCache.put(taskId, 0);
            return 0;
        }

        int maxDepth = 0;
        for (String depId : deps) {
            int depDepth = calculateTaskDepth(depId, dependencies, depthCache);
            if (depDepth > maxDepth) {
                maxDepth = depDepth;
            }
        }

        int depth = maxDepth + 1;
        depthCache.put(taskId, depth);
        return depth;
    }

    /**
     * 预估完成时间
     */
    private void estimateCompletionTime(TaskPlan plan) {
        // 使用关键路径法估算
        DependencyResolver.CriticalPath criticalPath =
                new DependencyResolver().calculateCriticalPath(plan.getTasks());

        int totalDuration = criticalPath.getTotalDuration();

        // 考虑并发因素调整
        double concurrencyFactor = calculateConcurrencyFactor(plan);
        int adjustedDuration = (int) (totalDuration / concurrencyFactor);

        LocalDateTime estimatedCompletion = plan.getCreateTime()
                .plusSeconds(adjustedDuration);

        plan.setEstimatedCompletionTime(estimatedCompletion);

        // 保存估算信息到元数据
        plan.getMetadata().put("critical_path_duration", totalDuration);
        plan.getMetadata().put("concurrency_factor", concurrencyFactor);
        plan.getMetadata().put("adjusted_duration", adjustedDuration);
        plan.getMetadata().put("critical_path_tasks", criticalPath.getTasks());
    }

    /**
     * 计算并发因子
     */
    private double calculateConcurrencyFactor(TaskPlan plan) {
        double factor = 1.0;

        // 统计可并行任务的比例
        long parallelTasks = plan.getTasks().stream()
                .filter(t -> t.getMaxConcurrent() != null && t.getMaxConcurrent() > 1)
                .count();

        double parallelRatio = (double) parallelTasks / plan.getTasks().size();

        // 根据并行比例调整因子
        if (parallelRatio > 0.3) {
            factor = 1.5;
        }
        if (parallelRatio > 0.6) {
            factor = 2.0;
        }

        // 考虑策略影响
        if (plan.getStrategy() == PlanningStrategyEnum.PARALLEL) {
            factor *= 1.2;
        }

        return Math.min(factor, 3.0);  // 最大并发因子为3
    }

    /**
     * 验证计划可行性
     */
    private void validatePlan(TaskPlan plan) {
        // 检查任务依赖是否都有效
        Set<String> taskIds = new HashSet<>();
        for (Task task : plan.getTasks()) {
            taskIds.add(task.getTaskId());
        }

        for (Task task : plan.getTasks()) {
            if (task.getDependencies() != null) {
                for (String depId : task.getDependencies()) {
                    if (!taskIds.contains(depId)) {
                        throw new PlanningException(
                                String.format("任务%s依赖的任务%s不存在于计划中",
                                        task.getTaskId(), depId));
                    }
                }
            }
        }

        // 检查是否有孤立任务（无依赖也无被依赖）
        validateTaskConnectivity(plan);

        // 检查资源分配是否合理
        validateResourceAllocation(plan);

        log.debug("计划验证通过");
    }

    /**
     * 验证任务连接性
     */
    private void validateTaskConnectivity(TaskPlan plan) {
        Map<String, Task> taskMap = new HashMap<>();
        Map<String, List<String>> dependencies = new HashMap<>();
        Map<String, List<String>> reverseDependencies = new HashMap<>();

        for (Task task : plan.getTasks()) {
            taskMap.put(task.getTaskId(), task);
            dependencies.put(task.getTaskId(),
                    task.getDependencies() != null ? task.getDependencies() : new ArrayList<>());
            reverseDependencies.put(task.getTaskId(), new ArrayList<>());
        }

        // 构建反向依赖
        for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
            for (String depId : entry.getValue()) {
                reverseDependencies.get(depId).add(entry.getKey());
            }
        }

        // 查找既无依赖也无被依赖的任务
        List<String> isolatedTasks = new ArrayList<>();
        for (Task task : plan.getTasks()) {
            List<String> deps = dependencies.get(task.getTaskId());
            List<String> revDeps = reverseDependencies.get(task.getTaskId());

            if ((deps == null || deps.isEmpty()) &&
                    (revDeps == null || revDeps.isEmpty())) {
                isolatedTasks.add(task.getTaskId());
            }
        }

        if (!isolatedTasks.isEmpty()) {
            log.warn("发现孤立任务: {}", isolatedTasks);
            // 可以选择处理，比如给孤立任务添加依赖或警告
        }
    }

    /**
     * 验证资源分配
     */
    private void validateResourceAllocation(TaskPlan plan) {
        int totalEstimatedDuration = plan.getTasks().stream()
                .mapToInt(Task::getEstimatedDuration)
                .sum();

        // 检查是否有任务预估时间过长
        List<Task> longTasks = plan.getTasks().stream()
                .filter(t -> t.getEstimatedDuration() != null && t.getEstimatedDuration() > 300)
                .toList();

        if (!longTasks.isEmpty()) {
            log.info("发现长时间任务: {}个", longTasks.size());
        }

        // 检查是否有任务超时设置不合理
        List<Task> invalidTimeoutTasks = plan.getTasks().stream()
                .filter(t -> t.getTimeout() != null &&
                        t.getEstimatedDuration() != null &&
                        t.getTimeout() < t.getEstimatedDuration())
                .toList();

        if (!invalidTimeoutTasks.isEmpty()) {
            log.warn("发现超时设置不合理的任务: {}个", invalidTimeoutTasks.size());
        }
    }

    /**
     * 动态调整计划
     */
    public void adjustPlan(TaskPlan plan, AdjustmentReason reason) {
        log.info("调整任务计划，原因: {}", reason);

        switch (reason) {
            case TASK_FAILED:
                handleTaskFailure(plan);
                break;
            case RESOURCE_SHORTAGE:
                handleResourceShortage(plan);
                break;
            case PRIORITY_CHANGED:
                handlePriorityChange(plan);
                break;
            case NEW_CONSTRAINT:
                handleNewConstraint(plan);
                break;
        }

        // 重新优化计划
        List<Task> optimizedTasks = optimizer.optimize(plan.getTasks());
        plan.setTasks(optimizedTasks);
        plan.updateStatistics();

        log.info("计划调整完成");
    }

    public enum AdjustmentReason {
        TASK_FAILED,
        RESOURCE_SHORTAGE,
        PRIORITY_CHANGED,
        NEW_CONSTRAINT,
        USER_INTERVENTION
    }

    private void handleTaskFailure(TaskPlan plan) {
        // 找出失败的任务
        List<Task> failedTasks = plan.getTasks().stream()
                .filter(t -> t.getStatus() == TaskStatusEnum.FAILED)
                .filter(Task::canRetry)
                .toList();

        // 重置失败任务状态
        for (Task task : failedTasks) {
            task.setStatus(TaskStatusEnum.PENDING);
            task.setRetryCount(task.getRetryCount() + 1);
            log.info("重试任务: {}, 重试次数: {}", task.getTaskId(), task.getRetryCount());
        }
    }

    private void handleResourceShortage(TaskPlan plan) {
        // 降低并发限制
        for (Task task : plan.getTasks()) {
            if (task.getMaxConcurrent() != null && task.getMaxConcurrent() > 1) {
                task.setMaxConcurrent(task.getMaxConcurrent() / 2);
            }
        }

        // 调整策略为线性执行
        plan.setStrategy(PlanningStrategyEnum.LINEAR);
    }

    private void handlePriorityChange(TaskPlan plan) {
        // 重新计算任务优先级
        for (Task task : plan.getTasks()) {
            int newPriority = calculateDynamicPriority(task, plan);
            task.setPriority(newPriority);
        }

        // 重新排序
        plan.getTasks().sort(Comparator.comparingInt(Task::getPriority).reversed());
    }

    private int calculateDynamicPriority(Task task, TaskPlan plan) {
        int basePriority = task.getPriority() != null ? task.getPriority() : 5;

        // 考虑任务状态
        if (task.getStatus() == TaskStatusEnum.FAILED && task.canRetry()) {
            basePriority += 2;  // 失败重试的任务提高优先级
        }

        // 考虑任务在关键路径上
        if (plan.getMetadata().containsKey("critical_path_tasks")) {
            List<String> criticalPath = (List<String>) plan.getMetadata().get("critical_path_tasks");
            if (criticalPath.contains(task.getTaskId())) {
                basePriority += 3;
            }
        }

        return Math.min(basePriority, 10);
    }

    private void handleNewConstraint(TaskPlan plan) {
        // 处理新的约束条件
        // 这里可以根据具体约束调整计划
    }

    /**
     * 创建简化计划（快速模式）
     */
    public TaskPlan createSimplePlan(IntentRecognitionModule.AgentIntent intent) {
        log.info("创建简化任务计划");

        // 直接创建任务，跳过复杂的优化步骤
        List<Task> tasks = taskFactory.createTasks(intent, PlanningStrategyEnum.LINEAR);

        TaskPlan plan = TaskPlan.builder()
                .planId("SIMPLE_PLAN_" + System.currentTimeMillis())
                .originalIntent(intent)
                .tasks(tasks)
                .strategy(PlanningStrategyEnum.LINEAR)
                .status(PlanStatusEnum.READY)
                .createTime(LocalDateTime.now())
                .priority(intent.getPriority().ordinal())
                .metadata(Map.of("plan_type", "simple"))
                .build();

        plan.updateStatistics();

        return plan;
    }
}
