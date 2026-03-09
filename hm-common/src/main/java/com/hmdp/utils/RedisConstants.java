package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;

    // ========== 单Token机制（已废弃，保留兼容） ==========
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    // ========== 双Token机制 ==========
    /**
     * Access Token Key前缀（短效令牌，用于访问资源）
     */
    public static final String ACCESS_TOKEN_KEY = "login:access:";
    /**
     * Refresh Token Key前缀（长效令牌，用于刷新Access Token）
     */
    public static final String REFRESH_TOKEN_KEY = "login:refresh:";

    /**
     * Access Token有效期：30分钟（单位：分钟）
     */
    public static final Long ACCESS_TOKEN_TTL = 30L;
    /**
     * Refresh Token有效期：7天（单位：分钟）
     */
    public static final Long REFRESH_TOKEN_TTL = 7 * 24 * 60L; // 10080分钟

    /**
     * Access Token自动续期阈值：剩余10分钟时续期
     */
    public static final Long ACCESS_TOKEN_REFRESH_THRESHOLD = 10L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
