/**
 * @author qkcao
 * @date 2026/1/23 16:05
 */
package com.rental.guard.ai.domain.dto;

public enum ApiResponseCode implements ResponseCode<ApiResponseCode> {
    SUCCESS("0", "成功"),
    FAILURE("9000", "业务处理失败"),
    DUPLICATE_ERROR("9994", "重复处理异常"),
    DATA_EMPTY("9995", "数据为空"),
    DB_ERROR("9996", "数据库错误"),
    PARAM_NULL("9997", "参数为空"),
    PARAM_ERROR("9998", "参数错误"),
    SYSTEM_ERROR("9999", "系统异常");
    private String code;
    private String message;

    ApiResponseCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return this.code;
    }

    public ApiResponseCode setCode(String code) {
        this.code = code;
        return this;
    }

    public String getMessage() {
        return this.message;
    }

    public ApiResponseCode setMessage(String message) {
        this.message = message;
        return this;
    }

}

