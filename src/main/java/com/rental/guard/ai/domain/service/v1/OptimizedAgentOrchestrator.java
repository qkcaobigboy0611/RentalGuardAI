/**
 * @author qkcao
 * @date 2026/2/4 18:32
 */
package com.rental.guard.ai.domain.service.v1;

import com.rental.guard.ai.domain.dto.v1.AgentResponse;
import com.rental.guard.ai.domain.dto.v1.Message;
import com.rental.guard.ai.domain.dto.v1.SessionManager;
import com.rental.guard.ai.domain.service.LLMService;
import com.rental.guard.ai.domain.service.ReActAgent;
import com.rental.guard.ai.domain.service.v1.tool.AgentTool;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * 优化后的智能体编排器 - 支持ReAct循环
 */
@Slf4j
@Service
public class OptimizedAgentOrchestrator {

    @Autowired
    private SessionRepository sessionRepository;

    // 保留 LLMService 用于简单的场景识别和图片分析
    @Autowired
    private LLMService llmService;
    @Autowired
    private RAGService ragService;
    @Autowired
    private MCPService mcpService;
    @Autowired
    private ZhipuSearchService zhipuSearchService;

    // 【修改点1】移除 ObjectMapper，引入 LangChain4j 的 ChatLanguageModel
    @Autowired
    private ChatLanguageModel chatLanguageModel;

    @Autowired
    private List<AgentTool> agentTools; // Spring会自动注入所有实现AgentTool的Bean
    @Autowired
    private LongTermMemoryService memoryService;
    @Autowired
    private SimpleKnowledgeGraphService kgService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(15);
    private ReActEngine reActEngine;
    private final Map<String, ScenarioHandler> scenarioHandlers = new HashMap<>();

    @Autowired
    public OptimizedAgentOrchestrator() {
    }

