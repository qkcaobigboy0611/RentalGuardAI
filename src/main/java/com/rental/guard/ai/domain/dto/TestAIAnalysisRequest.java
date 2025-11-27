/**
 * @author qkcao
 * @date 2025/9/16 18:39
 */
package com.rental.guard.ai.domain.dto;

import lombok.Data;

/**
 * 测试AI分析请求DTO
 */
@Data
public class TestAIAnalysisRequest {

    /**
     * 用户ID1
     */
    private String userId1;

    /**
     * 用户ID2
     */
    private String userId2;

    /**
     * 用户1手机号（可选，用于快速查找）
     */
    private String phone1;

    /**
     * 用户2手机号（可选，用于快速查找）
     */
    private String phone2;

    /**
     * 聊天记录条数（默认20条）
     */
    private Integer messageCount;

    /**
     * 直接提供的聊天内容（如果不提供用户信息）
     */
    private String chatContent;
}
