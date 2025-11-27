/**
 * @author qkcao
 * @date 2025/9/17 10:09
 */
package com.rental.guard.ai.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rental.guard.ai.infrastructure.po.PoChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChannelMapper extends BaseMapper<PoChannel> {
    @Select("select id from channel where (creator_id = #{creatorId} and peer_id = #{peerId}) or (peer_id = #{creatorId} and creator_id = #{peerId})")
    Long selectChannelId(String creatorId, String peerId);
}

