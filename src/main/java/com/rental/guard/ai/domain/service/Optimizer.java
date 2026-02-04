/**
 * @author qkcao
 * @date 2026/1/22 19:07
 */
package com.rental.guard.ai.domain.service;

import com.rental.guard.ai.domain.dto.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class Optimizer {

    /**
     * 优化任务执行顺序
     */
    public List<Task> optimize(List<Task> tasks) {
        log.debug("开始优化任务执行顺序，原始任务数: {}", tasks.size());

        // 1. 拓扑排序
        List<Task> sortedTasks = topologicalSort(tasks);

        // 2. 基于优先级的调整
        sortedTasks = adjustByPriority(sortedTasks);

        // 3. 合并相似任务
        sortedTasks = mergeSimilarTasks(sortedTasks);

        // 4. 并行化优化
        sortedTasks = parallelizeTasks(sortedTasks);

        // 5. 负载均衡
        sortedTasks = balanceWorkload(sortedTasks);

        log.debug("优化完成，优化后任务数: {}", sortedTasks.size());
        return sortedTasks;
    }

    /**
     * 拓扑排序（考虑依赖关系）
     */
    private List<Task> topologicalSort(List<Task> tasks) {
        Map<String, Task> taskMap = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        // 初始化
        for (Task task : tasks) {
            String taskId = task.getTaskId();
            taskMap.put(taskId, task);
            adjacency.put(taskId, new ArrayList<>());
            inDegree.put(taskId, 0);
        }

        // 构建图
        for (Task task : tasks) {
            if (task.getDependencies() != null) {
                for (String depId : task.getDependencies()) {
                    adjacency.get(depId).add(task.getTaskId());
                    inDegree.put(task.getTaskId(),
                            inDegree.get(task.getTaskId()) + 1);
                }
            }
        }

        // 使用优先队列，按优先级排序
        PriorityQueue<Task> queue = new PriorityQueue<>(
                Comparator.comparingInt(Task::getPriority).reversed()
        );

        for (String taskId : inDegree.keySet()) {
            if (inDegree.get(taskId) == 0) {
                queue.offer(taskMap.get(taskId));
            }
        }

        List<Task> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            Task task = queue.poll();
            result.add(task);

            for (String neighborId : adjacency.get(task.getTaskId())) {
                int newInDegree = inDegree.get(neighborId) - 1;
                inDegree.put(neighborId, newInDegree);
                if (newInDegree == 0) {
                    queue.offer(taskMap.get(neighborId));
                }
            }
        }

        if (result.size() != tasks.size()) {
            log.warn("拓扑排序后任务数不一致，可能存在循环依赖");
            return tasks;  // 返回原始顺序
        }

        return result;
    }

    /**
     * 基于优先级调整
     */
    private List<Task> adjustByPriority(List<Task> tasks) {
        // 对于没有依赖关系的任务，按优先级重新排序
        List<Task> independentTasks = new ArrayList<>();
        List<Task> dependentTasks = new ArrayList<>();

        for (Task task : tasks) {
            if (task.getDependencies() == null || task.getDependencies().isEmpty()) {
                independentTasks.add(task);
            } else {
                dependentTasks.add(task);
            }
        }

        // 独立任务按优先级排序
        independentTasks.sort(Comparator.comparingInt(Task::getPriority).reversed());

        // 合并结果
        List<Task> result = new ArrayList<>(independentTasks);
        result.addAll(dependentTasks);

        return result;
    }

    /**
     * 合并相似任务
     */
    private List<Task> mergeSimilarTasks(List<Task> tasks) {
        Map<String, List<Task>> taskGroups = new HashMap<>();

        // 按任务类型和参数分组
        for (Task task : tasks) {
            String groupKey = buildGroupKey(task);
            taskGroups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(task);
        }

        List<Task> mergedTasks = new ArrayList<>();

        for (List<Task> group : taskGroups.values()) {
            if (group.size() == 1) {
                mergedTasks.add(group.get(0));
            } else {
                // 合并相似任务
                Task mergedTask = mergeTaskGroup(group);
                mergedTasks.add(mergedTask);
                log.debug("合并了{}个相似任务: {}", group.size(), mergedTask.getTaskType());
            }
        }

        return mergedTasks;
    }

    /**
     * 构建分组键
     */
    private String buildGroupKey(Task task) {
        StringBuilder key = new StringBuilder();
        key.append(task.getTaskType()).append("|");

        // 添加关键参数
        if (task.getParameters() != null) {
            // 只考虑某些关键参数进行合并
            List<String> keyParams = Arrays.asList("user_id", "time_range", "risk_level");
            for (String paramKey : keyParams) {
                if (task.getParameters().containsKey(paramKey)) {
                    key.append(paramKey).append("=").append(task.getParameters().get(paramKey)).append("|");
                }
            }
        }

        return key.toString();
    }

    /**
     * 合并任务组
     */
    private Task mergeTaskGroup(List<Task> group) {
        Task firstTask = group.get(0);

        // 合并参数
        Map<String, Object> mergedParams = new HashMap<>(firstTask.getParameters());
        List<Object> mergedValues = new ArrayList<>();

        for (int i = 1; i < group.size(); i++) {
            Task task = group.get(i);
            if (task.getParameters() != null) {
                for (Map.Entry<String, Object> entry : task.getParameters().entrySet()) {
                    if ("user_id".equals(entry.getKey())) {
                        // 合并用户ID
                        if (!mergedParams.containsKey("user_ids")) {
                            mergedParams.put("user_ids", new ArrayList<String>());
                        }
                        ((List<String>) mergedParams.get("user_ids")).add((String) entry.getValue());
                    } else {
                        // 其他参数取第一个任务的
                        mergedParams.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                }
            }

            // 合并依赖
            if (task.getDependencies() != null) {
                for (String depId : task.getDependencies()) {
                    if (firstTask.getDependencies() == null) {
                        firstTask.setDependencies(new ArrayList<>());
                    }
                    if (!firstTask.getDependencies().contains(depId)) {
                        firstTask.getDependencies().add(depId);
                    }
                }
            }
        }

        // 更新任务信息
        return Task.builder()
                .taskId(firstTask.getTaskId() + "_merged")
                .taskType(firstTask.getTaskType())
                .name(firstTask.getName() + " (合并版)")
                .description(firstTask.getDescription() + "，合并了" + (group.size() - 1) + "个相似任务")
                .parameters(mergedParams)
                .dependencies(firstTask.getDependencies())
                .priority(firstTask.getPriority())
                .estimatedDuration(firstTask.getEstimatedDuration() * group.size() / 2)  // 合并后预估时间减少
                .requiredTools(firstTask.getRequiredTools())
                .maxConcurrent(firstTask.getMaxConcurrent())
                .metadata(firstTask.getMetadata())
                .build();
    }

    /**
     * 并行化优化
     */
    private List<Task> parallelizeTasks(List<Task> tasks) {
        // 识别可以并行执行的任务
        Map<Integer, List<Task>> levelMap = new HashMap<>();

        // 计算任务层级（基于依赖深度）
        for (Task task : tasks) {
            int level = calculateDependencyLevel(task, tasks);
            levelMap.computeIfAbsent(level, k -> new ArrayList<>()).add(task);
        }

        // 为同一层级的任务设置并行标记
        for (List<Task> levelTasks : levelMap.values()) {
            if (levelTasks.size() > 1) {
                for (Task task : levelTasks) {
                    if (task.getMetadata() == null) {
                        task.setMetadata(new HashMap<>());
                    }
                    task.getMetadata().put("can_parallel", true);
                    task.getMetadata().put("parallel_group", "level_" + calculateDependencyLevel(task, tasks));
                }
            }
        }

        return tasks;
    }

    /**
     * 计算依赖层级
     */
    private int calculateDependencyLevel(Task task, List<Task> allTasks) {
        if (task.getDependencies() == null || task.getDependencies().isEmpty()) {
            return 0;
        }

        int maxLevel = 0;
        Map<String, Task> taskMap = new HashMap<>();
        for (Task t : allTasks) {
            taskMap.put(t.getTaskId(), t);
        }

        for (String depId : task.getDependencies()) {
            Task depTask = taskMap.get(depId);
            if (depTask != null) {
                int depLevel = calculateDependencyLevel(depTask, allTasks);
                if (depLevel > maxLevel) {
                    maxLevel = depLevel;
                }
            }
        }

        return maxLevel + 1;
    }

    /**
     * 负载均衡
     */
    private List<Task> balanceWorkload(List<Task> tasks) {
        // 估算每个任务的负载
        Map<String, Integer> taskLoad = new HashMap<>();
        for (Task task : tasks) {
            taskLoad.put(task.getTaskId(), estimateTaskLoad(task));
        }

        // 重新安排任务，避免负载不均衡
        // 这里使用简单的轮询调度算法
        List<Task> balanced = new ArrayList<>();
        List<Task> heavyTasks = new ArrayList<>();
        List<Task> lightTasks = new ArrayList<>();

        for (Task task : tasks) {
            if (taskLoad.get(task.getTaskId()) > 50) {  // 阈值
                heavyTasks.add(task);
            } else {
                lightTasks.add(task);
            }
        }

        // 交替安排重任务和轻任务
        int i = 0, j = 0;
        while (i < heavyTasks.size() || j < lightTasks.size()) {
            if (i < heavyTasks.size()) {
                balanced.add(heavyTasks.get(i++));
            }
            if (j < lightTasks.size()) {
                balanced.add(lightTasks.get(j++));
            }
        }

        return balanced;
    }

    /**
     * 估算任务负载
     */
    private int estimateTaskLoad(Task task) {
        int baseLoad = 10;  // 基础负载

        // 根据任务类型增加负载
        if (task.getTaskType().getDisplayName().contains("ANALYSIS")) {
            baseLoad += 30;
        }
        if (task.getTaskType().getDisplayName().contains("BATCH")) {
            baseLoad += 20;
        }

        // 根据预估时间增加负载
        if (task.getEstimatedDuration() != null) {
            baseLoad += task.getEstimatedDuration() / 10;
        }

        // 根据并发限制调整负载
        if (task.getMaxConcurrent() != null && task.getMaxConcurrent() > 1) {
            baseLoad /= task.getMaxConcurrent();  // 可并行的任务负载较低
        }

        return Math.min(baseLoad, 100);  // 限制最大负载为100
    }
}
