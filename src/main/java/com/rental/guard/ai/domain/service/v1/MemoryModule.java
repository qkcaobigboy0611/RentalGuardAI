/**
 * @author qkcao
 * @date 2026/1/27 10:24
 */
package com.rental.guard.ai.domain.service.v1;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记忆模块：管理对话历史和上下文
 */
public class MemoryModule {
    private final Map<String, ConversationContext> sessionContexts = new ConcurrentHashMap<>();

    public ConversationContext getOrCreateContext(String sessionId) {
        return sessionContexts.computeIfAbsent(sessionId,
                k -> new ConversationContext(sessionId));
    }

    public void removeContext(String sessionId) {
        sessionContexts.remove(sessionId);
    }

    public ConversationContext getContext(String sessionId) {
        return sessionContexts.get(sessionId);
    }

}
