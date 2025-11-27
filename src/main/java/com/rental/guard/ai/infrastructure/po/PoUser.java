/**
 * @author qkcao
 * @date 2025/9/17 10:05
 */
package com.rental.guard.ai.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@ToString
@TableName(value = "user", autoResultMap = true)
@Accessors(chain = true)
public class PoUser {
    @TableId(type = IdType.AUTO)
    private String id;

    private String userId;

    private String nickName;

    private String phone;

    private String ip;
}
