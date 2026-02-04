/**
 * @author qkcao
 * @date 2026/1/28 17:33
 */
package com.rental.guard.ai.domain.service.v1;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 会话上下文
 */
@Data
public class SessionContext {
    private String currentIntent;
    private Map<String, String> extractedEntities = new HashMap<>();
    private List<String> pendingActions = new ArrayList<>();
    private Map<String, Object> contextData = new HashMap<>();
    private String conversationFlow; // 对话流程状态
    private Date contextCreatedAt = new Date();

    public void addEntity(String key, String value) {
        extractedEntities.put(key, value);
    }

    public void addPendingAction(String action) {
        pendingActions.add(action);
    }

    public void clearPendingActions() {
        pendingActions.clear();
    }
}
