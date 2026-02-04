/**
 * @author qkcao
 * @date 2026/1/27 10:35
 */
package com.rental.guard.ai.domain.service.v1;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * 行动模块：执行决策并产生动作
 */
@Slf4j
public class ActionModule {

    public AgentAction executeAction(AgentDecision decision, ConversationContext context) {
        AgentAction action = new AgentAction();
        action.setDecision(decision);
        action.setTimestamp(LocalDateTime.now());

        switch (decision.getStrategy()) {
            case BLOCK_AND_WARN:
                action.setType(ActionType.BLOCK);
                action.setMessage("对话已被阻断，检测到高风险欺诈行为");
                action.setLogLevel(LogLevel.ERROR);
                break;

            case WARN_AND_MONITOR:
                action.setType(ActionType.WARN);
                action.setMessage("请注意：检测到中等风险，建议保持警惕");
                action.setLogLevel(LogLevel.WARN);
                break;

            case CONTINUE:
                action.setType(ActionType.CONTINUE);
                action.setMessage("对话继续");
                action.setLogLevel(LogLevel.INFO);
                break;
        }

        // 记录审计日志
        logAudit(action, context);

        return action;
    }

    private void logAudit(AgentAction action, ConversationContext context) {
        String logMsg = String.format("会话[%s] - 动作: %s - 风险等级: %s",
                context.getSessionId(),
                action.getType(),
                action.getDecision().getRiskLevel());

        switch (action.getLogLevel()) {
            case ERROR:
                log.error(logMsg);
                break;
            case WARN:
                log.warn(logMsg);
                break;
            default:
                log.info(logMsg);
        }
    }

}
