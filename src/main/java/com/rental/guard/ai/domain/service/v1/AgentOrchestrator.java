/**
 * @author qkcao
 * @date 2026/1/28 15:28
 */
package com.rental.guard.ai.domain.service.v1;

import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.guard.ai.domain.dto.v1.AgentResponse;
import com.rental.guard.ai.domain.dto.v1.Message;
import com.rental.guard.ai.domain.dto.v1.SessionManager;
import com.rental.guard.ai.domain.service.LLMService;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 智能体编排器 - 协调各个组件处理用户请求
 *
 *
 * 缺乏真正的“思考-行动”循环 (No ReAct Loop)
 * 证据：目前的逻辑是线性的：Retrieve -> Generate -> Return。
 *
 * 风险：真正的智能体（Agent）应该具备 ReAct (Reason + Act) 能力。
 * 即：模型决定调用工具 -> 执行工具 -> 观察结果 -> 再决定是否继续。
 * 目前的实现是一次性把所有上下文丢进去，不支持多步工具调用（例如：先查天气，发现是雨天，再查打车软件）。
 */
@Slf4j
@Service
public class AgentOrchestrator {

    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private ZhipuSearchService zhipuSearchService;
    private final LLMService llmService;
    private final RAGService ragService;
    private final MCPService mcpService;
    private final ObjectMapper objectMapper;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    // 场景处理器映射
    private final Map<String, ScenarioHandler> scenarioHandlers = new HashMap<>();

    @Autowired
    public AgentOrchestrator(LLMService llmService,
                             RAGService ragService,
                             MCPService mcpService,
                             ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.ragService = ragService;
        this.mcpService = mcpService;
        this.objectMapper = objectMapper;

        // 注册场景处理器
        registerScenarioHandlers();
    }

    /**
     * 注册场景处理器
     */
    private void registerScenarioHandlers() {
        scenarioHandlers.put("合同审核", new ContractReviewHandler());
        scenarioHandlers.put("距离欺诈", new DistanceFraudHandler());
        scenarioHandlers.put("租金欺诈", new RentFraudHandler());
        scenarioHandlers.put("霸王条款", new UnfairClauseHandler());
    }

