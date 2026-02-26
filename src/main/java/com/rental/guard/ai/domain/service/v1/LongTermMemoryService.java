/**
 * @author qkcao
 * @date 2026/2/5 14:56
 */
package com.rental.guard.ai.domain.service.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ListenableFuture;
import com.rental.guard.ai.domain.dto.v1.Message;
import com.rental.guard.ai.domain.service.LLMService;
import com.rental.guard.ai.infrastructure.mapper.LongTermMemoryMapper;
import com.rental.guard.ai.infrastructure.mapper.UserProfileMapper;
import com.rental.guard.ai.infrastructure.po.LongTermMemory;
import com.rental.guard.ai.infrastructure.po.UserProfile;
import com.rental.guard.ai.infrastructure.service.EmbeddingService;
import io.qdrant.client.grpc.Points;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 长期记忆服务（使用Redis + MySQL + 内存缓存）
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LongTermMemoryService {

    @Autowired
    private LongTermMemoryMapper memoryRepository;
    @Autowired
    private UserProfileMapper userProfileMapper;
    @Autowired
    private EmbeddingService embeddingService;

    private final QdrantService qdrantService;
    private final RedisTemplate<String, String> redisTemplate;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;
    // 内存缓存
    private Map<String, String> memoryCache = new HashMap<>();

    /**
     * 获取用户记忆上下文
     */
    public String getUserMemoryContext(String sessionId, String userId) {
        String cacheKey = String.format("memory:context:%s:%s", userId, sessionId);

        // 1. 检查内存缓存
        String cached = memoryCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 2. 检查Redis缓存
        String redisResult = redisTemplate.opsForValue().get(cacheKey);
        if (redisResult != null) {
            memoryCache.put(cacheKey, redisResult);
            return redisResult;
        }

        // 3. 查询数据库并构建记忆上下文
        String memoryContext = buildMemoryContext(userId, sessionId);

        // 4. 缓存结果
        redisTemplate.opsForValue().set(cacheKey, memoryContext, 1, TimeUnit.HOURS);
        memoryCache.put(cacheKey, memoryContext);

        return memoryContext;
    }

    /**
     * 构建记忆上下文
     */
    private String buildMemoryContext(String userId, String sessionId) {
        StringBuilder context = new StringBuilder();

        try {
            // 1. 获取用户画像
            UserProfile profile = userProfileMapper.findByUserId(userId);
            if (profile != null) {
                context.append("【用户画像】\n");
                context.append(formatUserProfile(profile)).append("\n\n");
            }

            // 2. 获取最近的重要记忆
            List<LongTermMemory> recentMemories = memoryRepository.findRecentImportantMemories(
                    userId, 10
            );

            if (!recentMemories.isEmpty()) {
                context.append("【历史记忆】\n");
                for (LongTermMemory memory : recentMemories) {
                    context.append(formatMemoryItem(memory)).append("\n");
                }
                context.append("\n");
            }

            // 3. 获取风险记忆
            List<LongTermMemory> riskMemories = memoryRepository.findByUserIdAndCategory(
                    userId, "risk"
            );

            if (!riskMemories.isEmpty()) {
                context.append("【风险记录】\n");
                riskMemories.stream()
                        .limit(5)
                        .forEach(memory ->
                                context.append(formatMemoryItem(memory)).append("\n")
                        );
                context.append("\n");
            }

            // 4. 获取黑名单
            if (profile != null && StringUtils.isNotBlank(profile.getBlacklistedAgencies())) {
                List<String> blacklist = parseJsonArray(profile.getBlacklistedAgencies());
                if (!blacklist.isEmpty()) {
                    context.append("【黑名单】\n");
                    blacklist.forEach(item -> context.append("- ").append(item).append("\n"));
                    context.append("\n");
                }
            }

        } catch (Exception e) {
            log.error("构建记忆上下文失败", e);
            context.append("记忆系统暂时不可用\n");
        }

        return context.toString();
    }

    /**
     * 格式化用户画像
     */
    private String formatUserProfile(UserProfile profile) {
        StringBuilder sb = new StringBuilder();

        // 基础信息
        if (StringUtils.isNotBlank(profile.getUserName())) {
            sb.append("用户：").append(profile.getUserName()).append("\n");
        }

        // 租房偏好
        sb.append("租房偏好：\n");
        if (profile.getBudgetMin() != null && profile.getBudgetMax() != null) {
            sb.append("- 预算：").append(profile.getBudgetMin())
                    .append(" - ").append(profile.getBudgetMax()).append("元\n");
        }

        if (StringUtils.isNotBlank(profile.getPreferredLocation())) {
            sb.append("- 意向区域：").append(profile.getPreferredLocation()).append("\n");
        }

        if (StringUtils.isNotBlank(profile.getHouseType())) {
            sb.append("- 房屋类型：").append(profile.getHouseType()).append("\n");
        }

        // 风险偏好
        if (profile.getRiskTolerance() != null) {
            sb.append("- 风险承受度：").append(String.format("%.1f", profile.getRiskTolerance())).append("\n");
        }

        return sb.toString();
    }

    /**
     * 格式化记忆项
     */
    private String formatMemoryItem(LongTermMemory memory) {
        try {
            Map<String, Object> valueMap = parseJson(memory.getMemoryValue());
            StringBuilder sb = new StringBuilder();

            sb.append("- ").append(memory.getMemoryText());

            if (valueMap.containsKey("risk_level")) {
                sb.append(" [风险等级：").append(valueMap.get("risk_level")).append("]");
            }

            if (valueMap.containsKey("date")) {
                sb.append(" (").append(valueMap.get("date")).append(")");
            }

            return sb.toString();
        } catch (Exception e) {
            return "- " + memory.getMemoryText();
        }
    }

    /**
     * 异步更新记忆
     */
    @Async
    public void updateMemoryAsync(String userId, List<Message> messages) {
        try {
            // 1. 提取重要信息
            List<MemoryUpdate> updates = extractImportantInfo(messages);

            // 2. 批量保存记忆
            batchSaveMemories(userId, updates);

            // 3. 更新用户画像
            updateUserProfile(userId, updates);

            // 4. 清除缓存
            clearUserCache(userId);

            log.info("记忆更新完成 - user: {}, updates: {}", userId, updates.size());

        } catch (Exception e) {
            log.error("异步更新记忆失败", e);
        }
    }

    /**
     * 提取重要信息
     */
    private List<MemoryUpdate> extractImportantInfo(List<Message> messages) {
        List<MemoryUpdate> updates = new ArrayList<>();

        for (Message message : messages) {
            if (message.getSender().equals("user")) {
                String content = message.getContentAsString();

                // 使用规则提取
                updates.addAll(extractByRules(content));

                // 使用LLM提取重要信息
                updates.addAll(extractByLLM(content));
            }
        }

        return updates;
    }

    /**
     * 规则提取
     */
    private List<MemoryUpdate> extractByRules(String content) {
        List<MemoryUpdate> updates = new ArrayList<>();

        // 提取预算信息
        Pattern budgetPattern = Pattern.compile("(\\d+)元?(\\s*[-~到至]\\s*)(\\d+)元?");
        Matcher budgetMatcher = budgetPattern.matcher(content);
        if (budgetMatcher.find()) {
            MemoryUpdate update = MemoryUpdate.builder()
                    .category("preference")
                    .subCategory("budget")
                    .key("budget_range")
                    .value(String.format("{\"min\": %s, \"max\": %s}",
                            budgetMatcher.group(1), budgetMatcher.group(3)))
                    .text("预算范围：" + budgetMatcher.group(1) + "-" + budgetMatcher.group(3) + "元")
                    .confidence(0.9)
                    .build();
            updates.add(update);
        }

        // 提取地点信息
        String[] locationKeywords = {"地区", "区域", "位置", "地段", "在", "住"};
        for (String keyword : locationKeywords) {
            if (content.contains(keyword)) {
                // 简单提取，实际可使用更复杂的方法
                int index = content.indexOf(keyword);
                if (index > 0 && index < content.length() - keyword.length() + 10) {
                    String location = content.substring(
                            Math.max(0, index - 10),
                            Math.min(content.length(), index + 10)
                    );

                    MemoryUpdate update = MemoryUpdate.builder()
                            .category("preference")
                            .subCategory("location")
                            .key("preferred_location")
                            .value(String.format("{\"location\": \"%s\"}", location))
                            .text("意向地区：" + location)
                            .confidence(0.7)
                            .build();
                    updates.add(update);
                }
            }
        }

        return updates;
    }

    /**
     * LLM提取重要信息
     */
    private List<MemoryUpdate> extractByLLM(String content) {
        try {
            String prompt = String.format("""
                    从以下用户对话中提取重要的租房相关记忆信息：
                                    
                    对话内容：%s
                                    
                    请提取：
                    1. 租房偏好（预算、地区、房型等）
                    2. 遇到的风险或问题
                    3. 重要的中介、地址、电话等信息
                    4. 其他重要信息
                                    
                    返回JSON格式：
                    {
                      "memories": [
                        {
                          "category": "preference/risk/interaction/blacklist",
                          "subCategory": "budget/location/contract/etc",
                          "key": "记忆键",
                          "value": "记忆值（JSON格式）",
                          "text": "自然语言描述",
                          "confidence": 0.9
                        }
                      ]
                    }
                    """, content);

            String response = llmService.generate(prompt);
            Map<String, Object> result = parseJson(response);

            if (result.containsKey("memories")) {
                List<Map<String, Object>> memories = (List<Map<String, Object>>) result.get("memories");
                return memories.stream()
                        .map(this::mapToMemoryUpdate)
                        .collect(Collectors.toList());
            }

        } catch (Exception e) {
            log.error("LLM提取记忆失败", e);
        }

        return Collections.emptyList();
    }

    /**
     * 批量保存记忆
     */
    private void batchSaveMemories(String userId, List<MemoryUpdate> updates) {
        List<LongTermMemory> memories = updates.stream()
                .map(update -> LongTermMemory.builder()
                        .userId(userId)
                        .category(update.getCategory())
                        .subCategory(update.getSubCategory())
                        .memoryKey(update.getKey())
                        .memoryValue(update.getValue())
                        .memoryText(update.getText())
                        .confidence(update.getConfidence())
                        .source("system_extract")
                        .priority(calculatePriority(update))
                        .accessCount(0)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());

        for (LongTermMemory memory : memories) {
            memoryRepository.insert(memory);
        }
    }

    /**
     * 更新用户画像
     */
    private void updateUserProfile(String userId, List<MemoryUpdate> updates) {
        UserProfile profile = userProfileMapper.findByUserId(userId);
        if (profile == null) {
            profile = createDefaultProfile(userId);
        }

        boolean updated = false;

        for (MemoryUpdate update : updates) {
            switch (update.getKey()) {
                case "budget_range":
                    Map<String, Object> budget = parseJson(update.getValue());
                    profile.setBudgetMin(((Number) budget.get("min")).intValue());
                    profile.setBudgetMax(((Number) budget.get("max")).intValue());
                    updated = true;
                    break;

                case "preferred_location":
                    Map<String, Object> location = parseJson(update.getValue());
                    profile.setPreferredLocation((String) location.get("location"));
                    updated = true;
                    break;

                case "risk_agency":
                    // 添加到黑名单
                    addToBlacklist(profile, "agencies", update.getText());
                    updated = true;
                    break;
            }
        }

        if (updated) {
            profile.setUpdatedAt(LocalDateTime.now());
            profile.setLastActiveAt(LocalDateTime.now());
            userProfileMapper.insert(profile);
        }
    }

    /**
     * 清除用户缓存
     */
    private void clearUserCache(String userId) {
        // 清除Redis缓存
        Set<String> keys = redisTemplate.keys("memory:*:" + userId + ":*");
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // 清除内存缓存
        memoryCache.clear();
    }

    // 辅助方法
    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Integer calculatePriority(MemoryUpdate update) {
        // 根据类别和置信度计算优先级
        Map<String, Integer> categoryPriority = Map.of(
                "risk", 10,
                "blacklist", 9,
                "preference", 7,
                "interaction", 5
        );

        int basePriority = categoryPriority.getOrDefault(update.getCategory(), 5);
        double confidenceBonus = update.getConfidence() * 2;

        return (int) (basePriority + confidenceBonus);
    }

    /**
     * 将Map数据映射为MemoryUpdate对象
     */
    private MemoryUpdate mapToMemoryUpdate(Map<String, Object> memoryMap) {
        try {
            return MemoryUpdate.builder()
                    .category((String) memoryMap.getOrDefault("category", "interaction"))
                    .subCategory((String) memoryMap.getOrDefault("subCategory", "general"))
                    .key((String) memoryMap.getOrDefault("key", generateMemoryKey(memoryMap)))
                    .value((String) memoryMap.getOrDefault("value", ""))
                    .text((String) memoryMap.getOrDefault("text", ""))
                    .confidence(((Number) memoryMap.getOrDefault("confidence", 0.7)).doubleValue())
                    .build();
        } catch (Exception e) {
            log.error("映射MemoryUpdate失败", e);
            // 返回默认的MemoryUpdate
            return MemoryUpdate.builder()
                    .category("interaction")
                    .subCategory("general")
                    .key("memory_" + System.currentTimeMillis())
                    .value("{}")
                    .text("未识别的记忆信息")
                    .confidence(0.5)
                    .build();
        }
    }

    /**
     * 生成记忆键
     */
    private String generateMemoryKey(Map<String, Object> data) {
        try {
            StringBuilder keyBuilder = new StringBuilder();

            // 提取关键字段生成记忆键
            if (data.containsKey("category")) {
                keyBuilder.append(data.get("category")).append("_");
            }

            if (data.containsKey("subCategory")) {
                keyBuilder.append(data.get("subCategory")).append("_");
            }

            if (data.containsKey("text")) {
                String text = (String) data.get("text");
                if (text.length() > 10) {
                    keyBuilder.append(text.substring(0, Math.min(20, text.length())));
                } else {
                    keyBuilder.append(text);
                }
            } else {
                keyBuilder.append("key_").append(System.currentTimeMillis());
            }

            // 清理键中的特殊字符
            String key = keyBuilder.toString()
                    .replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "_")
                    .replaceAll("_+", "_")
                    .trim();

            return key.length() > 0 ? key : "memory_" + UUID.randomUUID().toString().substring(0, 8);

        } catch (Exception e) {
            log.error("生成记忆键失败", e);
            return "memory_" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    /**
     * 创建默认用户画像
     */
    private UserProfile createDefaultProfile(String userId) {
        return UserProfile.builder()
                .userId(userId)
                .userName(null)
                .userAge(null)
                .userCity(null)
                // 租房偏好默认值
                .budgetMin(2000)
                .budgetMax(5000)
                .preferredLocation(null)
                .houseType("apartment")
                .minArea(20)
                .maxArea(80)
                .floorPreference(2) // 默认中层
                .decorationLevel("简装")
                .orientation("南")
                // 风险偏好默认值
                .riskTolerance(0.5)
                .riskType("moderate")
                .verifyEverything(true)
                .preferDetailedContract(true)
                // 交互偏好默认值
                .responseStyle("detailed")
                .preferLegalRef(true)
                .preferMarketData(true)
                // 黑名单/信任列表（初始为空）
                .blacklistedAgencies("[]")
                .blacklistedAddresses("[]")
                .blacklistedPhones("[]")
                .trustedAgencies("[]")
                // 统计信息
                .totalSessions(0)
                .riskCasesReported(0)
                .commonScenarios("[]")
                .lastTopics("[]")
                .lastActiveAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 将实体添加到黑名单
     */
    private void addToBlacklist(UserProfile profile, String blacklistType, String entity) {
        if (StringUtils.isBlank(entity)) {
            return;
        }

        try {
            String jsonArray = getBlacklistJson(profile, blacklistType);
            List<String> blacklist = parseJsonArray(jsonArray);

            // 避免重复添加
            if (!blacklist.contains(entity)) {
                blacklist.add(entity);

                // 限制黑名单大小（最多100条）
                if (blacklist.size() > 100) {
                    blacklist = blacklist.subList(blacklist.size() - 100, blacklist.size());
                }

                String updatedJson = objectMapper.writeValueAsString(blacklist);

                // 更新对应的黑名单字段
                switch (blacklistType.toLowerCase()) {
                    case "agencies":
                        profile.setBlacklistedAgencies(updatedJson);
                        break;
                    case "addresses":
                        profile.setBlacklistedAddresses(updatedJson);
                        break;
                    case "phones":
                        profile.setBlacklistedPhones(updatedJson);
                        break;
                    default:
                        log.warn("未知的黑名单类型: {}", blacklistType);
                        return;
                }

                log.info("添加到黑名单 - 用户: {}, 类型: {}, 实体: {}",
                        profile.getUserId(), blacklistType, entity);

                // 同时从信任列表中移除（如果存在）
                removeFromTrustedList(profile, blacklistType, entity);

            } else {
                log.debug("实体已在黑名单中 - 用户: {}, 类型: {}, 实体: {}",
                        profile.getUserId(), blacklistType, entity);
            }

        } catch (Exception e) {
            log.error("添加到黑名单失败", e);
        }
    }

    /**
     * 获取对应类型的黑名单JSON
     */
    private String getBlacklistJson(UserProfile profile, String blacklistType) {
        switch (blacklistType.toLowerCase()) {
            case "agencies":
                return profile.getBlacklistedAgencies();
            case "addresses":
                return profile.getBlacklistedAddresses();
            case "phones":
                return profile.getBlacklistedPhones();
            default:
                return "[]";
        }
    }

    /**
     * 从信任列表中移除实体
     */
    private void removeFromTrustedList(UserProfile profile, String type, String entity) {
        try {
            if (StringUtils.isNotBlank(profile.getTrustedAgencies())) {
                List<String> trustedList = parseJsonArray(profile.getTrustedAgencies());
                if (trustedList.remove(entity)) {
                    String updatedJson = objectMapper.writeValueAsString(trustedList);
                    profile.setTrustedAgencies(updatedJson);
                    log.info("从信任列表中移除 - 用户: {}, 实体: {}", profile.getUserId(), entity);
                }
            }
        } catch (Exception e) {
            log.error("从信任列表移除失败", e);
        }
    }

    /**
     * 解析JSON数组
     */
    private List<String> parseJsonArray(String json) {
        try {
            if (StringUtils.isBlank(json) || "[]".equals(json) || "null".equals(json)) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            log.error("解析JSON数组失败: {}", json, e);
            return new ArrayList<>();
        }
    }

    /**
     * 将实体从黑名单中移除
     */
    public void removeFromBlacklist(UserProfile profile, String blacklistType, String entity) {
        if (StringUtils.isBlank(entity)) {
            return;
        }

        try {
            String jsonArray = getBlacklistJson(profile, blacklistType);
            List<String> blacklist = parseJsonArray(jsonArray);

            if (blacklist.remove(entity)) {
                String updatedJson = objectMapper.writeValueAsString(blacklist);

                switch (blacklistType.toLowerCase()) {
                    case "agencies":
                        profile.setBlacklistedAgencies(updatedJson);
                        break;
                    case "addresses":
                        profile.setBlacklistedAddresses(updatedJson);
                        break;
                    case "phones":
                        profile.setBlacklistedPhones(updatedJson);
                        break;
                }

                log.info("从黑名单中移除 - 用户: {}, 类型: {}, 实体: {}",
                        profile.getUserId(), blacklistType, entity);
            }

        } catch (Exception e) {
            log.error("从黑名单移除失败", e);
        }
    }

    /**
     * 检查实体是否在黑名单中
     */
    public boolean isInBlacklist(UserProfile profile, String blacklistType, String entity) {
        if (StringUtils.isBlank(entity)) {
            return false;
        }

        try {
            String jsonArray = getBlacklistJson(profile, blacklistType);
            List<String> blacklist = parseJsonArray(jsonArray);
            return blacklist.contains(entity);
        } catch (Exception e) {
            log.error("检查黑名单失败", e);
            return false;
        }
    }

    /**
     * 获取用户的所有黑名单（合并不同类型的黑名单）
     */
    public Map<String, List<String>> getAllBlacklists(UserProfile profile) {
        Map<String, List<String>> allBlacklists = new HashMap<>();

        try {
            allBlacklists.put("agencies", parseJsonArray(profile.getBlacklistedAgencies()));
            allBlacklists.put("addresses", parseJsonArray(profile.getBlacklistedAddresses()));
            allBlacklists.put("phones", parseJsonArray(profile.getBlacklistedPhones()));

            return allBlacklists;
        } catch (Exception e) {
            log.error("获取所有黑名单失败", e);
            return allBlacklists;
        }
    }

    /**
     * 归档会话记忆：将短期对话压缩并存入长期记忆
     */
    public void archiveSession(String userId, String sessionId, String scenario, String fullChatHistory) {
        try {
            // 1. 调用 LLM 生成压缩摘要 (关键：提取特征)
            String prompt = "请分析以下租房咨询对话，提取核心事实（房东信息、合同关键金额、风险点），" +
                    "生成一段150字以内的语义记忆摘要。对话内容：" + fullChatHistory;
            String summary = llmService.generate(prompt);

            // 2. 生成向量
            List<Float> vector = embeddingService.getEmbedding(summary);
            String vectorId = UUID.randomUUID().toString();

            // 3. 存入向量数据库 (语义搜索用)
            qdrantService.upsertPoint("user_memories", vectorId, vector, summary);

            // 4. 存入 MySQL (结构化展示用)
            LongTermMemory memory = LongTermMemory.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .scenario(scenario)
                    .memoryKey(scenario + "_summary")
                    .memoryContent(summary)
                    .importanceScore(0.8) // 默认高，可根据 LLM 判定调整
                    .vectorId(vectorId)
                    .build();
            memoryRepository.insert(memory);

            log.info("会话记忆已成功归档 - User: {}, VectorId: {}", userId, vectorId);
        } catch (Exception e) {
            log.error("归档长期记忆失败", e);
        }
    }

    /**
     * 加载相关记忆：为当前请求提供背景（优化异步处理版本）
     */
    public String retrieveRelevantMemories(String userId, String query) {
        // 1. 获取查询向量 (通常是同步操作)
        List<Float> queryVector = embeddingService.getEmbedding(query);

        // 2. 构建 Qdrant 搜索请求
        Points.SearchPoints searchReq = Points.SearchPoints.newBuilder()
                .setCollectionName("user_memories")
                .addAllVector(queryVector)
                .setLimit(3)
                .setWithPayload(Points.WithPayloadSelector.newBuilder()
                        .setEnable(true)
                        .build())
                .setScoreThreshold(0.7f)
                .build();

        // 3. 【关键优化点】发起异步搜索，不阻塞，立即向下执行
        ListenableFuture<List<Points.ScoredPoint>> qdrantFuture = qdrantService.searchAsync(searchReq);

        // 4. 【并行执行】在等待 Qdrant 返回的过程中，去查询 MySQL
        // 这样 Qdrant 的网络 IO 和 MySQL 的磁盘 IO 是并行的
        List<LongTermMemory> topMemories = memoryRepository.selectTopMemoriesByUser(userId);

        StringBuilder sb = new StringBuilder("【用户历史背景记忆】\n");

        try {
            // 5. 等待并获取 Qdrant 的异步结果 (阻塞直到超时或返回)
            // 建议加上超时时间，防止 Qdrant 挂了导致整个接口卡死，例如：qdrantFuture.get(2, TimeUnit.SECONDS)
            List<Points.ScoredPoint> scoredPoints = qdrantFuture.get();

            // 6. 处理语义记忆结果
            if (scoredPoints != null && !scoredPoints.isEmpty()) {
                for (Points.ScoredPoint point : scoredPoints) {
                    // 从 Payload 中提取之前存入的文本内容 (假设 Key 为 "content")
                    String content = "";
                    if (point.containsPayload("content")) {
                        content = point.getPayloadMap().get("content").getStringValue();
                    } else if (point.containsPayload("summary")) {
                        content = point.getPayloadMap().get("summary").getStringValue();
                    }

                    if (!content.isEmpty()) {
                        sb.append("- 相关历史记录: ").append(content)
                                .append(" (相关度: ").append(String.format("%.2f", point.getScore())).append(")\n");
                    }
                }
            } else {
                sb.append("- 暂无相关语义记忆\n");
            }

        } catch (InterruptedException | ExecutionException e) {
            // 7. 异常处理：如果向量搜索失败，记录日志并降级处理（仅显示 MySQL 记忆）
            log.error("Qdrant 异步搜索异常，用户: {}", userId, e);
            sb.append("- [警告] 语义记忆检索暂时不可用\n");
            // 保持线程中断状态
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }

        // 8. 组合 MySQL 存储的置信度最高的记忆
        if (topMemories != null && !topMemories.isEmpty()) {
            topMemories.forEach(m -> sb.append("- 关键偏好特征: ").append(m.getMemoryContent()).append("\n"));
        }

        return sb.toString();
    }
}

/**
 * 记忆更新对象
 */
@Data
@Builder
class MemoryUpdate {
    private String category;
    private String subCategory;
    private String key;
    private String value;  // JSON格式
    private String text;   // 自然语言描述
    private Double confidence;
}
