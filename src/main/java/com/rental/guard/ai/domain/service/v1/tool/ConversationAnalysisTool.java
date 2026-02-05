/**
 * @author qkcao
 * @date 2026/2/4 18:29
 */
package com.rental.guard.ai.domain.service.v1.tool;


import com.rental.guard.ai.domain.dto.v1.Message;
import com.rental.guard.ai.domain.dto.v1.SessionManager;
import com.rental.guard.ai.domain.service.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 会话分析工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationAnalysisTool implements AgentTool {

    private final LLMService llmService;

    @Override
    public String getName() {
        return "conversation_analysis";
    }

    @Override
    public String getDescription() {
        return "分析当前对话历史，识别关键信息、矛盾点、未解决问题等。当需要理解对话上下文时使用此工具。";
    }

    @Override
    public String getParameters() {
        return """
                {
                    "type": "object",
                    "properties": {
                        "analysis_type": {
                            "type": "string",
                            "description": "分析类型",
                            "enum": ["summary", "contradictions", "missing_info", "key_points"]
                        },
                        "focus": {
                            "type": "string",
                            "description": "关注的重点"
                        }
                    },
                    "required": ["analysis_type"]
                }
                """;
    }

    @Override
    public CompletableFuture<Object> execute(Map<String, Object> parameters, SessionManager session) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String analysisType = (String) parameters.get("analysis_type");
                String focus = parameters.containsKey("focus") ?
                        (String) parameters.get("focus") : null;

                log.info("执行对话分析工具: type={}, focus={}", analysisType, focus);

                List<Message> messages = session.getMessageHistory();
                String conversationText = messages.stream()
                        .map(msg -> msg.getSender() + ": " + msg.getContentAsString())
                        .collect(Collectors.joining("\n"));

                String prompt = buildAnalysisPrompt(analysisType, focus, conversationText);
                String analysisResult = llmService.generate(prompt, Map.of());

                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("tool", getName());
                resultMap.put("analysis_type", analysisType);
                resultMap.put("focus", focus);
                resultMap.put("analysis_result", analysisResult);
                resultMap.put("message_count", messages.size());
                resultMap.put("timestamp", System.currentTimeMillis());

                return resultMap;
            } catch (Exception e) {
                log.error("执行对话分析工具失败", e);
                return Map.of("error", e.getMessage());
            }
        });
    }

    private String buildAnalysisPrompt(String type, String focus, String conversation) {
        switch (type) {
            case "summary":
                return String.format("""
                        请总结以下对话的主要内容，提取关键信息：
                                            
                        %s
                                            
                        总结要求：
                        1. 提取主要讨论的问题
                        2. 识别涉及的风险点
                        3. 总结已给出的建议
                        4. 用简洁的语言表达
                        5. 不超过200字
                        """, conversation);

            case "contradictions":
                return String.format("""
                        请分析以下对话中是否存在矛盾或不一致的信息：
                                            
                        %s
                                            
                        分析要求：
                        1. 识别前后矛盾的信息点
                        2. 指出可能的事实错误
                        3. 标记需要澄清的内容
                        4. 提供验证建议
                        """, conversation);

            case "missing_info":
                String focusPart = focus != null ?
                        String.format("重点关注：%s\n", focus) : "";
                return String.format("""
                        请分析以下对话中缺失的关键信息：
                                            
                        %s
                        %s
                                            
                        分析要求：
                        1. 识别解决问题所需但缺失的信息
                        2. 按重要性排序
                        3. 提供具体的问题建议
                        """, conversation, focusPart);

            case "key_points":
            default:
                return String.format("""
                        请提取以下对话的关键信息点：
                                            
                        %s
                                            
                        提取要求：
                        1. 列出所有重要事实
                        2. 标记风险点和机会点
                        3. 识别用户的核心需求
                        4. 用条目化方式呈现
                        """, conversation);
        }
    }
}
