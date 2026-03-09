package com.hmdp.user.service;  // 微服务拆分：包名从 com.hmdp.service 修改为 com.hmdp.user.service

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long followUserId, Boolean isFollow);

    Result isFollow(Long followUserId);

    Result followCommons(Long id);

    /**
     * 查询用户的粉丝列表
     * @param followUserId 被关注的用户ID
     * @return 粉丝列表
     */
    List<Follow> getFans(Long followUserId);

    /**
     * 查询用户的关注列表
     * @param userId 用户ID
     * @return 关注列表
     */
    List<Follow> getFollows(Long userId);
}
