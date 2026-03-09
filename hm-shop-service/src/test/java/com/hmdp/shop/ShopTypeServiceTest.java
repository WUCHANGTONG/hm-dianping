package com.hmdp.shop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 商铺类型服务全量测试
 * 一键运行：右键类名 → Run 'ShopTypeServiceTest'
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ShopTypeServiceTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "http://localhost:8082";
    private static final RestTemplate restTemplate = new RestTemplate();

    /**
     * 测试1：查询商铺类型列表
     */
    @Test
    @Order(1)
    @DisplayName("T1-查询商铺类型列表")
    void testQueryTypeList() {
        log.info("\n========== 测试1：查询商铺类型列表 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/shop-type/list",
                Result.class
        );

        Result result = response.getBody();
        assertTrue(result.getSuccess(), "查询类型列表应该成功");
        assertNotNull(result.getData(), "应该返回类型列表");

        List<?> typeList = objectMapper.convertValue(result.getData(), List.class);
        assertFalse(typeList.isEmpty(), "类型列表不应该为空");

        // 验证类型数据
        Map<String, Object> firstType = (Map<String, Object>) typeList.get(0);
        assertNotNull(firstType.get("id"), "类型ID不应该为空");
        assertNotNull(firstType.get("name"), "类型名称不应该为空");
        assertNotNull(firstType.get("sort"), "排序字段不应该为空");

        log.info("商铺类型数量: {}", typeList.size());
        for (Object type : typeList) {
            Map<String, Object> typeMap = (Map<String, Object>) type;
            log.info("类型: id={}, name={}, sort={}",
                    typeMap.get("id"), typeMap.get("name"), typeMap.get("sort"));
        }

        log.info("✅ 测试1通过：商铺类型列表查询正常");
    }

    /**
     * 测试2：验证类型列表排序
     */
    @Test
    @Order(2)
    @DisplayName("T2-验证类型列表排序")
    void testTypeListSortOrder() {
        log.info("\n========== 测试2：验证类型列表排序 ==========");

        ResponseEntity<Result> response = restTemplate.getForEntity(
                BASE_URL + "/shop-type/list",
                Result.class
        );

        Result result = response.getBody();
        List<Map<String, Object>> typeList = objectMapper.convertValue(result.getData(), List.class);

        // 验证按sort字段升序排序
        for (int i = 1; i < typeList.size(); i++) {
            Integer prevSort = (Integer) typeList.get(i - 1).get("sort");
            Integer currSort = (Integer) typeList.get(i).get("sort");
            assertTrue(prevSort <= currSort,
                    "类型列表应该按sort字段升序排序: " + prevSort + " <= " + currSort);
        }

        log.info("✅ 测试2通过：类型列表排序正常");
    }

    @AfterAll
    static void printSummary() {
        log.info("\n========== 商铺类型服务测试全部完成 ==========");
        log.info("测试列表：");
        log.info("  T1-查询商铺类型列表 ✓");
        log.info("  T2-验证类型列表排序 ✓");
    }
}
