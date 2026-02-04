/**
 * @author qkcao
 * @date 2026/1/23 18:32
 */
package com.rental.guard.ai.domain.service;


import com.rental.guard.ai.domain.dto.Task;
import com.rental.guard.ai.domain.dto.TaskTypeEnum;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.rental.guard.ai.domain.service.RuleBasedTaskDecomposer.DependencyType.*;

@Component
@Slf4j
public class RuleBasedTaskDecomposer {

        // 任务分解规则库
        private final Map<TaskTypeEnum, DecompositionRule> decompositionRules;

        // 参数提取规则
        private final Map<String, ParameterExtractionRule> parameterExtractionRules;

        // 依赖关系规则
        private final Map<TaskTypeEnum, List<DependencyRule>> dependencyRules;

        public RuleBasedTaskDecomposer() {
            this.decompositionRules = initializeDecompositionRules();
            this.parameterExtractionRules = initializeParameterExtractionRules();
            this.dependencyRules = initializeDependencyRules();
            log.info("规则基础任务分解器初始化完成，加载{}条分解规则", decompositionRules.size());
        }

        /**
         * 基于规则的任务分解
         */
        public List<Task> decompose(List<Task> tasks) {
            log.debug("开始基于规则的任务分解，输入任务数: {}", tasks.size());

            List<Task> decomposedTasks = new ArrayList<>();

            for (Task task : tasks) {
                // 检查是否需要分解
                if (shouldDecompose(task)) {
                    List<Task> subtasks = decomposeTask(task);
                    decomposedTasks.addAll(subtasks);
                    log.debug("任务{}分解为{}个子任务", task.getTaskId(), subtasks.size());
                } else {
                    decomposedTasks.add(task);
                }
            }

            // 解析子任务间的依赖关系
            decomposedTasks = resolveDependencies(decomposedTasks);

            log.debug("规则分解完成，输出任务数: {}", decomposedTasks.size());
            return decomposedTasks;
        }

        /**
         * 分解单个任务
         */
        private List<Task> decomposeTask(Task parentTask) {
            TaskTypeEnum taskType = parentTask.getTaskType();

            // 查找匹配的分解规则
            DecompositionRule rule = decompositionRules.get(taskType);

            if (rule == null) {
                log.warn("没有找到任务类型{}的分解规则", taskType);
                return Collections.singletonList(parentTask);
            }

            // 应用分解规则
            List<Task> subtasks = applyDecompositionRule(rule, parentTask);

            // 设置父子关系
            for (Task subtask : subtasks) {
                initializeSubtask(subtask, parentTask);
            }

            return subtasks;
        }

        /**
         * 检查任务是否需要分解
         */
        private boolean shouldDecompose(Task task) {
            // 根据任务类型判断是否需要分解
            if (task.getTaskType().isCompositeType()) {
                return true;
            }

            // 特殊任务类型需要分解
            Set<TaskTypeEnum> decomposableTypes = Set.of(
                    TaskTypeEnum.USER_INVESTIGATION,
                    TaskTypeEnum.COMPREHENSIVE_INVESTIGATION,
                    TaskTypeEnum.TARGETED_INVESTIGATION,
                    TaskTypeEnum.BATCH_ANALYSIS,
                    TaskTypeEnum.REAL_TIME_MONITORING,
                    TaskTypeEnum.BATCH_REPORT_GENERATION,
                    TaskTypeEnum.COMPOSITE_ANALYSIS,
                    TaskTypeEnum.COMPOSITE_MONITORING,
                    TaskTypeEnum.COMPOSITE_REPORTING
            );

            if (decomposableTypes.contains(task.getTaskType())) {
                return true;
            }

            // 根据预估时间判断
            if (task.getEstimatedDuration() != null && task.getEstimatedDuration() > 60) {
                return true;
            }

            // 检查参数复杂度
            if (isComplexParameters(task.getParameters())) {
                return true;
            }

            return false;
        }

        /**
         * 检查参数复杂度
         */
        private boolean isComplexParameters(Map<String, Object> parameters) {
            if (parameters == null || parameters.isEmpty()) {
                return false;
            }

            // 多个实体或多个条件
            int complexityScore = 0;

            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                Object value = entry.getValue();

                if (value instanceof Collection) {
                    complexityScore += ((Collection<?>) value).size();
                } else if (value instanceof Map) {
                    complexityScore += ((Map<?, ?>) value).size();
                } else if (value instanceof String) {
                    String strValue = (String) value;
                    if (strValue.contains(",") || strValue.contains(";")) {
                        complexityScore += 2;
                    }
                }
            }

