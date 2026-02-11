/**
 * @author qkcao
 * @date 2026/2/10 15:50
 */
package com.rental.guard.ai.domain.service;

import com.rental.guard.ai.domain.dto.AgentDecision;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * LangChain4j AI 服务接口
 * 自动处理 Prompt 构建和 JSON 解析
 */
public interface ReActAgent {

    @SystemMessage("""
        你是一个租房风险分析专家智能体。
        
        # 决策策略
        请基于用户输入和上下文历史，决定下一步行动：
        1. **信息不足时**：选择 `tool_call` 调用相关工具获取信息（如查询合同、搜索房源、风险检测）。
        2. **信息充足时**：选择 `final_answer` 直接回答用户。
        3. **无法决策时**：如果多次尝试无果，选择 `final_answer` 并说明情况。
        
        # 注意事项
        - 不要重复调用相同的工具获取相同的信息。
        - 必须严格遵循输出结构。
        """)
    @UserMessage("{{context}}")
    AgentDecision think(@V("context") String context);
}
