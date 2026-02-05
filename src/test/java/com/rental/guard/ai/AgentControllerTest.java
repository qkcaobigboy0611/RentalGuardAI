/**
 * @author qkcao
 * @date 2026/1/29 11:42
 */
package com.rental.guard.ai;

import com.rental.guard.ai.domain.dto.v1.AgentResponse;
import com.rental.guard.ai.domain.service.v1.AgentOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.CompletableFuture;

@Slf4j
@SpringBootTest
public class AgentControllerTest {
    @Autowired
    private AgentOrchestrator agentOrchestrator;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    public void aa() {
        String sessionId = "test_session_123";
        String userInput = "合同里说断租不退押金，这合法吗？";
        CompletableFuture<AgentResponse> agentResponseCompletableFuture = agentOrchestrator.processRequestV2(sessionId, userInput, null);
        System.out.println(agentResponseCompletableFuture);
    }

    @Test
    public void demo() {
        redisTemplate.opsForValue().set("name", "RentalGuard");
        Object val = redisTemplate.opsForValue().get("name");
        System.out.println(val);
    }
}
