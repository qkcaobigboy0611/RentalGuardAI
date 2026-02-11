/**
 * @author qkcao
 * @date 2026/2/10 15:49
 */
package com.rental.guard.ai.domain.dto;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 定义 ReAct 思考的结构化输出
 */
@Data
public class AgentDecision {

    @Description("当前决策动作，只能是 'tool_call' (需要获取更多信息) 或 'final_answer' (信息已足够或无法获取)")
    private String action;

    @Description("思考过程和判断依据")
    private String reasoning;

    @Description("需要调用的工具列表，仅当 action 为 'tool_call' 时有效")
    private List<ToolCallRequest> toolCalls;

    @Description("最终回复给用户的内容，仅当 action 为 'final_answer' 时有效")
    private String answer;

    @Description("对当前决策的置信度 (0.0 - 1.0)")
    private Double confidence;

    @Data
    public static class ToolCallRequest {
        @Description("要调用的工具名称")
        private String tool;

        @Description("工具调用的参数字典")
        private Map<String, Object> parameters;
    }
}
