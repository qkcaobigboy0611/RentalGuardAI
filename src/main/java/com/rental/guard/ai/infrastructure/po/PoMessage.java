/**
 * @author qkcao
 * @date 2025/9/17 10:17
 */
package com.rental.guard.ai.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@Accessors(chain = true)
@TableName(value = "message", autoResultMap = true)
public class PoMessage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long channelId;

    private String creatorId;

    private Integer type;

    private String payload;

    private Integer offset;

    private Date createTime;
}

