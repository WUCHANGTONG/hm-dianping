package com.hmdp.user.service;  // 微服务拆分：包名从 com.hmdp.service 修改为 com.hmdp.user.service

import com.hmdp.entity.UserInfo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-24
 */
public interface IUserInfoService extends IService<UserInfo> {

}
