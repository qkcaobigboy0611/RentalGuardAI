/**
 * @author qkcao
 * @date 2025/9/16 14:23
 */
package com.rental.guard.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonConfig {
    @Bean
    public ChatMemory chatMemory() {
        // 最近10个对话/或者按照最近token的数量
        return MessageWindowChatMemory.builder().maxMessages(10).build();
    }

    @Bean
    public ChatClient chatClient(OllamaChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                .defaultSystem("你是一个专业的反欺诈分析专家，专门识别租房场景中的诈骗和杀猪盘行为。")
                .defaultAdvisors(
                        new SimpleLoggerAdvisor()).build();
    }
}
