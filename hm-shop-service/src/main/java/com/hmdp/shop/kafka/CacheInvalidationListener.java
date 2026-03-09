package com.hmdp.shop.kafka;

import com.hmdp.utils.MultiLevelCacheClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 缓存失效监听器
 * 监听Kafka广播消息，清除本地Caffeine缓存
 * 解决Gemini提到的"薛定谔的更新"问题
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationListener {

    private final MultiLevelCacheClient cacheClient;

    /**
     * 监听缓存失效广播
     * 当店铺信息在MySQL更新后，Canal会发送消息到Kafka，所有节点收到后清除本地缓存
     */
    @KafkaListener(
            topics = "cache-invalidation",
            groupId = "cache-invalidation-group"
    )
    public void handleCacheInvalidation(
            @Payload ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {

        String key = record.value();
        log.info("【缓存失效广播】收到消息，清除本地缓存: key={}", key);

        try {
            // 清除Caffeine本地缓存
            cacheClient.invalidateLocalCache(key);
            log.info("【缓存失效广播】本地缓存已清除: {}", key);

            // 确认消息
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("【缓存失效广播】处理失败: {}", key, e);
            // 不确认，让Kafka重试
        }
    }
}
