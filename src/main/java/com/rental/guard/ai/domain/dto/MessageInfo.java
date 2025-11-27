/**
 * @author qkcao
 * @date 2025/9/17 10:19
 */
package com.rental.guard.ai.domain.dto;

import com.rental.guard.ai.infrastructure.po.PoMessage;
import lombok.Data;

import java.util.Date;

@Data
public class MessageInfo {
    private long id;

    private Integer type;

    private String payload;

    private long channelId;

    private String creatorId;

    private int offset;

    private Date createTime;

    public static MessageInfo fromPO(PoMessage message) {
        MessageInfo messageInfo = new MessageInfo();
        messageInfo.setId(message.getId());
        messageInfo.setType(message.getType());
        messageInfo.setPayload(message.getPayload());
        messageInfo.setChannelId(message.getChannelId());
        messageInfo.setCreatorId(message.getCreatorId());
        messageInfo.setOffset(message.getOffset());
        messageInfo.setCreateTime(message.getCreateTime());

        return messageInfo;
    }
}
