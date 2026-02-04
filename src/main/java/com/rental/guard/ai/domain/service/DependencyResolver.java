/**
 * @author qkcao
 * @date 2026/1/22 19:04
 */
package com.rental.guard.ai.domain.service;

import com.rental.guard.ai.domain.dto.PlanningException;
import com.rental.guard.ai.domain.dto.Task;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 依赖解析器实现
 */
@Component
public class DependencyResolver {

    /**
     * 解析任务依赖关系
     */
    public List<Task> resolve(List<Task> tasks) {
        // 创建任务映射
        Map<String, Task> taskMap = new HashMap<>();
        for (Task task : tasks) {
            taskMap.put(task.getTaskId(), task);
        }

        // 解析显式依赖
        resolveExplicitDependencies(tasks, taskMap);

        // 解析隐式依赖
        resolveImplicitDependencies(tasks, taskMap);

        // 检测循环依赖
        detectCircularDependencies(tasks);

        // 验证依赖完整性
        validateDependencies(tasks, taskMap);

        return tasks;
    }

    /**
     * 解析显式依赖
     */
    private void resolveExplicitDependencies(List<Task> tasks,
                                             Map<String, Task> taskMap) {
        // 这里主要处理已经在任务中定义的dependencies字段
        // 验证依赖的任务是否存在
        for (Task task : tasks) {
            if (task.getDependencies() != null) {
                for (String depId : task.getDependencies()) {
                    if (!taskMap.containsKey(depId)) {
                        throw new PlanningException(
                                String.format("任务%s依赖的任务%s不存在", task.getTaskId(), depId));
                    }
                }
            }
        }
    }

    /**
     * 解析隐式依赖
     */
    private void resolveImplicitDependencies(List<Task> tasks,
                                             Map<String, Task> taskMap) {
        // 1. 根据数据流解析依赖（outputs -> inputs）
        resolveDataFlowDependencies(tasks, taskMap);

        // 2. 根据资源约束解析依赖
        resolveResourceDependencies(tasks, taskMap);

        // 3. 根据时间约束解析依赖
        resolveTemporalDependencies(tasks, taskMap);
    }

    /**
     * 解析数据流依赖
     */
    private void resolveDataFlowDependencies(List<Task> tasks,
                                             Map<String, Task> taskMap) {
        // 构建输出映射：output -> taskId
        Map<String, List<String>> outputToTask = new HashMap<>();

        // 第一遍：收集所有任务的输出
        for (Task task : tasks) {
            if (task.getOutputs() != null) {
                for (String output : task.getOutputs()) {
                    outputToTask.computeIfAbsent(output, k -> new ArrayList<>())
                            .add(task.getTaskId());
                }
            }
        }

        // 第二遍：根据参数解析输入依赖
        for (Task task : tasks) {
            Set<String> implicitDeps = new HashSet<>();

            // 从参数中解析需要的输入
            List<String> requiredInputs = extractRequiredInputs(task);

            for (String input : requiredInputs) {
                if (outputToTask.containsKey(input)) {
                    implicitDeps.addAll(outputToTask.get(input));
                }
            }

            // 合并依赖
            if (!implicitDeps.isEmpty()) {
                Set<String> allDeps = new HashSet<>();
                if (task.getDependencies() != null) {
                    allDeps.addAll(task.getDependencies());
                }
                allDeps.addAll(implicitDeps);
                task.setDependencies(new ArrayList<>(allDeps));
            }
        }
    }

    /**
     * 提取任务需要的输入
     */
    private List<String> extractRequiredInputs(Task task) {
        List<String> inputs = new ArrayList<>();

        // 从参数中提取输入引用
        if (task.getParameters() != null) {
            for (Object value : task.getParameters().values()) {
                if (value instanceof String) {
                    String strValue = (String) value;
                    // 检查是否是数据引用格式，如 ${data.user_info}
                    if (strValue.startsWith("${") && strValue.endsWith("}")) {
                        String dataKey = strValue.substring(2, strValue.length() - 1);
                        inputs.add(dataKey);
                    }
                }
            }
        }

        return inputs;
    }

    /**
     * 解析资源依赖
     */
    private void resolveResourceDependencies(List<Task> tasks,
                                             Map<String,Task> taskMap) {
        // 分组：按需要的资源
        Map<String, List<Task>> tasksByResource = new HashMap<>();

        for (Task task : tasks) {
            if (task.getRequiredResources() != null) {
                for (String resource : task.getRequiredResources()) {
                    tasksByResource.computeIfAbsent(resource, k -> new ArrayList<>())
                            .add(task);
                }
            }
        }

        // 处理需要独占资源的任务
        for (Map.Entry<String, List<Task>> entry : tasksByResource.entrySet()) {
            List<Task> resourceTasks = entry.getValue();
            if (resourceTasks.size() > 1) {
                // 按优先级排序，高优先级的先执行
                resourceTasks.sort(Comparator.comparingInt(Task::getPriority)
                        .reversed());

                // 为后续任务添加依赖
                for (int i = 1; i < resourceTasks.size(); i++) {
                    Task currentTask = resourceTasks.get(i);
                    Task previousTask = resourceTasks.get(i - 1);

                    List<String> deps = new ArrayList<>();
                    if (currentTask.getDependencies() != null) {
                        deps.addAll(currentTask.getDependencies());
                    }
                    deps.add(previousTask.getTaskId());
                    currentTask.setDependencies(deps);
                }
            }
        }
    }

