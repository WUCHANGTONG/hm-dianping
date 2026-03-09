package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * 多级缓存客户端（L1 Caffeine本地缓存 + L2 Redis分布式缓存）
 * 采用逻辑过期方案，保证高可用性
 */
@Slf4j
@Component
@SuppressWarnings("unchecked")
public class MultiLevelCacheClient {

    // 异步线程池（用于缓存重建）
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // L1: 本地缓存（Caffeine）- 速度快，容量小
    private final Cache<String, Object> localCache = Caffeine.newBuilder()
            .maximumSize(10000)                              // 最多存1万条
            .expireAfterWrite(10, TimeUnit.SECONDS)          // 写入后10秒过期（测试用）
            .recordStats()                                    // 开启统计
            .build();

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 获取本地缓存实例（用于Kafka监听清除）
     */
    public Cache<String, Object> getLocalCache() {
        return localCache;
    }

    /**
     * 根据key清除本地缓存
     */
    public void invalidateLocalCache(String key) {
        localCache.invalidate(key);
        log.debug("本地缓存已清除: {}", key);
    }

    /**
     * 多级缓存查询（逻辑过期方案）
     *
     * L1 (Caffeine) -> L2 (Redis逻辑过期) -> DB
     * 特点：数据永不过期，通过逻辑时间判断，过期后立返回旧数据，后台异步重建
     *
     * @param keyPrefix           key前缀
     * @param id                  查询ID
     * @param type                返回类型
     * @param dbFallback          数据库查询函数
     * @param logicalExpireSeconds 逻辑过期时间（秒）
     * @return 查询结果
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallback, Long logicalExpireSeconds) {

        String key = keyPrefix + id;
        String lockKey = LOCK_SHOP_KEY + id;

        // ========== 1. 查询 L1 本地缓存 ==========
        R localValue = (R) localCache.getIfPresent(key);
        if (localValue != null) {
            log.debug("L1本地缓存命中: {}", key);
            return localValue;
        }

        // ========== 2. 查询 L2 Redis缓存 ==========
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2.1 Redis中完全没有数据（缓存未命中，需要回源查询）
        if (StrUtil.isBlank(json)) {
            log.debug("L2 Redis无数据，回源查询数据库: {}", key);

            // 查询数据库
            R dbResult = dbFallback.apply(id);

            if (dbResult != null) {
                // 写入缓存（带逻辑过期时间）
                setWithLogicalExpire(key, dbResult, logicalExpireSeconds);
                log.info("缓存写入成功 - key: {}, 数据已回源", key);
            } else {
                log.warn("数据库中也不存在该数据: {}", key);
            }

            return dbResult;
        }

        // 2.2 反序列化，拿到数据和逻辑过期时间
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // ========== 3. 判断是否逻辑过期 ==========
        boolean isExpired = expireTime.isBefore(LocalDateTime.now());

        if (!isExpired) {
            // 3.1 未过期，正常返回，回填L1
            log.debug("L2 Redis未过期，直接返回: {}", key);
            localCache.put(key, r);
            return r;
        }

        // 3.2 已过期！需要重建，但先返回旧数据
        log.warn("L2 Redis已过期，返回旧数据并异步重建: {}, 过期时间: {}", key, expireTime);

        // ========== 4. 尝试获取锁（仅用于防止重复重建，不阻塞） ==========
        boolean isLock = tryLock(lockKey);

        if (isLock) {
            // 4.1 获取锁成功，double-check（可能别的线程已经重建好了）
            String newJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(newJson)) {
                RedisData newData = JSONUtil.toBean(newJson, RedisData.class);
                if (newData.getExpireTime().isAfter(LocalDateTime.now())) {
                    // 已经被别的线程重建了，释放锁直接返回
                    unlock(lockKey);
                    R newR = JSONUtil.toBean((JSONObject) newData.getData(), type);
                    localCache.put(key, newR);
                    return newR;
                }
            }

            // 确实需要重建，提交异步任务
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    log.info("开始异步重建缓存: {}", key);

                    // 查数据库
                    R newR = dbFallback.apply(id);

                    if (newR != null) {
                        // 封装新的RedisData（新的过期时间）
                        RedisData data = new RedisData();
                        data.setData(newR);
                        data.setExpireTime(LocalDateTime.now().plusSeconds(logicalExpireSeconds));

                        // 写入Redis（不设置TTL，永不过期，靠逻辑时间判断）
                        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(data));

                        // 更新L1本地缓存
                        localCache.put(key, newR);

                        log.info("缓存重建完成: {}", key);
                    }
                } catch (Exception e) {
                    log.error("缓存重建失败: {}", key, e);
                } finally {
                    unlock(lockKey);
                }
            });
        } else {
            // 4.2 没拿到锁，说明别的线程在重建了，不管它
            log.debug("已有其他线程在重建缓存，直接返回旧数据: {}", key);
        }

        // ========== 5. 立即返回过期数据（不等待重建） ==========
        // 同时回填到L1，减少下次访问的压力
        localCache.put(key, r);
        return r;
    }

    /**
     * 写入数据（带逻辑过期时间）
     * 用于初始加载或主动更新
     */
    public <R> void setWithLogicalExpire(String key, R value, Long expireSeconds) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        // 写入Redis，不设置TTL（永不过期）
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));

        // 回填L1本地缓存
        localCache.put(key, value);

        log.debug("设置逻辑过期缓存: {}, 过期时间: {}", key, redisData.getExpireTime());
    }

    /**
     * 删除多级缓存
     */
    public void delete(String keyPrefix, Object id) {
        String key = keyPrefix + id;

        // 删除Redis
        stringRedisTemplate.delete(key);

        // 删除本地缓存
        localCache.invalidate(key);

        log.debug("删除多级缓存: {}", key);
    }

    /**
     * 预热缓存（提前加载热点数据）
     */
    public <R, ID> void preheatCache(String keyPrefix, ID id, R value, Long expireSeconds) {
        setWithLogicalExpire(keyPrefix + id, value, expireSeconds);
        log.info("缓存预热完成: {}{}", keyPrefix, id);
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 获取缓存统计信息
     */
    public void printStats() {
        log.info("L1本地缓存统计 - 命中率: {}, 大小: {}",
                localCache.stats().hitRate(),
                localCache.estimatedSize());
    }
}
