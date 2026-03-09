package com.hmdp.seckill.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.seckill.config.KafkaConfig;
import com.hmdp.seckill.dto.SeckillOrderMessage;
import com.hmdp.seckill.service.IVoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka秒杀订单消费者
 * 异步处理秒杀订单创建
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderKafkaListener {

    private final IVoucherOrderService voucherOrderService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 监听秒杀订单Topic
     * 使用手动ACK模式确保消息可靠性
     */
    @KafkaListener(
            topics = KafkaConfig.SECKILL_ORDER_TOPIC,
            groupId = "seckill-order-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleSeckillOrder(
            @Payload ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {

        String value = record.value();
        log.info("收到秒杀订单消息: partition={}, offset={}, value={}",
                record.partition(), record.offset(), value);

        try {
            SeckillOrderMessage message = objectMapper.readValue(value, SeckillOrderMessage.class);

            // 创建订单
            voucherOrderService.createVoucherOrder(
                    message.getVoucherId(),
                    message.getUserId(),
                    message.getOrderId()
            );

            log.info("秒杀订单处理成功: orderId={}", message.getOrderId());

            // 手动确认消息
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("处理秒杀订单失败: {}", value, e);

            try {
                SeckillOrderMessage message = objectMapper.readValue(value, SeckillOrderMessage.class);
                message.incrementRetry();

                // 如果重试次数小于最大值，发送到重试队列
                if (message.getRetryCount() < MAX_RETRY_COUNT) {
                    log.warn("订单处理失败，准备重试: orderId={}, retryCount={}",
                            message.getOrderId(), message.getRetryCount());

                    kafkaTemplate.send(KafkaConfig.SECKILL_ORDER_TOPIC,
                            objectMapper.writeValueAsString(message));
                } else {
                    // 超过最大重试次数，发送到死信队列
                    log.error("订单超过最大重试次数，发送到死信队列: orderId={}", message.getOrderId());
                    kafkaTemplate.send(KafkaConfig.SECKILL_ORDER_DLQ_TOPIC,
                            objectMapper.writeValueAsString(message));
                }

                // 确认消息（避免阻塞）
                acknowledgment.acknowledge();

            } catch (Exception ex) {
                log.error("处理失败消息时异常", ex);
                // 不确认消息，让Kafka重发
            }
        }
    }

    /**
     * 监听死信队列（用于人工处理或监控告警）
     */
    @KafkaListener(
            topics = KafkaConfig.SECKILL_ORDER_DLQ_TOPIC,
            groupId = "seckill-order-dlq-group"
    )
    public void handleDeadLetter(@Payload ConsumerRecord<String, String> record) {
        log.error("收到死信消息: {}", record.value());
        // 这里可以：
        // 1. 发送告警通知
        // 2. 记录到数据库
        // 3. 人工介入处理
    }
}
