package com.hmdp.seckill.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.seckill.config.KafkaConfig;
import com.hmdp.seckill.dto.SeckillOrderMessage;
import com.hmdp.seckill.mapper.VoucherOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 秒杀订单延迟检查消费者
 * 5分钟后检查订单是否创建成功，失败则回滚Redis
 * 解决Gemini提到的"伪最终一致性"问题
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillDelayCheckListener {

    private final StringRedisTemplate stringRedisTemplate;
    private final VoucherOrderMapper voucherOrderMapper;
    private final ObjectMapper objectMapper;

    /**
     * 补偿脚本：回滚库存和用户标记
     */
    private static final DefaultRedisScript<Long> RECOVER_SCRIPT;

    static {
        RECOVER_SCRIPT = new DefaultRedisScript<>();
        RECOVER_SCRIPT.setLocation(new ClassPathResource("seckill-recover.lua"));
        RECOVER_SCRIPT.setResultType(Long.class);
    }

    /**
     * 延迟检查（5分钟后执行）
     * 检查订单是否创建成功，失败则补偿Redis
     */
    @KafkaListener(
            topics = KafkaConfig.SECKILL_DELAY_CHECK_TOPIC,
            groupId = "seckill-delay-check-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDelayCheck(
            @Payload ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {

        String value = record.value();
        log.info("【补偿检查】收到延迟检查消息: offset={}, value={}", record.offset(), value);

        try {
            SeckillOrderMessage message = objectMapper.readValue(value, SeckillOrderMessage.class);
            Long orderId = message.getOrderId();
            Long userId = message.getUserId();
            Long voucherId = message.getVoucherId();

            // 1. 检查订单是否在数据库中存在
            Long count = voucherOrderMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>()
                            .eq(com.hmdp.entity.VoucherOrder::getId, orderId)
            );

            if (count > 0) {
                // 订单存在，正常完成
                log.info("【补偿检查】订单已正常创建: orderId={}", orderId);
            } else {
                // 2. 订单不存在，需要回滚Redis
                log.error("【补偿检查】订单未创建，开始回滚Redis: orderId={}, userId={}, voucherId={}",
                        orderId, userId, voucherId);

                // 执行补偿脚本：恢复库存+移除用户标记
                Long result = stringRedisTemplate.execute(
                        RECOVER_SCRIPT,
                        Collections.emptyList(),
                        voucherId.toString(), userId.toString()
                );

                if (result != null && result == 0) {
                    log.info("【补偿检查】Redis回滚成功: orderId={}", orderId);
                    // TODO: 可以发送短信/通知告知用户抢购失败
                } else {
                    log.error("【补偿检查】Redis回滚失败: orderId={}", orderId);
                }
            }

            // 确认消息
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("【补偿检查】处理延迟检查消息失败: {}", value, e);
            // 不确认消息，让Kafka重试
        }
    }
}