    @PostConstruct
    public void init() {
        // 初始化工具映射
        Map<String, AgentTool> toolMap = agentTools.stream()
                .collect(Collectors.toMap(AgentTool::getName, tool -> tool));

        // 【修改点2】构建 ReActAgent 并初始化 ReActEngine
        // 使用 LangChain4j 的 AiServices 动态代理接口，自动处理 Prompt 和 JSON 解析
        ReActAgent reActAgent = AiServices.builder(ReActAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // 传递构造好的 reActAgent，不再传递 llmService 和 objectMapper
        this.reActEngine = new ReActEngine(reActAgent, toolMap, memoryService, kgService);

        log.info("初始化ReAct引擎完成 (LangChain4j Powered)，可用工具: {}", toolMap.keySet());

        // 注册场景处理器
        registerScenarioHandlers();
    }

    private void registerScenarioHandlers() {
        // 假设这些 Handler 类已在项目中定义，此处仅保留原有逻辑
        // 如果这些 Handler 是内部类或需要注入，请确保它们可用
        scenarioHandlers.put("合同审核", new ContractReviewHandler());
        scenarioHandlers.put("距离欺诈", new DistanceFraudHandler());
        scenarioHandlers.put("租金欺诈", new RentFraudHandler());
        scenarioHandlers.put("霸王条款", new UnfairClauseHandler());
    }

    /**
     * 新的处理入口 - 支持ReAct循环
     * 引入长期记忆管理器：负责维护用户的画像，记录用户的租房偏好（预算，地点）和历史风险记录
     * 简易知识图谱服务：负责存储和查询实体关系（如：房东电话-》关联房源-》关联中介公司-》是否黑名单）
     */
    public CompletableFuture<AgentResponse> processRequestWithReAct(
            String sessionId,
            String userInput,
            String localPath,
            String userId) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 获取或创建会话(引入长期记忆管理器和简易知识图谱服务)
                SessionManager session = sessionRepository.getOrCreateSession(sessionId);

                // 2. 处理图片输入（如果有）
                String finalUserInput = processImageInput(userInput, localPath);

                // 3. 保存用户消息
                Message userMessage = Message.createUserMessage(sessionId, finalUserInput);
                session.addMessage(userMessage);

                // 4. 场景识别
                String scenario = detectScenario(finalUserInput, session);
                session.setCurrentScenario(scenario);
                session.incrementScenarioCount(scenario);

                log.info("开始ReAct处理 - 会话: {}, 场景: {}", sessionId, scenario);

                // 5. 执行ReAct循环
                AgentResponse response = reActEngine.executeReActLoop(
                        sessionId, finalUserInput, session, scenario, userId
                ).join();

                // 6. 应用场景特定处理
                ScenarioHandler handler = scenarioHandlers.getOrDefault(
                        scenario, new DefaultScenarioHandler());
                handler.process(response, session, response.getSupportingDocuments());

                // 7. 保存智能体响应
                Message agentMessage = Message.createAgentMessage(sessionId, response);
                session.addMessage(agentMessage);

                // 8. 更新会话风险画像
                session.updateRiskProfile(response.getRiskLevel(), scenario);

                // 9. 保存会话
                sessionRepository.saveSession(session);

                log.info("ReAct处理完成 - 会话: {}, 风险等级: {}, 置信度: {}",
                        sessionId, response.getRiskLevel(), response.getConfidence());

                return response;

            } catch (Exception e) {
                log.error("ReAct处理失败", e);
                return createErrorResponse(sessionId, userInput, e.getMessage());
            }
        }, executorService);
    }

    /**
     * 处理图片输入
     */
    private String processImageInput(String userInput, String localPath) {
        if (StringUtils.isNotEmpty(localPath)) {
            try {
                String imageAnalysis = llmService.simpleMultiModalConversationCall(localPath);
                return String.format("""
                        用户输入：%s
                        图片分析结果：
                        %s
                        请基于以上信息回答问题。
                        """, userInput, imageAnalysis);
            } catch (Exception e) {
                log.error("图片分析失败", e);
                return userInput + "（图片分析失败，请重新上传）";
            }
        }
        return userInput;
    }

    /**
     * 场景识别
     */
    private String detectScenario(String userInput, SessionManager session) {
        // 使用LLM进行场景识别
        String prompt = """
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
        // 简单的清洗逻辑
        scenario = scenario.replaceAll("[^\\u4e00-\\u9fa5]", "").trim();

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

        if (input.contains("合同") || input.contains("条款") || input.contains("押金")) {
            return "合同审核";
        } else if (input.contains("距离") || input.contains("地铁") || input.contains("分钟")) {
            return "距离欺诈";
        } else if (input.contains("租金") || input.contains("价格") || input.contains("费用")) {
            return "租金欺诈";
        } else if (input.contains("霸王") || input.contains("违法") || input.contains("不公平")) {
            return "霸王条款";
        } else {
            return "合同审核";
        }
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
     * 批量处理请求（支持ReAct）
     */
    public CompletableFuture<List<AgentResponse>> batchProcessRequests(
            List<String> sessionIds,
            List<String> userInputs,
            String userId) {

        List<CompletableFuture<AgentResponse>> futures = new ArrayList<>();

        for (int i = 0; i < Math.min(sessionIds.size(), userInputs.size()); i++) {
            futures.add(processRequestWithReAct(sessionIds.get(i), userInputs.get(i), null, userId));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    /**
     * 获取系统状态
     */
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();

        status.put("timestamp", LocalDateTime.now().toString());
        status.put("react_engine", "active (LangChain4j)");
        status.put("available_tools", agentTools.stream()
                .map(AgentTool::getName)
                .collect(Collectors.toList()));
        status.put("executor_service",
                Map.of("active_threads", ((ThreadPoolExecutor) executorService).getActiveCount(),
                        "pool_size", ((ThreadPoolExecutor) executorService).getPoolSize(),
                        "queue_size", ((ThreadPoolExecutor) executorService).getQueue().size()));

        return status;
    }

    /**
     * 向后兼容 - 保持原有接口
     */
    public CompletableFuture<AgentResponse> processRequestV2(
            String sessionId, String userInput, String localPath, String userId) {
        // 可以调用新的ReAct方法，或保持原有逻辑
        return processRequestWithReAct(sessionId, userInput, localPath, userId);
    }

    // -------------------------------------------------------------------------
    // 内部类或接口定义的 Placeholder (为了代码完整性，如果原始文件中有请保留)
    // -------------------------------------------------------------------------

    public interface ScenarioHandler {
        void process(AgentResponse response, SessionManager session, List<AgentResponse.RetrievedDocument> docs);
    }

    public static class DefaultScenarioHandler implements ScenarioHandler {
        @Override
        public void process(AgentResponse response, SessionManager session, List<AgentResponse.RetrievedDocument> docs) {
            // 默认不做额外处理
        }
    }

    public static class ContractReviewHandler implements ScenarioHandler {
        @Override
        public void process(AgentResponse response, SessionManager session, List<AgentResponse.RetrievedDocument> docs) {
            // 合同审核后处理逻辑
        }
    }

    public static class DistanceFraudHandler implements ScenarioHandler {
        @Override
        public void process(AgentResponse response, SessionManager session, List<AgentResponse.RetrievedDocument> docs) {
            // 距离欺诈后处理逻辑
        }
    }

    public static class RentFraudHandler implements ScenarioHandler {
        @Override
        public void process(AgentResponse response, SessionManager session, List<AgentResponse.RetrievedDocument> docs) {
            // 租金欺诈后处理逻辑
        }
    }

    public static class UnfairClauseHandler implements ScenarioHandler {
        @Override
        public void process(AgentResponse response, SessionManager session, List<AgentResponse.RetrievedDocument> docs) {
            // 霸王条款后处理逻辑
        }
    }
}
