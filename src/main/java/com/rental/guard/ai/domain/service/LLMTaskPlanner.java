/**
 * @author qkcao
 * @date 2026/1/23 18:15
 */
package com.rental.guard.ai.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.guard.ai.domain.dto.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * LLM增强的任务规划器（主类）
 */
@Component
@Slf4j
public class LLMTaskPlanner {
    @Autowired
    private LLMService llmService;
    private final OllamaService ollamaService;
    private final LLMTaskDecomposer llmTaskDecomposer;
    private final LLMStrategySelector llmStrategySelector;
    private final LLMOptimizer llmOptimizer;
    private final LLMExceptionHandler llmExceptionHandler;
    private final DependencyResolver dependencyResolver;
    private final ResourceAllocator resourceAllocator;
    private final TaskFactory taskFactory;

    private final TaskPlanner fallbackPlanner; // 回退规划器

    public LLMTaskPlanner(OllamaService ollamaService,
                          LLMTaskDecomposer llmTaskDecomposer,
                          LLMStrategySelector llmStrategySelector,
                          LLMOptimizer llmOptimizer,
                          LLMExceptionHandler llmExceptionHandler,
                          DependencyResolver dependencyResolver,
                          ResourceAllocator resourceAllocator,
                          TaskFactory taskFactory,
                          TaskPlanner fallbackPlanner) {
        this.ollamaService = ollamaService;
        this.llmTaskDecomposer = llmTaskDecomposer;
        this.llmStrategySelector = llmStrategySelector;
        this.llmOptimizer = llmOptimizer;
        this.llmExceptionHandler = llmExceptionHandler;
        this.dependencyResolver = dependencyResolver;
        this.resourceAllocator = resourceAllocator;
        this.taskFactory = taskFactory;
        this.fallbackPlanner = fallbackPlanner;
    }

    /**
     * LLM增强的智能任务规划
     */
    public TaskPlan planWithLLM(IntentRecognitionModule.AgentIntent intent) {
        log.info("开始LLM增强的任务规划，意图: {}", intent.getIntentType());

        long startTime = System.currentTimeMillis();

        try {
            // 1. 验证意图并评估复杂度
            PlanningComplexity complexity = evaluatePlanningComplexity(intent);

            // 2. 选择规划模式
            PlanningMode mode = selectPlanningMode(intent, complexity);

            // 3. 执行规划
            TaskPlan plan;

            switch (mode) {
                case FULL_LLM:
                    plan = executeFullLLMPlanning(intent, complexity);
                    break;
                case HYBRID:
                    plan = executeHybridPlanning(intent, complexity);
                    break;
                case FALLBACK:
                    plan = executeFallbackPlanning(intent);
                    break;
                default:
                    plan = executeHybridPlanning(intent, complexity);
            }

            // 4. 生成计划解释
            String explanation = generatePlanExplanation(plan);
            plan.getMetadata().put("llm_explanation", explanation);

            // 5. 验证和优化计划
            plan = validateAndOptimizePlan(plan);

            long endTime = System.currentTimeMillis();
            log.info("LLM规划完成，模式: {}，耗时: {}ms，任务数: {}",
                    mode, endTime - startTime, plan.getTasks().size());

            return plan;

        } catch (Exception e) {
            log.error("LLM规划失败，使用回退规划器", e);
            return fallbackPlanner.plan(intent);
        }
    }

    /**
     * 评估规划复杂度
     */
    private PlanningComplexity evaluatePlanningComplexity(IntentRecognitionModule.AgentIntent intent) {
        String prompt = String.format("""
                    请评估以下用户请求的任务规划复杂度：
                                
                    意图类型：%s
                    实体：%s
                    参数：%s
                    置信度：%.2f
                    优先级：%s
                                
                    请从以下维度评估（1-5分，5为最复杂）：
                    1. 数据复杂性（需要处理的数据量和多样性）
                    2. 逻辑复杂性（业务流程的复杂度）
                    3. 风险等级（防欺诈风险控制需求）
                    4. 实时性要求（是否需要实时处理）
                    5. 资源需求（计算、内存、网络等）
                                
                    【重要】请严格按照以下JSON格式返回，不要添加额外说明：
                    {
                      "dataComplexity": 分数,
                      "logicComplexity": 分数,
                      "riskLevel": 分数,
                      "realtimeRequirement": 分数,
                      "resourceRequirement": 分数,
                      "overallScore": 分数,
                      "reason": "简要理由"
                    }
                    只返回JSON，不要有其他内容。
                    """,
                intent.getIntentType(),
                intent.getEntities(),
                intent.getParameters(),
                intent.getConfidence(),
                intent.getPriority()
        );

        try {
            String response = llmService.generate(prompt);
            return parseComplexityResponse(response);
        } catch (Exception e) {
            log.warn("LLM复杂度评估失败，使用默认评估", e);
            return createDefaultComplexity(intent);
        }
    }

