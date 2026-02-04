/**
 * @author qkcao
 * @date 2026/1/23 17:52
 */
package com.rental.guard.ai.domain.service;

import com.rental.guard.ai.domain.dto.Task;
import com.rental.guard.ai.domain.dto.TaskTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * LLM增强的任务分解器
 */
@Component
@Slf4j
public class LLMTaskDecomposer {

    private final OllamaService ollamaService;
    private final RuleBasedTaskDecomposer ruleDecomposer;

    // 缓存分解结果
    private final Map<String, List<Task>> decompositionCache = new HashMap<>();

    public LLMTaskDecomposer(OllamaService ollamaService, RuleBasedTaskDecomposer ruleDecomposer) {
        this.ollamaService = ollamaService;
        this.ruleDecomposer = ruleDecomposer;
    }

    /**
     * LLM增强的任务分解
     */
    public List<Task> decomposeWithLLM(Task parentTask,
                                                   IntentRecognitionModule.AgentIntent intent) {
        String cacheKey = generateCacheKey(parentTask, intent);

        // 检查缓存
        if (decompositionCache.containsKey(cacheKey)) {
            log.debug("使用缓存的任务分解结果");
            return new ArrayList<>(decompositionCache.get(cacheKey));
        }

        // 先尝试规则分解
        List<Task> ruleBasedTasks = ruleDecomposer.decompose(Collections.singletonList(parentTask));
        if (ruleBasedTasks.size() == 1 && ruleBasedTasks.get(0) == parentTask) {
            log.info("规则分解无效，使用LLM分解任务: {}", parentTask.getTaskType());
            return decomposeWithLLMOnly(parentTask, intent);
        }

        // LLM优化规则分解结果
        List<Task> optimizedTasks = optimizeWithLLM(ruleBasedTasks, parentTask, intent);

        // 缓存结果
        decompositionCache.put(cacheKey, new ArrayList<>(optimizedTasks));

        return optimizedTasks;
    }

    /**
     * 完全使用LLM分解任务
     */
    private List<Task> decomposeWithLLMOnly(Task parentTask,
                                                        IntentRecognitionModule.AgentIntent intent) {
        try {
            // 构建分解提示
            String prompt = buildDecompositionPrompt(parentTask, intent);

            // 使用思维链分析
            OllamaService.ChainOfThoughtResponse thought = ollamaService.chainOfThought(prompt);
            log.debug("思维链推理完成，置信度: {}", thought.getConfidence());

            if (thought.getConfidence() < 0.6) {
                log.warn("LLM分解置信度过低: {}", thought.getConfidence());
                return Collections.singletonList(parentTask);
            }

            // 生成结构化分解
            OllamaService.TaskDecompositionResponse decomposition =
                    ollamaService.generateStructuredResponse(prompt,
                            OllamaService.TaskDecompositionResponse.class);

            // 转换为任务对象
            List<Task> subtasks = convertToTasks(decomposition, parentTask);

            // 验证分解结果
            if (validateDecomposition(subtasks, parentTask)) {
                log.info("LLM分解成功，生成{}个子任务", subtasks.size());
                return subtasks;
            } else {
                log.warn("LLM分解验证失败，使用原始任务");
                return Collections.singletonList(parentTask);
            }

        } catch (Exception e) {
            log.error("LLM任务分解失败，使用规则分解", e);
            return ruleDecomposer.decompose(Collections.singletonList(parentTask));
        }
    }

    /**
     * 使用LLM优化任务序列
     */
    private List<Task> optimizeWithLLM(List<Task> tasks,
                                                   Task parentTask,
                                                   IntentRecognitionModule.AgentIntent intent) {
        try {
            String prompt = buildOptimizationPrompt(tasks, parentTask, intent);

            OllamaService.ChainOfThoughtResponse thought = ollamaService.chainOfThought(prompt);

            if (thought.getConfidence() < 0.7) {
                log.debug("优化置信度过低，使用原始任务序列");
                return tasks;
            }

            // 分析优化建议
            String optimizationSuggestion = analyzeOptimization(thought.getConclusion());

            // 应用优化
            List<Task> optimizedTasks = applyOptimization(tasks, optimizationSuggestion);

            log.info("LLM优化完成，优化建议: {}", optimizationSuggestion);
            return optimizedTasks;

        } catch (Exception e) {
            log.warn("LLM优化失败，使用原始任务序列", e);
            return tasks;
        }
    }

