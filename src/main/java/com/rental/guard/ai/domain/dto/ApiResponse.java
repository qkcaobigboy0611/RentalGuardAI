/**
 * @author qkcao
 * @date 2026/1/23 16:03
 */
package com.rental.guard.ai.domain.dto;

import java.util.function.Supplier;

public class ApiResponse<T> {
    protected String code;
    protected String message;
    protected T data;
    /**
     * 响应码信息，SCF不用序列化
     */
    private ResponseCode responseCode;

    public ApiResponse() {
    }

    public ApiResponse(ResponseCode responseCode) {
        this.responseCode = responseCode;
        this.code = responseCode.getCode();
        this.message = responseCode.getMessage();
    }

    public static <T> ApiResponse<T> buildSuccess() {
        ApiResponse<T> response = new ApiResponse(ApiResponseCode.SUCCESS);
        return response;
    }

    public static <T> ApiResponse<T> buildSuccess(ResponseCode responseCode) {
        ApiResponse<T> response = new ApiResponse(responseCode);
        return response;
    }

    public static <T> ApiResponse<T> buildSuccess(T data) {
        ApiResponse<T> response = new ApiResponse(ApiResponseCode.SUCCESS);
        response.data = data;
        return response;
    }

    /**
     * 带异常判断的返回,配合校验
     *
     * @param responseCode
     * @param e
     * @param <T>
     * @return
     */
    public static <T> ApiResponse<T> buildFailure(ResponseCode responseCode, Throwable e) {
        if (e instanceof Exception) {
            return ApiResponse.buildFailure(ApiResponseCode.PARAM_ERROR.setMessage(e.getMessage()));
        } else {
            ApiResponse<T> response = new ApiResponse(responseCode);
            return response;
        }
    }

    /**
     * 业务异常的统一转化
     *
     * @param e
     * @param <T>
     * @return
     */
    public static <T> ApiResponse<T> buildFailure(Exception e) {
        ApiResponse<T> response = new ApiResponse();
        response.code = e.getMessage();
        response.message = e.getMessage();
        return response;
    }

    public static <T> ApiResponse<T> buildFailure(ResponseCode responseCode) {
        ApiResponse<T> response = new ApiResponse(responseCode);
        return response;
    }

    /**
     * 支持函数式编程，用于UT测试
     *
     * @param supplier
     * @param <R>
     * @return
     */
    public static <R> R buildApiResonse(Supplier<R> supplier) {
        return supplier.get();
    }

    public static <T> ApiResponse<T> buildFailure(String errorCode, String errorMsg) {
        ApiResponse<T> response = new ApiResponse();
        response.code = errorCode;
        response.message = errorMsg;
        return response;
    }


    public String getCode() {
        return this.code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return this.message;
    }

    public ApiResponse setMessage(String message) {
        this.message = message;
        return this;
    }

    public T getData() {
        return this.data;
    }

    public void setData(T data) {
        this.data = data;
    }

}
