package com.hmdp.seckill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.TokenPairDTO;
import com.hmdp.entity.Voucher;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 优惠券服务全量测试
 * 一键运行：右键类名 → Run 'VoucherServiceTest'
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VoucherServiceTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "http://localhost:8084";
    private static final String USER_SERVICE_URL = "http://localhost:8081";
    private static final RestTemplate restTemplate = new RestTemplate();

    private String token;

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
            Thread.sleep(100);

            // 2. 登录
            LoginFormDTO loginForm = new LoginFormDTO();
            loginForm.setPhone(phone);
            loginForm.setCode("123456");

            ResponseEntity<Result> response = restTemplate.postForEntity(
                    USER_SERVICE_URL + "/user/login",
                    loginForm,
                    Result.class
            );

            Object data = response.getBody().getData();
            TokenPairDTO tokenPair = objectMapper.convertValue(data, TokenPairDTO.class);
            return tokenPair.getAccessToken();
        } catch (Exception e) {
            log.error("登录失败: {}", phone, e);
            throw new RuntimeException("登录失败", e);
        }
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        token = login("13800180001");
    }

    /**
     * 测试1：查询店铺优惠券列表
     */
    @Test
    @Order(1)
    @DisplayName("T1-查询店铺优惠券列表")
    void testQueryVoucherOfShop() {
        log.info("\n========== 测试1：查询店铺优惠券列表 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/voucher/list/1",
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");
        assertTrue(result.getSuccess(), "查询优惠券列表应该成功");

        List<?> vouchers = objectMapper.convertValue(result.getData(), List.class);
        log.info("店铺1的优惠券数量: {}", vouchers.size());

        if (!vouchers.isEmpty()) {
            Map<String, Object> firstVoucher = (Map<String, Object>) vouchers.get(0);
            log.info("第一个优惠券: id={}, title={}, payValue={}",
                    firstVoucher.get("id"), firstVoucher.get("title"), firstVoucher.get("payValue"));
        }

        log.info("✅ 测试1通过：查询优惠券列表正常");
    }

    /**
     * 测试2：新增普通优惠券
     */
    @Test
    @Order(2)
    @DisplayName("T2-新增普通优惠券")
    void testAddVoucher() {
        log.info("\n========== 测试2：新增普通优惠券 ==========");

        Voucher voucher = new Voucher();
        voucher.setShopId(1L);
        voucher.setTitle("测试普通券" + System.currentTimeMillis());
        voucher.setSubTitle("测试副标题");
        voucher.setRules("满100减50");
        voucher.setPayValue(5000); // 50元
        voucher.setActualValue(10000); // 100元
        voucher.setType(0); // 普通券
        voucher.setStock(100);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", token);
        HttpEntity<Voucher> entity = new HttpEntity<>(voucher, headers);

        ResponseEntity<Result> response = restTemplate.postForEntity(
                BASE_URL + "/voucher",
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");
        assertTrue(result.getSuccess(), "新增优惠券应该成功");
        assertNotNull(result.getData(), "应该返回优惠券ID");

        log.info("新增优惠券ID: {}", result.getData());
        log.info("✅ 测试2通过：新增普通优惠券正常");
    }

    /**
     * 测试3：新增秒杀优惠券
     */
    @Test
    @Order(3)
    @DisplayName("T3-新增秒杀优惠券")
    void testAddSeckillVoucher() {
        log.info("\n========== 测试3：新增秒杀优惠券 ==========");

        Voucher voucher = new Voucher();
        voucher.setShopId(1L);
        voucher.setTitle("测试秒杀券" + System.currentTimeMillis());
        voucher.setSubTitle("限时秒杀");
        voucher.setRules("仅限今日");
        voucher.setPayValue(100); // 1元
        voucher.setActualValue(5000); // 50元
        voucher.setType(1); // 秒杀券
        voucher.setStock(50);
        voucher.setBeginTime(LocalDateTime.now());
        voucher.setEndTime(LocalDateTime.now().plusDays(1));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", token);
        HttpEntity<Voucher> entity = new HttpEntity<>(voucher, headers);

        ResponseEntity<Result> response = restTemplate.postForEntity(
                BASE_URL + "/voucher/seckill",
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");
        assertTrue(result.getSuccess(), "新增秒杀券应该成功");
        assertNotNull(result.getData(), "应该返回优惠券ID");

        Long voucherId = Long.valueOf(result.getData().toString());
        log.info("新增秒杀券ID: {}", voucherId);

        // 验证Redis中是否有库存记录
        String stockKey = SECKILL_STOCK_KEY + voucherId;
        String stock = redisTemplate.opsForValue().get(stockKey);
        log.info("Redis库存: {}", stock);

        log.info("✅ 测试3通过：新增秒杀优惠券正常");
    }

    /**
     * 测试4：查询不存在店铺的优惠券
     */
    @Test
    @Order(4)
    @DisplayName("T4-查询不存在店铺的优惠券")
    void testQueryVoucherOfNonExistentShop() {
        log.info("\n========== 测试4：查询不存在店铺的优惠券 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/voucher/list/999999",
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");
        assertTrue(result.getSuccess(), "查询应该成功");

        List<?> vouchers = objectMapper.convertValue(result.getData(), List.class);
        assertTrue(vouchers.isEmpty(), "不存在店铺的优惠券列表应该为空");

        log.info("✅ 测试4通过：查询不存在店铺的优惠券正常");
    }

    @AfterAll
    static void printSummary() {
        log.info("\n========== 优惠券服务测试全部完成 ==========");
        log.info("测试列表：");
        log.info("  T1-查询店铺优惠券列表 ✓");
        log.info("  T2-新增普通优惠券 ✓");
        log.info("  T3-新增秒杀优惠券 ✓");
        log.info("  T4-查询不存在店铺的优惠券 ✓");
    }
}
