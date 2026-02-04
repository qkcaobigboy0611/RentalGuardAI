/**
 * @author qkcao
 * @date 2026/1/28 15:27
 */
package com.rental.guard.ai.domain.service.v1;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.rental.guard.ai.domain.dto.v1.AgentResponse;
import com.rental.guard.ai.domain.service.FraudDetectionService;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG服务 - 检索增强生成
 */
@Slf4j
@Service
public class RAGService {

    private final QdrantService qdrantService;
    private final ResourceLoader resourceLoader;
    @Autowired
    private FraudDetectionService fraudDetectionService;

    @Autowired
    public RAGService(QdrantService qdrantService,
                      ResourceLoader resourceLoader) {
        this.qdrantService = qdrantService;
        this.resourceLoader = resourceLoader;

        // 初始化知识库
        initializeKnowledgeBase();
    }

    /**
     * 初始化知识库
     */
    private void initializeKnowledgeBase() {
        try {
            // 检查向量存储是否已初始化
            List<Float> aaaa = fraudDetectionService.getEmbedding("租房合同");
            boolean bbb = qdrantService.isEmptySimilarDocs("租房合同", "fraud_training_cases", aaaa);

            if (bbb) {
                log.info("开始初始化RAG知识库...");

                // 加载知识库文档
                loadKnowledgeDocuments();

                log.info("RAG知识库初始化完成");
            }
        } catch (Exception e) {
            log.error("初始化RAG知识库失败", e);
        }
    }

    /**
     * 加载知识库文档
     */
    private void loadKnowledgeDocuments() {
        try {
            // 法律知识
            loadLegalDocuments();

            // 市场数据知识
            loadMarketDataDocuments();

            // 风险案例知识
            loadCaseStudyDocuments();

            // 通用建议知识
            loadAdviceDocuments();

        } catch (Exception e) {
            log.error("加载知识库文档失败", e);
        }
    }

    /**
     * 加载法律文档
     */
    private void loadLegalDocuments() {
        String[] legalFiles = {
                "civil_code.txt",    // 民法典相关
                "contract_law.txt",  // 合同法相关
                "consumer_rights.txt", // 消费者权益保护法
                "advertisement_law.txt" // 广告法
        };

        for (String fileName : legalFiles) {
            try {
                Resource resource = resourceLoader.getResource( "knowledge/legal/" + fileName);
                if (resource.exists()) {
                    TextReader reader = new TextReader(resource);
                    List<Document> documents = reader.get();

                    // 分割文档
                    TokenTextSplitter splitter = new TokenTextSplitter();
                    List<Document> splitDocs = splitter.apply(documents);

                    // 添加元数据
                    splitDocs.forEach(doc -> {
                        doc.getMetadata().put("source", "法律条文");
                        doc.getMetadata().put("category", "legal");
                        doc.getMetadata().put("file", fileName);
                    });

                    // todo 存储到向量数据库
                    //vectorStore.add(splitDocs);


                    log.info("加载法律文档: {}，分割为 {} 个片段", fileName, splitDocs.size());
                }
            } catch (Exception e) {
                log.warn("加载法律文档失败: {}", fileName, e);
            }
        }
    }

    /**
     * 加载市场数据文档
     */
    private void loadMarketDataDocuments() {
        // 模拟市场数据
        List<String> marketData = Arrays.asList(
                "北京市平均租金数据：一居室4500-6000元，二居室6000-8000元",
                "上海市租金回报率：平均2.5%-3.5%",
                "地铁房溢价：靠近地铁站房源租金溢价10%-20%",
                "租房高峰期：春节后（2-3月）和毕业季（6-8月）",
                "租金谈判空间：通常为报价的5%-15%"
        );

        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < marketData.size(); i++) {
            Document doc = new Document(marketData.get(i));
            doc.getMetadata().put("source", "市场数据");
            doc.getMetadata().put("category", "market");
            doc.getMetadata().put("dataType", "rental_statistics");
            documents.add(doc);
        }

