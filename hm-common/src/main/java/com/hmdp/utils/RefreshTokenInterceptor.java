package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * Token刷新拦截器（双Token机制）
 *
 * 功能说明：
 * 1. 拦截所有请求，从请求头获取Access Token
 * 2. 验证Access Token是否有效
 * 3. 如果有效，将用户信息存入ThreadLocal，并根据剩余时间决定是否续期
 * 4. 如果过期，返回401让前端使用Refresh Token换取新的Access Token
 */
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取请求头中的Access Token
        String accessToken = request.getHeader("authorization");

        // 2.如果没有token，直接放行（后面的LoginInterceptor会处理是否需要拦截）
        if (StrUtil.isBlank(accessToken)) {
            return true;
        }

        // 3.基于Access Token获取Redis中的用户信息
        String accessTokenKey = ACCESS_TOKEN_KEY + accessToken;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(accessTokenKey);

        // 4.判断用户是否存在（Access Token是否有效）
        if (userMap.isEmpty()) {
            // Access Token已过期或无效，放行让LoginInterceptor处理
            // 如果是需要登录的接口，LoginInterceptor会返回401
            return true;
        }

        // 5.将查询到的hash数据转为UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // 6.保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);

        // 7.获取Token剩余过期时间
        Long expireTime = stringRedisTemplate.getExpire(accessTokenKey, TimeUnit.MINUTES);

        // 8.如果剩余时间小于阈值，则自动续期（滑动过期机制）
        if (expireTime != null && expireTime < ACCESS_TOKEN_REFRESH_THRESHOLD) {
            // 重置为完整的有效期
            stringRedisTemplate.expire(accessTokenKey, ACCESS_TOKEN_TTL, TimeUnit.MINUTES);

            // 同时续期对应的mapping key
            String accessToRefreshKey = ACCESS_TOKEN_KEY + "mapping:" + accessToken;
            stringRedisTemplate.expire(accessToRefreshKey, ACCESS_TOKEN_TTL, TimeUnit.MINUTES);

            log.debug("Access Token自动续期，用户ID: {}, 剩余时间: {}分钟", userDTO.getId(), expireTime);
        }

        // 9.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户，防止内存泄漏
        UserHolder.removeUser();
    }
}
