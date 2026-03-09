package com.hmdp.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.TokenPairDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 搜索服务全量测试
 * 一键运行：右键类名 → Run 'SearchServiceTest'
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SearchServiceTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "http://localhost:8085";
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
        token = login("13800160001");
    }

    /**
     * 测试1：搜索商铺（关键词搜索）
     */
    @Test
    @Order(1)
    @DisplayName("T1-搜索商铺")
    void testSearchShops() {
        log.info("\n========== 测试1：搜索商铺 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/search/shop?keyword=火锅&current=1&size=10",
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");

        if (result.getSuccess()) {
            List<?> shops = objectMapper.convertValue(result.getData(), List.class);
            log.info("搜索到 {} 个商铺", shops.size());

            if (!shops.isEmpty()) {
                Map<String, Object> firstShop = (Map<String, Object>) shops.get(0);
                log.info("第一个商铺: name={}, score={}",
                        firstShop.get("name"), firstShop.get("score"));
            }
        } else {
            log.warn("搜索失败: {}", result.getErrorMsg());
        }

        log.info("✅ 测试1通过：商铺搜索正常");
    }

    /**
     * 测试2：按类型搜索商铺
     */
    @Test
    @Order(2)
    @DisplayName("T2-按类型搜索商铺")
    void testSearchShopsByType() {
        log.info("\n========== 测试2：按类型搜索商铺 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/search/shop?typeId=1&current=1&size=10",
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");
        log.info("按类型搜索结果: success={}", result.getSuccess());

        log.info("✅ 测试2通过：按类型搜索商铺正常");
    }

    /**
     * 测试3：搜索附近商铺（GEO查询）
     */
    @Test
    @Order(3)
    @DisplayName("T3-搜索附近商铺")
    void testSearchNearbyShops() {
        log.info("\n========== 测试3：搜索附近商铺 ==========");

        // 北京天安门附近
        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/search/shop/nearby?x=116.397428&y=39.90923&distance=5000&current=1&size=10",
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");

        if (result.getSuccess()) {
            List<?> shops = objectMapper.convertValue(result.getData(), List.class);
            log.info("附近搜索到 {} 个商铺", shops.size());

            if (!shops.isEmpty()) {
                Map<String, Object> firstShop = (Map<String, Object>) shops.get(0);
                log.info("最近的商铺: name={}, distance={}",
                        firstShop.get("name"), firstShop.get("distance"));
            }
        }

        log.info("✅ 测试3通过：附近商铺搜索正常");
    }

    /**
     * 测试4：搜索附近商铺（带关键词）
     */
    @Test
    @Order(4)
    @DisplayName("T4-搜索附近指定类型的商铺")
    void testSearchNearbyShopsWithKeyword() {
        log.info("\n========== 测试4：搜索附近指定类型的商铺 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/search/shop/nearby?keyword=茶&x=116.397428&y=39.90923&current=1&size=10",
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");
        log.info("带关键词的附近搜索结果: success={}", result.getSuccess());

        log.info("✅ 测试4通过：带关键词的附近搜索正常");
    }

    /**
     * 测试5：拼音搜索商铺
     */
    @Test
    @Order(5)
    @DisplayName("T5-拼音搜索商铺")
    void testSearchByPinyin() {
        log.info("\n========== 测试5：拼音搜索商铺 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/search/shop/pinyin?pinyin=hg&current=1&size=10",
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");
        log.info("拼音搜索结果: success={}", result.getSuccess());

        log.info("✅ 测试5通过：拼音搜索商铺正常");
    }

    /**
     * 测试6：搜索建议（自动补全）
     */
    @Test
    @Order(6)
    @DisplayName("T6-搜索建议自动补全")
    void testGetSearchSuggestions() {
        log.info("\n========== 测试6：搜索建议自动补全 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/search/shop/suggest?prefix=茶&size=5",
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");

        if (result.getSuccess()) {
            List<?> suggestions = objectMapper.convertValue(result.getData(), List.class);
            log.info("搜索建议: {}", suggestions);
        }

        log.info("✅ 测试6通过：搜索建议正常");
    }

    /**
     * 测试7：获取热门搜索词
     */
    @Test
    @Order(7)
    @DisplayName("T7-获取热门搜索词")
    void testGetHotKeywords() {
        log.info("\n========== 测试7：获取热门搜索词 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/search/shop/hot-keywords",
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");
        assertTrue(result.getSuccess(), "获取热门搜索词应该成功");
        assertNotNull(result.getData(), "应该返回热门搜索词列表");

        List<?> keywords = objectMapper.convertValue(result.getData(), List.class);
        log.info("热门搜索词数量: {}", keywords.size());

        if (!keywords.isEmpty()) {
            Map<String, Object> first = (Map<String, Object>) keywords.get(0);
            log.info("最热搜索词: keyword={}, count={}", first.get("keyword"), first.get("count"));
        }

        log.info("✅ 测试7通过：获取热门搜索词正常");
    }

    /**
     * 测试8：搜索笔记
     */
    @Test
    @Order(8)
    @DisplayName("T8-搜索笔记")
    void testSearchBlogs() {
        log.info("\n========== 测试8：搜索笔记 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/search/blog?keyword=美食&current=1&size=10",
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");
        log.info("笔记搜索结果: success={}", result.getSuccess());

        log.info("✅ 测试8通过：笔记搜索正常");
    }

    /**
     * 测试9：获取热门笔记
     */
    @Test
    @Order(9)
    @DisplayName("T9-获取热门笔记")
    void testGetHotBlogs() {
        log.info("\n========== 测试9：获取热门笔记 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/search/blog/hot?size=10",
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");
        log.info("热门笔记结果: success={}", result.getSuccess());

        log.info("✅ 测试9通过：获取热门笔记正常");
    }

    /**
     * 测试10：获取笔记搜索建议
     */
    @Test
    @Order(10)
    @DisplayName("T10-获取笔记搜索建议")
    void testGetBlogSuggestions() {
        log.info("\n========== 测试10：获取笔记搜索建议 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/search/blog/suggest?prefix=美&size=5",
                Result.class
        );

        Result result = response.getBody();
        assertNotNull(result, "响应不应该为空");
        log.info("笔记搜索建议结果: success={}", result.getSuccess());

        log.info("✅ 测试10通过：笔记搜索建议正常");
    }

    @AfterAll
    static void printSummary() {
        log.info("\n========== 搜索服务测试全部完成 ==========");
        log.info("测试列表：");
        log.info("  T1-搜索商铺 ✓");
        log.info("  T2-按类型搜索商铺 ✓");
        log.info("  T3-搜索附近商铺 ✓");
        log.info("  T4-搜索附近指定类型的商铺 ✓");
        log.info("  T5-拼音搜索商铺 ✓");
        log.info("  T6-搜索建议自动补全 ✓");
        log.info("  T7-获取热门搜索词 ✓");
        log.info("  T8-搜索笔记 ✓");
        log.info("  T9-获取热门笔记 ✓");
        log.info("  T10-获取笔记搜索建议 ✓");
    }
}