    /**
     * 处理用户请求
     */
    public CompletableFuture<AgentResponse> processRequestV2(String sessionId, String userInput, String localPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 获取或创建会话
                SessionManager session = sessionRepository.getOrCreateSession(sessionId);

                // 2. 处理图片输入（如果有）
                String finalUserInput = processImageInput(userInput, localPath);

                // 2. 保存用户消息
                Message userMessage = Message.createUserMessage(sessionId, finalUserInput);
                session.addMessage(userMessage);
                sessionRepository.saveSession(session);

                // 3. 场景识别
                String scenario = detectScenario(finalUserInput, session);
                session.setCurrentScenario(scenario);
                session.incrementScenarioCount(scenario);

                log.info("处理请求 - 会话: {}, 场景: {}, 输入: {}",
                        sessionId, scenario, finalUserInput.substring(0, Math.min(50, finalUserInput.length())));

                // 4. 并行处理：RAG检索 + MCP上下文构建

                // 实时搜索数据
                CompletableFuture<String> searchFuture = CompletableFuture
                        .supplyAsync(() -> zhipuSearchService.searchInternetAsync(
                                userInput, scenario), executorService);

                CompletableFuture<List<com.rental.guard.ai.domain.dto.v1.AgentResponse.RetrievedDocument>> ragFuture = CompletableFuture
                        .supplyAsync(() -> ragService.retrieveRelevantDocuments(
                                ragService.enhanceQuery(userInput, scenario), scenario), executorService);

                CompletableFuture<Map<String, Object>> mcpFuture = CompletableFuture
                        .supplyAsync(() -> mcpService.buildModelContext(
                                sessionId, session.getMessageHistory(), scenario, null), executorService);

                // 等待并行任务完成
                CompletableFuture.allOf(ragFuture, mcpFuture, searchFuture).join();

                List<com.rental.guard.ai.domain.dto.v1.AgentResponse.RetrievedDocument> ragResults = ragFuture.get();
                Map<String, Object> mcpContext = mcpFuture.get();

                String searchContent = searchFuture.get();

                // 5. 调用MCP服务器
                Map<String, Object> mcpResponse = mcpService.callMCPServer(sessionId, mcpContext);

                // 6. 生成智能体响应
                AgentResponse agentResponseNo;

                if (Boolean.TRUE.equals(mcpResponse.get("success"))) {
                    // 使用增强处理流程
                    agentResponseNo = generateEnhancedResponse(session, userInput, scenario, ragResults, mcpResponse, searchContent);
                } else {
                    // 使用降级处理流程
                    agentResponseNo = generateFallbackResponse(session, userInput, scenario, ragResults);
                }

                // 7. 保存智能体响应消息
                Message agentMessageNo = Message.createAgentMessage(sessionId, agentResponseNo);
                session.addMessage(agentMessageNo);

                // 8. 更新会话风险画像
                session.updateRiskProfile(agentResponseNo.getRiskLevel(), scenario);

                // 9. 保存更新后的会话
                sessionRepository.saveSession(session);

                log.info("请求处理完成 - 会话: {}, 风险等级: {}, 置信度: {}",
                        sessionId, agentResponseNo.getRiskLevel(), agentResponseNo.getConfidence());

                return agentResponseNo;

            } catch (Exception e) {
                log.error("处理请求失败", e);
                return createErrorResponse(sessionId, userInput, e.getMessage());
            }
        }, executorService);
    }

    /**
     * 场景识别
     */
    private String detectScenario(String userInput, SessionManager session) {
        // 使用大模型进行场景识别
        String prompt = """
                你是一个专业的租房风险分析智能助手，专门帮助租客识别和应对租房过程中的各种风险。
                    你的职责包括：
                    1. 分析租房合同条款的合法性和风险
                    2. 识别虚假宣传和欺诈行为
                    3. 提供法律建议和市场数据分析
                    4. 生成具体的行动建议
                    
                    请基于用户的问题和提供的上下文信息，给出专业、准确、实用的建议。
                请识别以下租房相关问题属于哪个场景：
                                
                用户输入：%s
                                
                可选场景：
                1. 合同审核 - 涉及合同条款、押金、维修等问题
                2. 距离欺诈 - 涉及地理位置、交通时间、虚假宣传等问题
                3. 租金欺诈 - 涉及租金价格、市场对比、隐形费用等问题
                4. 霸王条款 - 涉及违法条款、不公平条款、消费者权益等问题
                                
                请只返回场景名称，不要有其他内容。
                """.formatted(userInput);

        String scenario = llmService.generate(prompt);

        // 清理响应，只保留场景名称
        scenario = scenario.replaceAll("[^\\u4e00-\\u9fa5]", "").trim();

        // 如果识别失败，使用关键词匹配
        if (!scenarioHandlers.containsKey(scenario)) {
            scenario = fallbackScenarioDetection(userInput);
        }

        return scenario;
    }

    /**
     * 降级场景识别
     */
    private String fallbackScenarioDetection(String userInput) {
        String input = userInput.toLowerCase();

        if (input.contains("合同") || input.contains("条款") || input.contains("押金") || input.contains("签约")) {
            return "合同审核";
        } else if (input.contains("距离") || input.contains("地铁") || input.contains("分钟") ||
                input.contains("步行") || input.contains("交通")) {
            return "距离欺诈";
        } else if (input.contains("租金") || input.contains("价格") || input.contains("砍价") ||
                input.contains("市场价") || input.contains("费用")) {
            return "租金欺诈";
        } else if (input.contains("霸王") || input.contains("违法") || input.contains("不退") ||
                input.contains("不公平") || input.contains("无效")) {
            return "霸王条款";
        } else {
            return "合同审核"; // 默认场景
        }
    }

    /**
     * 生成增强响应
     */
    private AgentResponse generateEnhancedResponse(SessionManager session, String userInput,
                                                   String scenario, List<com.rental.guard.ai.domain.dto.v1.AgentResponse.RetrievedDocument> ragResults,
                                                   Map<String, Object> mcpResponse,
                                                   String searchContent) {
        // 1. 获取场景特定处理器
        ScenarioHandler handler = scenarioHandlers.getOrDefault(scenario, new DefaultScenarioHandler());

        // 2. 获取会话上下文摘要
        String sessionContext = session.getContextSummary();

        // 3. 使用大模型生成分析
        AgentResponse response = analyzeScenario(userInput, scenario, ragResults, sessionContext, searchContent);

        // 4. 应用场景特定处理
        handler.process(response, session, ragResults);

        // 5. 添加MCP信息
        response.setModelUsed("GPT-4 + MCP");
        response.setModelParameters(mcpResponse);

        // 6. 设置响应ID和时间
        response.setResponseId("resp_" + UUID.randomUUID().toString());
        response.setSessionId(session.getSessionId());
        response.setGeneratedAt(LocalDateTime.now());

        return response;
    }

    /**
     * 生成降级响应
     */
    private AgentResponse generateFallbackResponse(SessionManager session, String userInput,
                                                   String scenario, List<AgentResponse.RetrievedDocument> ragResults) {
        // 使用预定义响应模板
        AgentResponse response = AgentResponse.createForScenario(scenario, userInput, 0.7);

        // 添加RAG结果
        response.setSupportingDocuments(ragResults);

        // 设置响应信息
        response.setResponseId("resp_fallback_" + UUID.randomUUID().toString());
        response.setSessionId(session.getSessionId());
        response.setGeneratedAt(LocalDateTime.now());
        response.setModelUsed("Fallback Template");

        return response;
    }

    /**
     * 创建错误响应
     */
    private AgentResponse createErrorResponse(String sessionId, String userInput, String errorMessage) {
        return AgentResponse.builder()
                .responseId("resp_error_" + UUID.randomUUID().toString())
                .sessionId(sessionId)
                .scenario("系统错误")
                .responseType(AgentResponse.ResponseType.ERROR)
                .coreLogic("系统处理请求时发生错误")
                .detailedAnalysis("错误信息：" + errorMessage + "\n建议稍后重试或联系技术支持")
                .riskLevel("未知")
                .confidence(0.0)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 批量处理请求
     */
    public CompletableFuture<List<AgentResponse>> batchProcessRequests(List<String> sessionIds, List<String> userInputs) {
        List<CompletableFuture<AgentResponse>> futures = new ArrayList<>();

        for (int i = 0; i < Math.min(sessionIds.size(), userInputs.size()); i++) {
            futures.add(processRequestV2(sessionIds.get(i), userInputs.get(i), null));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /**
     * 获取系统状态
     */
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();

        status.put("timestamp", LocalDateTime.now().toString());
        status.put("active_scenarios", scenarioHandlers.keySet());
        status.put("executor_service",
                Map.of("active_threads", ((ThreadPoolExecutor) executorService).getActiveCount(),
                        "pool_size", ((ThreadPoolExecutor) executorService).getPoolSize(),
                        "queue_size", ((ThreadPoolExecutor) executorService).getQueue().size()));

        return status;
    }




    /**
     * 场景分析 - 结合RAG结果进行智能分析
     */
    public AgentResponse analyzeScenario(String userInput, String scenario,
                                         List<com.rental.guard.ai.domain.dto.v1.AgentResponse.RetrievedDocument> ragResults,
                                         String sessionContext,
                                         String searchContent) {
        try {
            // 构建增强提示(不需要调用外部AI服务整理，一个是增加延迟，二是成本翻倍，三是信息丢失)
            String enhancedPrompt = buildEnhancedPrompt(userInput, scenario, ragResults, sessionContext, searchContent);

            Map<String, Object> responseMap = generateStructuredData(enhancedPrompt, Map.class);

            if (responseMap != null) {
                return AgentResponse.builder()
                        .scenario(scenario)
                        .riskLevel((String) responseMap.get("riskLevel"))
                        .coreLogic((String) responseMap.get("coreLogic"))
                        .detailedAnalysis((String) responseMap.get("detailedAnalysis"))
                        .keyFindings((List<String>) responseMap.get("keyFindings"))
                        .recommendations((List<String>) responseMap.get("recommendations"))
                        .legalReferences((List<String>) responseMap.get("legalReferences"))
                        .confidence(((Number) responseMap.get("confidence")).doubleValue())
                        .supportingDocuments(ragResults)
                        .generatedAt(java.time.LocalDateTime.now())
                        .build();
            }
        } catch (Exception e) {
            log.error("场景分析失败", e);
        }

        // 降级方案：返回预定义响应
        return AgentResponse.createForScenario(scenario, userInput, 0.7);
    }

    /**
     * 构建增强提示
     * @param userInput 用户输入
     * @param scenario 场景
     * @param ragResults rag结果
     * @param sessionContext 会话
     * @param searchContent 实时信息联网搜索
     * @return
     */
    /**
     * 构建增强提示 (Optimized Version - 中文版)
     * 采用结构化 Prompt 设计 (XML Tags) + 强制 JSON 输出模式
     */
    private String buildEnhancedPrompt(String userInput, String scenario,
                                       List<com.rental.guard.ai.domain.dto.v1.AgentResponse.RetrievedDocument> ragResults,
                                       String sessionContext,
                                       String searchContent) {
        StringBuilder prompt = new StringBuilder();

        // 1. 系统角色与任务定义 (System Persona & Task)
        // 翻译为中文设定，让模型更自然地输出中文回复
        prompt.append("你是一位资深的租房防欺诈安全顾问。");
        prompt.append("你的任务是严格基于提供的上下文数据，分析用户的查询是否存在潜在的租房欺诈风险。\n\n");

        // 2. 注入上下文数据 (使用 XML 标签隔离，防止指令注入)
        prompt.append("<context_data>\n");

        // 2.1 场景信息
        prompt.append("  <scenario>").append(scenario).append("</scenario>\n");

        // 2.2 会话历史 (Session History)
        if (sessionContext != null && !sessionContext.isEmpty()) {
            prompt.append("  <conversation_history>\n")
                    .append(sessionContext)
                    .append("\n  </conversation_history>\n");
        }

        // 2.3 实时联网搜索信息 (Web Search)
        if (searchContent != null && !searchContent.isEmpty()) {
            prompt.append("  <web_search_results>\n")
                    .append(searchContent)
                    .append("\n  </web_search_results>\n");
        }

        prompt.append("</context_data>\n\n");

        // 3. 注入 RAG 知识库参考信息 (Knowledge Base)
        if (ragResults != null && !ragResults.isEmpty()) {
            prompt.append("<knowledge_base>\n");
            // 限制数量，防止 Token 溢出，建议取 Top 3-5
            int limit = Math.min(ragResults.size(), 5);
            for (int i = 0; i < limit; i++) {
                com.rental.guard.ai.domain.dto.v1.AgentResponse.RetrievedDocument doc = ragResults.get(i);
                prompt.append("  <document id=\"").append(i + 1).append("\">\n")
                        .append("    <source>").append(doc.getSource()).append("</source>\n")
                        // 移除换行符以保持 XML 结构整洁
                        .append("    <content>").append(doc.getContent().replace("\n", " ")).append("</content>\n")
                        .append("    <relevance>").append(String.format("%.2f", doc.getRelevanceScore())).append("</relevance>\n")
                        .append("  </document>\n");
            }
            prompt.append("</knowledge_base>\n\n");
        }

        // 4. 用户当前输入 (User Input)
        prompt.append("<user_query>\n")
                .append(userInput)
                .append("\n</user_query>\n\n");

        // 5. 输出指令与格式要求 (Output Instruction)
        prompt.append("### 分析指令\n");
        prompt.append("1. 结合 <conversation_history>（对话历史）和 <knowledge_base>（知识库）对 <user_query> 进行深入分析。\n");
        prompt.append("2. 识别具体的欺诈特征信号（例如：制造紧迫感、房屋细节模糊、异常的支付或转账要求）。\n");
        prompt.append("3. 与提供的 <knowledge_base> 中的法律法规或案例进行交叉验证。\n");
        prompt.append("4. 重要：必须基于事实证据而非猜测来判定 'riskLevel'（风险等级）。\n\n");

        prompt.append("### 输出格式\n");
        prompt.append("你必须返回合法的 JSON 格式结果。不要使用 markdown 代码块包裹。JSON 结构如下（请使用中文填写内容，但字段名保持英文）：\n");
        prompt.append("{\n");
        prompt.append("  \"riskLevel\": \"CRITICAL | HIGH | MEDIUM | LOW\",\n"); // 保持英文枚举以便代码解析
        prompt.append("  \"confidence\": 0.0 到 1.0,\n");
        prompt.append("  \"coreLogic\": \"对主要推理逻辑的简要总结\",\n");
        prompt.append("  \"detailedAnalysis\": \"对发现的风险因素的详细分析说明\",\n");
        prompt.append("  \"keyFindings\": [\"发现的关键点 1\", \"发现的关键点 2\"],\n");
        prompt.append("  \"recommendations\": [\"可执行的建议或行动指南 1\", \"建议 2\"],\n");
        prompt.append("  \"legalReferences\": [\"来自知识库的相关法律条款或依据\"],\n");
        prompt.append("  \"missingInformation\": \"需要用户进一步补充或澄清的信息（如果没有则留空）\"\n");
        prompt.append("}");

        return prompt.toString();
    }


    /**
     * 从响应中提取JSON
     */
    private String extractJsonFromResponse(String response) {
        // 查找第一个{和最后一个}
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');

        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }

        // 如果没有找到完整的JSON，尝试清理响应
        return response.replaceAll("^```json\\s*", "")
                .replaceAll("\\s*```$", "")
                .trim();
    }

    /**
     * 获取对话历史摘要
     */
    public String summarizeConversation(List<Message> messageNos) {
        if (messageNos == null || messageNos.isEmpty()) {
            return "暂无对话历史";
        }

        String conversationText = messageNos.stream()
                .map(msg -> msg.getSender() + ": " + msg.getContentAsString())
                .reduce("", (a, b) -> a + "\n" + b);

        String summaryPrompt = """
                请总结以下对话的主要内容，提取关键信息：
                
                %s
                
                总结要求：
                1. 提取主要讨论的问题
                2. 识别涉及的风险点
                3. 总结已给出的建议
                4. 用简洁的语言表达
                5. 不超过200字
                """.formatted(conversationText);

        return llmService.generate(summaryPrompt, Map.of());
    }

    /**
     * 结构化数据生成（JSON格式）
     */
    public <T> T generateStructuredData(String prompt, Class<T> responseType) {
        try {
            String jsonPrompt = String.format("""
                    请将以下分析结果以JSON格式返回，严格遵循指定的结构：
                    
                    %s
                    
                    返回格式要求：
                    1. 必须是有效的JSON格式
                    2. 只返回JSON，不要有其他文字
                    3. 确保所有字段都按照要求提供
                    """, prompt);

            String jsonResponse = llmService.generate(jsonPrompt);

            // 清理响应，只提取JSON部分
            jsonResponse = extractJsonFromResponse(jsonResponse);

            return objectMapper.readValue(jsonResponse, responseType);
        } catch (Exception e) {
            log.error("LLM结构化数据生成失败", e);
            return null;
        }
    }

    public String processImageInput(String userInput, String localPath) throws NoApiKeyException, UploadFileException, IOException {
        String finalUserInput = null;
        // 2.如果是照片的话
        if(StringUtils.isNotEmpty(localPath)) {
            String s = llmService.simpleMultiModalConversationCall(localPath);
            // 方案1.3：结构化拼接（推荐）
            finalUserInput = String.format("""
                    用户输入：%s
                    图片分析结果：
                    %s
                    请基于以上信息回答问题。
                    """, userInput, s);
        } else {
            finalUserInput = userInput;
        }
        return finalUserInput;
    }
}