    /**
     * 构建分解提示
     */
    private String buildDecompositionPrompt(Task task,
                                            IntentRecognitionModule.AgentIntent intent) {
        return String.format("""
                        你是一个防欺诈系统的任务规划专家。请将以下任务分解为合适的子任务序列。
                                    
                        主任务信息：
                        任务类型：%s
                        任务名称：%s
                        任务描述：%s
                        参数：%s
                                    
                        用户意图：
                        意图类型：%s
                        实体：%s
                        参数：%s
                        置信度：%.2f
                                    
                        可用的任务类型：
                        %s
                                    
                        分解要求：
                        1. 任务粒度适中，既不过于粗放也不过于琐碎
                        2. 考虑任务间的依赖关系
                        3. 预估每个子任务的执行时间
                        4. 考虑防欺诈系统的特殊性（需要验证、审计、风险控制）
                        5. 如果涉及敏感数据，需要加入隐私保护步骤
                        6. 对于高风险操作，需要加入确认和验证步骤
                                    
                        请给出合理的任务分解方案。
                        """,
                task.getTaskType(),
                task.getName(),
                task.getDescription(),
                task.getParameters(),
                intent.getIntentType(),
                intent.getEntities(),
                intent.getParameters(),
                intent.getConfidence(),
                getAllTaskTypeDescriptions()
        );
    }

    /**
     * 构建优化提示
     */
    private String buildOptimizationPrompt(List<Task> tasks,
                                           Task parentTask,
                                           IntentRecognitionModule.AgentIntent intent) {
        StringBuilder tasksDesc = new StringBuilder();
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            tasksDesc.append(String.format("""
                            任务%d：
                            类型：%s
                            名称：%s
                            预估时间：%d秒
                            依赖：%s
                                            
                            """, i + 1, t.getTaskType(), t.getName(),
                    t.getEstimatedDuration(), t.getDependencies()));
        }

