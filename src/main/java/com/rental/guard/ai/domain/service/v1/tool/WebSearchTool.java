/**
 * @author qkcao
 * @date 2026/2/4 18:26
 */
package com.rental.guard.ai.domain.service.v1.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.guard.ai.domain.dto.v1.SessionManager;
import com.rental.guard.ai.domain.service.v1.ZhipuSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 联网搜索工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchTool implements AgentTool {

    private final ZhipuSearchService searchService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "从互联网搜索最新的租房市场信息、法律法规、新闻动态等。当需要最新、实时信息时使用此工具。";
    }

    @Override
    public String getParameters() {
        return """
                {
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "搜索关键词，最好是具体的问题或需要查询的主题"
                        },
                        "max_results": {
                            "type": "integer",
                            "description": "返回的最大结果数",
                            "default": 5
                        }
                    },
                    "required": ["query"]
                }
                """;
    }

    @Override
    public CompletableFuture<Object> execute(Map<String, Object> parameters, SessionManager session) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String query = (String) parameters.get("query");
                String scenario = session.getCurrentScenario();

                log.info("执行联网搜索工具: query={}, scenario={}", query, scenario);

                String result = searchService.searchInternetAsync(query, scenario);

                Map<String, Object> resultMap = Map.of(
                        "tool", getName(),
                        "query", query,
                        "results", result,
                        "timestamp", System.currentTimeMillis()
                );

                return resultMap;
            } catch (Exception e) {
                log.error("执行联网搜索工具失败", e);
                return Map.of("error", e.getMessage());
            }
        });
    }

    @Override
    public boolean shouldInvoke(String userInput, String scenario) {
        // 当用户询问最新信息、新闻、实时数据时调用
        String lowerInput = userInput.toLowerCase();
        return lowerInput.contains("最新") ||
                lowerInput.contains("新闻") ||
                lowerInput.contains("最近") ||
                lowerInput.contains("实时") ||
                lowerInput.contains("市场行情");
    }
}
