/**
 * @author qkcao
 * @date 2026/1/23 17:58
 */
package com.rental.guard.ai.domain.service;

import com.rental.guard.ai.domain.dto.PlanningConstraints;
import com.rental.guard.ai.domain.dto.PlanningStrategyEnum;
import com.rental.guard.ai.domain.dto.Task;
import com.rental.guard.ai.domain.dto.TaskTypeEnum;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LLM增强的策略选择器
 */
@Component
@Slf4j
public class LLMStrategySelector {

    private final OllamaService ollamaService;
    private final PlanningStrategySelector ruleSelector;

    // 策略历史记录，用于学习和优化
    private final Map<String, StrategyHistory> strategyHistory = new HashMap<>();

    public LLMStrategySelector(OllamaService ollamaService, PlanningStrategySelector ruleSelector) {
        this.ollamaService = ollamaService;
        this.ruleSelector = ruleSelector;
    }

    /**
     * LLM增强的策略选择
     */
    public PlanningStrategyEnum selectOptimalStrategy(
            IntentRecognitionModule.AgentIntent intent,
            PlanningConstraints constraints,
            List<Task> tasks) {

        // 1. 先获取规则选择的策略
        PlanningStrategyEnum ruleStrategy =
                ruleSelector.selectStrategyWithConstraints(intent, constraints);

        // 2. 使用LLM分析和调整
        PlanningStrategyEnum llmStrategy =
                analyzeWithLLM(intent, constraints, tasks, ruleStrategy);

        // 3. 结合历史记录进行决策
        PlanningStrategyEnum finalStrategy =
                combineWithHistory(intent, ruleStrategy, llmStrategy);

        // 4. 记录策略选择
        recordStrategySelection(intent, constraints, tasks, finalStrategy);

        log.info("策略选择完成：规则建议{}，LLM建议{}，最终选择{}",
                ruleStrategy, llmStrategy, finalStrategy);

        return finalStrategy;
    }

    /**
     * 使用LLM分析策略
     */
    private PlanningStrategyEnum analyzeWithLLM(
            IntentRecognitionModule.AgentIntent intent,
            PlanningConstraints constraints,
            List<Task> tasks,
            PlanningStrategyEnum baseStrategy) {

        try {
            String prompt = buildStrategyAnalysisPrompt(intent, constraints, tasks, baseStrategy);

            // 使用思维链分析
            OllamaService.ChainOfThoughtResponse thought = ollamaService.chainOfThought(prompt);

            if (thought.getConfidence() < 0.6) {
                log.debug("LLM策略分析置信度过低，使用规则策略");
                return baseStrategy;
            }

            // 生成结构化响应
            OllamaService.PlanningStrategyResponse strategyResponse =
                    ollamaService.generateStructuredResponse(prompt,
                            OllamaService.PlanningStrategyResponse.class);

            // 转换为策略枚举
            PlanningStrategyEnum llmStrategy =
                    parseStrategy(strategyResponse.getStrategy());

            log.debug("LLM策略分析完成，建议：{}，置信度：{}，理由：{}",
                    llmStrategy, strategyResponse.getConfidence(), strategyResponse.getReasoning());

            return llmStrategy;

        } catch (Exception e) {
            log.warn("LLM策略分析失败，使用规则策略", e);
            return baseStrategy;
        }
    }

    /**
     * 结合历史记录进行决策
     */
    private PlanningStrategyEnum combineWithHistory(
            IntentRecognitionModule.AgentIntent intent,
            PlanningStrategyEnum ruleStrategy,
            PlanningStrategyEnum llmStrategy) {

        String intentKey = intent.getIntentType().name();
        StrategyHistory history = strategyHistory.get(intentKey);

        if (history == null) {
            // 无历史记录，使用LLM策略（如果有足够置信度）
            return llmStrategy != null ? llmStrategy : ruleStrategy;
        }

        // 计算各策略的历史成功率
        double ruleSuccessRate = history.getSuccessRate(ruleStrategy);
        double llmSuccessRate = history.getSuccessRate(llmStrategy);

        // 选择历史成功率较高的策略
        if (llmSuccessRate > ruleSuccessRate + 0.1) { // LLM成功率显著更高
            log.debug("基于历史成功率选择LLM策略（{} vs {}）", llmSuccessRate, ruleSuccessRate);
            return llmStrategy;
        } else if (ruleSuccessRate > llmSuccessRate + 0.1) { // 规则成功率显著更高
            log.debug("基于历史成功率选择规则策略（{} vs {}）", ruleSuccessRate, llmSuccessRate);
            return ruleStrategy;
        } else {
            // 成功率相近，使用LLM策略（如果可用）
            return llmStrategy != null ? llmStrategy : ruleStrategy;
        }
    }

