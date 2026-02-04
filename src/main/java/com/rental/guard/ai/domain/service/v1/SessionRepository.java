/**
 * @author qkcao
 * @date 2026/1/28 15:29
 */
package com.rental.guard.ai.domain.service.v1;

import com.rental.guard.ai.domain.dto.v1.SessionManager;
import com.rental.guard.ai.domain.dto.v1.SessionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 会话存储库 - Redis实现
 */
@Slf4j
@Component
public class SessionRepository {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String USER_SESSIONS_KEY = "user:sessions:";
    private static final long DEFAULT_TTL = 1800; // 30分钟


    /**
     * 创建新会话
     */
    public SessionManager createSession(String userId) {
        SessionManager session = new SessionManager();
        session.setUserId(userId);
        session.setSessionId(generateSessionId(userId));
        session.setState(SessionState.ACTIVE);
        session.setCreatedAt(new Date());
        session.setLastActiveAt(new Date());

        saveSession(session);

        // 更新用户会话列表
        String userSessionsKey = USER_SESSIONS_KEY + userId;
        redisTemplate.opsForSet().add(userSessionsKey, session.getSessionId());
        redisTemplate.expire(userSessionsKey, 7, TimeUnit.DAYS); // 用户会话列表保存7天

        log.info("创建新会话: {}", session.getSessionId());
        return session;
    }

    /**
     * 获取或创建会话
     */
    public SessionManager getOrCreateSession(String sessionId) {
        SessionManager session = getSession(sessionId);
        if (session == null) {
            // 从sessionId中提取userId（格式：sess_userId_timestamp_uuid）
            String userId = extractUserIdFromSessionId(sessionId);
            if (userId == null) {
                userId = "anonymous_" + System.currentTimeMillis();
            }
            session = createSession(userId);
        }
        return session;
    }

    /**
     * 获取会话
     */
    public SessionManager getSession(String sessionId) {
        try {
            String key = SESSION_KEY_PREFIX + sessionId;

            SessionManager session = (SessionManager) redisTemplate.opsForValue().get(key);

            if (session != null) {
                // 更新最后活动时间
                session.setLastActiveAt(new Date());

                // 如果是活跃会话，续期TTL
                if (session.getState() == SessionState.ACTIVE) {
                    redisTemplate.expire(key, DEFAULT_TTL, TimeUnit.SECONDS);
                }
            }

            return session;
        } catch (Exception e) {
            log.error("获取会话失败: {}", sessionId, e);
            return null;
        }
    }

    /**
     * 保存会话
     */
    public void saveSession(SessionManager session) {
        try {
            String key = SESSION_KEY_PREFIX + session.getSessionId();

            // 更新最后活动时间
            session.setLastActiveAt(new Date());

            // 保存到Redis
            redisTemplate.opsForValue().set(key, session);

            log.debug("保存会话: {}", session.getSessionId());
        } catch (Exception e) {
            log.error("保存会话失败: {}", session.getSessionId(), e);
        }
    }

    /**
     * 删除会话
     */
    public boolean deleteSession(String sessionId) {
        try {
            SessionManager session = getSession(sessionId);
            if (session != null) {
                // 从用户会话列表中移除
                String userSessionsKey = USER_SESSIONS_KEY + session.getUserId();
                redisTemplate.opsForSet().remove(userSessionsKey, sessionId);
            }

            // 删除会话本身
            String key = SESSION_KEY_PREFIX + sessionId;
            return Boolean.TRUE.equals(redisTemplate.delete(key));
        } catch (Exception e) {
            log.error("删除会话失败: {}", sessionId, e);
            return false;
        }
    }

    /**
     * 获取用户的所有会话
     */
    public List<SessionManager> getUserSessions(String userId) {
        try {
            String userSessionsKey = USER_SESSIONS_KEY + userId;
            Set<Object> sessionIds = redisTemplate.opsForSet().members(userSessionsKey);

            if (sessionIds == null || sessionIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<SessionManager> sessions = new ArrayList<>();
            for (Object sessionIdObj : sessionIds) {
                String sessionId = (String) sessionIdObj;
                SessionManager session = getSession(sessionId);
                if (session != null) {
                    sessions.add(session);
                }
            }

            return sessions;
        } catch (Exception e) {
            log.error("获取用户会话失败: {}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取活跃会话数量
     */
    public long getActiveSessionCount() {
        try {
            // 注意：在生产环境中，这可能需要使用SCAN命令
            Set<String> keys = redisTemplate.keys(SESSION_KEY_PREFIX + "*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("获取活跃会话数量失败", e);
            return 0;
        }
    }

    /**
     * 清理过期会话
     */
    public int cleanupExpiredSessions() {
        try {
            Set<String> keys = redisTemplate.keys(SESSION_KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return 0;
            }

            int cleaned = 0;
            for (String key : keys) {
                SessionManager session = (SessionManager) redisTemplate.opsForValue().get(key);
                if (session != null && session.isExpired()) {
                    // 标记为过期并保存
                    session.setState(SessionState.EXPIRED);
                    saveSession(session);

                    // 设置短期TTL后自动删除
                    redisTemplate.expire(key, 300, TimeUnit.SECONDS); // 5分钟后删除

                    cleaned++;
                    log.info("清理过期会话: {}", session.getSessionId());
                }
            }

            return cleaned;
        } catch (Exception e) {
            log.error("清理过期会话失败", e);
            return 0;
        }
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId(String userId) {
        return String.format("sess_%s_%d_%s",
                userId,
                System.currentTimeMillis(),
                UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * 从会话ID中提取用户ID
     */
    private String extractUserIdFromSessionId(String sessionId) {
        if (sessionId != null && sessionId.startsWith("sess_")) {
            String[] parts = sessionId.split("_");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return null;
    }

    /**
     * 获取会话统计信息
     */
    public Map<String, Object> getSessionStatistics() {
        Map<String, Object> stats = new HashMap<>();

        try {
            Set<String> keys = redisTemplate.keys(SESSION_KEY_PREFIX + "*");
            if (keys != null) {
                stats.put("total_sessions", keys.size());

                // 按状态统计
                Map<SessionState, Integer> stateCounts = new HashMap<>();
                for (String key : keys) {
                    SessionManager session = (SessionManager) redisTemplate.opsForValue().get(key);
                    if (session != null) {
                        stateCounts.put(session.getState(),
                                stateCounts.getOrDefault(session.getState(), 0) + 1);
                    }
                }
                stats.put("sessions_by_state", stateCounts);
            }

            stats.put("timestamp", LocalDateTime.now().toString());
            stats.put("repository", "Redis");

        } catch (Exception e) {
            log.error("获取会话统计信息失败", e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }
}
