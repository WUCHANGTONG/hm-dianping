package com.hmdp.user.service;  // 微服务拆分：包名从 com.hmdp.service 修改为 com.hmdp.user.service

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import jakarta.servlet.http.HttpSession;


/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    /**
     * 用户登录（双Token机制）
     * @param loginForm 登录表单
     * @param session Session
     * @return 包含accessToken和refreshToken的结果
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 用户登出
     * @param accessToken 访问令牌
     * @param refreshToken 刷新令牌（可为空）
     * @return 登出结果
     */
    Result logout(String accessToken, String refreshToken);

    /**
     * 刷新Access Token
     * @param refreshToken 刷新令牌
     * @return 新的Token对
     */
    Result refreshToken(String refreshToken);

    Result sign();

    Result signCount();

}
