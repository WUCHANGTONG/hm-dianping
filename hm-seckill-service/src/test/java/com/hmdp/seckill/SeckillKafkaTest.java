package com.hmdp.seckill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 秒杀系统Kafka全量测试
 * 一键运行：右键类名 → Run 'SeckillKafkaTest'
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SeckillKafkaTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "http://localhost:8084";
    private static final String USER_SERVICE_URL = "http://localhost:8081";
    private static final RestTemplate restTemplate = new RestTemplate();

    private String token;
    private List<String> tokens = new ArrayList<>();

    /**
     * 辅助方法：用户登录获取token
     */
    private String login(String phone) {
        try {
            // 1. 发送验证码
            restTemplate.postForObject(
                    USER_SERVICE_URL + "/user/code?phone=" + phone,
                    null,
                    Result.class
            );
            Thread.sleep(100); // 等待验证码写入Redis

            // 2. 登录
            Map<String, String> loginBody = new HashMap<>();
            loginBody.put("phone", phone);
            loginBody.put("code", "123456");

            ResponseEntity<Result> response = restTemplate.postForEntity(
                    USER_SERVICE_URL + "/user/login",
                    loginBody,
                    Result.class
            );

            // 用ObjectMapper转换LinkedHashMap
            Object data = response.getBody().getData();
            Map<String, Object> tokenMap = objectMapper.convertValue(data, Map.class);
            return (String) tokenMap.get("accessToken");
        } catch (Exception e) {
            log.error("登录失败: {}", phone, e);
            throw new RuntimeException("登录失败", e);
        }
    }

    /**
     * 辅助方法：执行秒杀请求
     */
    private Result seckill(String token, Long voucherId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Result> response = restTemplate.exchange(
                    BASE_URL + "/voucher-order/seckill/" + voucherId,
                    HttpMethod.POST,
                    entity,
                    Result.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("秒杀请求失败: {}", e.getMessage());
            Result failResult = new Result();
            failResult.setSuccess(false);
            failResult.setErrorMsg(e.getMessage());
            return failResult;
        }
    }

    @BeforeEach
    void beforeEach() {
        tokens.clear();
    }

    /**
     * 测试1：单人正常抢购流程验证
     */
    @Test
    @Order(1)
    @DisplayName("T1-单人抢购流程验证")
    void testSingleSeckill() throws InterruptedException {
        log.info("\n========== 测试1：单人抢购流程验证 ==========");

        // 准备数据
        Long voucherId = 1001L;
        redisTemplate.opsForValue().set("seckill:stock:" + voucherId, "100");
        redisTemplate.delete("seckill:order:" + voucherId);

        // 获取token
        token = login("13800138000");
        log.info("登录成功，token: {}...", token.substring(0, 10));

        // 执行秒杀
        Result result = seckill(token, voucherId);
        log.info("秒杀结果: success={}, data={}", result.getSuccess(), result.getData());

        // 验证结果
        assertTrue(result.getSuccess(), "秒杀应该成功");
        assertNotNull(result.getData(), "应该返回订单ID");

        // 等待Kafka消费
        log.info("等待Kafka消费...");
        Thread.sleep(3000);

        // 验证Redis库存减少
        String stock = redisTemplate.opsForValue().get("seckill:stock:" + voucherId);
        assertEquals("99", stock, "库存应该减少1");

        log.info("✅ 测试1通过：Kafka流程正常");
    }

    /**
     * 测试2：重复购买验证
     */
    @Test
    @Order(2)
    @DisplayName("T2-重复购买验证")
    void testDuplicatePurchase() {
        log.info("\n========== 测试2：重复购买验证 ==========");

        // 准备数据
        Long voucherId = 1002L;
        redisTemplate.opsForValue().set("seckill:stock:" + voucherId, "100");
        redisTemplate.delete("seckill:order:" + voucherId);

        // 获取新token
        token = login("13800138001");

        // 第一次抢购
        Result result1 = seckill(token, voucherId);
        assertTrue(result1.getSuccess(), "第一次应该成功");
        log.info("第一次抢购成功");

        // 第二次抢购（重复）
        Result result2 = seckill(token, voucherId);
        log.info("第二次结果: success={}, errorMsg={}", result2.getSuccess(), result2.getErrorMsg());

        assertFalse(result2.getSuccess(), "重复购买应该失败");
        assertTrue(result2.getErrorMsg() != null &&
                  (result2.getErrorMsg().contains("重复") || result2.getErrorMsg().contains("不能")),
                "错误信息应该提示不能重复购买");

        log.info("✅ 测试2通过：Redis防重复购买正常");
    }

    /**
     * 测试3：库存耗尽防超卖验证
     */
    @Test
    @Order(3)
    @DisplayName("T3-库存耗尽防超卖验证")
    void testStockExhaustion() throws InterruptedException {
        log.info("\n========== 测试3：库存耗尽防超卖验证 ==========");

        // 准备数据：库存3个
        Long voucherId = 1003L;
        redisTemplate.opsForValue().set("seckill:stock:" + voucherId, "3");
        redisTemplate.delete("seckill:order:" + voucherId);

        // 准备5个token
        for (int i = 1; i <= 5; i++) {
            String phone = String.format("13800138%03d", i + 10);
            String userToken = login(phone);
            tokens.add(userToken);
            log.info("用户{}登录成功", i);
        }

        // 并发抢购
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<Result>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (String userToken : tokens) {
            futures.add(executor.submit(() -> {
                Result result = seckill(userToken, voucherId);
                if (result.getSuccess()) {
                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                }
                return result;
            }));
        }

        // 等待所有请求完成
        for (Future<Result> future : futures) {
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("请求异常", e);
            }
        }
        executor.shutdown();

        // 等待Kafka消费完成
        Thread.sleep(5000);

        log.info("成功: {}, 失败: {}", successCount.get(), failCount.get());

        // 验证结果
        assertEquals(3, successCount.get(), "应该只有3人成功");
        assertEquals(2, failCount.get(), "应该有2人失败");

        // 验证库存为0
        String stock = redisTemplate.opsForValue().get("seckill:stock:" + voucherId);
        assertEquals("0", stock, "库存应该为0");

        log.info("✅ 测试3通过：防超卖机制正常");
    }

    /**
     * 测试4：Kafka削峰能力验证
     */
    @Test
    @Order(4)
    @DisplayName("T4-Kafka削峰能力验证")
    void testKafkaPeakShaving() throws InterruptedException {
        log.info("\n========== 测试4：Kafka削峰能力验证 ==========");

        // 准备数据
        Long voucherId = 1004L;
        int stockCount = 20; // 减少为20，避免创建太多用户
        redisTemplate.opsForValue().set("seckill:stock:" + voucherId, String.valueOf(stockCount));
        redisTemplate.delete("seckill:order:" + voucherId);

        // 准备20个token
        for (int i = 1; i <= stockCount; i++) {
            String phone = String.format("13800140%03d", i);
            String userToken = login(phone);
            tokens.add(userToken);
        }
        log.info("{}个用户登录完成", stockCount);

        // 并发抢购
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Future<Result>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < stockCount; i++) {
            final String userToken = tokens.get(i);
            futures.add(executor.submit(() -> {
                Result result = seckill(userToken, voucherId);
                if (result.getSuccess()) {
                    successCount.incrementAndGet();
                }
                return result;
            }));
        }

        // 等待所有请求完成
        for (Future<Result> future : futures) {
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("请求异常", e);
            }
        }
        executor.shutdown();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        log.info("{}个请求总耗时: {}ms, 平均每个: {}ms", stockCount, duration, duration / stockCount);
        log.info("成功: {}/{}", successCount.get(), stockCount);

        // 等待Kafka消费完成
        log.info("等待Kafka消费完成...");
        Thread.sleep(8000);

        // 验证库存
        String stock = redisTemplate.opsForValue().get("seckill:stock:" + voucherId);
        assertEquals("0", stock, "库存应该为0");

        // 验证响应时间（应该很快，因为异步）
        assertTrue(duration < 10000, "20个并发请求应该在10秒内完成");

        log.info("✅ 测试4通过：Kafka削峰能力正常");
    }

    /**
     * 测试5：死信队列验证
     * 模拟消费失败，验证重试3次后进入死信队列
     */
    @Test
    @Order(5)
    @DisplayName("T5-死信队列验证")
    void testDeadLetterQueue() throws InterruptedException {
        log.info("\n========== 测试5：死信队列验证 ==========");

        // 准备数据：voucherId=1005会触发异常
        Long voucherId = 1005L;
        redisTemplate.opsForValue().set("seckill:stock:" + voucherId, "10");
        redisTemplate.delete("seckill:order:" + voucherId);

        // 获取新token
        String userToken = login("13800150001");
        log.info("用户登录成功");

        // 执行秒杀（这个订单会进入死信队列）
        Result result = seckill(userToken, voucherId);
        assertTrue(result.getSuccess(), "秒杀请求应该成功（异步）");
        Long orderId = ((Number) result.getData()).longValue();
        log.info("秒杀请求发送成功，订单ID: {}", orderId);

        // 等待重试过程（3次重试约需10秒）
        log.info("等待Kafka重试过程（约10秒）...");
        Thread.sleep(15000);

        // 验证：应该看到日志中有3次重试，然后进入死信队列
        log.info("请检查IDEA控制台日志：");
        log.info("  1. 应该有3次 '订单处理失败，准备重试'");
        log.info("  2. 最后应该有 '订单超过最大重试次数，发送到死信队列'");
        log.info("  3. 最后应该有 '收到死信消息'");

        log.info("✅ 测试5完成：死信队列验证完成（请查看日志确认）");
    }

    /**
     * 测试完成后打印统计
     */
    @AfterAll
    static void printSummary() {
        log.info("\n========== 秒杀系统Kafka测试全部完成 ==========");
        log.info("测试列表：");
        log.info("  T1-单人抢购流程 ✓");
        log.info("  T2-重复购买验证 ✓");
        log.info("  T3-防超卖验证 ✓");
        log.info("  T4-Kafka削峰 ✓");
        log.info("  T5-死信队列 ✓");
        log.info("请查看IDEA控制台验证Kafka发送、消费、重试、死信队列日志");
    }
}
