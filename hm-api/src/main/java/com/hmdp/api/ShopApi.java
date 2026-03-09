package com.hmdp.api;

import com.hmdp.dto.Result;
import com.hmdp.api.fallback.ShopApiFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 商户服务 Feign 接口
 * ============ 微服务调用：OpenFeign 远程调用 ============
 * 场景：点评服务需要查询商铺信息
 * 方案：通过 Feign 调用 shop-service
 */
@FeignClient(value = "hm-shop-service", fallback = ShopApiFallback.class)
public interface ShopApi {

    /**
     * 根据商铺ID查询商铺信息
     * @param shopId 商铺ID
     * @return 商铺信息
     */
    @GetMapping("/shop/{id}")
    Result getShopById(@PathVariable("id") Long shopId);
}
