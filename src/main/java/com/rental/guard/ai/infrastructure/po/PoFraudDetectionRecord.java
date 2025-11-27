/**
 * @author qkcao
 * @date 2025/9/16 18:14
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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("fraud_detection_record")
public class PoFraudDetectionRecord {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String userId;

    private Long channelId;

    private String triggerSensitiveWord;

    private String triggerMessage;

    private String chatContext;

    private String aiAnalysisResult;

    private BigDecimal riskScore;

    private Integer isFraud;

    private String fraudType;

    private String actionTaken;

    private Integer aiCostTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

