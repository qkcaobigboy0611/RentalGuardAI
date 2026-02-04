/**
 * @author qkcao
 * @date 2026/1/22 17:28
 */
package com.rental.guard.ai.domain.service;

import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;

import java.io.IOException;
import java.util.Map;

public interface LLMService {
    /**
     * 调用LLM生成响应
     */
    String generate(String prompt);

    /**
     * 带参数的生成
     */
    String generate(String prompt, Map<String, Object> parameters);

    /**
     * 解析图片
     */
    String simpleMultiModalConversationCall(String image) throws NoApiKeyException, UploadFileException, IOException;
}
