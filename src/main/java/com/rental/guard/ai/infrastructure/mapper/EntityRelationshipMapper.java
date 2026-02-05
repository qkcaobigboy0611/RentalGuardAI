/**
 * @author qkcao
 * @date 2026/2/5 16:33
 */
package com.rental.guard.ai.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rental.guard.ai.infrastructure.po.EntityRelationship;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntityRelationshipMapper extends BaseMapper<EntityRelationship> {

    /**
     * 根据源实体ID查询关系
     */
    @Select("SELECT * FROM entity_relationship WHERE source_entity_id = #{sourceEntityId} AND deleted = 0 ORDER BY confidence DESC, updated_at DESC")
    List<EntityRelationship> findBySourceEntityId(@Param("sourceEntityId") String sourceEntityId);

    /**
     * 根据目标实体ID查询关系
     */
    @Select("SELECT * FROM entity_relationship WHERE target_entity_id = #{targetEntityId} AND deleted = 0 ORDER BY confidence DESC, updated_at DESC")
    List<EntityRelationship> findByTargetEntityId(@Param("targetEntityId") String targetEntityId);

    /**
     * 查询实体的所有关系（无论是作为源还是目标）
     */
    @Select("SELECT * FROM entity_relationship WHERE (source_entity_id = #{entityId} OR target_entity_id = #{entityId}) AND deleted = 0 ORDER BY confidence DESC, updated_at DESC")
    List<EntityRelationship> findAllRelationships(@Param("entityId") String entityId);


    /**
     * 查询实体的所有关系（无论是作为源还是目标）
     */
    @Select("SELECT * FROM entity_relationship")
    List<EntityRelationship> findAll();

}
