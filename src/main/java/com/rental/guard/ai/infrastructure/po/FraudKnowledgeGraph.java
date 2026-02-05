/**
 * @author qkcao
 * @date 2026/2/5 15:42
 */
package com.rental.guard.ai.infrastructure.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

/**
 * 知识图谱实体（MySQL存储，所有字段平放）
 */
@Data
@Builder
@TableName(value = "fraud_knowledge_graph", autoResultMap = true)
public class FraudKnowledgeGraph {
    @Id
    private Long id;

    // 实体标识
    private String entityId;         // 唯一标识
    private String entityName;       // 实体名称
    private String entityType;       // AGENCY, LANDLORD, ADDRESS, PHONE, WECHAT, BANK_ACCOUNT

    // 风险信息
    private String riskLevel;        // LOW, MEDIUM, HIGH, CRITICAL
    private Integer reportCount;     // 举报次数
    private Integer confirmCount;    // 确认次数
    private Boolean isVerified;      // 是否已验证

    // 实体详情（平放字段）
    private String address;          // 地址（针对地址实体）
    private String phoneNumber;      // 电话号码（针对电话实体）
    private String wechatId;         // 微信ID
    private String agencyName;       // 中介公司名
    private String landlordName;     // 房东姓名
    private String bankAccount;      // 银行账户
    private String email;            // 邮箱

    // 关联信息（存储关联实体ID，逗号分隔）
    private String relatedEntityIds;  // 关联实体ID列表

    // 详情描述
    private String description;
    private String evidence;          // 证据描述
    private String source;            // 信息来源
    // 时间信息
    private LocalDateTime firstReportedAt;
    private LocalDateTime lastReportedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 统计信息
    private Integer searchCount;      // 搜索次数
    private Integer matchCount;       // 匹配次数
}