    /**
     * 构建策略分析提示
     */
    private String buildStrategyAnalysisPrompt(
            IntentRecognitionModule.AgentIntent intent,
            PlanningConstraints constraints,
            List<Task> tasks,
            PlanningStrategyEnum baseStrategy) {

        return String.format("""
                        你是一个任务规划策略专家。请分析以下场景，选择最优的执行策略。
                                    
                        用户意图：
                        类型：%s
                        实体：%s
                        参数：%s
                        优先级：%s
                        置信度：%.2f
                                    
                        规划约束：
                        时间紧迫：%s
                        资源受限：%s
                        需要高可用：%s
                        最大并发任务数：%d
                        超时时间：%d分钟
                                    
                        任务序列（共%d个任务）：
                        %s
                                    
                        规则建议的策略：%s
                                    
                        可选策略：
                        1. LINEAR（线性执行）：任务按顺序依次执行，简单可靠
                        2. PARALLEL（并行执行）：任务尽可能并行执行，提高效率
                        3. CONDITIONAL（条件执行）：根据条件选择执行路径，灵活
                        4. ITERATIVE（迭代执行）：循环执行某些任务，适合数据处理
                        5. BATCH（批量执行）：批量处理任务，适合大数据量
                        6. MONITORING（监控模式）：持续监控状态，适合实时任务
                                    
                        请考虑以下因素：
                        1. 任务间的依赖关系
                        2. 系统资源的限制
                        3. 用户意图的紧急程度
                        4. 防欺诈系统的特殊性（准确性要求高）
                        5. 任务的预估执行时间
                        6. 风险控制需求
                                    
                        请给出最优策略选择，并说明理由。
                        """,
                intent.getIntentType(),
                intent.getEntities(),
                intent.getParameters(),
                intent.getPriority(),
                intent.getConfidence(),
                constraints.getTimeCritical(),
                constraints.getResourceConstrained(),
                constraints.getRequiresHighAvailability(),
                constraints.getMaxConcurrentTasks(),
                constraints.getTimeoutMinutes(),
                tasks.size(),
                summarizeTasks(tasks),
                baseStrategy
        );
    }

    /**
     * 总结任务信息
     */
    private String summarizeTasks(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return "无具体任务";
        }

        StringBuilder summary = new StringBuilder();
        Map<TaskTypeEnum, Integer> typeCount = new HashMap<>();
        int totalEstimatedTime = 0;
        int maxDepth = 0;

        for (Task task : tasks) {
            // 统计任务类型
            typeCount.merge(task.getTaskType(), 1, Integer::sum);

            // 累加预估时间
            if (task.getEstimatedDuration() != null) {
                totalEstimatedTime += task.getEstimatedDuration();
            }

            // 估算依赖深度
            int depth = calculateDependencyDepth(task, tasks);
            maxDepth = Math.max(maxDepth, depth);
        }

        summary.append(String.format("""
                        总任务数：%d
                        任务类型分布：%s
                        总预估时间：%d秒
                        最大依赖深度：%d
                        """,
                tasks.size(),
                formatTypeCount(typeCount),
                totalEstimatedTime,
                maxDepth
        ));

