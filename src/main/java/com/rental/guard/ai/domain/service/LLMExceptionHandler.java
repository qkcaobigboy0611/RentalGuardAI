/**
 * @author qkcao
 * @date 2026/1/23 18:10
 */
package com.rental.guard.ai.domain.service;

import com.rental.guard.ai.domain.dto.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * LLM增强的异常处理器
 */
@Component
@Slf4j
public class LLMExceptionHandler {

    private final OllamaService ollamaService;
    private final Map<String, ExceptionHistory> exceptionHistory = new HashMap<>();

    public LLMExceptionHandler(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    /**
     * 处理任务执行异常
     */
    public ExceptionHandlingResult handleTaskException(Task task,
                                                       Exception exception,
                                                       TaskPlan plan) {

        log.info("处理任务异常：{}，异常：{}", task.getTaskId(), exception.getMessage());

        // 记录异常历史
        recordException(task, exception, plan);

        // 分析异常原因
        ExceptionAnalysis analysis = analyzeExceptionWithLLM(task, exception, plan);

        // 生成处理方案
        ExceptionHandlingSolution solution = generateHandlingSolution(analysis, task, plan);

        // 应用处理方案
        return applyHandlingSolution(solution, task, plan);
    }

    /**
     * 分析异常原因（LLM增强）
     */
    private ExceptionAnalysis analyzeExceptionWithLLM(Task task,
                                                      Exception exception,
                                                      TaskPlan plan) {

        try {
            String prompt = buildExceptionAnalysisPrompt(task, exception, plan);

            OllamaService.ChainOfThoughtResponse thought = ollamaService.chainOfThought(prompt);

            ExceptionAnalysis analysis = new ExceptionAnalysis();
            analysis.setTaskId(task.getTaskId());
            analysis.setExceptionType(exception.getClass().getSimpleName());
            analysis.setExceptionMessage(exception.getMessage());
            analysis.setReasoning(thought.getReasoning());
            analysis.setRootCause(extractRootCause(thought.getConclusion()));
            analysis.setConfidence(thought.getConfidence());

            // 识别异常模式
            analysis.setExceptionPattern(identifyExceptionPattern(analysis));

            log.debug("异常分析完成：原因={}，置信度={}，模式={}",
                    analysis.getRootCause(), analysis.getConfidence(), analysis.getExceptionPattern());

            return analysis;

        } catch (Exception e) {
            log.warn("LLM异常分析失败", e);
            return createDefaultAnalysis(task, exception);
        }
    }

    /**
     * 生成处理方案
     */
    private ExceptionHandlingSolution generateHandlingSolution(ExceptionAnalysis analysis,
                                                               Task task,
                                                               TaskPlan plan) {

        try {
            String prompt = buildSolutionPrompt(analysis, task, plan);

            String solutionText = ollamaService.generateText(prompt);

            ExceptionHandlingSolution solution = new ExceptionHandlingSolution();
            solution.setAnalysis(analysis);
            solution.setRecommendedAction(parseRecommendedAction(solutionText));
            solution.setRetryStrategy(extractRetryStrategy(solutionText));
            solution.setAlternativeApproach(extractAlternativeApproach(solutionText));
            solution.setPreventiveMeasures(extractPreventiveMeasures(solutionText));

            // 评估方案可行性
            evaluateSolutionFeasibility(solution, task, plan);

            return solution;

        } catch (Exception e) {
            log.warn("LLM生成处理方案失败", e);
            return createDefaultSolution(analysis, task);
        }
    }

    /**
     * 应用处理方案
     */
    private ExceptionHandlingResult applyHandlingSolution(ExceptionHandlingSolution solution,
                                                          Task task,
                                                          TaskPlan plan) {

        ExceptionHandlingResult result = new ExceptionHandlingResult();
        result.setSolution(solution);
        result.setTaskId(task.getTaskId());
        result.setTimestamp(System.currentTimeMillis());

        // 根据建议的行动进行处理
        switch (solution.getRecommendedAction()) {
            case RETRY:
                if (task.canRetry()) {
                    result.setActionTaken(ExceptionHandlingAction.RETRY_TASK);
                    task.setStatus(TaskStatusEnum.PENDING);
                    task.setRetryCount(task.getRetryCount() + 1);
                    result.setSuccess(true);
                    log.info("任务{}将重试，重试次数：{}", task.getTaskId(), task.getRetryCount());
                } else {
                    result.setActionTaken(ExceptionHandlingAction.SKIP_TASK);
                    task.setStatus(TaskStatusEnum.SKIPPED);
                    result.setSuccess(false);
                    log.warn("任务{}已达到最大重试次数，跳过", task.getTaskId());
                }
                break;

            case SKIP:
                result.setActionTaken(ExceptionHandlingAction.SKIP_TASK);
                task.setStatus(TaskStatusEnum.SKIPPED);
                result.setSuccess(true);
                log.info("跳过任务{}", task.getTaskId());
                break;

            case MODIFY_AND_RETRY:
                result.setActionTaken(ExceptionHandlingAction.MODIFY_TASK);
                modifyTaskBasedOnSolution(task, solution);
                result.setSuccess(true);
                log.info("修改并重试任务{}", task.getTaskId());
                break;

            case ESCALATE:
                result.setActionTaken(ExceptionHandlingAction.ESCALATE_TO_HUMAN);
                result.setSuccess(false);
                log.warn("任务{}需要人工干预", task.getTaskId());
                break;

            case CANCEL_PLAN:
                result.setActionTaken(ExceptionHandlingAction.CANCEL_PLAN);
                plan.setStatus(PlanStatusEnum.FAILED);
                result.setSuccess(false);
                log.error("取消整个计划{}", plan.getPlanId());
                break;
        }

        // 记录处理结果
        result.setFinalTaskStatus(task.getStatus());

        return result;
    }

    /**
     * 构建异常分析提示
     */
    private String buildExceptionAnalysisPrompt(Task task,
                                                Exception exception,
                                                TaskPlan plan) {

        return String.format("""
                        请分析以下任务执行异常的原因：
                                    
                        任务信息：
                        任务ID：%s
                        任务类型：%s
                        任务名称：%s
                        参数：%s
                        预估时间：%d秒
                        重试次数：%d
                                    
                        异常信息：
                        异常类型：%s
                        异常消息：%s
                        堆栈跟踪：%s
                                    
                        计划上下文：
                        计划ID：%s
                        总任务数：%d
                        已完成任务数：%d
                        失败任务数：%d
                                    
                        系统上下文：
                        时间：%s
                        资源使用：正常
                                    
                        请分析：
                        1. 异常的根本原因是什么？
                        2. 这是系统问题还是数据问题？
                        3. 是否可以自动恢复？
                        4. 是否需要人工干预？
                        5. 如何避免类似问题？
                                    
                        请给出详细的分析。
                        """,
                task.getTaskId(),
                task.getTaskType(),
                task.getName(),
                task.getParameters(),
                task.getEstimatedDuration(),
                task.getRetryCount(),
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                getStackTraceSummary(exception),
                plan.getPlanId(),
                plan.getTasks().size(),
                plan.getTasks().stream().filter(t -> t.isCompleted()).count(),
                plan.getTasks().stream().filter(t -> t.isFailed()).count(),
                new Date()
        );
    }

    /**
     * 构建解决方案提示
     */
    private String buildSolutionPrompt(ExceptionAnalysis analysis,
                                       Task task,
                                       TaskPlan plan) {

        return String.format("""
                        基于以下异常分析，请给出处理方案：
                                    
                        异常分析：
                        根本原因：%s
                        分析推理：%s
                        置信度：%.2f
                        异常模式：%s
                                    
                        任务信息：
                        任务类型：%s
                        剩余重试次数：%d
                        最大重试次数：%d
                        任务优先级：%d
                                    
                        计划上下文：
                        计划优先级：%d
                        截止时间：%s
                        是否实时任务：%s
                                    
                        请给出具体的处理方案，包括：
                        1. 建议采取的行动（重试、跳过、修改参数、升级人工等）
                        2. 重试策略（立即重试、延迟重试、指数退避）
                        3. 备选方案
                        4. 预防措施
                                    
                        请以清晰的结构化格式回答。
                        """,
                analysis.getRootCause(),
                analysis.getReasoning(),
                analysis.getConfidence(),
                analysis.getExceptionPattern(),
                task.getTaskType(),
                task.getMaxRetries() - task.getRetryCount(),
                task.getMaxRetries(),
                task.getPriority(),
                plan.getPriority(),
                plan.getDeadline(),
                task.getTaskType().isRealTimeType()
        );
    }

    /**
     * 提取根本原因
     */
    private String extractRootCause(String conclusion) {
        // 简单的关键词提取
        if (conclusion.contains("超时") || conclusion.contains("timeout")) {
            return "执行超时";
        } else if (conclusion.contains("资源") || conclusion.contains("resource")) {
            return "资源不足";
        } else if (conclusion.contains("数据") || conclusion.contains("data")) {
            return "数据问题";
        } else if (conclusion.contains("网络") || conclusion.contains("network")) {
            return "网络问题";
        } else if (conclusion.contains("配置") || conclusion.contains("configuration")) {
            return "配置错误";
        } else if (conclusion.contains("权限") || conclusion.contains("permission")) {
            return "权限不足";
        } else {
            return "未知原因";
        }
    }

    /**
     * 识别异常模式
     */
    private String identifyExceptionPattern(ExceptionAnalysis analysis) {
        String exceptionKey = analysis.getExceptionType() + "_" + analysis.getRootCause();
        ExceptionHistory history = exceptionHistory.get(exceptionKey);

        if (history != null && history.getOccurrenceCount() > 3) {
            return "重复发生";
        } else if (analysis.getExceptionMessage().contains("Connection")) {
            return "连接问题";
        } else if (analysis.getExceptionMessage().contains("Timeout")) {
            return "超时问题";
        } else if (analysis.getExceptionMessage().contains("Memory")) {
            return "内存问题";
        } else {
            return "偶发异常";
        }
    }

    /**
     * 解析建议行动
     */
    private RecommendedAction parseRecommendedAction(String solutionText) {
        if (solutionText.contains("重试") && solutionText.contains("立即")) {
            return RecommendedAction.RETRY;
        } else if (solutionText.contains("跳过") || solutionText.contains("忽略")) {
            return RecommendedAction.SKIP;
        } else if (solutionText.contains("修改") && solutionText.contains("重试")) {
            return RecommendedAction.MODIFY_AND_RETRY;
        } else if (solutionText.contains("人工") || solutionText.contains("升级")) {
            return RecommendedAction.ESCALATE;
        } else if (solutionText.contains("取消") || solutionText.contains("终止")) {
            return RecommendedAction.CANCEL_PLAN;
        } else {
            return RecommendedAction.RETRY; // 默认重试
        }
    }

    /**
     * 提取重试策略
     */
    private RetryStrategy extractRetryStrategy(String solutionText) {
        RetryStrategy strategy = new RetryStrategy();

        if (solutionText.contains("指数退避")) {
            strategy.setType(RetryType.EXPONENTIAL_BACKOFF);
            strategy.setInitialDelay(1000); // 1秒
            strategy.setMaxDelay(30000); // 30秒
            strategy.setMultiplier(2.0);
        } else if (solutionText.contains("固定延迟")) {
            strategy.setType(RetryType.FIXED_DELAY);
            strategy.setInitialDelay(5000); // 5秒
            strategy.setMaxDelay(5000);
            strategy.setMultiplier(1.0);
        } else {
            strategy.setType(RetryType.IMMEDIATE);
            strategy.setInitialDelay(0);
            strategy.setMaxDelay(0);
            strategy.setMultiplier(1.0);
        }

        return strategy;
    }

    /**
     * 提取备选方案
     */
    private String extractAlternativeApproach(String solutionText) {
        // 简单的文本提取
        if (solutionText.contains("备选方案：")) {
            int start = solutionText.indexOf("备选方案：");
            int end = solutionText.indexOf("\n", start);
            if (end > start) {
                return solutionText.substring(start + 5, end);
            }
        }

        return "使用不同的参数或工具重试";
    }

    /**
     * 提取预防措施
     */
    private List<String> extractPreventiveMeasures(String solutionText) {
        List<String> measures = new ArrayList<>();

        // 简单的关键词提取
        if (solutionText.contains("超时")) {
            measures.add("增加任务超时时间");
        }
        if (solutionText.contains("资源")) {
            measures.add("检查系统资源分配");
        }
        if (solutionText.contains("数据")) {
            measures.add("添加数据验证步骤");
        }

        if (measures.isEmpty()) {
            measures.add("记录异常并分析模式");
            measures.add("增加监控告警");
        }

        return measures;
    }

    /**
     * 评估方案可行性
     */
    private void evaluateSolutionFeasibility(ExceptionHandlingSolution solution,
                                             Task task,
                                             TaskPlan plan) {

        double feasibility = 0.5; // 基础可行性

        // 考虑任务剩余重试次数
        if (solution.getRecommendedAction() == RecommendedAction.RETRY) {
            if (task.canRetry()) {
                feasibility += 0.3;
            } else {
                feasibility -= 0.3;
            }
        }

        // 考虑计划紧迫性
        if (plan.getDeadline() != null &&
                plan.getDeadline().isBefore(java.time.LocalDateTime.now().plusHours(1))) {
            feasibility -= 0.2; // 时间紧迫时可行性降低
        }

        // 考虑异常分析置信度
        feasibility *= solution.getAnalysis().getConfidence();

        solution.setFeasibilityScore(Math.max(0, Math.min(1, feasibility)));
    }

    /**
     * 修改任务
     */
    private void modifyTaskBasedOnSolution(Task task, ExceptionHandlingSolution solution) {
        // 根据分析结果修改任务参数
        if (solution.getAnalysis().getRootCause().contains("超时")) {
            // 增加超时时间
            int newTimeout = task.getTimeout() != null ? task.getTimeout() * 2 : 120;
            task.setTimeout(newTimeout);
            task.addLog(Task.TaskLog.Level.INFO,
                    "根据异常分析增加超时时间至" + newTimeout + "秒");
        }

        if (solution.getAnalysis().getRootCause().contains("资源")) {
            // 调整资源要求
            if (task.getMetadata() == null) {
                task.setMetadata(new HashMap<>());
            }
            task.getMetadata().put("resource_adjusted", true);
            task.addLog(Task.TaskLog.Level.INFO, "调整资源分配");
        }

        // 重置状态准备重试
        task.setStatus(TaskStatusEnum.PENDING);
        task.setRetryCount(task.getRetryCount() + 1);
    }

    private void recordException(Task task, Exception exception, TaskPlan plan) {
        String exceptionKey = exception.getClass().getSimpleName() + "_" +
                extractRootCause(exception.getMessage());

        ExceptionHistory history = exceptionHistory.computeIfAbsent(exceptionKey,
                k -> new ExceptionHistory());

        ExceptionRecord record = new ExceptionRecord();
        record.setTaskId(task.getTaskId());
        record.setTaskType(task.getTaskType());
        record.setExceptionType(exception.getClass().getSimpleName());
        record.setExceptionMessage(exception.getMessage());
        record.setTimestamp(System.currentTimeMillis());
        record.setPlanId(plan.getPlanId());

        history.addRecord(record);
    }

    private String getStackTraceSummary(Exception e) {
        if (e.getStackTrace() == null || e.getStackTrace().length == 0) {
            return "无堆栈信息";
        }

        // 只取前3行堆栈
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, e.getStackTrace().length); i++) {
            sb.append(e.getStackTrace()[i].toString()).append("\n");
        }

