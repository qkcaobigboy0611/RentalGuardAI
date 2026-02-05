/**
 * @author qkcao
 * @date 2026/2/5 15:41
 */
package com.rental.guard.ai.infrastructure.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

/**
 * 用户画像（MySQL存储，所有字段平放）
 */
@Data
@Builder
@TableName(value = "user_profile", autoResultMap = true)
public class UserProfile {
    @Id
    private Long id;

    private String userId;

    // 基础信息
    private String userName;
    private Integer userAge;
    private String userCity;

    // 租房偏好（平放字段）
    private Integer budgetMin;
    private Integer budgetMax;
    private String preferredLocation;
    private String houseType;          // apartment, villa, shared
    private Integer minArea;
    private Integer maxArea;
    private Integer floorPreference;   // 1-低层, 2-中层, 3-高层
    private String decorationLevel;    // 精装, 简装, 毛坯
    private String orientation;        // 朝向

    // 风险偏好
    private Double riskTolerance;      // 0-1
    private String riskType;           // conservative, moderate, aggressive
    private Boolean verifyEverything;
    private Boolean preferDetailedContract;

    // 交互偏好
    private String responseStyle;      // detailed, concise, example_rich
    private Boolean preferLegalRef;
    private Boolean preferMarketData;

    // 黑名单/信任列表（JSON字符串存储）
    private String blacklistedAgencies;   // JSON数组
    private String blacklistedAddresses;  // JSON数组
    private String blacklistedPhones;     // JSON数组
    private String trustedAgencies;       // JSON数组

    // 历史统计
    private Integer totalSessions;
    private Integer riskCasesReported;
    private String commonScenarios;       // 常见咨询场景
    private String lastTopics;            // 最近咨询话题

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActiveAt;
}
