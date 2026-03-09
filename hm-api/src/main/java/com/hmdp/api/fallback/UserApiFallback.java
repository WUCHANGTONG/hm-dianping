package com.hmdp.api.fallback;

import com.hmdp.api.UserApi;
import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户服务降级实现
 * ============ 限流降级：Sentinel 熔断降级 ============
 * 场景：用户服务调用失败时，返回友好提示
 * 方案：实现 Feign 接口的 fallback 方法
 */
@Slf4j
@Component
public class UserApiFallback implements UserApi {

    @Override
    public Result getUserById(Long userId) {
        log.error("用户服务调用失败，userId={}", userId);
        return Result.fail("用户服务暂时不可用，请稍后再试");
    }

    @Override
    public Result getUsersByIds(String userIds) {
        log.error("用户服务批量查询失败，userIds={}", userIds);
        return Result.fail("用户服务暂时不可用，请稍后再试");
    }

    @Override
    public Result getUserList(String ids, String orderByField) {
        log.error("用户服务列表查询失败，ids={}", ids);
        return Result.fail("用户服务暂时不可用，请稍后再试");
    }

    @Override
    public Result getFans(Long followUserId) {
        log.error("查询粉丝列表失败，followUserId={}", followUserId);
        return Result.fail("用户服务暂时不可用，请稍后再试");
    }
}