        return sb.toString();
    }

    private ExceptionAnalysis createDefaultAnalysis(Task task, Exception exception) {
        ExceptionAnalysis analysis = new ExceptionAnalysis();
        analysis.setTaskId(task.getTaskId());
        analysis.setExceptionType(exception.getClass().getSimpleName());
        analysis.setExceptionMessage(exception.getMessage());
        analysis.setRootCause("未知原因");
        analysis.setReasoning("无法分析异常原因");
        analysis.setConfidence(0.3);
        analysis.setExceptionPattern("未知模式");
        return analysis;
    }

    private ExceptionHandlingSolution createDefaultSolution(ExceptionAnalysis analysis, Task task) {
        ExceptionHandlingSolution solution = new ExceptionHandlingSolution();
        solution.setAnalysis(analysis);
        solution.setRecommendedAction(task.canRetry() ?
                RecommendedAction.RETRY : RecommendedAction.SKIP);
        solution.setFeasibilityScore(0.5);
        return solution;
    }

    // 内部枚举和类
    public enum RecommendedAction {
        RETRY,           // 重试任务
        SKIP,           // 跳过任务
        MODIFY_AND_RETRY, // 修改后重试
        ESCALATE,       // 升级人工处理
        CANCEL_PLAN     // 取消整个计划
    }

    public enum ExceptionHandlingAction {
        RETRY_TASK,
        SKIP_TASK,
        MODIFY_TASK,
        ESCALATE_TO_HUMAN,
        CANCEL_PLAN
    }

    @Data
    public static class ExceptionAnalysis {
        private String taskId;
        private String exceptionType;
        private String exceptionMessage;
        private String reasoning;
        private String rootCause;
        private Double confidence;
        private String exceptionPattern;
    }

    @Data
    public static class ExceptionHandlingSolution {
        private ExceptionAnalysis analysis;
        private RecommendedAction recommendedAction;
        private RetryStrategy retryStrategy;
        private String alternativeApproach;
        private List<String> preventiveMeasures;
        private Double feasibilityScore;
    }

    @Data
    public static class ExceptionHandlingResult {
        private String taskId;
        private Long timestamp;
        private ExceptionHandlingSolution solution;
        private ExceptionHandlingAction actionTaken;
        private Boolean success;
        private TaskStatusEnum finalTaskStatus;
    }

    @Data
    public static class RetryStrategy {
        private RetryType type;
        private Integer initialDelay; // 毫秒
        private Integer maxDelay;     // 毫秒
        private Double multiplier;
    }

    public enum RetryType {
        IMMEDIATE,          // 立即重试
        FIXED_DELAY,        // 固定延迟
        EXPONENTIAL_BACKOFF // 指数退避
    }

    @Data
    private static class ExceptionHistory {
        private List<ExceptionRecord> records = new ArrayList<>();

        public int getOccurrenceCount() {
            return records.size();
        }

        public long getLastOccurrenceTime() {
            if (records.isEmpty()) {
                return 0;
            }
            return records.get(records.size() - 1).getTimestamp();
        }
        public void addRecord(ExceptionRecord record) {
            records.add(record);
        }
    }

    @Data
    private static class ExceptionRecord {
        private String taskId;
        private TaskTypeEnum taskType;
        private String exceptionType;
        private String exceptionMessage;
        private Long timestamp;
        private String planId;
    }
}
