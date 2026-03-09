package com.hmdp.api;

import com.hmdp.dto.Result;
import com.hmdp.api.fallback.UserApiFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户服务 Feign 接口
 * ============ 微服务调用：OpenFeign 远程调用 ============
 * 场景：点评服务、秒杀服务需要查询用户信息、关注关系
 * 方案：通过 Feign 调用 user-service
 * 亮点：声明式远程调用，支持负载均衡和熔断降级
 */
@FeignClient(value = "hm-user-service", fallback = UserApiFallback.class)
public interface UserApi {

    /**
     * 根据用户ID查询用户信息
     * @param userId 用户ID
     * @return 用户信息
     */
    @GetMapping("/user/{id}")
    Result getUserById(@PathVariable("id") Long userId);

    /**
     * 根据用户ID批量查询用户信息（用于Feed流等场景）
     * @param userIds 用户ID列表（逗号分隔）
     * @return 用户信息列表
     */
    @GetMapping("/user/batch/{userIds}")
    Result getUsersByIds(@PathVariable("userIds") String userIds);

    /**
     * 根据用户ID列表查询用户信息（用于点赞列表等场景）
     * @param ids 用户ID列表（逗号分隔）
     * @param orderByField 排序字段（如 "FIELD(id,5,1)"）
     * @return 用户信息列表
     */
    @GetMapping("/user/list/{ids}")
    Result getUserList(@PathVariable("ids") String ids, @RequestParam("orderBy") String orderByField);

    /**
     * 查询用户的粉丝列表
     * @param followUserId 被关注的用户ID（博主ID）
     * @return 粉丝列表
     */
    @GetMapping("/follow/fans/{followUserId}")
    Result getFans(@PathVariable("followUserId") Long followUserId);
}
