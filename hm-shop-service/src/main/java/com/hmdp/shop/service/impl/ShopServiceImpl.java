package com.hmdp.shop.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.shop.mapper.ShopMapper;
import com.hmdp.shop.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.MultiLevelCacheClient;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private MultiLevelCacheClient multiLevelCacheClient;

    /**
     * 查询商铺详情
     * Sentinel保护：限流/降级时走blockHandler方法
     */
    @Override
    @SentinelResource(
            value = "queryShopById",
            blockHandler = "queryShopByIdBlockHandler",
            fallback = "queryShopByIdFallback"
    )
    public Result queryById(Long id) {
        // 使用多级缓存（L1 Caffeine + L2 Redis逻辑过期）
        Shop shop = multiLevelCacheClient.queryWithLogicalExpire(
                CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById,
                30L  // 逻辑过期时间30秒
        );

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    /**
     * Sentinel限流/降级处理方法
     */
    public Result queryShopByIdBlockHandler(Long id, BlockException ex) {
        log.warn("查询商铺被限流或降级: id={}, 原因={}", id, ex.getMessage());
        return Result.fail("当前访问过于频繁，请稍后再试");
    }

    /**
     * Sentinel业务异常降级方法
     */
    public Result queryShopByIdFallback(Long id, Throwable ex) {
        log.error("查询商铺发生异常: id={}", id, ex);
        return Result.fail("服务暂时不可用，请稍后再试");
    }

    /**
     * 更新商铺信息
     */
    @Override
    @Transactional
    @SentinelResource(value = "updateShop", blockHandler = "updateShopBlockHandler")
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1. 更新数据库
        updateById(shop);
        // 2. 删除多级缓存（L1 + L2）
        multiLevelCacheClient.delete(CACHE_SHOP_KEY, id);
        return Result.ok();
    }

    /**
     * 更新商铺限流处理
     */
    public Result updateShopBlockHandler(Shop shop, BlockException ex) {
        log.warn("更新商铺被限流: shopId={}", shop.getId());
        return Result.fail("当前操作过于频繁，请稍后再试");
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );

        // 如果Redis中没有数据，回退到数据库查询
        if (results == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.isEmpty()) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        if (list.size() <= from) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });

        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
