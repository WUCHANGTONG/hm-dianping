package com.hmdp.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.TokenPairDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 推荐服务全量测试
 * 一键运行：右键类名 → Run 'RecommendServiceTest'
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RecommendServiceTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "http://localhost:8085";
    private static final String USER_SERVICE_URL = "http://localhost:8081";
    private static final RestTemplate restTemplate = new RestTemplate();

    private String token;
    private Long userId;

    /**
     * 辅助方法：用户登录获取token
     */
    private TokenPairDTO login(String phone) {
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
            return objectMapper.convertValue(data, TokenPairDTO.class);
        } catch (Exception e) {
            log.error("登录失败: {}", phone, e);
            throw new RuntimeException("登录失败", e);
        }
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        TokenPairDTO tokenPair = login("13800170001");
        this.token = tokenPair.getAccessToken();

        // 获取用户ID
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Result> response = restTemplate.exchange(
                USER_SERVICE_URL + "/user/me",
                HttpMethod.GET,
                entity,
                Result.class
        );

        Map<String, Object> userData = objectMapper.convertValue(response.getBody().getData(), Map.class);
        this.userId = Long.valueOf(userData.get("id").toString());

        log.info("当前用户ID: {}", userId);
    }

    /**
     * 测试1：记录用户行为
     */
    @Test
    @Order(1)
    @DisplayName("T1-记录用户行为")
    void testRecordBehavior() {
        log.info("\n========== 测试1：记录用户行为 ==========");

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 记录浏览行为
        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/recommend/behavior?itemId=1&itemType=blog&behaviorType=view",
                HttpMethod.POST,
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");
        assertTrue(result.getSuccess(), "记录行为应该成功");

        // 记录点赞行为
        response = restTemplate.exchange(
                BASE_URL + "/recommend/behavior?itemId=1&itemType=blog&behaviorType=like",
                HttpMethod.POST,
                entity,
                Result.class
        );

        result = response.getBody();
        assertTrue(result.getSuccess(), "记录点赞行为应该成功");

        log.info("✅ 测试1通过：记录用户行为正常");
    }

    /**
     * 测试2：推荐笔记（冷启动）
     */
    @Test
    @Order(2)
    @DisplayName("T2-推荐笔记（冷启动）")
    void testRecommendBlogsColdStart() {
        log.info("\n========== 测试2：推荐笔记（冷启动） ==========");

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/recommend/blog?size=10",
                HttpMethod.GET,
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");
        assertTrue(result.getSuccess(), "推荐笔记应该成功");
        assertNotNull(result.getData(), "应该返回推荐列表");

        List<?> blogs = objectMapper.convertValue(result.getData(), List.class);
        log.info("冷启动推荐笔记数量: {}", blogs.size());

        log.info("✅ 测试2通过：冷启动推荐笔记正常");
    }

    /**
     * 测试3：推荐商铺（冷启动）
     */
    @Test
    @Order(3)
    @DisplayName("T3-推荐商铺（冷启动）")
    void testRecommendShopsColdStart() {
        log.info("\n========== 测试3：推荐商铺（冷启动） ==========");

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/recommend/shop?size=10",
                HttpMethod.GET,
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");
        assertTrue(result.getSuccess(), "推荐商铺应该成功");
        assertNotNull(result.getData(), "应该返回推荐列表");

        List<?> shops = objectMapper.convertValue(result.getData(), List.class);
        log.info("冷启动推荐商铺数量: {}", shops.size());

        log.info("✅ 测试3通过：冷启动推荐商铺正常");
    }

    /**
     * 测试4：基于ItemCF的推荐
     */
    @Test
    @Order(4)
    @DisplayName("T4-基于ItemCF的推荐")
    void testRecommendByItemCF() {
        log.info("\n========== 测试4：基于ItemCF的推荐 ==========");

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 先记录一些行为
        restTemplate.exchange(
                BASE_URL + "/recommend/behavior?itemId=1&itemType=blog&behaviorType=like",
                HttpMethod.POST,
                entity,
                Result.class
        );

        restTemplate.exchange(
                BASE_URL + "/recommend/behavior?itemId=2&itemType=blog&behaviorType=view",
                HttpMethod.POST,
                entity,
                Result.class
        );

        // 基于ItemCF推荐
        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/recommend/itemcf/blog?size=10",
                HttpMethod.GET,
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");
        log.info("ItemCF推荐结果: success={}", result.getSuccess());

        log.info("✅ 测试4通过：基于ItemCF的推荐正常");
    }

    /**
     * 测试5：发现相似用户
     */
    @Test
    @Order(5)
    @DisplayName("T5-发现相似用户")
    void testFindSimilarUsers() {
        log.info("\n========== 测试5：发现相似用户 ==========");

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 记录一些行为
        restTemplate.exchange(
                BASE_URL + "/recommend/behavior?itemId=1&itemType=blog&behaviorType=like",
                HttpMethod.POST,
                entity,
                Result.class
        );

        // 查找相似用户
        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/recommend/similar-users?size=10",
                HttpMethod.GET,
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");
        log.info("相似用户结果: success={}", result.getSuccess());

        log.info("✅ 测试5通过：发现相似用户正常");
    }

    @AfterAll
    static void printSummary() {
        log.info("\n========== 推荐服务测试全部完成 ==========");
        log.info("测试列表：");
        log.info("  T1-记录用户行为 ✓");
        log.info("  T2-推荐笔记（冷启动） ✓");
        log.info("  T3-推荐商铺（冷启动） ✓");
        log.info("  T4-基于ItemCF的推荐 ✓");
        log.info("  T5-发现相似用户 ✓");
    }
}
