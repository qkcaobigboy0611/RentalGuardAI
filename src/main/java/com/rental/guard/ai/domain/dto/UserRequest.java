/**
 * @author qkcao
 * @date 2026/1/22 17:32
 */
package com.rental.guard.ai.domain.dto;

import lombok.Data;

import java.util.Map;

// 数据结构
@Data
public class UserRequest {
    private String input;
    private Map<String, Object> context;
    private String userId;
}
