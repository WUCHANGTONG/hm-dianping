package com.hmdp.user.service.impl;  // 微服务拆分：包名从 com.hmdp.service.impl 修改为 com.hmdp.user.service.impl

import com.hmdp.entity.UserInfo;
import com.hmdp.user.mapper.UserInfoMapper;  // 微服务拆分：import 从 com.hmdp.mapper 修改为 com.hmdp.user.mapper
import com.hmdp.user.service.IUserInfoService;  // 微服务拆分：import 从 com.hmdp.service 修改为 com.hmdp.user.service
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-24
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}