/**
 * 场景处理器接口
 */
interface ScenarioHandler {
    void process(AgentResponse response, SessionManager session, List<com.rental.guard.ai.domain.dto.v1.AgentResponse.RetrievedDocument> ragResults);
}

/**
 * 合同审核处理器
 */
class ContractReviewHandler implements ScenarioHandler {
    @Override
    public void process(AgentResponse response, SessionManager session, List<com.rental.guard.ai.domain.dto.v1.AgentResponse.RetrievedDocument> ragResults) {
        // 合同审核特定处理逻辑
        response.addRecommendation("建议请专业律师最终审核合同");
        response.addRecommendation("所有口头承诺必须书面化");

        // 更新会话上下文
        session.getContext().addEntity("document_type", "rental_contract");
        session.getContext().addPendingAction("contract_review_complete");
    }
}

/**
 * 距离欺诈处理器
 */
class DistanceFraudHandler implements ScenarioHandler {
    @Override
    public void process(AgentResponse response, SessionManager session, List<AgentResponse.RetrievedDocument> ragResults) {
        // 距离欺诈特定处理逻辑
        response.addRecommendation("使用多个地图应用验证距离");
        response.addRecommendation("在不同时间段测试通勤时间");

        // 如果有RAG结果，添加具体数据
        ragResults.stream()
                .filter(doc -> doc.getSource().contains("地图") || doc.getSource().contains("距离"))
                .findFirst()
                .ifPresent(doc -> {
                    response.addKeyFinding("参考数据：" + doc.getContent());
                });
    }
}

