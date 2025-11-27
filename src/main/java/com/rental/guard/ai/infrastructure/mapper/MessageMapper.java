/**
 * @author qkcao
 * @date 2025/9/17 10:17
 */
package com.rental.guard.ai.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rental.guard.ai.infrastructure.po.PoMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<PoMessage> {
    List<PoMessage> selectByChannelAndOffset(@Param("from") Long from, @Param("to")Long to, @Param("channelId")Long channelId);
}
