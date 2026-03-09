package com.hmdp.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.TokenPairDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 关注服务全量测试
 * 一键运行：右键类名 → Run 'FollowServiceTest'
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FollowServiceTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String USER_SERVICE_URL = "http://localhost:8081";
    private static final String BASE_URL = "http://localhost:8081";
    private static final RestTemplate restTemplate = new RestTemplate();

    private String token1; // 用户1的token
    private String token2; // 用户2的token
    private Long userId1;
    private Long userId2;

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

    /**
     * 辅助方法：获取当前登录用户ID
     */
    private Long getUserId(String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("authorization", token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Result> response = restTemplate.exchange(
                    USER_SERVICE_URL + "/user/me",
                    HttpMethod.GET,
                    entity,
                    Result.class
            );

            if (response.getBody() != null && response.getBody().getData() != null) {
                UserDTO user = objectMapper.convertValue(response.getBody().getData(), UserDTO.class);
                return user.getId();
            }
        } catch (Exception e) {
            log.error("获取用户ID失败", e);
        }
        return null;
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        // 登录两个用户
        TokenPairDTO pair1 = login("13800139001");
        TokenPairDTO pair2 = login("13800139002");

        this.token1 = pair1.getAccessToken();
        this.token2 = pair2.getAccessToken();

        // 获取用户ID
        this.userId1 = getUserId(token1);
        this.userId2 = getUserId(token2);

        log.info("用户1 ID: {}, 用户2 ID: {}", userId1, userId2);

        // 清理Redis中的关注记录
        redisTemplate.delete("follows:" + userId1);
        redisTemplate.delete("follows:" + userId2);
    }

    /**
     * 测试1：关注用户
     */
    @Test
    @Order(1)
    @DisplayName("T1-关注用户")
    void testFollow() throws InterruptedException {
        log.info("\n========== 测试1：关注用户 ==========");

        // 用户1关注用户2
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", token1);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/follow/" + userId2 + "/true",
                HttpMethod.PUT,
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "关注应该成功");

        // 等待Redis写入
        Thread.sleep(200);

        // 验证Redis中的关注记录
        Set<String> follows = redisTemplate.opsForSet().members("follows:" + userId1);
        assertNotNull(follows, "关注列表不应该为空");
        assertTrue(follows.contains(userId2.toString()), "Redis中应该包含被关注的用户ID");

        log.info("✅ 测试1通过：关注用户正常");
    }

    /**
     * 测试2：查询是否关注
     */
    @Test
    @Order(2)
    @DisplayName("T2-查询是否关注")
    void testIsFollow() {
        log.info("\n========== 测试2：查询是否关注 ==========");

        // 先关注
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", token1);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        restTemplate.exchange(
                BASE_URL + "/follow/" + userId2 + "/true",
                HttpMethod.PUT,
                entity,
                Result.class
        );

        // 查询是否关注
        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/follow/or/not/" + userId2,
                HttpMethod.GET,
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "查询应该成功");
        assertNotNull(result.getData(), "应该返回关注状态");

        Boolean isFollow = objectMapper.convertValue(result.getData(), Boolean.class);
        assertTrue(isFollow, "应该显示已关注");

        log.info("✅ 测试2通过：查询关注状态正常");
    }

    /**
     * 测试3：取消关注
     */
    @Test
    @Order(3)
    @DisplayName("T3-取消关注")
    void testUnfollow() throws InterruptedException {
        log.info("\n========== 测试3：取消关注 ==========");

        // 先关注
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", token1);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        restTemplate.exchange(
                BASE_URL + "/follow/" + userId2 + "/true",
                HttpMethod.PUT,
                entity,
                Result.class
        );

        Thread.sleep(200);

        // 取消关注
        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/follow/" + userId2 + "/false",
                HttpMethod.PUT,
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "取消关注应该成功");

        // 等待Redis更新
        Thread.sleep(200);

        // 验证Redis中的关注记录已被删除
        Set<String> follows = redisTemplate.opsForSet().members("follows:" + userId1);
        assertTrue(follows == null || !follows.contains(userId2.toString()),
                "Redis中不应该包含已取消关注的用户ID");

        log.info("✅ 测试3通过：取消关注正常");
    }

    /**
     * 测试4：查询共同关注
     */
    @Test
    @Order(4)
    @DisplayName("T4-查询共同关注")
    void testCommonFollows() throws InterruptedException {
        log.info("\n========== 测试4：查询共同关注 ==========");

        // 创建一个用户3
        TokenPairDTO pair3 = login("13800139003");
        String token3 = pair3.getAccessToken();
        Long userId3 = getUserId(token3);

        // 用户1和用户2都关注用户3
        HttpHeaders headers1 = new HttpHeaders();
        headers1.set("authorization", token1);
        HttpEntity<Void> entity1 = new HttpEntity<>(headers1);

        HttpHeaders headers2 = new HttpHeaders();
        headers2.set("authorization", token2);
        HttpEntity<Void> entity2 = new HttpEntity<>(headers2);

        restTemplate.exchange(
                BASE_URL + "/follow/" + userId3 + "/true",
                HttpMethod.PUT,
                entity1,
                Result.class
        );

        restTemplate.exchange(
                BASE_URL + "/follow/" + userId3 + "/true",
                HttpMethod.PUT,
                entity2,
                Result.class
        );
        Thread.sleep(200);

        // 用户1查询与用户2的共同关注
        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/follow/common/" + userId2,
                HttpMethod.GET,
                entity1,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "查询共同关注应该成功");
        assertNotNull(result.getData(), "应该返回共同关注列表");

        List<?> commonFollows = objectMapper.convertValue(result.getData(), List.class);
        assertFalse(commonFollows.isEmpty(), "共同关注列表不应该为空");

        log.info("共同关注数量: {}", commonFollows.size());
        log.info("✅ 测试4通过：查询共同关注正常");

        // 清理
        redisTemplate.delete("follows:" + userId3);
    }

    /**
     * 测试5：查询粉丝列表
     */
    @Test
    @Order(5)
    @DisplayName("T5-查询粉丝列表")
    void testGetFans() throws InterruptedException {
        log.info("\n========== 测试5：查询粉丝列表 ==========");

        // 用户2关注用户1
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", token2);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        restTemplate.exchange(
                BASE_URL + "/follow/" + userId1 + "/true",
                HttpMethod.PUT,
                entity,
                Result.class
        );

        Thread.sleep(200);

        // 用户1查询自己的粉丝
        HttpHeaders headers1 = new HttpHeaders();
        headers1.set("authorization", token1);
        HttpEntity<Void> entity1 = new HttpEntity<>(headers1);

        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/follow/my-fans",
                HttpMethod.GET,
                entity1,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "查询粉丝列表应该成功");

        log.info("✅ 测试5通过：查询粉丝列表正常");
    }

    /**
     * 测试6：查询关注列表
     */
    @Test
    @Order(6)
    @DisplayName("T6-查询关注列表")
    void testGetFollows() throws InterruptedException {
        log.info("\n========== 测试6：查询关注列表 ==========");

        // 用户1关注用户2
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", token1);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        restTemplate.exchange(
                BASE_URL + "/follow/" + userId2 + "/true",
                HttpMethod.PUT,
                entity,
                Result.class
        );

        Thread.sleep(200);

        // 查询关注列表
        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/follow/follows",
                HttpMethod.GET,
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "查询关注列表应该成功");

        log.info("✅ 测试6通过：查询关注列表正常");
    }

    @AfterAll
    static void printSummary() {
        log.info("\n========== 关注服务测试全部完成 ==========");
        log.info("测试列表：");
        log.info("  T1-关注用户 ✓");
        log.info("  T2-查询是否关注 ✓");
        log.info("  T3-取消关注 ✓");
        log.info("  T4-查询共同关注 ✓");
        log.info("  T5-查询粉丝列表 ✓");
        log.info("  T6-查询关注列表 ✓");
    }
}