        return summary.toString();
    }

    /**
     * 计算依赖深度
     */
    private int calculateDependencyDepth(Task task, List<Task> allTasks) {
        if (task.getDependencies() == null || task.getDependencies().isEmpty()) {
            return 0;
        }

        int maxDepth = 0;
        Map<String, Task> taskMap = new HashMap<>();
        for (Task t : allTasks) {
            taskMap.put(t.getTaskId(), t);
        }

        for (String depId : task.getDependencies()) {
            Task depTask = taskMap.get(depId);
            if (depTask != null) {
                int depth = calculateDependencyDepth(depTask, allTasks);
                maxDepth = Math.max(maxDepth, depth);
            }
        }

        return maxDepth + 1;
    }

    /**
     * 格式化类型统计
     */
    private String formatTypeCount(Map<TaskTypeEnum, Integer> typeCount) {
        if (typeCount.isEmpty()) {
            return "无";
        }

        List<String> items = new ArrayList<>();
        for (Map.Entry<TaskTypeEnum, Integer> entry : typeCount.entrySet()) {
            items.add(String.format("%s×%d", entry.getKey().name(), entry.getValue()));
        }

        return String.join(", ", items);
    }

    /**
     * 解析策略字符串
     */
    private PlanningStrategyEnum parseStrategy(String strategyStr) {
        if (strategyStr == null) {
            return null;
        }

        try {
            return PlanningStrategyEnum.valueOf(strategyStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("无法识别的策略：{}", strategyStr);

            // 尝试模糊匹配
            for (PlanningStrategyEnum strategy : PlanningStrategyEnum.values()) {
                if (strategyStr.toLowerCase().contains(strategy.name().toLowerCase())) {
                    return strategy;
                }
            }

            return null;
        }
    }

    /**
     * 记录策略选择
     */
    private void recordStrategySelection(
            IntentRecognitionModule.AgentIntent intent,
            PlanningConstraints constraints,
            List<Task> tasks,
            PlanningStrategyEnum selectedStrategy) {

        String intentKey = intent.getIntentType().name();
        StrategyHistory history = strategyHistory.computeIfAbsent(intentKey,
                k -> new StrategyHistory());

        StrategyRecord record = new StrategyRecord();
        record.setTimestamp(System.currentTimeMillis());
        record.setIntent(intent);
        record.setConstraints(constraints);
        record.setTaskCount(tasks.size());
        record.setSelectedStrategy(selectedStrategy);
        record.setSuccess(null); // 初始未知，后续更新

        history.addRecord(record);

        // 限制历史记录大小
        if (history.getRecords().size() > 1000) {
            history.getRecords().subList(0, 100).clear();
        }
    }

    /**
     * 更新策略执行结果
     */
    public void updateStrategyResult(String planId, boolean success,
                                     PlanningStrategyEnum strategy) {
        // 查找相关的历史记录并更新结果
        // 这里简化实现，实际中可能需要更复杂的关联逻辑
        for (StrategyHistory history : strategyHistory.values()) {
            for (StrategyRecord record : history.getRecords()) {
                if (record.getSelectedStrategy() == strategy) {
                    record.setSuccess(success);
                    record.setResultTimestamp(System.currentTimeMillis());
                }
            }
        }
    }

    /**
     * 获取策略建议报告
     */
    public String getStrategyAdviceReport() {
        StringBuilder report = new StringBuilder("策略选择历史报告\n");
        report.append("=".repeat(50)).append("\n\n");

        for (Map.Entry<String, StrategyHistory> entry : strategyHistory.entrySet()) {
            String intent = entry.getKey();
            StrategyHistory history = entry.getValue();

            report.append(String.format("意图类型：%s\n", intent));
            report.append(String.format("总记录数：%d\n", history.getRecords().size()));

            Map<PlanningStrategyEnum, StrategyStats> stats = history.calculateStats();
            for (Map.Entry<PlanningStrategyEnum, StrategyStats> statEntry : stats.entrySet()) {
                StrategyStats stat = statEntry.getValue();
                if (stat.totalCount > 0) {
                    report.append(String.format("  策略%s：使用%d次，成功率%.1f%%\n",
                            statEntry.getKey(), stat.totalCount, stat.successRate * 100));
                }
            }

            report.append("\n");
        }

        return report.toString();
    }

    // 内部数据类
    @Data
    private static class StrategyHistory {
        private List<StrategyRecord> records = new ArrayList<>();

        public double getSuccessRate(PlanningStrategyEnum strategy) {
            List<StrategyRecord> relevantRecords = records.stream()
                    .filter(r -> r.getSelectedStrategy() == strategy && r.getSuccess() != null)
                    .collect(Collectors.toList());

            if (relevantRecords.isEmpty()) {
                return 0.5; // 默认成功率
            }

            long successCount = relevantRecords.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getSuccess()))
                    .count();

            return (double) successCount / relevantRecords.size();
        }

        public Map<PlanningStrategyEnum, StrategyStats> calculateStats() {
            Map<PlanningStrategyEnum, StrategyStats> stats = new HashMap<>();

            for (StrategyRecord record : records) {
                if (record.getSuccess() != null) {
                    StrategyStats stat = stats.computeIfAbsent(record.getSelectedStrategy(),
                            k -> new StrategyStats());
                    stat.totalCount++;
                    if (record.getSuccess()) {
                        stat.successCount++;
                    }
                }
            }

            // 计算成功率
            for (StrategyStats stat : stats.values()) {
                stat.calculateSuccessRate();
            }

            return stats;
        }

        public void addRecord(StrategyRecord record) {
            records.add(record);
        }
    }

    @Data
    private static class StrategyRecord {
        private Long timestamp;
        private IntentRecognitionModule.AgentIntent intent;
        private PlanningConstraints constraints;
        private Integer taskCount;
        private PlanningStrategyEnum selectedStrategy;
        private Boolean success; // 执行结果
        private Long resultTimestamp;
        private String planId;
    }

    @Data
    private static class StrategyStats {
        private int totalCount = 0;
        private int successCount = 0;
        private double successRate = 0.0;

        public void calculateSuccessRate() {
            if (totalCount > 0) {
                successRate = (double) successCount / totalCount;
            }
        }
    }
}
