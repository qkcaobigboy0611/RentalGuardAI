/**
 * @author qkcao
 * @date 2026/2/11 11:37
 */
package com.rental.guard.ai.domain.service.Scenario;

import com.rental.guard.ai.domain.dto.v1.AgentResponse;
import com.rental.guard.ai.domain.dto.v1.SessionManager;

import java.util.List;

public interface ScenarioHandler {
    void process(AgentResponse response, SessionManager session, List<AgentResponse.RetrievedDocument> docs);
}
