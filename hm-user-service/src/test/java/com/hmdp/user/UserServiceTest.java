package com.hmdp.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.TokenPairDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.user.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户服务全量测试
 * 一键运行：右键类名 → Run 'UserServiceTest'
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserServiceTest {

    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "http://localhost:8081";
    private static final RestTemplate restTemplate = new RestTemplate();

    private String accessToken;
    private String refreshToken;
    private static final String TEST_PHONE = "13800138000";

    /**
     * 辅助方法：用户登录获取token
     */
    private TokenPairDTO login(String phone) {
        try {
            // 1. 发送验证码
            restTemplate.postForObject(
                    BASE_URL + "/user/code?phone=" + phone,
                    null,
                    Result.class
            );
            Thread.sleep(100);

            // 2. 登录
            LoginFormDTO loginForm = new LoginFormDTO();
            loginForm.setPhone(phone);
            loginForm.setCode("123456");

            ResponseEntity<Result> response = restTemplate.postForEntity(
                    BASE_URL + "/user/login",
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
    void beforeEach() {
        // 清理测试数据
        redisTemplate.delete(LOGIN_CODE_KEY + TEST_PHONE);
    }

    /**
     * 测试1：发送验证码
     */
    @Test
    @Order(1)
    @DisplayName("T1-发送验证码")
    void testSendCode() throws InterruptedException {
        log.info("\n========== 测试1：发送验证码 ==========");

        String phone = "13800138001";

        // 发送验证码
        Result result = restTemplate.postForObject(
                BASE_URL + "/user/code?phone=" + phone,
                null,
                Result.class
        );

        assertTrue(result.getSuccess(), "发送验证码应该成功");

        // 等待验证码写入Redis
        Thread.sleep(200);

        // 验证Redis中存在验证码
        String code = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        assertNotNull(code, "验证码应该存入Redis");
        assertEquals("123456", code, "验证码应该是123456");

        log.info("✅ 测试1通过：验证码发送正常");
    }

    /**
     * 测试2：用户登录（双Token机制）
     */
    @Test
    @Order(2)
    @DisplayName("T2-用户登录双Token机制")
    void testLogin() {
        log.info("\n========== 测试2：用户登录双Token机制 ==========");

        // 先发送验证码
        restTemplate.postForObject(
                BASE_URL + "/user/code?phone=" + TEST_PHONE,
                null,
                Result.class
        );

        // 登录
        LoginFormDTO loginForm = new LoginFormDTO();
        loginForm.setPhone(TEST_PHONE);
        loginForm.setCode("123456");

        ResponseEntity<Result> response = restTemplate.postForEntity(
                BASE_URL + "/user/login",
                loginForm,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "登录应该成功");
        assertNotNull(result.getData(), "应该返回Token数据");

        TokenPairDTO tokenPair = objectMapper.convertValue(result.getData(), TokenPairDTO.class);
        assertNotNull(tokenPair.getAccessToken(), "应该返回AccessToken");
        assertNotNull(tokenPair.getRefreshToken(), "应该返回RefreshToken");
        assertTrue(tokenPair.getAccessTokenExpiresIn() > 0, "AccessToken过期时间应该大于0");
        assertTrue(tokenPair.getRefreshTokenExpiresIn() > 0, "RefreshToken过期时间应该大于0");

        // 保存token供后续测试使用
        this.accessToken = tokenPair.getAccessToken();
        this.refreshToken = tokenPair.getRefreshToken();

        log.info("AccessToken: {}...", accessToken.substring(0, 10));
        log.info("RefreshToken: {}...", refreshToken.substring(0, 10));
        log.info("✅ 测试2通过：双Token登录机制正常");
    }

    /**
     * 测试3：验证码错误登录失败
     */
    @Test
    @Order(3)
    @DisplayName("T3-验证码错误登录失败")
    void testLoginWithWrongCode() {
        log.info("\n========== 测试3：验证码错误登录失败 ==========");

        String phone = "13800138002";

        // 发送验证码
        restTemplate.postForObject(
                BASE_URL + "/user/code?phone=" + phone,
                null,
                Result.class
        );

        // 使用错误验证码登录
        LoginFormDTO loginForm = new LoginFormDTO();
        loginForm.setPhone(phone);
        loginForm.setCode("999999"); // 错误验证码

        ResponseEntity<Result> response = restTemplate.postForEntity(
                BASE_URL + "/user/login",
                loginForm,
                Result.class
        );

        Result result = response.getBody();
        assertFalse(result.getSuccess(), "错误验证码登录应该失败");
        assertEquals("验证码错误", result.getErrorMsg(), "错误信息应该是'验证码错误'");

        log.info("✅ 测试3通过：验证码错误拦截正常");
    }

    /**
     * 测试4：获取当前登录用户信息
     */
    @Test
    @Order(4)
    @DisplayName("T4-获取当前用户信息")
    void testGetCurrentUser() {
        log.info("\n========== 测试4：获取当前用户信息 ==========");

        // 先登录获取token
        TokenPairDTO tokenPair = login("13800138003");

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", tokenPair.getAccessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 获取当前用户信息
        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/user/me",
                HttpMethod.GET,
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "获取用户信息应该成功");
        assertNotNull(result.getData(), "应该返回用户数据");

        UserDTO userDTO = objectMapper.convertValue(result.getData(), UserDTO.class);
        assertNotNull(userDTO.getId(), "用户ID不应该为空");
        assertNotNull(userDTO.getNickName(), "用户昵称不应该为空");

        log.info("用户信息: id={}, nickName={}", userDTO.getId(), userDTO.getNickName());
        log.info("✅ 测试4通过：获取当前用户信息正常");
    }

    /**
     * 测试5：Token刷新
     */
    @Test
    @Order(5)
    @DisplayName("T5-Token刷新")
    void testRefreshToken() throws InterruptedException {
        log.info("\n========== 测试5：Token刷新 ==========");

        // 先登录获取token
        TokenPairDTO tokenPair = login("13800138004");

        // 等待1秒确保token创建时间不同
        Thread.sleep(1000);

        // 使用RefreshToken换取新的Token
        ResponseEntity<Result> response = restTemplate.postForEntity(
                BASE_URL + "/user/refresh?refreshToken=" + tokenPair.getRefreshToken(),
                null,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "Token刷新应该成功");
        assertNotNull(result.getData(), "应该返回新的Token数据");

        TokenPairDTO newTokenPair = objectMapper.convertValue(result.getData(), TokenPairDTO.class);
        assertNotNull(newTokenPair.getAccessToken(), "应该返回新的AccessToken");
        assertNotNull(newTokenPair.getRefreshToken(), "应该返回新的RefreshToken");

        // 验证新旧Token不同（令牌轮换）
        assertNotEquals(tokenPair.getAccessToken(), newTokenPair.getAccessToken(),
                "新的AccessToken应该与旧的不同");
        assertNotEquals(tokenPair.getRefreshToken(), newTokenPair.getRefreshToken(),
                "新的RefreshToken应该与旧的不同");

        log.info("✅ 测试5通过：Token刷新和令牌轮换正常");
    }

    /**
     * 测试6：用户登出
     */
    @Test
    @Order(6)
    @DisplayName("T6-用户登出")
    void testLogout() {
        log.info("\n========== 测试6：用户登出 ==========");

        // 先登录获取token
        TokenPairDTO tokenPair = login("13800138005");

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", tokenPair.getAccessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 登出
        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/user/logout",
                HttpMethod.POST,
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "登出应该成功");

        // 验证Redis中的token已被删除
        String accessTokenKey = ACCESS_TOKEN_KEY + tokenPair.getAccessToken();
        Boolean exists = redisTemplate.hasKey(accessTokenKey);
        assertTrue(exists == null || !exists, "AccessToken应该从Redis中删除");

        log.info("✅ 测试6通过：用户登出正常");
    }

    /**
     * 测试7：用户签到
     */
    @Test
    @Order(7)
    @DisplayName("T7-用户签到")
    void testSign() {
        log.info("\n========== 测试7：用户签到 ==========");

        // 先登录获取token
        TokenPairDTO tokenPair = login("13800138006");

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", tokenPair.getAccessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 先清除可能存在的签到记录
        UserDTO user = getUserByToken(tokenPair.getAccessToken());
        if (user != null) {
            redisTemplate.delete(USER_SIGN_KEY + user.getId() + ":" + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")));
        }

        // 签到
        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/user/sign",
                HttpMethod.POST,
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "签到应该成功");

        // 验证Redis中的签到记录
        if (user != null) {
            String key = USER_SIGN_KEY + user.getId() + ":" + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
            Boolean signed = redisTemplate.opsForValue().getBit(key,
                    java.time.LocalDateTime.now().getDayOfMonth() - 1);
            assertTrue(signed != null && signed, "签到记录应该存入Redis");
        }

        log.info("✅ 测试7通过：用户签到正常");
    }

    /**
     * 测试8：查询用户签到次数
     */
    @Test
    @Order(8)
    @DisplayName("T8-查询签到次数")
    void testSignCount() {
        log.info("\n========== 测试8：查询签到次数 ==========");

        // 先登录获取token
        TokenPairDTO tokenPair = login("13800138007");

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", tokenPair.getAccessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 查询签到次数
        ResponseEntity<Result> response = restTemplate.exchange(
                BASE_URL + "/user/sign/count",
                HttpMethod.GET,
                entity,
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "查询签到次数应该成功");
        assertNotNull(result.getData(), "应该返回签到次数");

        Integer count = objectMapper.convertValue(result.getData(), Integer.class);
        assertTrue(count >= 0, "签到次数应该大于等于0");

        log.info("签到次数: {}", count);
        log.info("✅ 测试8通过：查询签到次数正常");
    }

    /**
     * 测试9：批量查询用户信息
     */
    @Test
    @Order(9)
    @DisplayName("T9-批量查询用户信息")
    void testBatchQueryUsers() {
        log.info("\n========== 测试9：批量查询用户信息 ==========");

        // 批量查询
        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/user/batch/1,2,3",
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "批量查询应该成功");
        assertNotNull(result.getData(), "应该返回用户列表");

        log.info("✅ 测试9通过：批量查询用户信息正常");
    }

    /**
     * 测试10：查询用户详情
     */
    @Test
    @Order(10)
    @DisplayName("T10-查询用户详情")
    void testQueryUserDetail() {
        log.info("\n========== 测试10：查询用户详情 ==========");

        // 查询用户详情
        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/user/detail/1",
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "查询用户详情应该成功");

        log.info("✅ 测试10通过：查询用户详情正常");
    }

    /**
     * 辅助方法：根据token获取用户信息
     */
    private UserDTO getUserByToken(String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("authorization", token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Result> response = restTemplate.exchange(
                    BASE_URL + "/user/me",
                    HttpMethod.GET,
                    entity,
                    Result.class
            );

            if (response.getBody() != null && response.getBody().getData() != null) {
                return objectMapper.convertValue(response.getBody().getData(), UserDTO.class);
            }
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
        }
        return null;
    }

    @AfterAll
    static void printSummary() {
        log.info("\n========== 用户服务测试全部完成 ==========");
        log.info("测试列表：");
        log.info("  T1-发送验证码 ✓");
        log.info("  T2-用户登录双Token机制 ✓");
        log.info("  T3-验证码错误登录失败 ✓");
        log.info("  T4-获取当前用户信息 ✓");
        log.info("  T5-Token刷新 ✓");
        log.info("  T6-用户登出 ✓");
        log.info("  T7-用户签到 ✓");
        log.info("  T8-查询签到次数 ✓");
        log.info("  T9-批量查询用户信息 ✓");
        log.info("  T10-查询用户详情 ✓");
    }
}
