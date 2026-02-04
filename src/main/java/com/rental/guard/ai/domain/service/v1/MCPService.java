/**
 * @author qkcao
 * @date 2026/1/28 15:27
 */
package com.rental.guard.ai.domain.service.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.guard.ai.domain.dto.v1.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * MCP服务 - 模型上下文协议实现
 * 负责管理模型上下文、工具调用和结构化输出
 */
@Slf4j
@Service
public class MCPService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${mcp.default.context.size:10}")
    private int defaultContextSize;

    // 上下文缓存
    private final Map<String, List<Map<String, Object>>> contextCache = new HashMap<>();

    @Autowired
    public MCPService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 构建模型上下文
     */
    public Map<String, Object> buildModelContext(String sessionId, List<Message> messageNos,
                                                 String scenario, Map<String, Object> additionalContext) {
        Map<String, Object> context = new HashMap<>();

        // 1. 系统指令
        context.put("system", buildSystemInstructions(scenario));

        // 2. 对话历史
        context.put("history", buildConversationHistory(messageNos));

        // 3. 当前查询
        context.put("query", extractCurrentQuery(messageNos));

        // 4. 场景特定上下文
        context.put("scenario_context", buildScenarioContext(scenario));

        // 5. 工具可用性
        context.put("available_tools", getAvailableTools(scenario));

        // 6. 输出格式要求
        context.put("output_format", getOutputFormatRequirements(scenario));

        // 7. 合并附加上下文
        if (additionalContext != null) {
            context.putAll(additionalContext);
        }

        // 缓存上下文
        cacheContext(sessionId, context);

        return context;
    }

    /**
     * 构建系统指令
     */
    private String buildSystemInstructions(String scenario) {
        Map<String, String> scenarioInstructions = new HashMap<>();
        scenarioInstructions.put("合同审核", """
                你是专业的租房合同审核专家。请仔细分析合同条款，重点关注：
                1. 押金条款的合理性和明确性
                2. 维修责任和损坏赔偿标准
                3. 解约条件和违约责任
                4. 条款的合法性和公平性
                                
                请提供具体的修改建议和法律依据。
                """);

        scenarioInstructions.put("距离欺诈", """
                你是房产信息验证专家。请分析距离宣传的真实性：
                1. 验证实际距离和宣传距离的差异
                2. 评估虚假宣传的法律风险
                3. 提供验证方法和谈判策略
                4. 引用相关法律条款
                                
                请基于数据和事实进行分析。
                """);

        scenarioInstructions.put("租金欺诈", """
                你是租金市场分析专家。请评估租金价格的合理性：
                1. 对比周边市场价
                2. 分析价格构成和隐形费用
                3. 提供砍价策略和依据
                4. 识别价格欺诈风险
                                
                请提供数据支持的分析。
                """);

        scenarioInstructions.put("霸王条款", """
                你是消费者权益保护专家。请识别违法条款：
                1. 分析条款的合法性和公平性
                2. 识别霸王条款和格式条款问题
                3. 提供法律维权建议
                4. 建议修改方案
                                
                请坚决维护消费者权益。
                """);

        String baseInstructions = """
                你是一个专业的租房风险分析智能助手。请遵循以下原则：
                1. 基于事实和法律进行分析
                2. 提供具体、可操作的建议
                3. 明确风险等级和置信度
                4. 引用可靠的法律和数据来源
                5. 保持专业、中立、客观的立场
                """;

        return baseInstructions + "\n\n" +
                scenarioInstructions.getOrDefault(scenario, "请根据用户问题提供专业分析。");
    }

    /**
     * 构建对话历史
     */
    private List<Map<String, Object>> buildConversationHistory(List<Message> messageNos) {
        if (messageNos == null || messageNos.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> history = new ArrayList<>();

        // 取最近N条消息
        List<Message> recentMessages = messageNos.size() > defaultContextSize ?
                messageNos.subList(messageNos.size() - defaultContextSize, messageNos.size()) :
                messageNos;

        for (Message msg : recentMessages) {
            Map<String, Object> historyEntry = new HashMap<>();
            historyEntry.put("role", msg.getSender().toLowerCase());
            historyEntry.put("content", msg.getContentAsString());
            historyEntry.put("timestamp", msg.getTimestamp().toString());

            if (msg.getMetadata() != null && !msg.getMetadata().isEmpty()) {
                historyEntry.put("metadata", msg.getMetadata());
            }

            history.add(historyEntry);
        }

        return history;
    }

    /**
     * 提取当前查询
     */
    private Map<String, Object> extractCurrentQuery(List<Message> messageNos) {
        if (messageNos == null || messageNos.isEmpty()) {
            return Collections.emptyMap();
        }

        Message lastMessageNo = messageNos.get(messageNos.size() - 1);
        Map<String, Object> query = new HashMap<>();
        query.put("text", lastMessageNo.getContentAsString());
        query.put("type", lastMessageNo.getMessageType().name());
        query.put("timestamp", lastMessageNo.getTimestamp().toString());

        return query;
    }

    /**
     * 构建场景特定上下文
     */
    private Map<String, Object> buildScenarioContext(String scenario) {
        Map<String, Object> context = new HashMap<>();
        context.put("scenario", scenario);

        // 场景特定的参数和配置
        switch (scenario) {
            case "合同审核":
                context.put("focus_areas", Arrays.asList("押金条款", "维修责任", "解约条件", "违约责任"));
                context.put("legal_references", Arrays.asList("民法典", "合同法", "消费者权益保护法"));
                context.put("risk_indicators", Arrays.asList("模糊表述", "不公平条款", "违法内容"));
                break;

            case "距离欺诈":
                context.put("verification_methods", Arrays.asList("地图测量", "实地考察", "官方数据"));
                context.put("legal_basis", Arrays.asList("广告法", "消费者权益保护法"));
                context.put("compensation_options", Arrays.asList("价格调整", "合同解除", "赔偿损失"));
                break;

            case "租金欺诈":
                context.put("data_sources", Arrays.asList("市场均价", "历史数据", "周边比较"));
                context.put("negotiation_strategies", Arrays.asList("数据对比", "价格分析", "条件交换"));
                context.put("hidden_fees", Arrays.asList("物业费", "维修费", "管理费"));
                break;

            case "霸王条款":
                context.put("illegal_clauses", Arrays.asList("不退押金", "单方解约权", "免责条款"));
                context.put("legal_actions", Arrays.asList("要求修改", "拒绝签署", "行政投诉", "法律诉讼"));
                context.put("protection_laws", Arrays.asList("民法典第四百九十七条", "消费者权益保护法第二十六条"));
                break;
        }

        return context;
    }

    /**
     * 获取可用工具
     */
    private List<Map<String, Object>> getAvailableTools(String scenario) {
        List<Map<String, Object>> tools = new ArrayList<>();

        // 通用工具
        tools.add(createTool("legal_lookup", "法律条文查询", "查询相关法律条文和司法解释"));
        tools.add(createTool("market_data", "市场数据查询", "查询租金市场数据和趋势"));
        tools.add(createTool("document_analysis", "文档分析", "分析上传的合同文档"));
        tools.add(createTool("risk_assessment", "风险评估", "评估特定条款的风险等级"));

        // 场景特定工具
        switch (scenario) {
            case "合同审核":
                tools.add(createTool("clause_review", "条款审查", "审查合同条款的合法性和公平性"));
                tools.add(createTool("red_flag_detection", "风险标识检测", "检测合同中的风险标识"));
                break;

            case "距离欺诈":
                tools.add(createTool("distance_verification", "距离验证", "验证实际距离和宣传距离"));
                tools.add(createTool("map_integration", "地图集成", "集成地图数据进行分析"));
                break;

            case "租金欺诈":
                tools.add(createTool("price_comparison", "价格对比", "对比周边房源价格"));
                tools.add(createTool("hidden_fee_detection", "隐形费用检测", "检测可能的隐形费用"));
                break;

            case "霸王条款":
                tools.add(createTool("illegality_check", "违法性检查", "检查条款的违法性"));
                tools.add(createTool("remediation_suggestion", "补救建议", "提供条款修改建议"));
                break;
        }

        return tools;
    }

    /**
     * 创建工具描述
     */
    private Map<String, Object> createTool(String name, String displayName, String description) {
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", name);
        tool.put("display_name", displayName);
        tool.put("description", description);
        tool.put("available", true);
        return tool;
    }

    /**
     * 获取输出格式要求
     */
    private Map<String, Object> getOutputFormatRequirements(String scenario) {
        Map<String, Object> format = new HashMap<>();
        format.put("required_sections", Arrays.asList("risk_level", "analysis", "recommendations"));
        format.put("style", "专业、清晰、可操作");

        if ("合同审核".equals(scenario)) {
            format.put("specific_requirements", Arrays.asList(
                    "必须引用具体法律条文",
                    "必须提供条款修改建议",
                    "必须明确风险等级"
            ));
        }

        return format;
    }

    /**
     * 缓存上下文
     */
    private void cacheContext(String sessionId, Map<String, Object> context) {
        if (!contextCache.containsKey(sessionId)) {
            contextCache.put(sessionId, new ArrayList<>());
        }

        List<Map<String, Object>> sessionContexts = contextCache.get(sessionId);
        sessionContexts.add(context);

        // 限制缓存大小
        if (sessionContexts.size() > 20) {
            sessionContexts.remove(0);
        }
    }

    /**
     * 获取缓存的上下文
     */
    public List<Map<String, Object>> getCachedContext(String sessionId) {
        return contextCache.getOrDefault(sessionId, Collections.emptyList());
    }

    /**
     * 清除上下文缓存
     */
    public void clearContextCache(String sessionId) {
        contextCache.remove(sessionId);
    }

    /**
     * 调用MCP服务器
     * 如果你直接把原始数据传给 GPT-4，可能会面临上下文过长或缺乏特定领域知识的问题。通过这个 callMCPServer：
     * 解耦：复杂的上下文处理逻辑被抽离到了专门的 MCP 服务器中。
     * 动态扩展：无需修改主程序代码，只需在 MCP 服务器端增加工具或逻辑，即可增强 AI 的能力。
     * 审计与追踪：通过 responseId 和 timestamp，你可以清晰地追踪每一条指令是如何被 MCP 增强的。
     */
    public Map<String, Object> callMCPServer(String sessionId, Map<String, Object> context) {
        try {
            // 构建MCP请求
            Map<String, Object> mcpRequest = new HashMap<>();
            mcpRequest.put("session_id", sessionId);
            mcpRequest.put("context", context);
            mcpRequest.put("timestamp", System.currentTimeMillis());

            // 调用MCP服务器（简化实现）
            // Map<String, Object> response = restTemplate.postForObject(
            //     mcpServerUrl + "/process",
            //     mcpRequest,
            //     Map.class
            // );

            // 模拟MCP响应
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("processed_context", context);
            response.put("tool_calls", Collections.emptyList());
            response.put("structured_output", true);

            return response;

        } catch (Exception e) {
            log.error("调用MCP服务器失败", e);

            // 返回降级响应
            Map<String, Object> fallbackResponse = new HashMap<>();
            fallbackResponse.put("success", false);
            fallbackResponse.put("error", "MCP服务暂时不可用");
            fallbackResponse.put("use_fallback", true);

            return fallbackResponse;
        }
    }

    /**
     * 生成结构化输出
     */
    public Map<String, Object> generateStructuredOutput(String scenario, Map<String, Object> mcpResponse) {
        Map<String, Object> structuredOutput = new HashMap<>();

        if (Boolean.TRUE.equals(mcpResponse.get("success"))) {
            // 基于MCP响应生成结构化输出
            structuredOutput.put("status", "success");
            structuredOutput.put("scenario", scenario);
            structuredOutput.put("generated_at", new Date().toString());

            // 添加分析结果占位符
            structuredOutput.put("analysis", Collections.emptyMap());
            structuredOutput.put("recommendations", Collections.emptyList());
            structuredOutput.put("risk_assessment", Collections.emptyMap());

        } else {
            // 降级输出
            structuredOutput.put("status", "fallback");
            structuredOutput.put("scenario", scenario);
            structuredOutput.put("message", "使用降级分析模式");
            structuredOutput.put("generated_at", new Date().toString());
        }

        return structuredOutput;
    }
}
