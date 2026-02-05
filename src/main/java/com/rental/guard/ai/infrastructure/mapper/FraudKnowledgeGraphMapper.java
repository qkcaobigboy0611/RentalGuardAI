/**
 * @author qkcao
 * @date 2026/2/5 14:58
 */
package com.rental.guard.ai.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rental.guard.ai.infrastructure.po.FraudKnowledgeGraph;
import com.rental.guard.ai.infrastructure.po.PoChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱模块实现
 */
@Mapper
public interface FraudKnowledgeGraphMapper extends BaseMapper<FraudKnowledgeGraph> {

    /**
     * 根据实体ID查询
     */
    @Select("SELECT * FROM fraud_knowledge_graph WHERE entity_id = #{entityId} AND deleted = 0")
    FraudKnowledgeGraph findByEntityId(@Param("entityId") String entityId);

    /**
     * 根据实体名称和类型查询
     */
    @Select("SELECT * FROM fraud_knowledge_graph WHERE entity_name = #{entityName} AND entity_type = #{entityType} AND deleted = 0")
    FraudKnowledgeGraph findByEntityNameAndType(@Param("entityName") String entityName,
                                                @Param("entityType") String entityType);

    /**
     * 根据关键词和风险等级搜索
     */
    @Select({
            "<script>",
            "SELECT * FROM fraud_knowledge_graph WHERE deleted = 0",
            "AND (",
            "   entity_name LIKE CONCAT('%', #{keyword}, '%')",
            "   OR address LIKE CONCAT('%', #{keyword}, '%')",
            "   OR phone_number LIKE CONCAT('%', #{keyword}, '%')",
            "   OR wechat_id LIKE CONCAT('%', #{keyword}, '%')",
            "   OR agency_name LIKE CONCAT('%', #{keyword}, '%')",
            "   OR landlord_name LIKE CONCAT('%', #{keyword}, '%')",
            "   OR description LIKE CONCAT('%', #{keyword}, '%')",
            ")",
            "<if test='riskLevels != null and riskLevels.size() > 0'>",
            "   AND risk_level IN",
            "   <foreach collection='riskLevels' item='level' open='(' separator=',' close=')'>",
            "       #{level}",
            "   </foreach>",
            "</if>",
            "ORDER BY report_count DESC, updated_at DESC",
            "<if test='limit != null'>",
            "   LIMIT #{limit}",
            "</if>",
            "</script>"
    })
    List<FraudKnowledgeGraph> searchByKeywordAndRisk(@Param("keyword") String keyword,
                                                     @Param("riskLevels") List<String> riskLevels,
                                                     @Param("limit") Integer limit);

    /**
     * 根据类型和风险等级查询
     */
    @Select("SELECT * FROM fraud_knowledge_graph WHERE entity_type = #{entityType} AND risk_level = #{riskLevel} AND deleted = 0 ORDER BY report_count DESC")
    List<FraudKnowledgeGraph> findByTypeAndRisk(@Param("entityType") String entityType,
                                                @Param("riskLevel") String riskLevel);

    /**
     * 根据类型和风险等级查询
     */
    @Select("SELECT * FROM fraud_knowledge_graph")
    List<FraudKnowledgeGraph> findAll();
}
