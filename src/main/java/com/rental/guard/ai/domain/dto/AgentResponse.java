/**
 * @author qkcao
 * @date 2026/1/22 17:33
 */
package com.rental.guard.ai.domain.dto;

import com.rental.guard.ai.domain.service.IntentRecognitionModule;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentResponse {
    private boolean success;
    private Object data;
    private IntentRecognitionModule.AgentIntent intent;
    private String message;
    private boolean requiresConfirmation;

    public static AgentResponse success(Object data, IntentRecognitionModule.AgentIntent intent) {
        return AgentResponse.builder()
                .success(true)
                .data(data)
                .intent(intent)
                .build();
    }

    public static AgentResponse confirmationRequired(IntentRecognitionModule.AgentIntent intent) {
        return AgentResponse.builder()
                .success(false)
                .intent(intent)
                .requiresConfirmation(true)
                .message("请确认您的意图：" + intent.getIntentType().getDisplayName())
                .build();
    }
}
