/**
 * @author qkcao
 * @date 2026/1/22 19:06
 */
package com.rental.guard.ai.domain.service;

import com.rental.guard.ai.domain.dto.ResourceAllocationException;
import com.rental.guard.ai.domain.dto.Task;
import com.rental.guard.ai.domain.dto.TaskTypeEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.rental.guard.ai.domain.dto.TaskTypeEnum.*;

@Component
public class ResourceAllocator {

    @Value("${agent.resources.max-concurrent-tasks:20}")
    private int maxConcurrentTasks;

    @Value("${agent.resources.cpu-cores:8}")
    private int availableCpuCores;

    @Value("${agent.resources.memory-mb:16384}")
    private int availableMemoryMB;

    // 可用工具列表
    private final Set<String> availableTools = Set.of(
            "UserQueryService",
            "ChatQueryService",
            "TransactionService",
            "RiskAnalyzer",
            "MLModel",
            "ReportGenerator",
            "DataPreprocessor",
            "ResultAggregator",
            "DataCollector",
            "DataAnalyzer",
            "ContentGenerator",
            "ReportFormatter",
            "ReportExporter",
            "TextProcessor"
    );

    /**
     * 为任务分配资源
     */
    public List<Task> allocate(List<Task> tasks) {
        List<Task> allocatedTasks = new ArrayList<>();

        // 资源池
        ResourcePool resourcePool = new ResourcePool(
                maxConcurrentTasks,
                availableCpuCores,
                availableMemoryMB
        );

        // 按优先级排序
        tasks.sort(Comparator.comparingInt(Task::getPriority).reversed());

        // 分配资源
        for (Task task : tasks) {
            allocateTaskResources(task, resourcePool);
            allocatedTasks.add(task);
        }

        return allocatedTasks;
    }

    /**
     * 为单个任务分配资源
     */
    private void allocateTaskResources(Task task, ResourcePool resourcePool) {
        // 1. 验证所需工具是否可用
        validateRequiredTools(task);

        // 2. 分配计算资源
        allocateComputingResources(task, resourcePool);

        // 3. 设置并发限制
        setConcurrencyLimits(task);

        // 4. 添加资源标签到元数据
        addResourceMetadata(task);
    }

    /**
     * 验证所需工具
     */
    private void validateRequiredTools(Task task) {
        if (task.getRequiredTools() != null) {
            for (String tool : task.getRequiredTools()) {
                if (!availableTools.contains(tool)) {
                    throw new ResourceAllocationException(
                            String.format("工具%s不可用，任务%s无法执行", tool, task.getTaskId()));
                }
            }
        }
    }

    /**
     * 分配计算资源
     */
    private void allocateComputingResources(Task task, ResourcePool resourcePool) {
        // 根据任务类型分配不同的资源
        int requiredCpu = estimateRequiredCpu(task);
        int requiredMemory = estimateRequiredMemory(task);

        // 检查资源是否足够
        if (!resourcePool.reserve(requiredCpu, requiredMemory)) {
            throw new ResourceAllocationException(
                    String.format("资源不足，无法执行任务%s", task.getTaskId()));
        }

        // 记录资源分配
        if (task.getMetadata() == null) {
            task.setMetadata(new HashMap<>());
        }
        task.getMetadata().put("allocated_cpu", requiredCpu);
        task.getMetadata().put("allocated_memory_mb", requiredMemory);
    }

    /**
     * 估算需要的CPU资源
     */
    private int estimateRequiredCpu(Task task) {
        switch (task.getTaskType()) {
            case ML_ANALYSIS:
                return 2;  // CPU密集型任务
            case RISK_ANALYSIS:
                return 2;  // CPU密集型任务
            case BATCH_ANALYSIS:
                return 1;
            case QUERY_USER_INFO:
            case QUERY_CHAT_HISTORY:
            case QUERY_TRANSACTION_HISTORY:
                return 1;  // IO密集型任务
            default:
                return 1;
        }
    }

    /**
     * 估算需要的内存资源
     */
    private int estimateRequiredMemory(Task task) {
        switch (task.getTaskType()) {
            case RISK_ANALYSIS:
            case ML_ANALYSIS:
                return 2048;  // 需要较多内存
            case BATCH_ANALYSIS:
                return 1024;
            case GENERATE_RISK_REPORT:
                return 512;
            default:
                return 256;
        }
    }

    /**
     * 设置并发限制
     */
    private void setConcurrencyLimits(Task task) {
        if (task.getMaxConcurrent() == null) {
            // 根据任务类型设置默认并发限制
            int maxConcurrent = switch (task.getTaskType()) {
                case QUERY_USER_INFO, QUERY_CHAT_HISTORY -> 5;
                case RISK_ANALYSIS, ML_ANALYSIS -> 2;
                case BATCH_ANALYSIS -> 3;
                default -> 1;
            };
            task.setMaxConcurrent(maxConcurrent);
        }
    }

    /**
     * 添加资源元数据
     */
    private void addResourceMetadata(Task task) {
        if (task.getMetadata() == null) {
            task.setMetadata(new HashMap<>());
        }

        task.getMetadata().put("resource_allocation_time", System.currentTimeMillis());
        task.getMetadata().put("resource_version", "1.0");

        // 添加资源标签
        List<String> resourceTags = new ArrayList<>();
        if (task.getTaskType().getDisplayName().contains("ANALYSIS")) {
            resourceTags.add("analysis");
            resourceTags.add("compute_intensive");
        }
        if (task.getTaskType().getDisplayName().contains("QUERY")) {
            resourceTags.add("query");
            resourceTags.add("io_intensive");
        }

        task.getMetadata().put("resource_tags", resourceTags);
    }

    /**
     * 资源池
     */
    private static class ResourcePool {
        private final int maxConcurrentTasks;
        private final int totalCpuCores;
        private final int totalMemoryMB;

        private int usedConcurrentTasks = 0;
        private int usedCpuCores = 0;
        private int usedMemoryMB = 0;

        public ResourcePool(int maxConcurrentTasks, int totalCpuCores, int totalMemoryMB) {
            this.maxConcurrentTasks = maxConcurrentTasks;
            this.totalCpuCores = totalCpuCores;
            this.totalMemoryMB = totalMemoryMB;
        }

        public synchronized boolean reserve(int cpuCores, int memoryMB) {
            if (usedConcurrentTasks >= maxConcurrentTasks) {
                return false;
            }
            if (usedCpuCores + cpuCores > totalCpuCores) {
                return false;
            }
            if (usedMemoryMB + memoryMB > totalMemoryMB) {
                return false;
            }

            usedConcurrentTasks++;
            usedCpuCores += cpuCores;
            usedMemoryMB += memoryMB;
            return true;
        }

        public synchronized void release(int cpuCores, int memoryMB) {
            usedConcurrentTasks--;
            usedCpuCores -= cpuCores;
            usedMemoryMB -= memoryMB;

            // 确保不会出现负数
            usedConcurrentTasks = Math.max(0, usedConcurrentTasks);
            usedCpuCores = Math.max(0, usedCpuCores);
            usedMemoryMB = Math.max(0, usedMemoryMB);
        }
    }
}