    /**
     * 选择规划模式
     */
    private PlanningMode selectPlanningMode(IntentRecognitionModule.AgentIntent intent,
                                            PlanningComplexity complexity) {

        // 基于复杂度和置信度选择模式
        if (complexity.getOverallScore() >= 4 && intent.getConfidence() >= 0.8) {
            log.debug("高复杂度高置信度，使用完整LLM规划");
            return PlanningMode.FULL_LLM;
        } else if (complexity.getOverallScore() >= 3) {
            log.debug("中等复杂度，使用混合规划");
            return PlanningMode.HYBRID;
        } else if (intent.getConfidence() < 0.6) {
            log.debug("低置信度，使用回退规划");
            return PlanningMode.FALLBACK;
        } else {
            log.debug("低复杂度，使用混合规划");
            return PlanningMode.HYBRID;
        }
    }

    /**
     * 执行完整LLM规划
     */
    private TaskPlan executeFullLLMPlanning(IntentRecognitionModule.AgentIntent intent,
                                                        PlanningComplexity complexity) {

        // 1. 使用LLM生成完整任务序列
        List<Task> tasks = generateTasksWithLLM(intent);

        // 2. 使用LLM选择策略
        PlanningConstraints constraints =
                extractPlanningConstraints(intent);
        PlanningStrategyEnum strategy =
                llmStrategySelector.selectOptimalStrategy(intent, constraints, tasks);

        // 3. 使用LLM优化
       List<Task> optimizedTasks = llmOptimizer.optimizeWithLLM(tasks, strategy);

        // 4. 解析依赖关系
        List<Task> tasksWithDeps = dependencyResolver.resolve(optimizedTasks);

        // 5. 分配资源
        List<Task> finalTasks = resourceAllocator.allocate(tasksWithDeps);

        // 6. 创建计划
        return buildTaskPlan(intent, finalTasks, strategy);
    }

    /**
     * 执行混合规划
     */
    private TaskPlan executeHybridPlanning(IntentRecognitionModule.AgentIntent intent,
                                                       PlanningComplexity complexity) {

        // 1. 使用规则生成初始任务
        List<Task> initialTasks = taskFactory.createTasks(intent,
                PlanningStrategyEnum.LINEAR);

        // 2. 使用LLM进行任务分解
        List<Task> decomposedTasks = new ArrayList<>();
        for (Task task : initialTasks) {
            if (shouldDecomposeWithLLM(task, complexity)) {
                decomposedTasks.addAll(llmTaskDecomposer.decomposeWithLLM(task, intent));
            } else {
                decomposedTasks.add(task);
            }
        }

        // 3. 使用规则进行依赖解析和优化
        List<Task> ruleOptimized = fallbackPlanner.plan(intent).getTasks();

        // 4. 使用LLM进行策略选择和最终优化
        PlanningConstraints constraints =
                extractPlanningConstraints(intent);
        PlanningStrategyEnum strategy =
                llmStrategySelector.selectOptimalStrategy(intent, constraints, decomposedTasks);

        List<Task> finalTasks = llmOptimizer.optimizeWithLLM(ruleOptimized, strategy);

        // 5. 创建计划
        return buildTaskPlan(intent, finalTasks, strategy);
    }

    /**
     * 执行回退规划
     */
    private TaskPlan executeFallbackPlanning(IntentRecognitionModule.AgentIntent intent) {
        return fallbackPlanner.plan(intent);
    }

