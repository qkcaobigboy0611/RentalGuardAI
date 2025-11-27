/**
 * @author qkcao
 * @date 2025/9/16 18:28
 */
package com.rental.guard.ai.domain.dto;

import lombok.Data;

/**
 * 添加训练案例请求DTO
 */
@Data
public class AddTrainingCaseRequest {

    private String chatContent;

    /**
     * 用户手机号1
     */
    private String userPhone1;

    /**
     * 用户手机号2
     */
    private String userPhone2;

    /**
     * 是否欺诈
     */
    private Boolean isFraud;

    private String fraudType;

    private String description;
}
