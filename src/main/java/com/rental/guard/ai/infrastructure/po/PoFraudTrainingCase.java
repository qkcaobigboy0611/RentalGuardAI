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
import java.time.LocalDateTime;
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

    private String chatContent;

    private Integer isFraud;

    private String fraudType;

    private String source;

    private BigDecimal confidenceScore;

    private String description;

    private Float vector;

    private Date createTime;

    private Date updateTime;
}

