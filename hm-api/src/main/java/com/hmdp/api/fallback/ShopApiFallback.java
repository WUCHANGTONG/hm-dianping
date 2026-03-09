package com.hmdp.api.fallback;

import com.hmdp.api.ShopApi;
import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 商户服务降级实现
 * ============ 限流降级：Sentinel 熔断降级 ============
 * 场景：商户服务调用失败时，返回友好提示
 * 方案：实现 Feign 接口的 fallback 方法
 */
@Slf4j
@Component
public class ShopApiFallback implements ShopApi {

    @Override
    public Result getShopById(Long shopId) {
        log.error("商户服务调用失败，shopId={}", shopId);
        return Result.fail("商户服务暂时不可用，请稍后再试");
    }
}
