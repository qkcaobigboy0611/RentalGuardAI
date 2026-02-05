/**
 * @author qkcao
 * @date 2026/2/5 16:44
 */
package com.rental.guard.ai.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rental.guard.ai.infrastructure.po.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
    /**
     * 根据用户ID查询用户画像
     */
    @Select("SELECT * FROM user_profile WHERE user_id = #{userId} AND deleted = 0")
    UserProfile findByUserId(@Param("userId") String userId);

    /**
     * 查询相似用户画像（根据位置和预算范围）
     */
    @Select({
            "<script>",
            "SELECT * FROM user_profile WHERE deleted = 0",
            "<if test='location != null and location != \"\"'>",
            "   AND preferred_location LIKE CONCAT('%', #{location}, '%')",
            "</if>",
            "<if test='minBudget != null and maxBudget != null'>",
            "   AND (",
            "       (budget_min IS NOT NULL AND budget_max IS NOT NULL",
            "        AND budget_min <= #{maxBudget} AND budget_max >= #{minBudget})",
            "       OR (budget_min IS NULL AND budget_max IS NOT NULL",
            "        AND budget_max >= #{minBudget})",
            "       OR (budget_min IS NOT NULL AND budget_max IS NULL",
            "        AND budget_min <= #{maxBudget})",
            "   )",
            "</if>",
            "<if test='minBudget != null and maxBudget == null'>",
            "   AND (budget_min IS NULL OR budget_min >= #{minBudget})",
            "</if>",
            "<if test='minBudget == null and maxBudget != null'>",
            "   AND (budget_max IS NULL OR budget_max <= #{maxBudget})",
            "</if>",
            "ORDER BY updated_at DESC",
            "<if test='limit != null'>",
            "   LIMIT #{limit}",
            "</if>",
            "</script>"
    })
    List<UserProfile> findSimilarProfiles(@Param("location") String location,
                                          @Param("minBudget") Integer minBudget,
                                          @Param("maxBudget") Integer maxBudget,
                                          @Param("limit") Integer limit);
}
