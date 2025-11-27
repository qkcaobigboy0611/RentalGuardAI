/**
 * @author qkcao
 * @date 2025/9/17 10:09
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
@TableName(value = "channel", autoResultMap = true)
public class PoChannel {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String creatorId;

    private String peerId;

    private Integer type;

    private String meta;

    private Integer offset;

    private Long latest;

    private Date createTime;

    private Date updateTime;
}

