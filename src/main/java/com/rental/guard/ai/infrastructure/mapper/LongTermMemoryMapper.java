/**
 * @author qkcao
 * @date 2026/2/5 16:41
 */
package com.rental.guard.ai.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rental.guard.ai.infrastructure.po.LongTermMemory;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LongTermMemoryMapper extends BaseMapper<LongTermMemory> {
    /**
     * 根据用户ID和分类查询记忆
     */
    @Select("SELECT * FROM long_term_memory WHERE user_id = #{userId} AND category = #{category} AND deleted = 0 ORDER BY updated_at DESC")
    List<LongTermMemory> findByUserIdAndCategory(@Param("userId") String userId,
                                                 @Param("category") String category);

    /**
     * 查询用户最近的重要记忆（优先级>=7）
     */
    @Select("SELECT * FROM long_term_memory WHERE user_id = #{userId} AND priority >= 7 AND deleted = 0 ORDER BY updated_at DESC LIMIT #{limit}")
    List<LongTermMemory> findRecentImportantMemories(@Param("userId") String userId,
                                                     @Param("limit") int limit);

    /**
     * 根据关键词搜索用户记忆
     */
    @Select({
            "<script>",
            "SELECT * FROM long_term_memory WHERE user_id = #{userId} AND deleted = 0",
            "AND (",
            "   memory_text LIKE CONCAT('%', #{keyword}, '%')",
            "   OR memory_key LIKE CONCAT('%', #{keyword}, '%')",
            "   OR JSON_UNQUOTE(JSON_EXTRACT(memory_value, '$')) LIKE CONCAT('%', #{keyword}, '%')",
            ")",
            "ORDER BY priority DESC, updated_at DESC",
            "</script>"
    })
    List<LongTermMemory> searchByKeyword(@Param("userId") String userId,
                                         @Param("keyword") String keyword);


    @Insert("INSERT INTO long_term_memory(user_id, session_id, scenario, memory_key, memory_content, importance_score, vector_id, created_at) " +
            "VALUES(#{userId}, #{sessionId}, #{scenario}, #{memoryKey}, #{memoryContent}, #{importanceScore}, #{vectorId}, NOW())")
    int insert(LongTermMemory memory);

    @Select("SELECT * FROM long_term_memory WHERE user_id = #{userId} ORDER BY importance_score DESC LIMIT 10")
    List<LongTermMemory> selectTopMemoriesByUser(@Param("userId") String userId);
}
