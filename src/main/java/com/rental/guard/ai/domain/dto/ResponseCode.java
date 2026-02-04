/**
 * @author qkcao
 * @date 2026/1/23 16:04
 */
package com.rental.guard.ai.domain.dto;

public interface ResponseCode<E extends Enum> {
    /**
     * 响应编码
     *
     * @return
     */
    String getCode();

    /**
     * 响应信息
     *
     * @return
     */
    String getMessage();
}
