package com.hmdp.user.controller;  // 微服务拆分：包名从 com.hmdp.controller 修改为 com.hmdp.user.controller


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.user.service.IUserInfoService;  // 微服务拆分：import 从 com.hmdp.service 修改为 com.hmdp.user.service
import com.hmdp.user.service.IUserService;  // 微服务拆分：import 从 com.hmdp.service 修改为 com.hmdp.user.service
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;  // 微服务拆分：使用修正后的 IUserService 包路径

    @Resource
    private IUserInfoService userInfoService;  // 微服务拆分：使用修正后的 IUserInfoService 包路径

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能（双Token机制）
     * @param loginForm 登录参数，包含手机号、验证码
     * @return TokenPairDTO 包含accessToken和refreshToken
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // 实现登录功能，返回双Token
        return userService.login(loginForm, session);
    }

    /**
     * 登出功能
     * @param accessToken 访问令牌（从请求头获取）
     * @param refreshToken 刷新令牌（可选，从请求体或请求头获取）
     * @return 登出结果
     */
    @PostMapping("/logout")
    public Result logout(@RequestHeader("authorization") String accessToken,
                         @RequestParam(value = "refreshToken", required = false) String refreshToken){
        // 实现登出功能，清除Token
        return userService.logout(accessToken, refreshToken);
    }

    /**
     * 刷新Access Token
     * @param refreshToken 刷新令牌
     * @return 新的Token对
     */
    @PostMapping("/refresh")
    public Result refreshToken(@RequestParam("refreshToken") String refreshToken){
        // 使用Refresh Token换取新的双Token
        return userService.refreshToken(refreshToken);
    }

    @GetMapping("/me")
    public Result me(){
        // 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    @GetMapping("/detail/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }

    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }

    /**
     * 根据用户ID批量查询用户信息（用于Feed流等场景）
     * @param userIds 用户ID列表（逗号分隔）
     * @return 用户信息列表
     */
    @GetMapping("/batch/{userIds}")
    public Result getUsersByIds(@PathVariable("userIds") String userIds) {
        // 解析用户ID列表
        String[] idArray = userIds.split(",");
        List<Long> ids = Arrays.stream(idArray).map(Long::valueOf).collect(Collectors.toList());
        // 查询用户
        List<User> users = userService.listByIds(ids);
        // 转换为DTO
        List<UserDTO> userDTOS = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    /**
     * 根据用户ID列表查询用户信息（用于点赞列表等场景）
     * @param ids 用户ID列表（逗号分隔）
     * @param orderByField 排序字段（如 "FIELD(id,5,1)"）
     * @return 用户信息列表
     */
    @GetMapping("/list/{ids}")
    public Result getUserList(@PathVariable("ids") String ids, @RequestParam("orderBy") String orderByField) {
        // 解析用户ID列表
        String[] idArray = ids.split(",");
        List<Long> idList = Arrays.stream(idArray).map(Long::valueOf).collect(Collectors.toList());
        // 查询用户（带排序）
        List<User> users = userService.query()
                .in("id", idList)
                .last("ORDER BY " + orderByField)
                .list();
        // 转换为DTO
        List<UserDTO> userDTOS = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
