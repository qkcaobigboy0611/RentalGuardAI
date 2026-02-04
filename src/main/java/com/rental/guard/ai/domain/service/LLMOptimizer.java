/**
 * @author qkcao
 * @date 2026/1/23 18:04
 */
package com.rental.guard.ai.domain.service;

import com.rental.guard.ai.domain.dto.PlanningStrategyEnum;
import com.rental.guard.ai.domain.dto.Task;
import com.rental.guard.ai.domain.dto.TaskTypeEnum;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LLM增强的任务优化器
 */
@Component
@Slf4j
public class LLMOptimizer {

    private final OllamaService ollamaService;
    private final Optimizer ruleOptimizer;

    public LLMOptimizer(OllamaService ollamaService, Optimizer ruleOptimizer) {
        this.ollamaService = ollamaService;
        this.ruleOptimizer = ruleOptimizer;
    }

    /**
     * LLM增强的任务优化
     */
    public List<Task> optimizeWithLLM(List<Task> tasks,
                                      PlanningStrategyEnum strategy) {

        log.info("开始LLM增强的任务优化，任务数: {}", tasks.size());

        // 1. 先进行规则优化
        List<Task> ruleOptimized = ruleOptimizer.optimize(tasks);

        // 2. 如果任务数量少，直接返回规则优化结果
        if (ruleOptimized.size() <= 3) {
            return ruleOptimized;
        }

        // 3. 使用LLM进行深度优化
        try {
            List<Task> llmOptimized =
                    deepOptimizeWithLLM(ruleOptimized, strategy);

            // 4. 比较优化效果
            double ruleScore = calculateOptimizationScore(ruleOptimized);
            double llmScore = calculateOptimizationScore(llmOptimized);

            log.debug("优化效果比较：规则优化得分{}，LLM优化得分{}", ruleScore, llmScore);

            // 选择得分更高的方案
            if (llmScore > ruleScore * 1.1) { // LLM优化效果显著更好
                log.info("LLM优化效果更好，采用LLM优化方案");
                return llmOptimized;
            } else {
                log.info("规则优化效果相当或更好，采用规则优化方案");
                return ruleOptimized;
            }

        } catch (Exception e) {
            log.warn("LLM优化失败，使用规则优化结果", e);
            return ruleOptimized;
        }
    }

    /**
     * 深度LLM优化
     */
    private List<Task> deepOptimizeWithLLM(List<Task> tasks,
                                           PlanningStrategyEnum strategy) {

        // 分析任务特征
        TaskFeatures features = analyzeTaskFeatures(tasks);

        // 根据特征选择合适的优化方法
        if (features.hasSimilarTasks && features.taskCount > 5) {
            return optimizeSimilarTasks(tasks, strategy);
        } else if (features.hasComplexDependencies) {
            return optimizeDependencies(tasks, strategy);
        } else if (features.hasLongRunningTasks) {
            return optimizeLongRunningTasks(tasks, strategy);
        } else {
            return generalOptimization(tasks, strategy);
        }
    }

    /**
     * 优化相似任务
     */
    private List<Task> optimizeSimilarTasks(List<Task> tasks,
                                            PlanningStrategyEnum strategy) {

        String prompt = buildSimilarTasksPrompt(tasks, strategy);

        OllamaService.ChainOfThoughtResponse thought = ollamaService.chainOfThought(prompt);

        if (thought.getConfidence() < 0.7) {
            log.warn("相似任务优化置信度过低");
            return tasks;
        }

        // 生成优化方案
        String optimizationPlan = generateOptimizationPlan(thought.getConclusion());

        // 应用优化
        return applySimilarTasksOptimization(tasks, optimizationPlan);
    }

    /**
     * 优化依赖关系
     */
    private List<Task> optimizeDependencies(List<Task> tasks,
                                            PlanningStrategyEnum strategy) {

        // 构建依赖图分析提示
        String dependencyGraph = buildDependencyGraph(tasks);

        String prompt = String.format("""
                请分析以下任务依赖图，优化依赖关系以提高执行效率：
                            
                任务依赖图：
                %s
                            
                执行策略：%s
                            
                优化目标：
                1. 减少关键路径长度
                2. 增加并行度
                3. 消除不必要的依赖
                4. 平衡各路径负载
                            
                请给出具体的优化建议。
                """, dependencyGraph, strategy);

        String advice = ollamaService.generateText(prompt);

        return applyDependencyOptimization(tasks, advice);
    }

