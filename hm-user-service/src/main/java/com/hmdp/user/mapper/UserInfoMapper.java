package com.hmdp.user.mapper;  // 微服务拆分：包名从 com.hmdp.mapper 修改为 com.hmdp.user.mapper

import com.hmdp.entity.UserInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-24
 */
public interface UserInfoMapper extends BaseMapper<UserInfo> {

}