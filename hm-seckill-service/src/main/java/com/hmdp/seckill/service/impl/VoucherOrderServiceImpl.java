package com.hmdp.seckill.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.api.UserApi;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.seckill.config.KafkaConfig;
import com.hmdp.seckill.dto.SeckillOrderMessage;
import com.hmdp.seckill.mapper.VoucherOrderMapper;
import com.hmdp.seckill.service.ISeckillVoucherService;
import com.hmdp.seckill.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.support.SendResult;

/**
 * 秒杀订单服务实现类
 * 使用Kafka替代Redis Stream实现异步订单处理
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private UserApi userApi;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 秒杀脚本：库存扣减 + 用户标记
     * 注意：不再发送消息到Stream，由Java代码发送消息到Kafka
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    /**
     * 补偿脚本：回滚库存 + 移除用户标记
     * 用于Kafka发送失败时的反向补偿
     */
    private static final DefaultRedisScript<Long> RECOVER_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill-kafka.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

        RECOVER_SCRIPT = new DefaultRedisScript<>();
        RECOVER_SCRIPT.setLocation(new ClassPathResource("seckill-recover.lua"));
        RECOVER_SCRIPT.setResultType(Long.class);
    }

    /**
     * 秒杀下单入口
     * 1. 执行Lua脚本：扣减库存、标记用户
     * 2. 同步发送延迟检查消息（100ms超时），失败立即回滚
     * 3. 异步发送订单消息到Kafka
     *
     * 企业级改造：100ms超时同步发送 + 优雅降级
     * 防止Kafka网络抖动导致Tomcat线程池雪崩
     */
    @Override
    @SentinelResource(
            value = "seckillVoucher",
            blockHandler = "seckillVoucherBlockHandler"
    )
    public Result seckillVoucher(Long voucherId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long userId = user.getId();
        long orderId = redisIdWorker.nextId("order");

        // 1. 执行Lua脚本（扣库存 + 标记用户）
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );

        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 2. 有资格下单，发送消息到Kafka
        try {
            SeckillOrderMessage message = SeckillOrderMessage.create(orderId, userId, voucherId);
            String jsonMessage = objectMapper.writeValueAsString(message);

            // 【企业级关键改造】同步发送延迟检查消息，严控超时时间为100毫秒！防雪崩！
            // 延迟消息用于5分钟后检查订单是否创建成功，失败则补偿回滚
            SendResult<String, String> delayResult = kafkaTemplate
                    .send(KafkaConfig.SECKILL_DELAY_CHECK_TOPIC, String.valueOf(userId), jsonMessage)
                    .get(100, TimeUnit.MILLISECONDS);

            if (delayResult == null || delayResult.getRecordMetadata() == null) {
                throw new RuntimeException("延迟消息发送失败，返回结果为空");
            }

            RecordMetadata metadata = delayResult.getRecordMetadata();
            log.info("延迟检查消息发送成功: orderId={}, partition={}, offset={}",
                    orderId, metadata.partition(), metadata.offset());

            // 异步发送订单消息（不阻塞，失败有延迟消息兜底）
            kafkaTemplate.send(KafkaConfig.SECKILL_ORDER_TOPIC,
                    String.valueOf(userId),
                    jsonMessage
            ).whenComplete((sendResult, ex) -> {
                if (ex == null) {
                    log.info("秒杀订单消息发送成功: orderId={}, partition={}, offset={}",
                            orderId, sendResult.getRecordMetadata().partition(),
                            sendResult.getRecordMetadata().offset());
                } else {
                    log.error("秒杀订单消息发送失败（不影响，有延迟消息兜底）: orderId={}", orderId, ex);
                }
            });

            return Result.ok(orderId);

        } catch (TimeoutException e) {
            // 捕获超时异常，立刻回滚并快速失败
            log.error("【降级】Kafka延迟消息发送超时(100ms)，快速失败并回滚Redis: orderId={}, voucherId={}",
                    orderId, voucherId);
            rollbackRedis(voucherId, userId);
            return Result.fail("当前抢购人数过多，请稍后重试");

        } catch (Exception e) {
            // 捕获其他异常，同样回滚
            log.error("【降级】Kafka发送失败，回滚Redis: orderId={}, voucherId={}", orderId, voucherId, e);
            rollbackRedis(voucherId, userId);
            return Result.fail("系统繁忙，请稍后重试");
        }
    }

    /**
     * Redis库存回滚（补偿机制）
     * 当Kafka发送失败或超时时，恢复Redis库存和用户状态
     */
    private void rollbackRedis(Long voucherId, Long userId) {
        try {
            Long result = stringRedisTemplate.execute(
                    RECOVER_SCRIPT,
                    Collections.emptyList(),
                    voucherId.toString(), userId.toString()
            );
            if (result != null && result == 0) {
                log.info("Redis回滚成功: voucherId={}, userId={}", voucherId, userId);
            } else {
                log.error("Redis回滚失败: voucherId={}, userId={}", voucherId, userId);
            }
        } catch (Exception e) {
            log.error("Redis回滚异常: voucherId={}, userId={}", voucherId, userId, e);
        }
    }

    /**
     * Sentinel限流处理方法
     * 当秒杀接口触发限流时返回友好提示
     */
    public Result seckillVoucherBlockHandler(Long voucherId, BlockException ex) {
        log.warn("秒杀接口被限流: voucherId={}, 原因={}", voucherId, ex.getMessage());
        return Result.fail("当前秒杀过于火爆，请稍后再试");
    }

    /**
     * 创建订单（由Kafka消费者调用）
     * 使用分布式锁防止超卖
     */
    @Override
    public void createVoucherOrder(Long voucherId, Long userId, Long orderId) {
        log.info("开始创建订单: orderId={}, userId={}, voucherId={}", orderId, userId, voucherId);

        // 创建锁对象（按用户粒度加锁）
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);

        try {
            // 尝试获取锁
            boolean isLock = redisLock.tryLock();
            if (!isLock) {
                log.error("获取锁失败，可能重复下单: userId={}", userId);
                return;
            }

            try {
                // 5.1.查询订单（再次检查是否已购买）
                Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
                // 5.2.判断是否存在
                if (count > 0) {
                    log.error("用户已购买过该优惠券: userId={}, voucherId={}", userId, voucherId);
                    return;
                }

                // 6.扣减库存（使用乐观锁：库存 > 0）
                boolean success = seckillVoucherService.update()
                        .setSql("stock = stock - 1")
                        .eq("voucher_id", voucherId)
                        .gt("stock", 0)
                        .update();

                if (!success) {
                    log.error("库存扣减失败，库存不足: voucherId={}", voucherId);
                    // 补偿操作：恢复Redis库存标记
                    return;
                }

                // 7.创建订单
                VoucherOrder voucherOrder = new VoucherOrder();
                voucherOrder.setId(orderId);
                voucherOrder.setUserId(userId);
                voucherOrder.setVoucherId(voucherId);
                save(voucherOrder);

                log.info("订单创建成功: orderId={}", orderId);

            } finally {
                // 释放锁
                redisLock.unlock();
            }
        } catch (Exception e) {
            log.error("创建订单异常: orderId={}", orderId, e);
            throw e;  // 抛出异常，让Kafka消费者进行重试或发送到死信队列
        }
    }
}
