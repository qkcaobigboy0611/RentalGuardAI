/**
 * @author qkcao
 * @date 2025/9/16 18:16
 */
package com.rental.guard.ai.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 欺诈训练案例表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("fraud_training_case")
public class PoFraudTrainingCase {

    @TableId(type = IdType.AUTO)
    private Integer id;

    // 聊天内容
    private String chatContent;

    // 是否欺诈 0/1
    private Integer isFraud;

    // 欺诈类型
    private String fraudType;

    // 数据来源
    private String source;

    // 置信度
    private BigDecimal confidenceScore;

    // 描述
    private String description;

    private Date createTime;

    private Date updateTime;
}

