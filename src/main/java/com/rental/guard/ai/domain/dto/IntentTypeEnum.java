/**
 * @author qkcao
 * @date 2026/1/22 18:03
 */
package com.rental.guard.ai.domain.dto;


import lombok.Getter;

// 意图类型枚举
@Getter
public enum IntentTypeEnum {
    SINGLE_ANALYSIS("单次分析", "分析单条聊天记录或文本"),
    USER_INVESTIGATION("用户调查", "调查特定用户的风险历史"),
    REAL_TIME_MONITORING("实时监控", "监控聊天室或用户的实时行为"),
    REPORT_GENERATION("报告生成", "生成风险报告"),
    RULE_CONFIGURATION("规则配置", "配置检测规则"),
    BATCH_PROCESSING("批量处理", "批量分析数据"),
    DATA_QUERY("数据查询", "查询风险数据"),
    ALERT_MANAGEMENT("告警管理", "管理告警通知"),
    SYSTEM_CONFIG("系统配置", "系统参数配置"),
    UNKNOWN("未知意图", "无法识别的意图");

    private String displayName;
    private String description;

    IntentTypeEnum(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
