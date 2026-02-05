/**
 * @author qkcao
 * @date 2026/2/5 15:51
 */
package com.rental.guard.ai.domain.service.v1;

import com.rental.guard.ai.infrastructure.mapper.EntityRelationshipMapper;
import com.rental.guard.ai.infrastructure.mapper.FraudKnowledgeGraphMapper;
import com.rental.guard.ai.infrastructure.po.EntityRelationship;
import com.rental.guard.ai.infrastructure.po.FraudKnowledgeGraph;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简易知识图谱服务（使用MySQL + 内存索引）
 */
@Service
@Slf4j
public class SimpleKnowledgeGraphService {

    @Autowired
    private FraudKnowledgeGraphMapper graphMapper;
    @Autowired
    private EntityRelationshipMapper relationshipMapper;

    // 内存索引
    private final Map<String, List<FraudKnowledgeGraph>> entityIndex = new ConcurrentHashMap<>();
    private final Map<String, List<EntityRelationship>> relationshipIndex = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadKnowledgeGraphToMemory();
    }

    /**
     * 加载知识图谱到内存
     */
    private void loadKnowledgeGraphToMemory() {
        try {
            // 加载所有实体
            List<FraudKnowledgeGraph> allEntities = graphMapper.findAll();

            // 构建索引
            allEntities.forEach(entity -> {
                // 按实体类型索引
                entityIndex.computeIfAbsent(entity.getEntityType(), k -> new ArrayList<>())
                        .add(entity);

                // 按名称前缀索引（用于模糊匹配）
                if (StringUtils.isNotBlank(entity.getEntityName())) {
                    String prefix = entity.getEntityName().substring(0, Math.min(2, entity.getEntityName().length()));
                    entityIndex.computeIfAbsent("prefix:" + prefix, k -> new ArrayList<>())
                            .add(entity);
                }
            });

            // 加载所有关系
            List<EntityRelationship> allRelationships = relationshipMapper.findAll();
            allRelationships.forEach(rel -> {
                relationshipIndex.computeIfAbsent(rel.getSourceEntityId(), k -> new ArrayList<>())
                        .add(rel);
                relationshipIndex.computeIfAbsent(rel.getTargetEntityId(), k -> new ArrayList<>())
                        .add(rel);
            });

            log.info("知识图谱加载完成 - 实体: {}, 关系: {}",
                    allEntities.size(), allRelationships.size());

        } catch (Exception e) {
            log.error("加载知识图谱失败", e);
        }
    }

    /**
     * 实体查询和匹配
     */
    public List<EntityMatch> queryAndMatchEntities(String text) {
        List<EntityMatch> matches = new ArrayList<>();

        // 1. 提取文本中的实体
        List<ExtractedEntity> extractedEntities = extractEntitiesFromText(text);

        // 2. 匹配知识图谱
        for (ExtractedEntity extracted : extractedEntities) {
            List<FraudKnowledgeGraph> matchedEntities = findMatchingEntities(extracted);

            for (FraudKnowledgeGraph entity : matchedEntities) {
                double similarity = calculateSimilarity(extracted, entity);
                if (similarity > 0.7) {  // 相似度阈值
                    EntityMatch match = EntityMatch.builder()
                            .extractedEntity(extracted)
                            .kgEntity(entity)
                            .similarity(similarity)
                            .riskLevel(entity.getRiskLevel())
                            .relatedEntities(findRelatedEntities(entity.getEntityId()))
                            .build();
                    matches.add(match);
                }
            }
        }

        // 3. 按相似度排序
        matches.sort(Comparator.comparing(EntityMatch::getSimilarity).reversed());

        return matches;
    }

    /**
     * 从文本中提取实体
     */
    private List<ExtractedEntity> extractEntitiesFromText(String text) {
        List<ExtractedEntity> entities = new ArrayList<>();

        // 提取电话号码
        Pattern phonePattern = Pattern.compile("1[3-9]\\d{9}");
        Matcher phoneMatcher = phonePattern.matcher(text);
        while (phoneMatcher.find()) {
            entities.add(ExtractedEntity.builder()
                    .text(phoneMatcher.group())
                    .type("PHONE")
                    .position(phoneMatcher.start())
                    .build());
        }

        // 提取地址（简化版）
        String[] addressKeywords = {"路", "街", "巷", "小区", "大厦", "公寓"};
        for (String keyword : addressKeywords) {
            int index = text.indexOf(keyword);
            if (index > 0) {
                int start = Math.max(0, index - 10);
                int end = Math.min(text.length(), index + 10);
                String address = text.substring(start, end);
                entities.add(ExtractedEntity.builder()
                        .text(address)
                        .type("ADDRESS")
                        .position(index)
                        .build());
            }
        }

        // 提取中介公司（常见中介名称）
        String[] agencyKeywords = {"链家", "我爱我家", "中原", "贝壳", "安居客", "房天下"};
        for (String keyword : agencyKeywords) {
            if (text.contains(keyword)) {
                int index = text.indexOf(keyword);
                entities.add(ExtractedEntity.builder()
                        .text(keyword)
                        .type("AGENCY")
                        .position(index)
                        .build());
            }
        }

        return entities;
    }

    /**
     * 查找匹配的实体
     */
    private List<FraudKnowledgeGraph> findMatchingEntities(ExtractedEntity extracted) {
        List<FraudKnowledgeGraph> matches = new ArrayList<>();
        String type = extracted.getType();

        // 从内存索引中查找
        List<FraudKnowledgeGraph> typeEntities = entityIndex.get(type);
        if (typeEntities != null) {
            for (FraudKnowledgeGraph entity : typeEntities) {
                if (isEntityMatch(extracted, entity)) {
                    matches.add(entity);
                }
            }
        }

        // 模糊匹配
        if (matches.isEmpty() && extracted.getText().length() >= 2) {
            String prefix = extracted.getText().substring(0, 2);
            List<FraudKnowledgeGraph> prefixEntities = entityIndex.get("prefix:" + prefix);
            if (prefixEntities != null) {
                for (FraudKnowledgeGraph entity : prefixEntities) {
                    if (entity.getEntityName().contains(extracted.getText()) ||
                            extracted.getText().contains(entity.getEntityName())) {
                        matches.add(entity);
                    }
                }
            }
        }

        return matches;
    }

    /**
     * 判断实体是否匹配
     */
    private boolean isEntityMatch(ExtractedEntity extracted, FraudKnowledgeGraph entity) {
        String extractedText = extracted.getText();
        String entityName = entity.getEntityName();

        switch (extracted.getType()) {
            case "PHONE":
                return extractedText.equals(entity.getPhoneNumber());

            case "ADDRESS":
                return entityName.contains(extractedText) ||
                        extractedText.contains(entityName) ||
                        (entity.getAddress() != null && entity.getAddress().contains(extractedText));

            case "AGENCY":
                return entityName.contains(extractedText) ||
                        extractedText.contains(entityName);

            default:
                return false;
        }
    }

    /**
     * 计算相似度
     */
    private double calculateSimilarity(ExtractedEntity extracted, FraudKnowledgeGraph entity) {
        String extractedText = extracted.getText();
        String entityName = entity.getEntityName();

        // 精确匹配
        if (extractedText.equals(entityName) ||
                (extracted.getType().equals("PHONE") && extractedText.equals(entity.getPhoneNumber()))) {
            return 1.0;
        }

        // 包含匹配
        if (entityName.contains(extractedText) || extractedText.contains(entityName)) {
            return 0.9;
        }

        // 编辑距离相似度
        int maxLength = Math.max(extractedText.length(), entityName.length());
        int distance = StringUtils.getLevenshteinDistance(extractedText, entityName);
        return 1.0 - (double) distance / maxLength;
    }

    /**
     * 查找关联实体
     */
    private List<FraudKnowledgeGraph> findRelatedEntities(String entityId) {
        List<FraudKnowledgeGraph> related = new ArrayList<>();
        List<EntityRelationship> relationships = relationshipIndex.get(entityId);

        if (relationships != null) {
            for (EntityRelationship rel : relationships) {
                String relatedEntityId = rel.getSourceEntityId().equals(entityId) ?
                        rel.getTargetEntityId() : rel.getSourceEntityId();

                graphMapper.findByEntityId(relatedEntityId);
            }
        }

        return related;
    }

    /**
     * 风险评估
     */
    public RiskAssessment assessRisk(String text) {
        List<EntityMatch> matches = queryAndMatchEntities(text);

        RiskAssessment.RiskAssessmentBuilder assessment = RiskAssessment.builder();

        assessment.matches(matches);

        // 计算最高风险
        if (!matches.isEmpty()) {
            EntityMatch highestRisk = matches.stream()
                    .max(Comparator.comparing(m -> getRiskScore(m.getRiskLevel())))
                    .orElse(null);

            if (highestRisk != null) {
                assessment.highestRiskLevel(highestRisk.getRiskLevel());
                assessment.highestRiskEntity(highestRisk.getKgEntity().getEntityName());
                assessment.riskScore(calculateOverallRiskScore(matches));
            }
        }

        return assessment.build();
    }

    /**
     * 添加新的风险实体
     */
    public void addRiskEntity(String userId, RiskEntityReport report) {
        try {
            // 检查是否已存在
            FraudKnowledgeGraph entity1 = graphMapper.findByEntityNameAndType(
                    report.getEntityName(), report.getEntityType());

            if (entity1 != null) {
                // 更新现有实体
                entity1.setReportCount(entity1.getReportCount() + 1);
                entity1.setRiskLevel(calculateNewRiskLevel(entity1, report));
                entity1.setLastReportedAt(LocalDateTime.now());
                entity1.setUpdatedAt(LocalDateTime.now());
                graphMapper.insert(entity1);

                log.info("更新风险实体 - entity: {}, reports: {}",
                        entity1.getEntityName(), entity1.getReportCount());
            } else {
                // 创建新实体
                FraudKnowledgeGraph entity = FraudKnowledgeGraph.builder()
                        .entityId(UUID.randomUUID().toString())
                        .entityName(report.getEntityName())
                        .entityType(report.getEntityType())
                        .riskLevel(report.getInitialRiskLevel())
                        .reportCount(1)
                        .confirmCount(0)
                        .isVerified(false)
                        .description(report.getDescription())
                        .evidence(report.getEvidence())
                        .source("user_report:" + userId)
                        .firstReportedAt(LocalDateTime.now())
                        .lastReportedAt(LocalDateTime.now())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                // 设置具体字段
                switch (report.getEntityType()) {
                    case "PHONE":
                        entity.setPhoneNumber(report.getEntityName());
                        break;
                    case "ADDRESS":
                        entity.setAddress(report.getEntityName());
                        break;
                    case "AGENCY":
                        entity.setAgencyName(report.getEntityName());
                        break;
                }

                graphMapper.insert(entity);

                // 添加到内存索引
                entityIndex.computeIfAbsent(entity.getEntityType(), k -> new ArrayList<>())
                        .add(entity);

                log.info("添加新风险实体 - entity: {}, type: {}",
                        entity.getEntityName(), entity.getEntityType());
            }

        } catch (Exception e) {
            log.error("添加风险实体失败", e);
            throw new RuntimeException("添加风险实体失败", e);
        }
    }

    // 辅助方法
    private double getRiskScore(String riskLevel) {
        Map<String, Double> riskScores = Map.of(
                "CRITICAL", 4.0,
                "HIGH", 3.0,
                "MEDIUM", 2.0,
                "LOW", 1.0
        );
        return riskScores.getOrDefault(riskLevel, 0.0);
    }

    private double calculateOverallRiskScore(List<EntityMatch> matches) {
        if (matches.isEmpty()) return 0.0;

        double totalScore = matches.stream()
                .mapToDouble(m -> getRiskScore(m.getRiskLevel()) * m.getSimilarity())
                .sum();

        return totalScore / matches.size();
    }

    private String calculateNewRiskLevel(FraudKnowledgeGraph entity, RiskEntityReport report) {
        // 基于现有风险等级和新的举报计算新风险等级
        Map<String, Integer> riskWeights = Map.of(
                "LOW", 1,
                "MEDIUM", 2,
                "HIGH", 3,
                "CRITICAL", 4
        );

        int currentWeight = riskWeights.getOrDefault(entity.getRiskLevel(), 1);
        int reportWeight = riskWeights.getOrDefault(report.getInitialRiskLevel(), 1);

        int newWeight = Math.min(4, Math.max(1, (currentWeight + reportWeight) / 2));

        return riskWeights.entrySet().stream()
                .filter(e -> e.getValue() == newWeight)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("MEDIUM");
    }
}

/**
 * 实体匹配结果
 */
@Data
@Builder
class EntityMatch {
    private ExtractedEntity extractedEntity;
    private FraudKnowledgeGraph kgEntity;
    private Double similarity;
    private String riskLevel;
    private List<FraudKnowledgeGraph> relatedEntities;
}

/**
 * 提取的实体
 */
@Data
@Builder
class ExtractedEntity {
    private String text;
    private String type;
    private Integer position;
}

/**
 * 风险评估结果
 */
@Data
@Builder
class RiskAssessment {
    private List<EntityMatch> matches;
    private String highestRiskLevel;
    private String highestRiskEntity;
    private Double riskScore;
    private String recommendation;
}

/**
 * 风险实体举报
 */
@Data
@Builder
class RiskEntityReport {
    private String entityName;
    private String entityType;  // PHONE, ADDRESS, AGENCY, etc.
    private String initialRiskLevel;
    private String description;
    private String evidence;
    private String reporterUserId;
}