    /**
     * 使用LLM生成任务序列
     */
    private List<Task> generateTasksWithLLM(IntentRecognitionModule.AgentIntent intent) {
        String prompt = buildTaskGenerationPrompt(intent);

        try {
            OllamaService.TaskDecompositionResponse response =
                    ollamaService.generateStructuredResponse(prompt,
                            OllamaService.TaskDecompositionResponse.class);

            // 转换为任务对象
            List<Task> tasks = new ArrayList<>();
            for (int i = 0; i < response.getSubtasks().size(); i++) {
                OllamaService.TaskDecompositionResponse.Subtask subtask = response.getSubtasks().get(i);

                Task task = Task.builder()
                        .taskId("LLM_TASK_" + System.currentTimeMillis() + "_" + i)
                        .taskType(TaskTypeEnum.fromName(subtask.getTask_type()))
                        .name(subtask.getName())
                        .description(subtask.getDescription())
                        .parameters(subtask.getParameters() != null ?
                                subtask.getParameters() : new HashMap<>())
                        .estimatedDuration(subtask.getEstimated_duration())
                        .priority(intent.getPriority().ordinal())
                        .maxRetries(3)
                        .timeout(subtask.getEstimated_duration() != null ?
                                subtask.getEstimated_duration() * 2 : 60)
                        .build();

                tasks.add(task);
            }

            // 设置依赖关系
            setLLMDependencies(tasks, response.getDependencies());

            return tasks;

        } catch (Exception e) {
            log.error("LLM任务生成失败", e);
            throw new PlanningException("LLM任务生成失败: " + e.getMessage());
        }
    }

    /**
     * 生成计划解释
     */
    private String generatePlanExplanation(TaskPlan plan) {
        String prompt = String.format("""
                        请为以下任务计划生成自然语言解释，面向非技术用户：
                                    
                        计划概述：
                        计划ID：%s
                        意图：%s
                        任务数量：%d
                        执行策略：%s
                        预估总时间：%d秒
                                    
                        任务列表：
                        %s
                                    
                        请解释：
                        1. 这个计划要完成什么目标？
                        2. 主要分为哪些步骤？
                        3. 预计需要多长时间？
                        4. 需要注意什么风险？
                        5. 用户可以得到什么结果？
                                    
                        请使用友好、清晰的语言，避免技术术语。
                        """,
                plan.getPlanId(),
                plan.getOriginalIntent().getIntentType(),
                plan.getTasks().size(),
                plan.getStrategy(),
                plan.getTasks().stream()
                        .filter(t -> t.getEstimatedDuration() != null)
                        .mapToInt(Task::getEstimatedDuration)
                        .sum(),
                summarizeTasksForExplanation(plan.getTasks())
        );

        try {
            return ollamaService.generateText(prompt);
        } catch (Exception e) {
            log.warn("生成计划解释失败", e);
            return "这是一个自动生成的任务计划，包含" + plan.getTasks().size() + "个步骤。";
        }
    }

    /**
     * 验证和优化计划
     */
    private TaskPlan validateAndOptimizePlan(TaskPlan plan) {
        // 验证计划完整性
        TaskPlan.ValidationResult validation = plan.validate();
        if (!validation.isValid()) {
            log.warn("计划验证失败: {}", validation.getErrors());

            // 尝试使用LLM修复
            try {
                plan = attemptPlanRepair(plan, validation);
            } catch (Exception e) {
                log.error("计划修复失败", e);
                throw new PlanningException("计划验证失败且无法修复: " + validation.getErrors());
            }
        }

        // 计算关键路径
        List<Task> criticalPath = plan.getCriticalPathTasks();
        plan.getMetadata().put("critical_path",
                criticalPath.stream().map(Task::getTaskId).collect(Collectors.toList()));

        // 更新统计信息
        plan.updateStatistics();

        return plan;
    }

    /**
     * 尝试修复计划
     */
    private TaskPlan attemptPlanRepair(TaskPlan plan,
                                                   TaskPlan.ValidationResult validation) {

        String prompt = String.format("""
                        以下任务计划存在验证问题，请帮助修复：
                                    
                        计划信息：
                        计划ID：%s
                        意图：%s
                        任务数：%d
                                    
                        验证错误：
                        %s
                                    
                        验证警告：
                        %s
                                    
                        当前任务依赖关系：
                        %s
                                    
                        请提出具体的修复建议，确保：
                        1. 消除所有验证错误
                        2. 保持计划的完整性
                        3. 优化执行效率
                        4. 考虑防欺诈系统的特殊要求
                                    
                        请给出修复后的任务依赖关系。
                        """,
                plan.getPlanId(),
                plan.getOriginalIntent().getIntentType(),
                plan.getTasks().size(),
                String.join("\n", validation.getErrors()),
                String.join("\n", validation.getWarnings()),
                buildDependencyDescription(plan.getTasks())
        );

        String repairAdvice = ollamaService.generateText(prompt);
        log.info("收到计划修复建议：{}", repairAdvice);

        // 应用修复（这里简化处理）
        // 实际实现中需要解析建议并修改计划

        return plan;
    }

