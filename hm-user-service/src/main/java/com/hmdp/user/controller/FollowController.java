package com.hmdp.user.controller;  // 微服务拆分：包名从 com.hmdp.controller 修改为 com.hmdp.user.controller


import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.user.service.IFollowService;  // 微服务拆分：import 从 com.hmdp.service 修改为 com.hmdp.user.service
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;  // 微服务拆分：使用修正后的 IFollowService 包路径

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id){
        return followService.followCommons(id);
    }

    /**
     * 查询用户的粉丝列表（供Feed流推送使用）
     * @param followUserId 被关注的用户ID（博主ID）
     * @return 粉丝列表
     */
    @GetMapping("/fans/{followUserId}")
    public Result getFans(@PathVariable("followUserId") Long followUserId) {
        return Result.ok(followService.getFans(followUserId));
    }

    /**
     * 查询当前用户的关注列表
     * @return 关注列表
     */
    @GetMapping("/follows")
    public Result getFollows() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(followService.getFollows(user.getId()));
    }

    /**
     * 查询当前用户的粉丝列表
     * @return 粉丝列表
     */
    @GetMapping("/my-fans")
    public Result getMyFans() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(followService.getFans(user.getId()));
    }
}