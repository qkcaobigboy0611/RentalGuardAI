/**
 * @author qkcao
 * @date 2026/2/5 15:43
 */
package com.rental.guard.ai.infrastructure.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

/**
 * 实体关系表（存储实体间关系）
 */
@Data
@Builder
@TableName(value = "entity_relationship", autoResultMap = true)
public class EntityRelationship {
    @Id
    private Long id;

    private String relationshipId;   // 关系唯一ID

    // 关联实体
    private String sourceEntityId;   // 源实体ID
    private String targetEntityId;   // 目标实体ID

    // 关系信息
    private String relationshipType; // OWNS, WORKS_FOR, LOCATED_AT, USES, ALIAS_OF
    private String relationshipDesc; // 关系描述

    private Double confidence;       // 关系置信度
    private String evidence;         // 证据

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