    /**
     * 优化长时间任务
     */
    private List<Task> optimizeLongRunningTasks(List<Task> tasks,
                                                PlanningStrategyEnum strategy) {

        // 找出长时间任务
        List<Task> longTasks = tasks.stream()
                .filter(t -> t.getEstimatedDuration() != null && t.getEstimatedDuration() > 60)
                .collect(Collectors.toList());

        if (longTasks.isEmpty()) {
            return tasks;
        }

        String prompt = buildLongTasksPrompt(longTasks, strategy);

        String advice = ollamaService.generateText(prompt);

        return applyLongTasksOptimization(tasks, longTasks, advice);
    }

    /**
     * 通用优化
     */
    private List<Task> generalOptimization(List<Task> tasks,
                                           PlanningStrategyEnum strategy) {

        String prompt = buildGeneralOptimizationPrompt(tasks, strategy);

        OllamaService.ChainOfThoughtResponse thought = ollamaService.chainOfThought(prompt);

        if (thought.getConfidence() > 0.6) {
            return applyGeneralOptimization(tasks, thought.getConclusion());
        }

        return tasks;
    }

    /**
     * 分析任务特征
     */
    private TaskFeatures analyzeTaskFeatures(List<Task> tasks) {
        TaskFeatures features = new TaskFeatures();
        features.taskCount = tasks.size();

        // 分析任务类型分布
        Map<TaskTypeEnum, Integer> typeCount = new HashMap<>();
        for (Task task : tasks) {
            typeCount.merge(task.getTaskType(), 1, Integer::sum);
        }

        // 检查是否有相似任务（相同类型数量>1）
        features.hasSimilarTasks = typeCount.values().stream().anyMatch(count -> count > 1);

        // 检查依赖复杂度
        int maxDependencies = tasks.stream()
                .filter(t -> t.getDependencies() != null)
                .mapToInt(t -> t.getDependencies().size())
                .max()
                .orElse(0);
        features.hasComplexDependencies = maxDependencies > 2;

        // 检查是否有长时间任务
        features.hasLongRunningTasks = tasks.stream()
                .anyMatch(t -> t.getEstimatedDuration() != null && t.getEstimatedDuration() > 120);

        // 计算预估总时间
        features.totalEstimatedTime = tasks.stream()
                .filter(t -> t.getEstimatedDuration() != null)
                .mapToInt(Task::getEstimatedDuration)
                .sum();

        // 分析并行潜力
        features.parallelPotential = calculateParallelPotential(tasks);

        return features;
    }

    /**
     * 计算并行潜力
     */
    private double calculateParallelPotential(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return 0.0;
        }

        // 统计独立任务数量（无依赖）
        long independentTasks = tasks.stream()
                .filter(t -> t.getDependencies() == null || t.getDependencies().isEmpty())
                .count();

        // 统计任务类型多样性
        long distinctTypes = tasks.stream()
                .map(Task::getTaskType)
                .distinct()
                .count();

