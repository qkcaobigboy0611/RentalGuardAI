/**
 * @author qkcao
 * @date 2026/2/5 15:40
 */
package com.rental.guard.ai.infrastructure.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

/**
 * 长期记忆实体（MySQL存储，所有字段平放）
 */
@Data
@Builder
@TableName(value = "long_term_memory", autoResultMap = true)
public class LongTermMemory {

    @Id
    private Long id;

    // 关联信息
    private String userId;
    private String sessionId;

    // 记忆分类
    private String category;        // 记忆分类：preference, risk, interaction, blacklist
    private String subCategory;     // 子分类：budget, location, contract, etc.

    // 记忆内容（平放字段）
    private String memoryKey;       // 记忆键，用于快速检索
    private String memoryValue;     // 记忆值（JSON格式存储）
    private String memoryText;      // 记忆文本（用于向量搜索）

    private String scenario; // 场景

    private String memoryContent;  // 压缩后的记忆内容

    private Double importanceScore; // 重要程度 (0-1)

    // 元数据
    private Double confidence;      // 置信度 0-1
    private String source;          // 来源：user_input, system_extract, external_api
    private Integer priority;       // 优先级 1-10

    // 统计信息
    private Integer accessCount;
    private LocalDateTime lastAccessedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 向量搜索相关
    private String embedding;       // 嵌入向量（JSON数组字符串）
    private Double similarity;      // 最近一次查询的相似度

    private String vectorId;       // 对应的 Qdrant 向量 ID
}
