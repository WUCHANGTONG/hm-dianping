package com.hmdp.seckill.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀下单
     * @param voucherId 优惠券ID
     * @return 订单ID
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 创建订单（由Kafka消费者调用）
     * @param voucherId 优惠券ID
     * @param userId 用户ID
     * @param orderId 订单ID
     */
    void createVoucherOrder(Long voucherId, Long userId, Long orderId);
}