    // 辅助方法
    private boolean shouldDecomposeWithLLM(Task task, PlanningComplexity complexity) {
        return complexity.getOverallScore() >= 3 ||
                task.getTaskType().isCompositeType() ||
                (task.getEstimatedDuration() != null && task.getEstimatedDuration() > 60);
    }

    private PlanningConstraints extractPlanningConstraints(
            IntentRecognitionModule.AgentIntent intent) {

        PlanningConstraints constraints =
                new PlanningConstraints();

        constraints.setTimeCritical(intent.getPriority() == IntentRecognitionModule.Priority.HIGH);
        constraints.setRequiresHighAvailability(
                intent.getIntentType() == IntentTypeEnum.REAL_TIME_MONITORING);

        return constraints;
    }

    private TaskPlan buildTaskPlan(IntentRecognitionModule.AgentIntent intent,
                                               List<Task> tasks,
                                               PlanningStrategyEnum strategy) {

        String planId = "LLM_PLAN_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8);

        TaskPlan plan = TaskPlan.builder()
                .planId(planId)
                .originalIntent(intent)
                .tasks(tasks)
                .strategy(strategy)
                .status(PlanStatusEnum.READY)
                .createTime(LocalDateTime.now())
                .priority(intent.getPriority().ordinal())
                .metadata(new HashMap<>())
                .build();

        plan.getMetadata().put("planning_mode", "LLM_ENHANCED");
        plan.getMetadata().put("llm_confidence", intent.getConfidence());

        return plan;
    }

