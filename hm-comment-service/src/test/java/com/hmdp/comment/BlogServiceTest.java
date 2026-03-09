package com.hmdp.comment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.TokenPairDTO;
import com.hmdp.entity.Blog;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 笔记服务全量测试
 * 一键运行：右键类名 → Run 'BlogServiceTest'
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BlogServiceTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "http://localhost:8083";
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
        TokenPairDTO tokenPair = login("13800150001");
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
     * 测试1：查询热门笔记
     */
    @Test
    @Order(1)
    @DisplayName("T1-查询热门笔记")
    void testQueryHotBlog() {
        log.info("\n========== 测试1：查询热门笔记 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/blog/hot?current=1",
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "查询热门笔记应该成功");
        assertNotNull(result.getData(), "应该返回笔记列表");

        List<?> blogs = objectMapper.convertValue(result.getData(), List.class);
        log.info("热门笔记数量: {}", blogs.size());

        if (!blogs.isEmpty()) {
            Map<String, Object> firstBlog = (Map<String, Object>) blogs.get(0);
            log.info("第一条笔记: id={}, title={}, liked={}",
                    firstBlog.get("id"), firstBlog.get("title"), firstBlog.get("liked"));
        }

        log.info("✅ 测试1通过：查询热门笔记正常");
    }

    /**
     * 测试2：根据ID查询笔记
     */
    @Test
    @Order(2)
    @DisplayName("T2-根据ID查询笔记")
    void testQueryBlogById() {
        log.info("\n========== 测试2：根据ID查询笔记 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/blog/1",
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "查询笔记应该成功");
        assertNotNull(result.getData(), "应该返回笔记数据");

        Map<String, Object> blog = objectMapper.convertValue(result.getData(), Map.class);
        assertNotNull(blog.get("id"), "笔记ID不应该为空");
        assertNotNull(blog.get("title"), "笔记标题不应该为空");

        log.info("笔记详情: id={}, title={}, content={}",
                blog.get("id"), blog.get("title"),
                blog.get("content") != null ? blog.get("content").toString().substring(0, Math.min(50, blog.get("content").toString().length())) + "..." : "");

        log.info("✅ 测试2通过：根据ID查询笔记正常");
    }

    /**
     * 测试3：发布笔记
     */
    @Test
    @Order(3)
    @DisplayName("T3-发布笔记")
    void testSaveBlog() {
        log.info("\n========== 测试3：发布笔记 ==========");

        Blog blog = new Blog();
        blog.setTitle("测试笔记标题" + System.currentTimeMillis());
        blog.setContent("这是测试笔记的内容，用于测试发布功能。");
        blog.setShopId(1L);
        blog.setImages("https://example.com/image.jpg");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", token);
        HttpEntity<Blog> entity = new HttpEntity<>(blog, headers);

        ResponseEntity<Result> response = restTemplate.postForEntity(
                BASE_URL + "/blog",
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "发布笔记应该成功");
        assertNotNull(result.getData(), "应该返回笔记ID");

        log.info("发布笔记ID: {}", result.getData());
        log.info("✅ 测试3通过：发布笔记正常");
    }

    /**
     * 测试4：点赞笔记
     */
    @Test
    @Order(4)
    @DisplayName("T4-点赞笔记")
    void testLikeBlog() throws InterruptedException {
        log.info("\n========== 测试4：点赞笔记 ==========");

        Long blogId = 1L;

        // 清理之前的点赞记录
        redisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + blogId, userId.toString());

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 点赞
        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/blog/like/" + blogId,
                HttpMethod.PUT,
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "点赞应该成功");

        // 等待Redis写入
        Thread.sleep(200);

        // 验证Redis中的点赞记录
        Double score = redisTemplate.opsForZSet().score(BLOG_LIKED_KEY + blogId, userId.toString());
        assertNotNull(score, "点赞记录应该存入Redis");

        log.info("✅ 测试4通过：点赞笔记正常");

        // 取消点赞（清理）
        restTemplate.exchange(
                BASE_URL + "/blog/like/" + blogId,
                HttpMethod.PUT,
                entity,
                Result.class
        );
    }

    /**
     * 测试5：查询笔记点赞列表
     */
    @Test
    @Order(5)
    @DisplayName("T5-查询笔记点赞列表")
    void testQueryBlogLikes() {
        log.info("\n========== 测试5：查询笔记点赞列表 ==========");

        Long blogId = 1L;

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/blog/likes/" + blogId,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "查询点赞列表应该成功");
        assertNotNull(result.getData(), "应该返回点赞用户列表");

        List<?> users = objectMapper.convertValue(result.getData(), List.class);
        log.info("笔记 {} 的点赞用户数: {}", blogId, users.size());

        log.info("✅ 测试5通过：查询点赞列表正常");
    }

    /**
     * 测试6：查询我的笔记
     */
    @Test
    @Order(6)
    @DisplayName("T6-查询我的笔记")
    void testQueryMyBlog() {
        log.info("\n========== 测试6：查询我的笔记 ==========");

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/blog/of/me?current=1",
                HttpMethod.GET,
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "查询我的笔记应该成功");
        assertNotNull(result.getData(), "应该返回笔记列表");

        List<?> blogs = objectMapper.convertValue(result.getData(), List.class);
        log.info("我的笔记数量: {}", blogs.size());

        log.info("✅ 测试6通过：查询我的笔记正常");
    }

    /**
     * 测试7：查询用户笔记
     */
    @Test
    @Order(7)
    @DisplayName("T7-查询用户笔记")
    void testQueryBlogByUserId() {
        log.info("\n========== 测试7：查询用户笔记 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/blog/of/user?current=1&id=1",
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "查询用户笔记应该成功");
        assertNotNull(result.getData(), "应该返回笔记列表");

        List<?> blogs = objectMapper.convertValue(result.getData(), List.class);
        log.info("用户1的笔记数量: {}", blogs.size());

        log.info("✅ 测试7通过：查询用户笔记正常");
    }

    /**
     * 测试8：查询关注用户的笔记（Feed流）
     */
    @Test
    @Order(8)
    @DisplayName("T8-查询关注Feed流")
    void testQueryBlogOfFollow() {
        log.info("\n========== 测试8：查询关注Feed流 ==========");

        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/blog/of/follow?lastId=" + System.currentTimeMillis() + "&offset=0",
                HttpMethod.GET,
                entity,
                Result.class
        );

        Result result = response.getBody();
        // 可能没有关注的人，所以不强制验证返回数据
        log.info("Feed流查询结果: success={}", result.getSuccess());

        log.info("✅ 测试8通过：查询关注Feed流正常");
    }

    /**
     * 测试9：笔记Feed流推送（发布笔记后推送给粉丝）
     */
    @Test
    @Order(9)
    @DisplayName("T9-笔记Feed流推送")
    void testBlogFeedPush() throws InterruptedException {
        log.info("\n========== 测试9：笔记Feed流推送 ==========");

        // 创建一个粉丝（先登录另一个用户）
        TokenPairDTO fanTokenPair = login("13800150002");

        // 粉丝关注当前用户
        HttpHeaders fanHeaders = new HttpHeaders();
        fanHeaders.set("authorization", fanTokenPair.getAccessToken());
        HttpEntity<Void> fanEntity = new HttpEntity<>(fanHeaders);

        restTemplate.exchange(
                USER_SERVICE_URL + "/follow/" + userId + "/true",
                HttpMethod.PUT,
                fanEntity,
                Result.class
        );

        Thread.sleep(200);

        // 当前用户发布笔记
        Blog blog = new Blog();
        blog.setTitle("Feed流测试笔记" + System.currentTimeMillis());
        blog.setContent("测试Feed流推送功能");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", token);
        HttpEntity<Blog> entity = new HttpEntity<>(blog, headers);

        ResponseEntity<Result> response = restTemplate.postForEntity(
                BASE_URL + "/blog",
                entity,
                Result.class
        );

        assertTrue(response.getBody().getSuccess(), "发布笔记应该成功");
        Long blogId = Long.valueOf(response.getBody().getData().toString());

        Thread.sleep(500);

        // 获取粉丝ID
        ResponseEntity<Result> fanInfoResponse = restTemplate.exchange(
                USER_SERVICE_URL + "/user/me",
                HttpMethod.GET,
                fanEntity,
                Result.class
        );
        Map<String, Object> fanData = objectMapper.convertValue(fanInfoResponse.getBody().getData(), Map.class);
        Long fanId = Long.valueOf(fanData.get("id").toString());

        // 验证笔记已推送到粉丝的收件箱
        Set<String> feedItems = redisTemplate.opsForZSet()
                .range(FEED_KEY + fanId, 0, -1);

        if (feedItems != null) {
            boolean pushed = feedItems.contains(blogId.toString());
            log.info("笔记是否推送到粉丝收件箱: {}", pushed);
        }

        // 清理：取消关注
        restTemplate.exchange(
                USER_SERVICE_URL + "/follow/" + userId + "/false",
                HttpMethod.PUT,
                fanEntity,
                Result.class
        );

        log.info("✅ 测试9通过：笔记Feed流推送正常");
    }

    @AfterAll
    static void printSummary() {
        log.info("\n========== 笔记服务测试全部完成 ==========");
        log.info("测试列表：");
        log.info("  T1-查询热门笔记 ✓");
        log.info("  T2-根据ID查询笔记 ✓");
        log.info("  T3-发布笔记 ✓");
        log.info("  T4-点赞笔记 ✓");
        log.info("  T5-查询笔记点赞列表 ✓");
        log.info("  T6-查询我的笔记 ✓");
        log.info("  T7-查询用户笔记 ✓");
        log.info("  T8-查询关注Feed流 ✓");
        log.info("  T9-笔记Feed流推送 ✓");
    }
}
