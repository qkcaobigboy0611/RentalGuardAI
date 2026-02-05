/**
 * @author qkcao
 * @date 2026/2/4 18:28
 */
package com.rental.guard.ai.domain.service.v1.tool;

import com.rental.guard.ai.domain.dto.v1.AgentResponse;
import com.rental.guard.ai.domain.dto.v1.SessionManager;
import com.rental.guard.ai.domain.service.v1.RAGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * RAG检索工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagRetrievalTool implements AgentTool {

    private final RAGService ragService;

    @Override
    public String getName() {
        return "rag_retrieval";
    }

    @Override
    public String getDescription() {
        return "从本地知识库检索相关的法律条文、合同模板、风险案例等文档。当需要专业法律知识或案例参考时使用此工具。";
    }

    @Override
    public String getParameters() {
        return """
                {
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "检索查询，可以是问题或关键词"
                        },
                        "scenario": {
                            "type": "string",
                            "description": "场景类型，如合同审核、距离欺诈等",
                            "enum": ["合同审核", "距离欺诈", "租金欺诈", "霸王条款"]
                        },
                        "top_k": {
                            "type": "integer",
                            "description": "返回的文档数量",
                            "default": 5
                        }
                    },
                    "required": ["query", "scenario"]
                }
                """;
    }

    @Override
    public CompletableFuture<Object> execute(Map<String, Object> parameters, SessionManager session) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String query = (String) parameters.get("query");
                String scenario = (String) parameters.get("scenario");
                int topK = parameters.containsKey("top_k") ?
                        (Integer) parameters.get("top_k") : 5;

                log.info("执行RAG检索工具: query={}, scenario={}, topK={}", query, scenario, topK);

                // 增强查询
                String enhancedQuery = ragService.enhanceQuery(query, scenario);

                // 检索文档
                List<AgentResponse.RetrievedDocument> documents =
                        ragService.retrieveRelevantDocuments(enhancedQuery, scenario);

                // 限制返回数量
                if (documents.size() > topK) {
                    documents = documents.subList(0, topK);
                }

                Map<String, Object> resultMap = Map.of(
                        "tool", getName(),
                        "query", query,
                        "enhanced_query", enhancedQuery,
                        "documents", documents,
                        "document_count", documents.size(),
                        "timestamp", System.currentTimeMillis()
                );

                return resultMap;
            } catch (Exception e) {
                log.error("执行RAG检索工具失败", e);
                return Map.of("error", e.getMessage());
            }
        });
    }

    @Override
    public boolean shouldInvoke(String userInput, String scenario) {
        // 当用户询问法律、合同、条款等专业问题时调用
        String lowerInput = userInput.toLowerCase();
        return lowerInput.contains("法律") ||
                lowerInput.contains("条款") ||
                lowerInput.contains("合同") ||
                lowerInput.contains("案例") ||
                lowerInput.contains("规定") ||
                lowerInput.contains("法规");
    }
}