        // todo vectorStore.add(documents);
        log.info("加载市场数据文档: {} 条", documents.size());
    }

    /**
     * 加载案例研究文档
     */
    private void loadCaseStudyDocuments() {
        List<String> cases = Arrays.asList(
                "案例1：合同模糊条款纠纷 - 租客因'自然损耗'定义不明确被扣全部押金",
                "案例2：虚假距离宣传 - 中介声称5分钟到地铁，实际需要20分钟步行",
                "案例3：隐性费用欺诈 - 合同外收取多项未明确费用",
                "案例4：霸王条款维权 - '断租不退押金'条款被法院判定无效",
                "案例5：租金高于市场价 - 租客成功通过市场数据对比砍价15%"
        );

        List<Document> documents = new ArrayList<>();
        for (String caseText : cases) {
            Document doc = new Document(caseText);
            doc.getMetadata().put("source", "风险案例");
            doc.getMetadata().put("category", "case_study");
            documents.add(doc);
        }

        // todo vectorStore.add(documents);
        log.info("加载案例研究文档: {} 个", documents.size());
    }

    /**
     * 加载建议文档
     */
    private void loadAdviceDocuments() {
        List<String> adviceList = Arrays.asList(
                "签约前建议：实地考察房屋状况，拍照记录现有损坏",
                "合同审查建议：重点关注押金条款、维修责任、解约条件",
                "价格谈判建议：收集周边同类房源价格作为谈判依据",
                "风险防范建议：所有承诺要求书面化，保留沟通记录",
                "法律维权建议：遇到霸王条款可向市场监督管理局投诉"
        );

        List<Document> documents = new ArrayList<>();
        for (String advice : adviceList) {
            Document doc = new Document(advice);
            doc.getMetadata().put("source", "专家建议");
            doc.getMetadata().put("category", "advice");
            documents.add(doc);
        }

        // todo vectorStore.add(documents);
        log.info("加载建议文档: {} 条", documents.size());
    }

    /**
     * 检索相关文档
     */
    public List<AgentResponse.RetrievedDocument> retrieveRelevantDocuments(String query, String scenario) {
        try {
            // 将查询文本转换为向量（这里需要实现文本向量化）
            List<Float> queryVector = fraudDetectionService.getEmbedding(query);

            // 构建过滤条件
            Points.Filter.Builder filterBuilder = Points.Filter.newBuilder();

            // 根据场景添加过滤器
            if (scenario != null) {
                List<Points.Condition> conditions = new ArrayList<>();

                switch (scenario) {
                    case "合同审核":
                        conditions.add(buildCategoryCondition("legal"));
                        break;
                    case "租金欺诈":
                        conditions.add(buildCategoryCondition("market"));
                        break;
                    case "距离欺诈":
                        conditions.add(buildCategoryCondition("case_study"));
                        break;
                    case "霸王条款":
                        conditions.add(buildCategoryCondition("legal"));
                        break;
                }
                if (!conditions.isEmpty()) {
                    filterBuilder.addAllMust(conditions);
                }
            }

            Points.Filter filter = filterBuilder.build();

            // 使用 SearchPoints 构建搜索请求
            Points.SearchPoints searchReq = Points.SearchPoints.newBuilder()
                    .setCollectionName("fraud_training_cases")
                    .addAllVector(queryVector)  // 查询向量
                    .setLimit(5)
                    .setWithPayload(Points.WithPayloadSelector.newBuilder()
                            .setEnable(true)
                            .build())
                    .setScoreThreshold((float)0.7)
                    .setFilter(filter)
                    .build();


            // 执行搜索
            ListenableFuture<List<Points.ScoredPoint>> listListenableFuture = qdrantService.searchAsync(searchReq);


            // 处理搜索结果
            return listListenableFuture.get().stream()
                    .map(scoredPoint -> {
                        // 获取元数据
                        Map<String, JsonWithInt.Value> payloadMap = scoredPoint.getPayloadMap();
                        Map<String, Object> metadata = convertPayloadToMap(payloadMap);

                        // 获取相似度分数（Qdrant 返回的是相似度分数，通常 0-1 之间）
                        float similarityScore = scoredPoint.getScore();

                        // 确保分数在合理范围内
                        double relevance = Math.min(Math.max(similarityScore, 0.0), 1.0);

                        // 获取文档内容
                        String content = extractContentFromPayload(payloadMap);

                        return AgentResponse.RetrievedDocument.builder()
                                .documentId(extractDocumentId(scoredPoint, metadata))
                                .source((String) metadata.getOrDefault("source", "未知来源"))
                                .content(content)
                                .relevanceScore(relevance)
                                .metadata(metadata)
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("检索文档失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建分类过滤条件
     */
    private Points.Condition buildCategoryCondition(String category) {
        return Points.Condition.newBuilder()
                .setField(Points.FieldCondition.newBuilder()
                        .setKey("category")
                        .setMatch(Points.Match.newBuilder()
                                .setKeyword(category)
                                .build())
                        .build())
                .build();
    }

    /**
     * 构建过滤表达式
     */
    private String buildFilterExpression(Map<String, Object> filters) {
        return filters.entrySet().stream()
                .map(entry -> String.format("%s == '%s'", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(" AND "));
    }

    /**
     * 增强查询 - 使用查询扩展技术
     */
    public String enhanceQuery(String originalQuery, String scenario) {
        // 根据场景添加相关关键词
        Map<String, List<String>> scenarioKeywords = new HashMap<>();
        scenarioKeywords.put("合同审核", Arrays.asList("押金", "条款", "合同", "法律", "民法典"));
        scenarioKeywords.put("距离欺诈", Arrays.asList("地铁", "步行", "距离", "虚假宣传", "广告法"));
        scenarioKeywords.put("租金欺诈", Arrays.asList("价格", "市场价", "砍价", "谈判", "费用"));
        scenarioKeywords.put("霸王条款", Arrays.asList("违法", "无效", "格式条款", "消费者权益"));

        StringBuilder enhancedQuery = new StringBuilder(originalQuery);

        if (scenarioKeywords.containsKey(scenario)) {
            List<String> keywords = scenarioKeywords.get(scenario);
            for (String keyword : keywords) {
                if (!originalQuery.contains(keyword)) {
                    enhancedQuery.append(" ").append(keyword);
                }
            }
        }

        return enhancedQuery.toString();
    }

    /**
     * 添加用户文档到知识库
     */
    public void addUserDocument(String content, Map<String, Object> metadata) {
        try {
            Document document = new Document(content, metadata);
            // todo vectorStore.add(List.of(document));
            log.info("添加用户文档到知识库");
        } catch (Exception e) {
            log.error("添加用户文档失败", e);
        }
    }

    /**
     * 获取知识库统计信息
     */
    public Map<String, Object> getKnowledgeBaseStats() {
        Map<String, Object> stats = new HashMap<>();

        // 这里应该实现获取向量存储统计信息的逻辑
        // 简化处理：返回模拟数据
        stats.put("totalDocuments", 150);
        stats.put("categories", Arrays.asList("legal", "market", "case_study", "advice"));
        stats.put("lastUpdated", new Date());

        return stats;
    }

    /**
     * 提取文档内容
     */
    private String extractContent(Struct payload) {
        Value contentValue = payload.getFieldsMap().get("content");
        if (contentValue != null && contentValue.getKindCase() == Value.KindCase.STRING_VALUE) {
            return contentValue.getStringValue();
        }

        // 如果没有 content 字段，尝试其他可能的字段
        Value textValue = payload.getFieldsMap().get("text");
        if (textValue != null && textValue.getKindCase() == Value.KindCase.STRING_VALUE) {
            return textValue.getStringValue();
        }

        return "";
    }

    /**
     * 提取文档ID
     */
    private String extractDocumentId(Points.ScoredPoint scoredPoint, Map<String, Object> metadata) {
        // 优先从元数据中获取
        if (metadata.containsKey("id")) {
            return metadata.get("id").toString();
        }

        // 如果没有，使用 Qdrant 的 point id
        return String.valueOf(scoredPoint.getId().getNum());
    }

    // 将 JsonWithInt.Value 转换为 Java Object
    private Map<String, Object> convertPayloadToMap(Map<String, JsonWithInt.Value> payloadMap) {
        Map<String, Object> result = new HashMap<>();

        for (Map.Entry<String, JsonWithInt.Value> entry : payloadMap.entrySet()) {
            result.put(entry.getKey(), convertJsonWithIntValue(entry.getValue()));
        }

        return result;
    }

    private Object convertJsonWithIntValue(JsonWithInt.Value value) {
        // 根据 JsonWithInt.Value 的类型进行转换
        // 这取决于您使用的 Qdrant Java 客户端版本的具体实现

        if (value == null) {
            return null;
        }

        // 假设 JsonWithInt.Value 有相应的方法来获取不同类型的数据
        // 您需要根据实际的 API 进行调整

        try {
            // 方法1: 如果是字符串
            if (value.hasStringValue()) {
                return value.getStringValue();
            }

            // 方法2: 如果是数字
            if (value.hasIntegerValue()) {
                return value.getIntegerValue(); // 或者 getLongValue()
            }

            // 方法3: 如果是浮点数
            if ( value.hasDoubleValue()) {
                return value.getDoubleValue(); // 或者 getDoubleValue()
            }

            // 方法4: 如果是布尔值
            if (value.hasBoolValue()) {
                return value.getBoolValue();
            }

            // 方法6: 如果是数组
            if (value.hasListValue()) {
                return value.getListValue().getValuesList().stream()
                        .map(this::convertJsonWithIntValue)
                        .collect(Collectors.toList());
            }

            // 方法7: 尝试直接获取值
            return value.toString();

        } catch (Exception e) {
            // 如果以上方法都不适用，尝试通用的方法
            return extractValueFromJsonWithInt(value);
        }
    }

    // 从 payloadMap 中提取内容
    private String extractContentFromPayload(Map<String, JsonWithInt.Value> payloadMap) {
        JsonWithInt.Value contentValue = payloadMap.get("content");
        if (contentValue != null) {
            try {
                // 尝试获取字符串值
                if (contentValue.hasStringValue()) {
                    return contentValue.getStringValue();
                }
                // 或者直接 toString
                return contentValue.toString();
            } catch (Exception e) {
                // 处理异常
            }
        }
        return "";
    }

    // 如果 JsonWithInt.Value 有特定的 getValue 方法
    private Object extractValueFromJsonWithInt(JsonWithInt.Value value) {
        try {
            // 方法1: 尝试调用 getValue() 方法
            java.lang.reflect.Method getValueMethod = value.getClass().getMethod("getValue");
            Object rawValue = getValueMethod.invoke(value);
            if (rawValue != null) {
                return rawValue;
            }
        } catch (Exception e) {
            // 忽略异常
        }

        // 方法2: 使用反射获取所有字段
        try {
            java.lang.reflect.Field[] fields = value.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                Object fieldValue = field.get(value);
                if (fieldValue != null) {
                    return fieldValue;
                }
            }
        } catch (Exception e) {
            // 忽略异常
        }

        // 方法3: 最后返回字符串表示
        return value.toString();
    }

}
