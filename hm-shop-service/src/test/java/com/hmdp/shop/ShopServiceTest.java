package com.hmdp.shop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.TokenPairDTO;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 商铺服务全量测试
 * 一键运行：右键类名 → Run 'ShopServiceTest'
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ShopServiceTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "http://localhost:8082";
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
        token = login("13800140001");
    }

    /**
     * 测试1：根据ID查询商铺（测试多级缓存）
     */
    @Test
    @Order(1)
    @DisplayName("T1-根据ID查询商铺")
    void testQueryShopById() {
        log.info("\n========== 测试1：根据ID查询商铺 ==========");

        Long shopId = 1L;

        // 清理缓存
        redisTemplate.delete(CACHE_SHOP_KEY + shopId);

        // 第一次查询（应该走数据库）
        long start1 = System.currentTimeMillis();
        ResponseEntity<Result> response1 = restTemplate.getForEntity(
                BASE_URL + "/shop/" + shopId,
                Result.class
        );
        long time1 = System.currentTimeMillis() - start1;

        Result result1 = response1.getBody();
        assertTrue(result1.getSuccess(), "查询商铺应该成功");
        assertNotNull(result1.getData(), "应该返回商铺数据");

        Map<String, Object> shop1 = objectMapper.convertValue(result1.getData(), Map.class);
        assertNotNull(shop1.get("id"), "商铺ID不应该为空");
        log.info("第一次查询耗时: {}ms", time1);

        // 第二次查询（应该走缓存）
        long start2 = System.currentTimeMillis();
        ResponseEntity<Result> response2 = restTemplate.getForEntity(
                BASE_URL + "/shop/" + shopId,
                Result.class
        );
        long time2 = System.currentTimeMillis() - start2;

        Result result2 = response2.getBody();
        assertTrue(result2.getSuccess(), "第二次查询应该成功");

        log.info("第二次查询耗时: {}ms", time2);
        log.info("缓存加速效果: {}ms", time1 - time2);

        assertTrue(time2 < time1, "缓存查询应该比数据库查询快");

        log.info("✅ 测试1通过：商铺查询和多级缓存正常");
    }

    /**
     * 测试2：新增商铺
     */
    @Test
    @Order(2)
    @DisplayName("T2-新增商铺")
    void testSaveShop() {
        log.info("\n========== 测试2：新增商铺 ==========");

        Shop shop = new Shop();
        shop.setName("测试商铺" + System.currentTimeMillis());
        shop.setTypeId(1L);
        shop.setAddress("测试地址");
        shop.setArea("测试商圈");
        shop.setAvgPrice(100);
        shop.setSold(0);
        shop.setScore(5.0);
        shop.setX(116.397428);
        shop.setY(39.90923);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", token);
        HttpEntity<Shop> entity = new HttpEntity<>(shop, headers);

        ResponseEntity<Result> response = restTemplate.postForEntity(
                BASE_URL + "/shop",
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "新增商铺应该成功");
        assertNotNull(result.getData(), "应该返回商铺ID");

        log.info("新增商铺ID: {}", result.getData());
        log.info("✅ 测试2通过：新增商铺正常");
    }

    /**
     * 测试3：更新商铺（测试缓存一致性）
     */
    @Test
    @Order(3)
    @DisplayName("T3-更新商铺")
    void testUpdateShop() {
        log.info("\n========== 测试3：更新商铺 ==========");

        // 先查询一个商铺
        ResponseEntity<Result> queryResponse = restTemplate.getForEntity(
                BASE_URL + "/shop/1",
                Result.class
        );

        Map<String, Object> shopData = objectMapper.convertValue(queryResponse.getBody().getData(), Map.class);
        Integer originalId = (Integer) shopData.get("id");

        // 更新商铺
        Shop shop = new Shop();
        shop.setId(originalId.longValue());
        shop.setName("更新后的名称" + System.currentTimeMillis());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", token);
        HttpEntity<Shop> entity = new HttpEntity<>(shop, headers);

        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/shop",
                HttpMethod.PUT,
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "更新商铺应该成功");

        // 验证缓存已被删除
        Boolean exists = redisTemplate.hasKey(CACHE_SHOP_KEY + originalId);
        assertTrue(exists == null || !exists, "更新后缓存应该被删除");

        log.info("✅ 测试3通过：更新商铺和缓存一致性正常");
    }

    /**
     * 测试4：按类型查询商铺
     */
    @Test
    @Order(4)
    @DisplayName("T4-按类型查询商铺")
    void testQueryShopByType() {
        log.info("\n========== 测试4：按类型查询商铺 ==========");

        // 不带坐标查询
        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/shop/of/type?typeId=1&current=1",
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "查询应该成功");
        assertNotNull(result.getData(), "应该返回商铺列表");

        List<?> shops = objectMapper.convertValue(result.getData(), List.class);
        log.info("按类型查询到 {} 个商铺", shops.size());

        log.info("✅ 测试4通过：按类型查询商铺正常");
    }

    /**
     * 测试5：按类型查询附近商铺（GEO搜索）
     */
    @Test
    @Order(5)
    @DisplayName("T5-查询附近商铺")
    void testQueryNearbyShops() {
        log.info("\n========== 测试5：查询附近商铺 ==========");

        // 使用坐标查询附近商铺（北京天安门附近）
        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/shop/of/type?typeId=1&current=1&x=116.397428&y=39.90923",
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "查询附近商铺应该成功");
        assertNotNull(result.getData(), "应该返回商铺列表");

        List<?> shops = objectMapper.convertValue(result.getData(), List.class);
        log.info("附近商铺查询到 {} 个", shops.size());

        log.info("✅ 测试5通过：附近商铺查询正常");
    }

    /**
     * 测试6：按名称查询商铺
     */
    @Test
    @Order(6)
    @DisplayName("T6-按名称查询商铺")
    void testQueryShopByName() {
        log.info("\n========== 测试6：按名称查询商铺 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/shop/of/name?name=茶&current=1",
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "查询应该成功");
        assertNotNull(result.getData(), "应该返回商铺列表");

        log.info("✅ 测试6通过：按名称查询商铺正常");
    }

    /**
     * 测试7：不存在的商铺
     */
    @Test
    @Order(7)
    @DisplayName("T7-查询不存在的商铺")
    void testQueryNonExistentShop() {
        log.info("\n========== 测试7：查询不存在的商铺 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/shop/999999",
                Result.class
        );

        Result result = response.getBody();
        assertFalse(result.getSuccess(), "查询不存在的商铺应该失败");
        assertEquals("店铺不存在！", result.getErrorMsg(), "错误信息应该是'店铺不存在！'");

        log.info("✅ 测试7通过：不存在商铺处理正常");
    }

    @AfterAll
    static void printSummary() {
        log.info("\n========== 商铺服务测试全部完成 ==========");
        log.info("测试列表：");
        log.info("  T1-根据ID查询商铺 ✓");
        log.info("  T2-新增商铺 ✓");
        log.info("  T3-更新商铺 ✓");
        log.info("  T4-按类型查询商铺 ✓");
        log.info("  T5-查询附近商铺 ✓");
        log.info("  T6-按名称查询商铺 ✓");
        log.info("  T7-查询不存在的商铺 ✓");
    }
}
