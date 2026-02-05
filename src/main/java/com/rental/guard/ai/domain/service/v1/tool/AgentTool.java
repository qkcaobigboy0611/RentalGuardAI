/**
 * @author qkcao
 * @date 2026/2/4 18:04
 */
package com.rental.guard.ai.domain.service.v1.tool;

import com.rental.guard.ai.domain.dto.v1.SessionManager;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 工具接口 - 所有智能体可调用的工具实现此接口
 */
public interface AgentTool {

    /**
     * 工具名称
     */
    String getName();

    /**
     * 工具描述（用于LLM决定是否调用）
     */
    String getDescription();

    /**
     * 工具参数模式（JSON Schema格式）
     */
    String getParameters();

    /**
     * 执行工具
     *
     * @param parameters 工具参数
     * @param session    会话上下文
     * @return 执行结果
     */
    CompletableFuture<Object> execute(Map<String, Object> parameters, SessionManager session);

    /**
     * 是否需要调用此工具
     */
    default boolean shouldInvoke(String userInput, String scenario) {
        return true;
    }
}
