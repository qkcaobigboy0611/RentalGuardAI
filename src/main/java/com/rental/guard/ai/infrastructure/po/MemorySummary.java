/**
 * @author qkcao
 * @date 2026/2/5 15:44
 */
package com.rental.guard.ai.infrastructure.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

/**
 * 记忆摘要（用于快速检索）
 */
@Data
@Builder
@TableName(value = "memory_summary", autoResultMap = true)
public class MemorySummary {
    @Id
    private Long id;

    private String userId;
    private String summaryKey;       // 摘要键：preference_summary, risk_summary, etc.
    private String summaryContent;   // 摘要内容（JSON格式）

    private LocalDateTime lastUpdatedAt;
    private LocalDateTime nextUpdateAt;
}
