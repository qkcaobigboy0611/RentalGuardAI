/**
 * @author qkcao
 * @date 2025/9/17 10:04
 */
package com.rental.guard.ai.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rental.guard.ai.infrastructure.po.PoUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper extends BaseMapper<PoUser> {
    PoUser selectByPhone(@Param("phone") String phone);

    PoUser selectByUserId(@Param("userId") String userId);

}
