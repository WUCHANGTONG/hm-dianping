package com.hmdp.seckill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 秒杀订单消息DTO
 * 用于Kafka消息传递
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage {

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 优惠券ID
     */
    private Long voucherId;

    /**
     * 消息创建时间
     */
    private Long createTime;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 创建消息（初始化重试次数为0）
     */
    public static SeckillOrderMessage create(Long orderId, Long userId, Long voucherId) {
        return SeckillOrderMessage.builder()
                .orderId(orderId)
                .userId(userId)
                .voucherId(voucherId)
                .createTime(System.currentTimeMillis())
                .retryCount(0)
                .build();
    }

    /**
     * 增加重试次数
     */
    public void incrementRetry() {
        this.retryCount++;
    }
}
