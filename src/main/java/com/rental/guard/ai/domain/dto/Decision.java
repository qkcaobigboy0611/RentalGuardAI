/**
 * @author qkcao
 * @date 2026/2/10 11:30
 */
package com.rental.guard.ai.domain.dto;

// Decision.java - 使用LangChain4j的@StructuredPrompt和注解
import dev.langchain4j.model.output.structured.Description;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Decision {
    public enum ActionType {
        TOOL_CALL,
        FINAL_ANSWER
    }

    @Description("下一步行动类型：TOOL_CALL 或 FINAL_ANSWER")
    private ActionType action;

    @Description("思考推理过程，解释为何选择此行动")
    private String reasoning;

    @Description("置信度，0到1之间的小数")
    private Double confidence;

    @Description("当action为TOOL_CALL时，需要调用的工具列表")
    private List<ToolCall> toolCalls;

    @Description("当action为FINAL_ANSWER时，最终回复内容")
    private String answer;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        @Description("工具名称，必须是availableTools中的一种")
        private String tool;

        @Description("工具调用参数，JSON对象")
        private Map<String, Object> parameters;

        @Description("调用此工具的目的说明")
        private String purpose;
    }
}