        return (independentTasks * 0.4 + distinctTypes * 0.6) / tasks.size();
    }

    /**
     * 计算优化得分
     */
    private double calculateOptimizationScore(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return 0.0;
        }

        double score = 0.0;

        // 1. 并行度得分（独立任务比例）
        double parallelScore = tasks.stream()
                .filter(t -> t.getDependencies() == null || t.getDependencies().isEmpty())
                .count() / (double) tasks.size();
        score += parallelScore * 0.3;

        // 2. 预估时间得分（时间越短越好）
        int totalTime = tasks.stream()
                .filter(t -> t.getEstimatedDuration() != null)
                .mapToInt(Task::getEstimatedDuration)
                .sum();
        double timeScore = Math.max(0, 1 - totalTime / 600.0); // 假设基线10分钟
        score += timeScore * 0.4;

        // 3. 任务数量得分（任务越少越好，但不要太少）
        double countScore = Math.max(0, 1 - Math.abs(tasks.size() - 5) / 10.0); // 最佳数量5
        score += countScore * 0.2;

        // 4. 依赖深度得分（深度越浅越好）
        int maxDepth = calculateMaxDependencyDepth(tasks);
        double depthScore = Math.max(0, 1 - maxDepth / 5.0);
        score += depthScore * 0.1;

        return score;
    }

    /**
     * 计算最大依赖深度
     */
    private int calculateMaxDependencyDepth(List<Task> tasks) {
        Map<String, Task> taskMap = new HashMap<>();
        for (Task task : tasks) {
            taskMap.put(task.getTaskId(), task);
        }

        int maxDepth = 0;
        for (Task task : tasks) {
            int depth = calculateTaskDepth(task, taskMap, new HashMap<>());
            maxDepth = Math.max(maxDepth, depth);
        }

        return maxDepth;
    }

    private int calculateTaskDepth(Task task,
                                   Map<String, Task> taskMap,
                                   Map<String, Integer> depthCache) {

        if (depthCache.containsKey(task.getTaskId())) {
            return depthCache.get(task.getTaskId());
        }

        if (task.getDependencies() == null || task.getDependencies().isEmpty()) {
            depthCache.put(task.getTaskId(), 0);
            return 0;
        }

        int maxDepth = 0;
        for (String depId : task.getDependencies()) {
            Task depTask = taskMap.get(depId);
            if (depTask != null) {
                int depth = calculateTaskDepth(depTask, taskMap, depthCache);
                maxDepth = Math.max(maxDepth, depth);
            }
        }

        int depth = maxDepth + 1;
        depthCache.put(task.getTaskId(), depth);
        return depth;
    }

    /**
     * 构建依赖图
     */
    private String buildDependencyGraph(List<Task> tasks) {
        StringBuilder graph = new StringBuilder();

        for (Task task : tasks) {
            graph.append(String.format("任务[%s] (%s)",
                    task.getTaskId(), task.getTaskType()));

            if (task.getDependencies() != null && !task.getDependencies().isEmpty()) {
                graph.append(" 依赖 -> ");
                graph.append(String.join(", ", task.getDependencies()));
            }

            graph.append("\n");
        }

        return graph.toString();
    }

    // 构建各种提示的方法
    private String buildSimilarTasksPrompt(List<Task> tasks,
                                           PlanningStrategyEnum strategy) {
        // 实现提示构建逻辑
        return "";
    }

    private String buildLongTasksPrompt(List<Task> longTasks,
                                        PlanningStrategyEnum strategy) {
        // 实现提示构建逻辑
        return "";
    }

    private String buildGeneralOptimizationPrompt(List<Task> tasks,
                                                  PlanningStrategyEnum strategy) {
        // 实现提示构建逻辑
        return "";
    }

    // 应用优化方案的方法
    private List<Task> applySimilarTasksOptimization(List<Task> tasks,
                                                     String optimizationPlan) {
        // 实现优化应用逻辑
        return tasks;
    }

    private List<Task> applyDependencyOptimization(List<Task> tasks,
                                                   String advice) {
        // 实现优化应用逻辑
        return tasks;
    }

    private List<Task> applyLongTasksOptimization(List<Task> tasks,
                                                  List<Task> longTasks,
                                                  String advice) {
        // 实现优化应用逻辑
        return tasks;
    }

    private List<Task> applyGeneralOptimization(List<Task> tasks,
                                                String conclusion) {
        // 实现优化应用逻辑
        return tasks;
    }

    private String generateOptimizationPlan(String conclusion) {
        // 生成具体的优化计划
        return "";
    }

    // 内部数据类
    @Data
    private static class TaskFeatures {
        private int taskCount;
        private boolean hasSimilarTasks;
        private boolean hasComplexDependencies;
        private boolean hasLongRunningTasks;
        private int totalEstimatedTime;
        private double parallelPotential;
    }
}
