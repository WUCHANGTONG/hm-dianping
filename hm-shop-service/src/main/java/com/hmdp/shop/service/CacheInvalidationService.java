package com.hmdp.shop.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 缓存失效广播服务
 * 当店铺数据变更时，广播消息让所有节点清除本地缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheInvalidationService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public static final String CACHE_INVALIDATION_TOPIC = "cache-invalidation";

    /**
     * 广播缓存失效消息
     * @param key 缓存key（如 cache:shop:1）
     */
    public void broadcastInvalidation(String key) {
        try {
            kafkaTemplate.send(CACHE_INVALIDATION_TOPIC, key);
            log.info("【缓存失效广播】已发送: {}", key);
        } catch (Exception e) {
            log.error("【缓存失效广播】发送失败: {}", key, e);
        }
    }

    /**
     * 店铺更新后调用此方法
     * @param shopId 店铺ID
     */
    public void invalidateShopCache(Long shopId) {
        String key = "cache:shop:" + shopId;

        // 1. 先删除本地Redis（当前节点）
        // redisTemplate.delete(key);  // 在调用方处理

        // 2. 广播让其他节点清除本地缓存
        broadcastInvalidation(key);
    }
}