/**
 * 租金欺诈处理器
 */
class RentFraudHandler implements ScenarioHandler {
    @Override
    public void process(AgentResponse response, SessionManager session, List<AgentResponse.RetrievedDocument> ragResults) {
        // 租金欺诈特定处理逻辑
        response.addRecommendation("获取至少3个同类房源价格对比");
        response.addRecommendation("要求提供所有费用明细清单");

        // 从RAG结果中提取市场数据
        ragResults.stream()
                .filter(doc -> doc.getSource().contains("市场") || doc.getSource().contains("租金"))
                .limit(2)
                .forEach(doc -> response.addDataReference(doc.getContent()));
    }
}

/**
 * 霸王条款处理器
 */
class UnfairClauseHandler implements ScenarioHandler {
    @Override
    public void process(AgentResponse response, SessionManager session, List<AgentResponse.RetrievedDocument> ragResults) {
        // 霸王条款特定处理逻辑
        response.addRecommendation("立即停止签署流程");
        response.addRecommendation("向市场监督管理局举报");

        // 添加法律依据
        ragResults.stream()
                .filter(doc -> doc.getSource().contains("法律") || doc.getSource().contains("民法典"))
                .limit(3)
                .forEach(doc -> response.addLegalReference(doc.getContent()));
    }
}

/**
 * 默认场景处理器
 */
class DefaultScenarioHandler implements ScenarioHandler {
    @Override
    public void process(AgentResponse response, SessionManager session, List<AgentResponse.RetrievedDocument> ragResults) {
        // 默认处理逻辑
        response.addRecommendation("建议咨询专业人士获取进一步帮助");
    }
}