            return complexityScore >= 3;
        }

        /**
         * 应用分解规则
         */
        private List<Task> applyDecompositionRule(DecompositionRule rule,
                                                              Task parentTask) {
            List<Task> subtasks = new ArrayList<>();

            for (TaskTemplate template : rule.getTemplates()) {
                Task subtask = createSubtaskFromTemplate(template, parentTask);

                // 提取和设置参数
                Map<String, Object> extractedParams = extractParameters(parentTask, template);
                if (!extractedParams.isEmpty()) {
                    subtask.getParameters().putAll(extractedParams);
                }

                subtasks.add(subtask);
            }

            return subtasks;
        }

        /**
         * 从模板创建子任务
         */
        private Task createSubtaskFromTemplate(TaskTemplate template,
                                                           Task parentTask) {
            return Task.builder()
                    .taskId(generateSubtaskId(parentTask.getTaskId(), template.getOrder()))
                    .taskType(template.getTaskType())
                    .name(template.getName())
                    .description(template.getDescription())
                    .parameters(new HashMap<>(template.getDefaultParameters()))
                    .priority(parentTask.getPriority())
                    .estimatedDuration(template.getEstimatedDuration())
                    .timeout(template.getTimeout())
                    .maxRetries(template.getMaxRetries())
                    .requiredTools(new ArrayList<>(template.getRequiredTools()))
                    .maxConcurrent(template.getMaxConcurrent())
                    .atomic(template.isAtomic())
                    .build();
        }

        /**
         * 提取参数
         */
        private Map<String, Object> extractParameters(Task parentTask,
                                                      TaskTemplate template) {
            Map<String, Object> extractedParams = new HashMap<>();

            for (ParameterMapping mapping : template.getParameterMappings()) {
                String sourceParam = mapping.getSourceParameter();
                String targetParam = mapping.getTargetParameter();

                if (parentTask.getParameters() != null &&
                        parentTask.getParameters().containsKey(sourceParam)) {

                    Object value = parentTask.getParameters().get(sourceParam);

                    // 应用转换规则
                    if (mapping.getTransformation() != null) {
                        value = applyTransformation(value, mapping.getTransformation());
                    }

                    extractedParams.put(targetParam, value);
                } else if (mapping.getDefaultValue() != null) {
                    // 使用默认值
                    extractedParams.put(targetParam, mapping.getDefaultValue());
                }
            }

            // 应用参数提取规则
            ParameterExtractionRule extractionRule = parameterExtractionRules.get(
                    template.getTaskType().name());

            if (extractionRule != null) {
                extractedParams.putAll(extractionRule.extract(parentTask.getParameters()));
            }

            return extractedParams;
        }

        /**
         * 应用参数转换
         */
        private Object applyTransformation(Object value, String transformation) {
            if (value == null) {
                return null;
            }

            switch (transformation) {
                case "TO_LIST":
                    if (value instanceof String) {
                        String strValue = (String) value;
                        if (strValue.contains(",")) {
                            return Arrays.asList(strValue.split(","));
                        } else if (strValue.contains(";")) {
                            return Arrays.asList(strValue.split(";"));
                        } else {
                            return Collections.singletonList(strValue);
                        }
                    }
                    break;

                case "TO_LOWER_CASE":
                    if (value instanceof String) {
                        return ((String) value).toLowerCase();
                    }
                    break;

                case "TO_UPPER_CASE":
                    if (value instanceof String) {
                        return ((String) value).toUpperCase();
                    }
                    break;

                case "TRIM":
                    if (value instanceof String) {
                        return ((String) value).trim();
                    }
                    break;

                case "PARSE_INT":
                    if (value instanceof String) {
                        try {
                            return Integer.parseInt((String) value);
                        } catch (NumberFormatException e) {
                            log.warn("解析整数失败: {}", value);
                        }
                    }
                    break;
            }

            return value;
        }

        /**
         * 初始化子任务
         */
        private void initializeSubtask(Task subtask, Task parentTask) {
            if (subtask.getMetadata() == null) {
                subtask.setMetadata(new HashMap<>());
            }

            subtask.getMetadata().put("parentTaskId", parentTask.getTaskId());
            subtask.getMetadata().put("parentTaskType", parentTask.getTaskType().name());
            subtask.getMetadata().put("decompositionMethod", "RULE_BASED");

            // 继承父任务的业务属性
            if (parentTask.getBusinessType() != null) {
                subtask.setBusinessType(parentTask.getBusinessType());
            }
            if (parentTask.getTenantId() != null) {
                subtask.setTenantId(parentTask.getTenantId());
            }
            if (parentTask.getProjectId() != null) {
                subtask.setProjectId(parentTask.getProjectId());
            }
        }

        /**
         * 解析任务依赖关系
         */
        private List<Task> resolveDependencies(List<Task> tasks) {
            // 构建任务ID到任务的映射
            Map<String, Task> taskMap = new HashMap<>();
            for (Task task : tasks) {
                taskMap.put(task.getTaskId(), task);
            }

            // 应用依赖规则
            for (Task task : tasks) {
                List<DependencyRule> rules = dependencyRules.get(task.getTaskType());

                if (rules != null) {
                    List<String> dependencies = new ArrayList<>();

                    for (DependencyRule rule : rules) {
                        String dependencyTaskId = findDependencyTaskId(rule, task, taskMap);
                        if (dependencyTaskId != null && !dependencies.contains(dependencyTaskId)) {
                            dependencies.add(dependencyTaskId);
                        }
                    }

                    if (!dependencies.isEmpty()) {
                        task.setDependencies(dependencies);
                    }
                }
            }

            return tasks;
        }

        /**
         * 查找依赖任务ID
         */
        private String findDependencyTaskId(DependencyRule rule, Task task,
                                            Map<String, Task> taskMap) {

            switch (rule.getDependencyType()) {
                case PARAMETER_MATCH:
                    return findTaskByParameterMatch(rule, task, taskMap);

                case TASK_TYPE_MATCH:
                    return findTaskByTypeMatch(rule, task, taskMap);

                case PARENT_CHILD:
                    return findParentTask(task, taskMap);

                case SEQUENTIAL:
                    return findPreviousTaskInSequence(rule, task, taskMap);

                case DATA_FLOW:
                    return findDataProducerTask(rule, task, taskMap);

                default:
                    return null;
            }
        }

        /**
         * 根据参数匹配查找任务
         */
        private String findTaskByParameterMatch(DependencyRule rule, Task task,
                                                Map<String, Task> taskMap) {

            if (rule.getMatchCondition() == null) {
                return null;
            }

            for (Task candidate : taskMap.values()) {
                if (candidate.getTaskId().equals(task.getTaskId())) {
                    continue; // 跳过自己
                }

                if (matchesCondition(candidate, rule.getMatchCondition())) {
                    return candidate.getTaskId();
                }
            }

            return null;
        }

        /**
         * 根据任务类型匹配查找任务
         */
        private String findTaskByTypeMatch(DependencyRule rule, Task task,
                                           Map<String, Task> taskMap) {

            if (rule.getRequiredTaskType() == null) {
                return null;
            }

            for (Task candidate : taskMap.values()) {
                if (candidate.getTaskId().equals(task.getTaskId())) {
                    continue;
                }

                if (candidate.getTaskType() == rule.getRequiredTaskType()) {
                    // 检查额外条件
                    if (matchesCondition(candidate, rule.getMatchCondition())) {
                        return candidate.getTaskId();
                    }
                }
            }

            return null;
        }

        /**
         * 查找父任务
         */
        private String findParentTask(Task task, Map<String, Task> taskMap) {
            if (task.getMetadata() != null && task.getMetadata().containsKey("parentTaskId")) {
                String parentId = (String) task.getMetadata().get("parentTaskId");
                return parentId;
            }
            return null;
        }

        /**
         * 查找序列中的前一个任务
         */
        private String findPreviousTaskInSequence(DependencyRule rule, Task task,
                                                  Map<String, Task> taskMap) {
            // 这里简化处理，实际中需要更复杂的序列识别逻辑
            return null;
        }

        /**
         * 查找数据生产者任务
         */
        private String findDataProducerTask(DependencyRule rule, Task task,
                                            Map<String, Task> taskMap) {
            // 这里简化处理，实际中需要数据流分析
            return null;
        }

        /**
         * 检查任务是否匹配条件
         */
        private boolean matchesCondition(Task task, Map<String, Object> condition) {
            if (condition == null || condition.isEmpty()) {
                return true;
            }

            for (Map.Entry<String, Object> entry : condition.entrySet()) {
                String paramName = entry.getKey();
                Object expectedValue = entry.getValue();

                if (task.getParameters() == null ||
                        !task.getParameters().containsKey(paramName)) {
                    return false;
                }

                Object actualValue = task.getParameters().get(paramName);

                if (!Objects.equals(actualValue, expectedValue)) {
                    // 尝试字符串比较
                    if (actualValue instanceof String && expectedValue instanceof String) {
                        if (!((String) actualValue).equalsIgnoreCase((String) expectedValue)) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }

            return true;
        }

        /**
         * 生成子任务ID
         */
        private String generateSubtaskId(String parentTaskId, int order) {
            return parentTaskId + "_SUB_" + order + "_" +
                    System.currentTimeMillis() % 10000;
        }

        // ==================== 规则初始化方法 ====================

        /**
         * 初始化分解规则
         */
        private Map<TaskTypeEnum, DecompositionRule> initializeDecompositionRules() {
            Map<TaskTypeEnum, DecompositionRule> rules = new HashMap<>();

            // 用户调查任务分解规则
            rules.put(TaskTypeEnum.USER_INVESTIGATION, createUserInvestigationRule());

            // 批量分析任务分解规则
            rules.put(TaskTypeEnum.BATCH_ANALYSIS, createBatchAnalysisRule());

            // 实时监控任务分解规则
            rules.put(TaskTypeEnum.REAL_TIME_MONITORING, createRealTimeMonitoringRule());

            // 报告生成任务分解规则
            rules.put(TaskTypeEnum.BATCH_REPORT_GENERATION, createReportGenerationRule());

            // 全面调查任务分解规则
            rules.put(TaskTypeEnum.COMPREHENSIVE_INVESTIGATION, createComprehensiveInvestigationRule());

            // 定向调查任务分解规则
            rules.put(TaskTypeEnum.TARGETED_INVESTIGATION, createTargetedInvestigationRule());

            // 复合分析任务分解规则
            rules.put(TaskTypeEnum.COMPOSITE_ANALYSIS, createCompositeAnalysisRule());

            // 复合监控任务分解规则
            rules.put(TaskTypeEnum.COMPOSITE_MONITORING, createCompositeMonitoringRule());

            // 复合报告任务分解规则
            rules.put(TaskTypeEnum.COMPOSITE_REPORTING, createCompositeReportingRule());

            return rules;
        }

        /**
         * 创建用户调查任务分解规则
         */
        private DecompositionRule createUserInvestigationRule() {
            List<TaskTemplate> templates = new ArrayList<>();

            // 1. 查询用户基本信息
            templates.add(TaskTemplate.builder()
                    .order(1)
                    .taskType(TaskTypeEnum.QUERY_USER_INFO)
                    .name("查询用户基本信息")
                    .description("获取用户注册信息、认证状态和基本资料")
                    .defaultParameters(Map.of("include_sensitive", false))
                    .parameterMappings(Arrays.asList(
                            ParameterMapping.builder()
                                    .sourceParameter("user_id")
                                    .targetParameter("user_id")
                                    .build(),
                            ParameterMapping.builder()
                                    .sourceParameter("include_historical")
                                    .targetParameter("include_history")
                                    .defaultValue(true)
                                    .build()
                    ))
                    .estimatedDuration(10)
                    .timeout(30)
                    .maxRetries(2)
                    .requiredTools(Arrays.asList("UserQueryService"))
                    .atomic(true)
                    .build());

            // 2. 查询聊天记录
            templates.add(TaskTemplate.builder()
                    .order(2)
                    .taskType(TaskTypeEnum.QUERY_CHAT_HISTORY)
                    .name("查询用户聊天记录")
                    .description("获取用户近期的聊天记录和历史对话")
                    .defaultParameters(Map.of("include_deleted", false))
                    .parameterMappings(Arrays.asList(
                            ParameterMapping.builder()
                                    .sourceParameter("user_id")
                                    .targetParameter("user_id")
                                    .build(),
                            ParameterMapping.builder()
                                    .sourceParameter("time_range")
                                    .targetParameter("time_range")
                                    .defaultValue("最近30天")
                                    .build()
                    ))
                    .estimatedDuration(15)
                    .timeout(45)
                    .maxRetries(2)
                    .requiredTools(Arrays.asList("ChatQueryService"))
                    .maxConcurrent(3)
                    .atomic(true)
                    .build());

            // 3. 查询交易记录
            templates.add(TaskTemplate.builder()
                    .order(3)
                    .taskType(TaskTypeEnum.QUERY_TRANSACTION_HISTORY)
                    .name("查询交易记录")
                    .description("获取用户的支付、退款和财务交易记录")
                    .defaultParameters(new HashMap<>())
                    .parameterMappings(Arrays.asList(
                            ParameterMapping.builder()
                                    .sourceParameter("user_id")
                                    .targetParameter("user_id")
                                    .build(),
                            ParameterMapping.builder()
                                    .sourceParameter("time_range")
                                    .targetParameter("time_range")
                                    .defaultValue("最近90天")
                                    .build()
                    ))
                    .estimatedDuration(12)
                    .timeout(40)
                    .maxRetries(2)
                    .requiredTools(Arrays.asList("TransactionService"))
                    .atomic(true)
                    .build());

            // 4. 风险分析
            templates.add(TaskTemplate.builder()
                    .order(4)
                    .taskType(TaskTypeEnum.RISK_ANALYSIS)
                    .name("综合风险分析")
                    .description("综合分析用户的行为模式和风险指标")
                    .defaultParameters(Map.of("analysis_level", "standard"))
                    .parameterMappings(Arrays.asList(
                            ParameterMapping.builder()
                                    .sourceParameter("user_id")
                                    .targetParameter("user_id")
                                    .build(),
                            ParameterMapping.builder()
                                    .sourceParameter("risk_threshold")
                                    .targetParameter("threshold")
                                    .defaultValue(0.7)
                                    .build()
                    ))
                    .estimatedDuration(20)
                    .timeout(60)
                    .maxRetries(1)
                    .requiredTools(Arrays.asList("RiskAnalyzer", "PatternMatcher"))
                    .atomic(true)
                    .build());

            // 5. 生成调查报告
            templates.add(TaskTemplate.builder()
                    .order(5)
                    .taskType(TaskTypeEnum.GENERATE_INVESTIGATION_REPORT)
                    .name("生成调查报告")
                    .description("生成详细的用户风险调查报告")
                    .defaultParameters(Map.of("format", "pdf", "include_recommendations", true))
                    .parameterMappings(Arrays.asList(
                            ParameterMapping.builder()
                                    .sourceParameter("user_id")
                                    .targetParameter("user_id")
                                    .build(),
                            ParameterMapping.builder()
                                    .sourceParameter("report_type")
                                    .targetParameter("report_type")
                                    .defaultValue("user_investigation")
                                    .build()
                    ))
                    .estimatedDuration(25)
                    .timeout(75)
                    .maxRetries(1)
                    .requiredTools(Arrays.asList("ReportGenerator"))
                    .atomic(true)
                    .build());

            return DecompositionRule.builder()
                    .taskType(TaskTypeEnum.USER_INVESTIGATION)
                    .name("用户调查分解规则")
                    .description("将用户调查任务分解为多个查询和分析步骤")
                    .templates(templates)
                    .build();
        }

        /**
         * 创建批量分析任务分解规则
         */
        private DecompositionRule createBatchAnalysisRule() {
            List<TaskTemplate> templates = new ArrayList<>();

            // 1. 准备批量数据
            templates.add(TaskTemplate.builder()
                    .order(1)
                    .taskType(TaskTypeEnum.DATA_PREPROCESSING)
                    .name("准备批量数据")
                    .description("准备需要分析的批量数据，包括数据验证和格式化")
                    .defaultParameters(Map.of("validation_level", "strict"))
                    .parameterMappings(Arrays.asList(
                            ParameterMapping.builder()
                                    .sourceParameter("data_source")
                                    .targetParameter("input_source")
                                    .build(),
                            ParameterMapping.builder()
                                    .sourceParameter("batch_size")
                                    .targetParameter("chunk_size")
                                    .defaultValue(100)
                                    .build()
                    ))
                    .estimatedDuration(30)
                    .timeout(90)
                    .maxRetries(2)
                    .requiredTools(Arrays.asList("DataPreprocessor", "Validator"))
                    .atomic(true)
                    .build());

            // 2. 批量分析子任务（根据batch_count动态生成）
            // 这里使用参数化模板
            templates.add(TaskTemplate.builder()
                    .order(2)
                    .taskType(TaskTypeEnum.BATCH_ANALYSIS_SUBTASK)
                    .name("批量分析子任务")
                    .description("执行单个批次的数据分析")
                    .defaultParameters(Map.of("batch_index", 0, "priority", "normal"))
                    .parameterMappings(Arrays.asList(
                            ParameterMapping.builder()
                                    .sourceParameter("analysis_type")
                                    .targetParameter("analysis_method")
                                    .defaultValue("standard")
                                    .build()
                    ))
                    .estimatedDuration(15)
                    .timeout(45)
                    .maxRetries(3)
                    .requiredTools(Arrays.asList("BatchAnalyzer"))
                    .maxConcurrent(5)
                    .atomic(true)
                    .build());

            // 3. 汇总分析结果
            templates.add(TaskTemplate.builder()
                    .order(3)
                    .taskType(TaskTypeEnum.DATA_VALIDATION)
                    .name("汇总分析结果")
                    .description("汇总和验证所有批次的分析结果")
                    .defaultParameters(Map.of("aggregation_method", "weighted_average"))
                    .parameterMappings(Collections.emptyList())
                    .estimatedDuration(20)
                    .timeout(60)
                    .maxRetries(2)
                    .requiredTools(Arrays.asList("ResultAggregator", "Validator"))
                    .atomic(true)
                    .build());

            return DecompositionRule.builder()
                    .taskType(TaskTypeEnum.BATCH_ANALYSIS)
                    .name("批量分析分解规则")
                    .description("将批量分析任务分解为数据准备、并行分析和结果汇总")
                    .templates(templates)
                    .dynamic(true) // 动态生成子任务
                    .build();
        }

        /**
         * 创建实时监控任务分解规则
         */
        private DecompositionRule createRealTimeMonitoringRule() {
            List<TaskTemplate> templates = new ArrayList<>();

            // 1. 初始化监控会话
            templates.add(TaskTemplate.builder()
                    .order(1)
                    .taskType(TaskTypeEnum.REAL_TIME_MONITORING)
                    .name("初始化监控会话")
                    .description("建立实时监控会话和配置监控参数")
                    .defaultParameters(Map.of(
                            "monitoring_mode", "continuous",
                            "alert_enabled", true,
                            "sampling_rate", 1.0
                    ))
                    .parameterMappings(Arrays.asList(
                            ParameterMapping.builder()
                                    .sourceParameter("monitor_targets")
                                    .targetParameter("targets")
                                    .transformation("TO_LIST")
                                    .build(),
                            ParameterMapping.builder()
                                    .sourceParameter("duration_minutes")
                                    .targetParameter("duration")
                                    .build()
                    ))
                    .estimatedDuration(5)
                    .timeout(15)
                    .maxRetries(1)
                    .requiredTools(Arrays.asList("MonitorSessionManager"))
                    .atomic(true)
                    .build());

            // 2. 启动数据流处理
            templates.add(TaskTemplate.builder()
                    .order(2)
                    .taskType(TaskTypeEnum.REAL_TIME_DATA_STREAMING)
                    .name("启动数据流处理")
                    .description("启动实时数据流处理管道")
                    .defaultParameters(Map.of("buffer_size", 1000, "parallelism", 2))
                    .parameterMappings(Collections.emptyList())
                    .estimatedDuration(10)
                    .timeout(30)
                    .maxRetries(2)
                    .requiredTools(Arrays.asList("StreamProcessor", "DataPipeline"))
                    .atomic(true)
                    .build());

            // 3. 配置实时规则引擎
            templates.add(TaskTemplate.builder()
                    .order(3)
                    .taskType(TaskTypeEnum.REAL_TIME_RULE_EXECUTION)
                    .name("配置实时规则引擎")
                    .description("配置和激活实时风险规则")
                    .defaultParameters(Map.of("rule_set", "realtime_fraud_detection"))
                    .parameterMappings(Arrays.asList(
                            ParameterMapping.builder()
                                    .sourceParameter("alert_threshold")
                                    .targetParameter("threshold")
                                    .defaultValue("medium")
                                    .build()
                    ))
                    .estimatedDuration(8)
                    .timeout(25)
                    .maxRetries(2)
                    .requiredTools(Arrays.asList("RuleEngine", "AlertManager"))
                    .atomic(true)
                    .build());

            // 4. 监控状态检查
            templates.add(TaskTemplate.builder()
                    .order(4)
                    .taskType(TaskTypeEnum.PERFORMANCE_MONITORING)
                    .name("监控状态检查")
                    .description("定期检查监控系统的状态和性能")
                    .defaultParameters(Map.of("check_interval", 60, "metrics_to_monitor", "all"))
                    .parameterMappings(Collections.emptyList())
                    .estimatedDuration(5)
                    .timeout(20)
                    .maxRetries(3)
                    .requiredTools(Arrays.asList("SystemMonitor", "MetricsCollector"))
                    .atomic(false) // 非原子任务，可以被打断
                    .build());

            return DecompositionRule.builder()
                    .taskType(TaskTypeEnum.REAL_TIME_MONITORING)
                    .name("实时监控分解规则")
                    .description("将实时监控任务分解为初始化、数据处理、规则配置和状态检查")
                    .templates(templates)
                    .build();
        }

        /**
         * 创建报告生成任务分解规则
         */
        private DecompositionRule createReportGenerationRule() {
            List<TaskTemplate> templates = new ArrayList<>();

            // 1. 收集报告数据
            templates.add(TaskTemplate.builder()
                    .order(1)
                    .taskType(TaskTypeEnum.COLLECT_REPORT_DATA)
                    .name("收集报告数据")
                    .description("收集报告所需的所有相关数据")
                    .defaultParameters(Map.of("data_freshness", "latest"))
                    .parameterMappings(Arrays.asList(
                            ParameterMapping.builder()
                                    .sourceParameter("report_type")
                                    .targetParameter("data_sources")
                                    .build(),
                            ParameterMapping.builder()
                                    .sourceParameter("time_range")
                                    .targetParameter("time_window")
                                    .build()
                    ))
                    .estimatedDuration(20)
                    .timeout(60)
                    .maxRetries(2)
                    .requiredTools(Arrays.asList("DataCollector", "DataValidator"))
                    .atomic(true)
                    .build());

            // 2. 分析数据
            templates.add(TaskTemplate.builder()
                    .order(2)
                    .taskType(TaskTypeEnum.ANALYZE_REPORT_DATA)
                    .name("分析报告数据")
                    .description("对收集的数据进行分析和处理")
                    .defaultParameters(Map.of("analysis_method", "statistical"))
                    .parameterMappings(Collections.emptyList())
                    .estimatedDuration(25)
                    .timeout(75)
                    .maxRetries(1)
                    .requiredTools(Arrays.asList("DataAnalyzer", "StatsCalculator"))
                    .atomic(true)
                    .build());

            // 3. 生成报告内容
            templates.add(TaskTemplate.builder()
                    .order(3)
                    .taskType(TaskTypeEnum.GENERATE_REPORT_CONTENT)
                    .name("生成报告内容")
                    .description("生成报告的详细内容和分析结果")
                    .defaultParameters(Map.of("content_type", "detailed", "language", "zh-CN"))
                    .parameterMappings(Arrays.asList(
                            ParameterMapping.builder()
                                    .sourceParameter("report_type")
                                    .targetParameter("template")
                                    .build()
                    ))
                    .estimatedDuration(30)
                    .timeout(90)
                    .maxRetries(1)
                    .requiredTools(Arrays.asList("ContentGenerator", "TemplateEngine"))
                    .atomic(true)
                    .build());

            // 4. 格式化报告
            templates.add(TaskTemplate.builder()
                    .order(4)
                    .taskType(TaskTypeEnum.GENERATE_VISUALIZATION)
                    .name("格式化报告")
                    .description("格式化报告并添加可视化图表")
                    .defaultParameters(Map.of("visualization_type", "charts", "style", "corporate"))
                    .parameterMappings(Collections.emptyList())
                    .estimatedDuration(15)
                    .timeout(45)
                    .maxRetries(2)
                    .requiredTools(Arrays.asList("ReportFormatter", "ChartGenerator"))
                    .atomic(true)
                    .build());

            // 5. 导出报告
            templates.add(TaskTemplate.builder()
                    .order(5)
                    .taskType(TaskTypeEnum.EXPORT_REPORT)
                    .name("导出报告")
                    .description("将报告导出为指定格式并分发")
                    .defaultParameters(Map.of("export_format", "pdf", "compression", true))
                    .parameterMappings(Arrays.asList(
                            ParameterMapping.builder()
                                    .sourceParameter("format")
                                    .targetParameter("output_format")
                                    .build(),
                            ParameterMapping.builder()
                                    .sourceParameter("recipients")
                                    .targetParameter("distribution_list")
                                    .transformation("TO_LIST")
                                    .build()
                    ))
                    .estimatedDuration(10)
                    .timeout(30)
                    .maxRetries(3)
                    .requiredTools(Arrays.asList("ReportExporter", "DistributionService"))
                    .atomic(true)
                    .build());

            return DecompositionRule.builder()
                    .taskType(TaskTypeEnum.BATCH_REPORT_GENERATION)
                    .name("报告生成分解规则")
                    .description("将报告生成任务分解为数据收集、分析、内容生成、格式化和导出")
                    .templates(templates)
                    .build();
        }

        /**
         * 创建全面调查任务分解规则
         */
        private DecompositionRule createComprehensiveInvestigationRule() {
            List<TaskTemplate> templates = new ArrayList<>();

            // 1. 基础信息查询（同用户调查）
            templates.addAll(createUserInvestigationRule().getTemplates().subList(0, 3));

            // 2. 设备信息查询
            templates.add(TaskTemplate.builder()
                    .order(4)
                    .taskType(TaskTypeEnum.QUERY_USER_DEVICES)
                    .name("查询用户设备信息")
                    .description("获取用户使用的设备信息和登录历史")
                    .defaultParameters(Map.of("include_location", true))
                    .parameterMappings(Arrays.asList(
                            ParameterMapping.builder()
                                    .sourceParameter("user_id")
                                    .targetParameter("user_id")
                                    .build()
                    ))
                    .estimatedDuration(12)
                    .timeout(35)
                    .maxRetries(2)
                    .requiredTools(Arrays.asList("DeviceQueryService"))
                    .atomic(true)
                    .build());

            // 3. IP历史查询
            templates.add(TaskTemplate.builder()
                    .order(5)
                    .taskType(TaskTypeEnum.QUERY_USER_IP_HISTORY)
                    .name("查询用户IP历史")
                    .description("查询用户的IP地址使用历史和地理位置")
                    .defaultParameters(Map.of("include_geolocation", true))
                    .parameterMappings(Arrays.asList(
                            ParameterMapping.builder()
                                    .sourceParameter("user_id")
                                    .targetParameter("user_id")
                                    .build(),
                            ParameterMapping.builder()
                                    .sourceParameter("time_range")
                                    .targetParameter("time_range")
                                    .defaultValue("最近180天")
                                    .build()
                    ))
                    .estimatedDuration(15)
                    .timeout(45)
                    .maxRetries(2)
                    .requiredTools(Arrays.asList("IPQueryService", "GeolocationService"))
                    .atomic(true)
                    .build());

            // 4. 社交关系分析
            templates.add(TaskTemplate.builder()
                    .order(6)
                    .taskType(TaskTypeEnum.SOCIAL_RISK_ANALYSIS)
                    .name("社交关系分析")
                    .description("分析用户的社交网络和关系风险")
                    .defaultParameters(Map.of("depth", 2))
                    .parameterMappings(Arrays.asList(
                            ParameterMapping.builder()
                                    .sourceParameter("user_id")
                                    .targetParameter("user_id")
                                    .build()
                    ))
                    .estimatedDuration(25)
                    .timeout(75)
                    .maxRetries(1)
                    .requiredTools(Arrays.asList("SocialAnalyzer", "NetworkAnalyzer"))
                    .atomic(true)
                    .build());

            // 5. 深度风险分析
            templates.add(TaskTemplate.builder()
                    .order(7)
                    .taskType(TaskTypeEnum.COMPREHENSIVE_INVESTIGATION)
                    .name("深度风险分析")
                    .description("综合分析所有维度的风险指标")
                    .defaultParameters(Map.of("analysis_depth", "deep"))
                    .parameterMappings(Arrays.asList(
                            ParameterMapping.builder()
                                    .sourceParameter("user_id")
                                    .targetParameter("user_id")
                                    .build()
                    ))
                    .estimatedDuration(35)
                    .timeout(105)
                    .maxRetries(1)
                    .requiredTools(Arrays.asList("DeepRiskAnalyzer", "MLModel"))
                    .atomic(true)
                    .build());

            return DecompositionRule.builder()
                    .taskType(TaskTypeEnum.COMPREHENSIVE_INVESTIGATION)
                    .name("全面调查分解规则")
                    .description("将全面调查任务分解为多维度信息查询和深度分析")
                    .templates(templates)
                    .build();
        }

        /**
         * 创建定向调查任务分解规则
         */
        private DecompositionRule createTargetedInvestigationRule() {
            List<TaskTemplate> templates = new ArrayList<>();

            // 根据调查目标动态生成模板
            templates.add(TaskTemplate.builder()
                    .order(1)
                    .taskType(TaskTypeEnum.TARGETED_INVESTIGATION)
                    .name("定向调查分析")
                    .description("针对特定问题进行定向调查分析")
                    .defaultParameters(Map.of("focus_area", "unknown"))
                    .parameterMappings(Arrays.asList(
                            ParameterMapping.builder()
                                    .sourceParameter("investigation_target")
                                    .targetParameter("target")
                                    .build(),
                            ParameterMapping.builder()
                                    .sourceParameter("time_range")
                                    .targetParameter("time_window")
                                    .defaultValue("最近30天")
                                    .build()
                    ))
                    .estimatedDuration(20)
                    .timeout(60)
                    .maxRetries(2)
                    .requiredTools(Arrays.asList("TargetedAnalyzer"))
                    .atomic(true)
                    .build());

            return DecompositionRule.builder()
                    .taskType(TaskTypeEnum.TARGETED_INVESTIGATION)
                    .name("定向调查分解规则")
                    .description("定向调查任务分解，根据具体目标调整")
                    .templates(templates)
                    .dynamic(true)
                    .build();
        }

        /**
         * 创建复合分析任务分解规则
         */
        private DecompositionRule createCompositeAnalysisRule() {
            List<TaskTemplate> templates = new ArrayList<>();

            templates.add(TaskTemplate.builder()
                    .order(1)
                    .taskType(TaskTypeEnum.COMPOSITE_ANALYSIS)
                    .name("复合分析预处理")
                    .description("准备复合分析所需的数据和配置")
                    .defaultParameters(Map.of("preprocessing_level", "standard"))
                    .parameterMappings(Collections.emptyList())
                    .estimatedDuration(15)
                    .timeout(45)
                    .maxRetries(2)
                    .requiredTools(Arrays.asList("CompositeAnalyzer"))
                    .atomic(true)
                    .build());

            return DecompositionRule.builder()
                    .taskType(TaskTypeEnum.COMPOSITE_ANALYSIS)
                    .name("复合分析分解规则")
                    .description("复合分析任务分解，需要进一步细化")
                    .templates(templates)
                    .build();
        }

        /**
         * 创建复合监控任务分解规则
         */
        private DecompositionRule createCompositeMonitoringRule() {
            List<TaskTemplate> templates = new ArrayList<>();

            templates.add(TaskTemplate.builder()
                    .order(1)
                    .taskType(TaskTypeEnum.COMPOSITE_MONITORING)
                    .name("复合监控初始化")
                    .description("初始化复合监控任务")
                    .defaultParameters(Map.of("monitoring_mode", "composite"))
                    .parameterMappings(Collections.emptyList())
                    .estimatedDuration(10)
                    .timeout(30)
                    .maxRetries(2)
                    .requiredTools(Arrays.asList("CompositeMonitor"))
                    .atomic(true)
                    .build());

            return DecompositionRule.builder()
                    .taskType(TaskTypeEnum.COMPOSITE_MONITORING)
                    .name("复合监控分解规则")
                    .description("复合监控任务分解，需要进一步细化")
                    .templates(templates)
                    .build();
        }

        /**
         * 创建复合报告任务分解规则
         */
        private DecompositionRule createCompositeReportingRule() {
            List<TaskTemplate> templates = new ArrayList<>();

            templates.add(TaskTemplate.builder()
                    .order(1)
                    .taskType(TaskTypeEnum.COMPOSITE_REPORTING)
                    .name("复合报告准备")
                    .description("准备复合报告生成")
                    .defaultParameters(Map.of("report_scope", "composite"))
                    .parameterMappings(Collections.emptyList())
                    .estimatedDuration(12)
                    .timeout(36)
                    .maxRetries(2)
                    .requiredTools(Arrays.asList("CompositeReporter"))
                    .atomic(true)
                    .build());

            return DecompositionRule.builder()
                    .taskType(TaskTypeEnum.COMPOSITE_REPORTING)
                    .name("复合报告分解规则")
                    .description("复合报告任务分解，需要进一步细化")
                    .templates(templates)
                    .build();
        }

        /**
         * 初始化参数提取规则
         */
        private Map<String, ParameterExtractionRule> initializeParameterExtractionRules() {
            Map<String, ParameterExtractionRule> rules = new HashMap<>();

            // QUERY_USER_INFO 参数提取规则
            rules.put("QUERY_USER_INFO", params -> {
                Map<String, Object> extracted = new HashMap<>();
                if (params != null) {
                    if (params.containsKey("user_id")) {
                        extracted.put("user_id", params.get("user_id"));
                    }
                    if (params.containsKey("include_sensitive")) {
                        extracted.put("include_sensitive_data", params.get("include_sensitive"));
                    }
                }
                return extracted;
            });

            // QUERY_CHAT_HISTORY 参数提取规则
            rules.put("QUERY_CHAT_HISTORY", params -> {
                Map<String, Object> extracted = new HashMap<>();
                if (params != null) {
                    if (params.containsKey("user_id")) {
                        extracted.put("user_id", params.get("user_id"));
                    }
                    if (params.containsKey("time_range")) {
                        extracted.put("time_window", params.get("time_range"));
                    }
                    if (params.containsKey("chat_type")) {
                        extracted.put("conversation_type", params.get("chat_type"));
                    }
                }
                return extracted;
            });

            // RISK_ANALYSIS 参数提取规则
            rules.put("RISK_ANALYSIS", params -> {
                Map<String, Object> extracted = new HashMap<>();
                if (params != null) {
                    if (params.containsKey("user_id")) {
                        extracted.put("target_user", params.get("user_id"));
                    }
                    if (params.containsKey("risk_threshold")) {
                        extracted.put("threshold", params.get("risk_threshold"));
                    }
                    extracted.putIfAbsent("analysis_mode", "comprehensive");
                }
                return extracted;
            });

            return rules;
        }

        /**
         * 初始化依赖关系规则
         */
        private Map<TaskTypeEnum, List<DependencyRule>> initializeDependencyRules() {
            Map<TaskTypeEnum, List<DependencyRule>> rules = new HashMap<>();

            // RISK_ANALYSIS 依赖规则
            List<DependencyRule> riskAnalysisDeps = new ArrayList<>();
            riskAnalysisDeps.add(DependencyRule.builder()
                    .dependencyType(TASK_TYPE_MATCH)
                    .requiredTaskType(TaskTypeEnum.QUERY_USER_INFO)
                    .matchCondition(Map.of("user_id", "${user_id}"))
                    .build());
            riskAnalysisDeps.add(DependencyRule.builder()
                    .dependencyType(TASK_TYPE_MATCH)
                    .requiredTaskType(TaskTypeEnum.QUERY_CHAT_HISTORY)
                    .matchCondition(Map.of("user_id", "${user_id}"))
                    .build());
            riskAnalysisDeps.add(DependencyRule.builder()
                    .dependencyType(TASK_TYPE_MATCH)
                    .requiredTaskType(TaskTypeEnum.QUERY_TRANSACTION_HISTORY)
                    .matchCondition(Map.of("user_id", "${user_id}"))
                    .build());
            rules.put(TaskTypeEnum.RISK_ANALYSIS, riskAnalysisDeps);

            // GENERATE_INVESTIGATION_REPORT 依赖规则
            List<DependencyRule> reportDeps = new ArrayList<>();
            reportDeps.add(DependencyRule.builder()
                    .dependencyType(TASK_TYPE_MATCH)
                    .requiredTaskType(TaskTypeEnum.RISK_ANALYSIS)
                    .matchCondition(Map.of("user_id", "${user_id}"))
                    .build());
            rules.put(TaskTypeEnum.GENERATE_INVESTIGATION_REPORT, reportDeps);

            // BATCH_ANALYSIS_SUBTASK 依赖规则
            List<DependencyRule> batchSubtaskDeps = new ArrayList<>();
            batchSubtaskDeps.add(DependencyRule.builder()
                    .dependencyType(PARENT_CHILD)
                    .build());
            rules.put(TaskTypeEnum.BATCH_ANALYSIS_SUBTASK, batchSubtaskDeps);

            // DATA_VALIDATION (结果汇总) 依赖规则
            List<DependencyRule> validationDeps = new ArrayList<>();
            validationDeps.add(DependencyRule.builder()
                    .dependencyType(TASK_TYPE_MATCH)
                    .requiredTaskType(TaskTypeEnum.BATCH_ANALYSIS_SUBTASK)
                    .build());
            rules.put(TaskTypeEnum.DATA_VALIDATION, validationDeps);

            return rules;
        }

        // ==================== 内部数据类 ====================

        /**
         * 分解规则
         */
        @Data
        @Builder
        static class DecompositionRule {
            private TaskTypeEnum taskType;
            private String name;
            private String description;
            private List<TaskTemplate> templates;
            @Builder.Default
            private boolean dynamic = false; // 是否动态生成子任务
            @Builder.Default
            private int maxSubtasks = 20; // 最大子任务数
        }

        /**
         * 任务模板
         */
        @Data
        @Builder
        static class TaskTemplate {
            private int order; // 执行顺序
            private TaskTypeEnum taskType;
            private String name;
            private String description;
            @Builder.Default
            private Map<String, Object> defaultParameters = new HashMap<>();
            @Builder.Default
            private List<ParameterMapping> parameterMappings = new ArrayList<>();
            private Integer estimatedDuration; // 秒
            private Integer timeout; // 秒
            @Builder.Default
            private Integer maxRetries = 3;
            @Builder.Default
            private List<String> requiredTools = new ArrayList<>();
            private Integer maxConcurrent; // 最大并发数
            @Builder.Default
            private boolean atomic = true; // 是否是原子任务
        }

        /**
         * 参数映射
         */
        @Data
        @Builder
        static class ParameterMapping {
            private String sourceParameter; // 源参数名
            private String targetParameter; // 目标参数名
            private String transformation; // 转换规则
            private Object defaultValue; // 默认值
        }

        /**
         * 参数提取规则（函数式接口）
         */
        @FunctionalInterface
        interface ParameterExtractionRule {
            Map<String, Object> extract(Map<String, Object> sourceParameters);
        }

        /**
         * 依赖规则
         */
        @Data
        @Builder
        static class DependencyRule {
            private DependencyType dependencyType;
            private TaskTypeEnum requiredTaskType; // 需要的任务类型
            private Map<String, Object> matchCondition; // 匹配条件
            private String parameterMapping; // 参数映射关系
        }

        /**
         * 依赖类型
         */
        enum DependencyType {
            PARAMETER_MATCH,   // 参数匹配
            TASK_TYPE_MATCH,   // 任务类型匹配
            PARENT_CHILD,      // 父子关系
            SEQUENTIAL,        // 顺序关系
            DATA_FLOW          // 数据流关系
        }

        // ==================== 工具方法 ====================

        /**
         * 获取任务分解统计信息
         */
        public Map<String, Object> getDecompositionStatistics() {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalRules", decompositionRules.size());

            Map<String, Integer> ruleCounts = new HashMap<>();
            for (Map.Entry<TaskTypeEnum, DecompositionRule> entry : decompositionRules.entrySet()) {
                ruleCounts.put(entry.getKey().name(), entry.getValue().getTemplates().size());
            }
            stats.put("ruleDetails", ruleCounts);

            return stats;
        }

        /**
         * 重新加载规则（热更新支持）
         */
        public synchronized void reloadRules() {
            log.info("重新加载任务分解规则");
            decompositionRules.clear();
            decompositionRules.putAll(initializeDecompositionRules());
            log.info("规则重新加载完成，现有{}条规则", decompositionRules.size());
        }

        /**
         * 添加自定义分解规则
         */
        public synchronized void addCustomRule(TaskTypeEnum taskType, DecompositionRule rule) {
            decompositionRules.put(taskType, rule);
            log.info("添加自定义分解规则：{}，包含{}个模板", taskType, rule.getTemplates().size());
        }

        /**
         * 删除分解规则
         */
        public synchronized void removeRule(TaskTypeEnum taskType) {
            if (decompositionRules.containsKey(taskType)) {
                decompositionRules.remove(taskType);
                log.info("删除分解规则：{}", taskType);
            }
        }

        /**
         * 导出规则配置
         */
        public String exportRules() {
            StringBuilder sb = new StringBuilder();
            sb.append("# 任务分解规则配置\n\n");

            for (Map.Entry<TaskTypeEnum, DecompositionRule> entry : decompositionRules.entrySet()) {
                DecompositionRule rule = entry.getValue();
                sb.append(String.format("## %s (%s)\n", rule.getName(), entry.getKey()));
                sb.append(String.format("描述: %s\n", rule.getDescription()));
                sb.append(String.format("动态生成: %s\n", rule.isDynamic()));
                sb.append(String.format("模板数量: %d\n\n", rule.getTemplates().size()));

                for (TaskTemplate template : rule.getTemplates()) {
                    sb.append(String.format("### 模板%d: %s\n", template.getOrder(), template.getName()));
                    sb.append(String.format("任务类型: %s\n", template.getTaskType()));
                    sb.append(String.format("描述: %s\n", template.getDescription()));
                    sb.append(String.format("预估时间: %d秒\n", template.getEstimatedDuration()));
                    sb.append(String.format("超时时间: %d秒\n", template.getTimeout()));
                    sb.append(String.format("最大重试: %d\n", template.getMaxRetries()));
                    sb.append(String.format("原子任务: %s\n", template.isAtomic()));

                    if (!template.getRequiredTools().isEmpty()) {
                        sb.append(String.format("所需工具: %s\n", String.join(", ", template.getRequiredTools())));
                    }

                    sb.append("\n");
                }

                sb.append("---\n\n");
            }

            return sb.toString();
        }
}