    private void setLLMDependencies(List<Task> tasks, List<List<String>> dependencies) {
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
                if (task != null) {
                    if (task.getDependencies() == null) {
                        task.setDependencies(new ArrayList<>());
                    }
                    task.getDependencies().add(depId);
                }
            }
        }
    }

    private String summarizeTasksForExplanation(List<Task> tasks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, tasks.size()); i++) {
            Task task = tasks.get(i);
            sb.append(String.format("%d. %s (%s)\n",
                    i + 1, task.getName(), task.getDescription()));
        }

        if (tasks.size() > 5) {
            sb.append("... 还有").append(tasks.size() - 5).append("个步骤\n");
        }

        return sb.toString();
    }

    private String buildDependencyDescription(List<Task> tasks) {
        StringBuilder sb = new StringBuilder();
        for (Task task : tasks) {
            sb.append(task.getTaskId()).append(" [").append(task.getTaskType()).append("]");
            if (task.getDependencies() != null && !task.getDependencies().isEmpty()) {
                sb.append(" 依赖 -> ").append(String.join(", ", task.getDependencies()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildTaskGenerationPrompt(IntentRecognitionModule.AgentIntent intent) {
        return String.format("""
                    请为以下防欺诈系统请求生成详细的任务序列：
                                
                    用户意图：%s
                    实体：%s
                    参数：%s
                    优先级：%s
                    置信度：%.2f
                                
                    请严格按照以下JSON格式返回，不要添加任何额外说明或注释：
                    {
                      "subtasks": [
                        {
                          "task_type": "数据收集|验证|分析|风险评估|报告|告警|控制",
                          "name": "任务名称",
                          "description": "任务描述",
                          "parameters": {
                            "param1": "value1",
                            "param2": "value2"
                          },
                          "estimated_duration": 预估时间（秒）
                        }
                      ],
                      "dependencies": [
                        [子任务索引, 依赖的子任务索引],
                        [1, 0]
                      ],
                      "reasoning": "任务序列的设计理由"
                    }
                    
                    要求：
                    1. 任务类型必须从以下选项中选择：数据收集、数据验证、风险分析、模式检测、评分计算、报告生成、告警触发、人工审核、控制措施
                    2. 每个子任务必须包含完整的参数信息
                    3. 依赖关系使用数组索引表示
                    4. 预估时间以秒为单位
                    
                    只返回JSON，不要有其他任何内容！
                    """,
                intent.getIntentType(),
                intent.getEntities(),
                intent.getParameters(),
                intent.getPriority(),
                intent.getConfidence()
        );
    }


    private PlanningComplexity parseComplexityResponse(String response) {
        PlanningComplexity complexity = new PlanningComplexity();

        try {
            // 清理响应，提取JSON部分
            String jsonStr = extractJsonFromResponse(response);

            // 方法1：直接使用JsonNode解析，避免类型转换问题
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonStr);

            // 使用四舍五入处理浮点数
            complexity.setDataComplexity(root.has("dataComplexity") ?
                    (int) Math.round(root.get("dataComplexity").asDouble()) : null);
            complexity.setLogicComplexity(root.has("logicComplexity") ?
                    (int) Math.round(root.get("logicComplexity").asDouble()) : null);
            complexity.setRiskLevel(root.has("riskLevel") ?
                    (int) Math.round(root.get("riskLevel").asDouble()) : null);
            complexity.setRealtimeRequirement(root.has("realtimeRequirement") ?
                    (int) Math.round(root.get("realtimeRequirement").asDouble()) : null);
            complexity.setResourceRequirement(root.has("resourceRequirement") ?
                    (int) Math.round(root.get("resourceRequirement").asDouble()) : null);
            complexity.setOverallScore(root.has("overallScore") ?
                    (int) Math.round(root.get("overallScore").asDouble()) : null);

        } catch (Exception e) {
            log.warn("解析复杂度响应失败", e);
        }

        // 确保有默认值
        if (complexity.getOverallScore() == null) {
            complexity.setOverallScore(3);
        }

        return complexity;
    }

    // 从响应中提取JSON字符串
    private String extractJsonFromResponse(String response) {
        // 方法1：直接查找 { 和 } 之间的内容
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');

        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }

        // 方法2：使用正则表达式提取JSON
        Pattern pattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group();
        }

        // 如果都失败，返回原始响应
        return response.trim();
    }

    // 保留原来的自然语言解析方法作为备用
    private PlanningComplexity parseNaturalLanguageResponse(String response) {
        PlanningComplexity complexity = new PlanningComplexity();

        try {
            String[] lines = response.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.contains("数据复杂性") && line.contains(":")) {
                    complexity.setDataComplexity(extractScore(line));
                } else if (line.contains("逻辑复杂性") && line.contains(":")) {
                    complexity.setLogicComplexity(extractScore(line));
                } else if (line.contains("风险等级") && line.contains(":")) {
                    complexity.setRiskLevel(extractScore(line));
                } else if (line.contains("实时性要求") && line.contains(":")) {
                    complexity.setRealtimeRequirement(extractScore(line));
                } else if (line.contains("资源需求") && line.contains(":")) {
                    complexity.setResourceRequirement(extractScore(line));
                } else if (line.contains("总体") || line.contains("Overall") || line.contains("综合")) {
                    complexity.setOverallScore(extractScore(line));
                }
            }
        } catch (Exception e) {
            log.warn("自然语言解析失败", e);
        }

        return complexity;
    }

    private Integer extractScore(String line) {
        try {
            // 提取数字
            String[] parts = line.split("[：:]");
            if (parts.length > 1) {
                String scoreStr = parts[1].trim();
                // 提取第一个数字
                for (char c : scoreStr.toCharArray()) {
                    if (Character.isDigit(c)) {
                        return Integer.parseInt(String.valueOf(c));
                    }
                }
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return 3; // 默认值
    }

    private PlanningComplexity createDefaultComplexity(IntentRecognitionModule.AgentIntent intent) {
        PlanningComplexity complexity = new PlanningComplexity();

        // 基于意图类型设置默认复杂度
        switch (intent.getIntentType()) {
            case USER_INVESTIGATION:
                complexity.setOverallScore(5);
                break;
            case REAL_TIME_MONITORING:
                complexity.setOverallScore(4);
                break;
            case SINGLE_ANALYSIS:
            case REPORT_GENERATION:
                complexity.setOverallScore(3);
                break;
            default:
                complexity.setOverallScore(2);
        }

        return complexity;
    }

    // 枚举和内部类
    private enum PlanningMode {
        FULL_LLM,      // 完全使用LLM规划
        HYBRID,        // 混合规划（规则+LLM）
        FALLBACK       // 回退到规则规划
    }

    @Data
    private static class PlanningComplexity {
        private Integer dataComplexity;      // 数据复杂性 1-5
        private Integer logicComplexity;     // 逻辑复杂性 1-5
        private Integer riskLevel;          // 风险等级 1-5
        private Integer realtimeRequirement; // 实时性要求 1-5
        private Integer resourceRequirement; // 资源需求 1-5
        private Integer overallScore;       // 总体评分 1-5
    }
}