    /**
     * 解析时间约束依赖
     */
    private void resolveTemporalDependencies(List<Task> tasks,
                                             Map<String, Task> taskMap) {
        // 这里可以处理如"必须在某个时间点之后执行"等时间约束
        // 目前简化处理，后续可根据需要扩展
    }

    /**
     * 检测循环依赖
     */
    private void detectCircularDependencies(List<Task> tasks) {
        Map<String, List<String>> adjacency = new HashMap<>();

        // 构建邻接表
        for (Task task : tasks) {
            adjacency.put(task.getTaskId(),
                    task.getDependencies() != null ?
                            new ArrayList<>(task.getDependencies()) :
                            new ArrayList<>());
        }

        // 使用DFS检测环
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String taskId : adjacency.keySet()) {
            if (hasCycle(taskId, adjacency, visited, recursionStack)) {
                throw new PlanningException("检测到循环依赖");
            }
        }
    }

    private boolean hasCycle(String taskId,
                             Map<String, List<String>> adjacency,
                             Set<String> visited,
                             Set<String> recursionStack) {
        if (recursionStack.contains(taskId)) {
            return true;
        }
        if (visited.contains(taskId)) {
            return false;
        }

        visited.add(taskId);
        recursionStack.add(taskId);

        for (String depId : adjacency.get(taskId)) {
            if (hasCycle(depId, adjacency, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(taskId);
        return false;
    }

    /**
     * 验证依赖完整性
     */
    private void validateDependencies(List<Task> tasks,
                                      Map<String, Task> taskMap) {
        for (Task task : tasks) {
            if (task.getDependencies() != null) {
                // 检查是否有重复依赖
                Set<String> depSet = new HashSet<>(task.getDependencies());
                if (depSet.size() != task.getDependencies().size()) {
                    // 移除重复
                    task.setDependencies(new ArrayList<>(depSet));
                }

                // 检查是否有自依赖
                if (task.getDependencies().contains(task.getTaskId())) {
                    throw new PlanningException(
                            String.format("任务%s不能依赖自身", task.getTaskId()));
                }
            }
        }
    }

    /**
     * 计算任务的关键路径
     */
    public CriticalPath calculateCriticalPath(List<Task> tasks) {
        Map<String, Task> taskMap = new HashMap<>();
        Map<String, List<String>> dependencies = new HashMap<>();

        // 初始化
        for (Task task : tasks) {
            taskMap.put(task.getTaskId(), task);
            dependencies.put(task.getTaskId(),
                    task.getDependencies() != null ? task.getDependencies() : new ArrayList<>());
        }

        // 计算最早开始时间
        Map<String, Integer> earliestStart = new HashMap<>();
        Map<String, Integer> earliestFinish = new HashMap<>();

        // 拓扑排序
        List<String> sortedTasks = topologicalSort(tasks);

        for (String taskId : sortedTasks) {
            Task task = taskMap.get(taskId);
            int est = 0;

            // 计算依赖任务的最晚完成时间
            for (String depId : dependencies.get(taskId)) {
                Task depTask = taskMap.get(depId);
                int depFinish = earliestFinish.get(depId);
                if (depFinish > est) {
                    est = depFinish;
                }
            }

            earliestStart.put(taskId, est);
            earliestFinish.put(taskId, est + task.getEstimatedDuration());
        }

        // 计算最晚开始时间
        Map<String, Integer> latestStart = new HashMap<>();
        Map<String, Integer> latestFinish = new HashMap<>();

        // 找出所有没有后续任务的任务
        Set<String> terminalTasks = new HashSet<>(taskMap.keySet());
        for (List<String> deps : dependencies.values()) {
            for (String depId : deps) {
                terminalTasks.remove(depId);
            }
        }

        // 逆序计算
        int projectDuration = 0;
        for (String taskId : terminalTasks) {
            projectDuration = Math.max(projectDuration, earliestFinish.get(taskId));
        }

        List<String> reversedSorted = new ArrayList<>(sortedTasks);
        Collections.reverse(reversedSorted);

        for (String taskId : reversedSorted) {
            Task task = taskMap.get(taskId);

            int lft = projectDuration;
            // 查找后续任务
            for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
                if (entry.getValue().contains(taskId)) {
                    String successorId = entry.getKey();
                    int successorLst = latestStart.get(successorId);
                    if (successorLst < lft) {
                        lft = successorLst;
                    }
                }
            }

            latestFinish.put(taskId, lft);
            latestStart.put(taskId, lft - task.getEstimatedDuration());
        }

        // 计算浮动时间并识别关键路径
        List<String> criticalPath = new ArrayList<>();
        for (String taskId : sortedTasks) {
            int slack = latestStart.get(taskId) - earliestStart.get(taskId);
            if (slack == 0) {
                criticalPath.add(taskId);
            }
        }

        return CriticalPath.builder()
                .tasks(criticalPath)
                .totalDuration(projectDuration)
                .build();
    }

    private List<String> topologicalSort(List<Task> tasks) {
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
            if (task.getDependencies() != null) {
                for (String depId : task.getDependencies()) {
                    adjacency.get(depId).add(task.getTaskId());
                    inDegree.put(task.getTaskId(),
                            inDegree.get(task.getTaskId()) + 1);
                }
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
            throw new PlanningException("存在循环依赖，无法进行拓扑排序");
        }

        return result;
    }

    @Data
    @Builder
    static class CriticalPath {
        private List<String> tasks;  // 关键路径上的任务ID
        private int totalDuration;   // 总时长
    }
}
