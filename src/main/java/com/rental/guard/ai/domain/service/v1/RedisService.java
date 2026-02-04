/**
 * @author qkcao
 * @date 2026/1/30 10:31
 */
package com.rental.guard.ai.domain.service.v1;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 设置 key-value，带过期时间（秒）
    public void set(String key, String value, long expireSeconds) {
        redisTemplate.opsForValue().set(key, value, expireSeconds, TimeUnit.SECONDS);
    }

    // 设置永久 key-value
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    // 获取值
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // 删除 key
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    // 检查 key 是否存在
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }
}
