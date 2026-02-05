/**
 * @author qkcao
 * @date 2026/2/5 14:59
 */
package com.rental.guard.ai.domain.service.v1.tool;

import com.rental.guard.ai.domain.dto.v1.SessionManager;
import com.rental.guard.ai.infrastructure.mapper.FraudKnowledgeGraphMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 知识图谱工具
 */
@Component("knowledge_graph_check")
@Slf4j
@RequiredArgsConstructor
public class KnowledgeGraphTool implements AgentTool {

    private final FraudKnowledgeGraphMapper graphMapper;

    @Override
    public String getName() {
        return "knowledge_graph_check";
    }

    @Override
    public String getDescription() {
        return "用于查询实体（电话号码、中介公司名、小区地址、微信号）是否存在已知的欺诈关联网络中。" +
                "当你怀疑某个主体有风险，或者用户提供了具体的联系方式/地址时，必须调用此工具。";
    }

    @Override
    public String getParameters() {
        return null;
    }

    @Override
    public CompletableFuture<Object> execute(Map<String, Object> params, SessionManager session) {
        return CompletableFuture.supplyAsync(() -> {
            String entityValue = (String) params.get("entity_value");
            Map<String, Object> result = new HashMap<>();

            if (entityValue == null) {
                result.put("error", "缺少参数 entity_value");
                return result;
            }

            // 1. 查找核心节点
            Map<String, Object> node = graphMapper.findNodeByValue(entityValue);
            if (node == null) {
                result.put("found", false);
                result.put("message", "图谱中未收录该实体，暂无直接风险记录。");
                return result;
            }

            // 2. 查找关联网络 (1跳邻居)
            Long nodeId = (Long) node.get("node_id");
            List<Map<String, Object>> neighbors = graphMapper.findNeighbors(nodeId);

            // 3. 构建分析结果
            boolean isHighRisk = "HIGH".equals(node.get("risk_level"));
            StringBuilder analysis = new StringBuilder();
            analysis.append(String.format("实体 [%s] 存在于数据库中，自身风险等级: %s, 标签: %s。\n",
                    entityValue, node.get("risk_level"), node.get("tags")));

            if (!neighbors.isEmpty()) {
                analysis.append("关联网络发现:\n");
                for (Map<String, Object> nb : neighbors) {
                    String risk = (String) nb.get("risk_level");
                    if ("HIGH".equals(risk)) {
                        isHighRisk = true; // 关联了高风险节点，传染风险
                    }
                    analysis.append(String.format("- 通过关系 [%s] 关联到 [%s] (类型: %s, 风险: %s)\n",
                            nb.get("rel_desc"), nb.get("node_value"), nb.get("node_type"), risk));
                }
            }

            result.put("found", true);
            result.put("is_high_risk", isHighRisk);
            result.put("analysis_text", analysis.toString());
            return result;
        });
    }

    @Override
    public boolean shouldInvoke(String userInput, String scenario) {
        return AgentTool.super.shouldInvoke(userInput, scenario);
    }
}