        return String.format("""
                        请分析以下任务序列的优化空间：
                                    
                        原任务：%s (%s)
                        用户意图：%s
                                    
                        当前任务序列（共%d个任务）：
                        %s
                                    
                        优化考虑因素：
                        1. 是否可以合并相似任务？
                        2. 是否可以并行执行某些任务？
                        3. 依赖关系是否合理？
                        4. 预估时间是否准确？
                        5. 是否存在冗余步骤？
                        6. 是否需要添加监控或验证步骤？
                        7. 风险控制是否充分？
                                    
                        请给出具体的优化建议。
                        """,
                parentTask.getName(), parentTask.getTaskType(),
                intent.getIntentType(),
                tasks.size(),
                tasksDesc.toString()
        );
    }

    /**
     * 转换LLM响应为任务对象
     */
    private List<Task> convertToTasks(OllamaService.TaskDecompositionResponse decomposition,
                                                  Task parentTask) {
        List<Task> tasks = new ArrayList<>();

        for (int i = 0; i < decomposition.getSubtasks().size(); i++) {
            OllamaService.TaskDecompositionResponse.Subtask subtask = decomposition.getSubtasks().get(i);

            Task task = Task.builder()
                    .taskId(generateSubtaskId(parentTask.getTaskId(), i))
                    .taskType(TaskTypeEnum.fromName(subtask.getTask_type()))
                    .name(subtask.getName())
                    .description(subtask.getDescription())
                    .parameters(subtask.getParameters() != null ? subtask.getParameters() : new HashMap<>())
                    .estimatedDuration(subtask.getEstimated_duration())
                    .priority(parentTask.getPriority())
                    .maxRetries(parentTask.getMaxRetries())
                    .timeout(subtask.getEstimated_duration() != null ?
                            subtask.getEstimated_duration() * 2 : 60)
                    .parentTaskId(parentTask.getTaskId())
                    .build();

            tasks.add(task);
        }

        // 设置依赖关系
        setDependencies(tasks, decomposition.getDependencies());

        return tasks;
    }

    /**
     * 设置任务依赖
     */
    private void setDependencies(List<Task> tasks, List<List<String>> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }

        Map<String, Task> taskMap = new HashMap<>();
        for (Task task : tasks) {
            taskMap.put(task.getTaskId(), task);
        }

        for (List<String> depPair : dependencies) {
            if (depPair.size() >= 2) {
                String taskId = depPair.get(0);
                String depId = depPair.get(1);

                Task task = taskMap.get(taskId);
                Task depTask = taskMap.get(depId);

                if (task != null && depTask != null) {
                    if (task.getDependencies() == null) {
                        task.setDependencies(new ArrayList<>());
                    }
                    task.getDependencies().add(depTask.getTaskId());

                    // 同时设置反向依赖
                    if (depTask.getDependents() == null) {
                        depTask.setDependents(new ArrayList<>());
                    }
                    depTask.getDependents().add(task.getTaskId());
                }
            }
        }
    }

    /**
     * 验证分解结果
     */
    private boolean validateDecomposition(List<Task> subtasks, Task parentTask) {
        if (subtasks.isEmpty()) {
            log.warn("分解结果为空");
            return false;
        }

        // 检查是否有循环依赖
        if (hasCircularDependency(subtasks)) {
            log.warn("发现循环依赖");
            return false;
        }

        // 检查所有任务是否都关联到父任务
        for (Task task : subtasks) {
            if (!task.getParentTaskId().equals(parentTask.getTaskId())) {
                log.warn("任务{}未正确关联到父任务", task.getTaskId());
                return false;
            }
        }

        // 检查参数完整性
        for (Task task : subtasks) {
            if (!task.validateParameters()) {
                log.warn("任务{}参数验证失败", task.getTaskId());
                return false;
            }
        }

        return true;
    }

    /**
     * 分析优化建议
     */
    private String analyzeOptimization(String conclusion) {
        // 使用LLM提取具体的优化建议
        String prompt = String.format("""
                请从以下分析结论中提取具体的优化建议：
                            
                分析结论：%s
                            
                请以列表形式返回具体的优化行动项，每个行动项以"- "开头。
                只返回行动项列表，不要有其他内容。
                """, conclusion);

        try {
            return ollamaService.generateText(prompt);
        } catch (Exception e) {
            log.warn("提取优化建议失败", e);
            return conclusion;
        }
    }

    /**
     * 应用优化
     */
    private List<Task> applyOptimization(List<Task> tasks, String optimizationSuggestion) {
        // 解析优化建议并应用
        List<Task> optimizedTasks = new ArrayList<>(tasks);

        // 这里可以添加具体的优化逻辑
        // 例如：合并相似任务、调整依赖关系、优化预估时间等

        log.debug("应用优化建议：{}", optimizationSuggestion);
        return optimizedTasks;
    }

    /**
     * 检查循环依赖
     */
    private boolean hasCircularDependency(List<Task> tasks) {
        Map<String, List<String>> graph = new HashMap<>();

        for (Task task : tasks) {
            graph.put(task.getTaskId(),
                    task.getDependencies() != null ? new ArrayList<>(task.getDependencies()) : new ArrayList<>());
        }

        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String taskId : graph.keySet()) {
            if (hasCycle(taskId, graph, visited, recursionStack)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasCycle(String taskId, Map<String, List<String>> graph,
                             Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(taskId)) return true;
        if (visited.contains(taskId)) return false;

        visited.add(taskId);
        recursionStack.add(taskId);

        for (String neighbor : graph.get(taskId)) {
            if (hasCycle(neighbor, graph, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(taskId);
        return false;
    }

    private String generateCacheKey(Task task, IntentRecognitionModule.AgentIntent intent) {
        return String.format("%s_%s_%s",
                task.getTaskType(),
                task.getParameters().hashCode(),
                intent.getIntentType()
        );
    }

    private String generateSubtaskId(String parentTaskId, int index) {
        return String.format("%s_sub_%d_%s",
                parentTaskId, index, UUID.randomUUID().toString().substring(0, 4));
    }

    private String getAllTaskTypeDescriptions() {
        StringBuilder sb = new StringBuilder();
        for (TaskTypeEnum type : TaskTypeEnum.values()) {
            sb.append(String.format("- %s: %s\n", type.name(), type.getDescription()));
        }
        return sb.toString();
    }
}
