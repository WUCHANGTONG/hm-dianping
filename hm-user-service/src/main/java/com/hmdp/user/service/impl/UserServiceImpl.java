package com.hmdp.user.service.impl;  // 微服务拆分：包名从 com.hmdp.service.impl 修改为 com.hmdp.user.service.impl

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.TokenPairDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.user.mapper.UserMapper;  // 微服务拆分：import 从 com.hmdp.mapper 修改为 com.hmdp.user.mapper
import com.hmdp.user.service.IUserService;  // 微服务拆分：import 从 com.hmdp.service 修改为 com.hmdp.user.service
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        // 3.符合，生成验证码
        String code = "123456"; // 测试用固定验证码

        // 4.保存验证码到 redis
        String key = LOGIN_CODE_KEY + phone;
        stringRedisTemplate.opsForValue().set(key, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 5.发送验证码
        log.info("发送短信验证码成功，手机号：{}，验证码：{}，RedisKey：{}", phone, code, key);
        // 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        // 3.从redis获取验证码并校验
        String key = LOGIN_CODE_KEY + phone;
        String cacheCode = stringRedisTemplate.opsForValue().get(key);
        String code = loginForm.getCode();
        log.info("登录验证 - 手机号：{}，RedisKey：{}，缓存验证码：{}，输入验证码：{}", phone, key, cacheCode, code);
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 不一致，报错
            log.warn("验证码错误 - 手机号：{}，原因：{}", phone, cacheCode == null ? "验证码已过期或不存在" : "验证码不匹配");
            return Result.fail("验证码错误");
        }

        // 4.一致，根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();

        // 5.判断用户是否存在
        if (user == null) {
            // 6.不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }

        // 7.保存用户信息到 redis中（双Token机制）
        TokenPairDTO tokenPair = generateTokenPair(user);

        // 8.删除验证码（防止重复使用）
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);

        // 9.返回双Token
        return Result.ok(tokenPair);
    }

    /**
     * 生成双Token
     * @param user 用户信息
     * @return TokenPairDTO
     */
    private TokenPairDTO generateTokenPair(User user) {
        // 1.生成Access Token和Refresh Token
        String accessToken = UUID.randomUUID().toString(true);
        String refreshToken = UUID.randomUUID().toString(true);

        // 2.将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue != null ? fieldValue.toString() : ""));

        // 3.存储Access Token（短效）
        String accessTokenKey = ACCESS_TOKEN_KEY + accessToken;
        stringRedisTemplate.opsForHash().putAll(accessTokenKey, userMap);
        stringRedisTemplate.expire(accessTokenKey, ACCESS_TOKEN_TTL, TimeUnit.MINUTES);

        // 4.存储Refresh Token（长效）- 只存用户ID，减少内存占用
        String refreshTokenKey = REFRESH_TOKEN_KEY + refreshToken;
        stringRedisTemplate.opsForValue().set(refreshTokenKey, user.getId().toString(), REFRESH_TOKEN_TTL, TimeUnit.MINUTES);

        // 5.建立Access Token和Refresh Token的映射关系（用于快速查找）
        String accessToRefreshKey = ACCESS_TOKEN_KEY + "mapping:" + accessToken;
        stringRedisTemplate.opsForValue().set(accessToRefreshKey, refreshToken, ACCESS_TOKEN_TTL, TimeUnit.MINUTES);

        // 6.返回Token对
        TokenPairDTO tokenPair = new TokenPairDTO();
        tokenPair.setAccessToken(accessToken);
        tokenPair.setRefreshToken(refreshToken);
        tokenPair.setAccessTokenExpiresIn(ACCESS_TOKEN_TTL * 60); // 转换为秒
        tokenPair.setRefreshTokenExpiresIn(REFRESH_TOKEN_TTL * 60); // 转换为秒

        return tokenPair;
    }

    @Override
    public Result logout(String accessToken, String refreshToken) {
        // 1.参数校验
        if (StrUtil.isBlank(accessToken)) {
            return Result.fail("Access Token不能为空");
        }

        // 2.删除Access Token
        String accessTokenKey = ACCESS_TOKEN_KEY + accessToken;
        Boolean accessDeleted = stringRedisTemplate.delete(accessTokenKey);

        // 3.删除Access Token和Refresh Token的映射
        String accessToRefreshKey = ACCESS_TOKEN_KEY + "mapping:" + accessToken;
        String storedRefreshToken = stringRedisTemplate.opsForValue().get(accessToRefreshKey);
        stringRedisTemplate.delete(accessToRefreshKey);

        // 4.如果传入了Refresh Token，或者能从映射中获取到，一并删除
        String targetRefreshToken = StrUtil.isNotBlank(refreshToken) ? refreshToken : storedRefreshToken;
        if (StrUtil.isNotBlank(targetRefreshToken)) {
            String refreshTokenKey = REFRESH_TOKEN_KEY + targetRefreshToken;
            stringRedisTemplate.delete(refreshTokenKey);
            log.debug("删除Refresh Token: {}", targetRefreshToken);
        }

        // 5.清除ThreadLocal中的用户信息
        UserHolder.removeUser();

        log.debug("用户登出成功，删除Access Token: {}, 删除数量: {}", accessToken, accessDeleted);
        return Result.ok();
    }

    @Override
    public Result refreshToken(String refreshToken) {
        // 1.校验参数
        if (StrUtil.isBlank(refreshToken)) {
            return Result.fail("Refresh Token不能为空");
        }

        // 2.校验Refresh Token是否有效
        String refreshTokenKey = REFRESH_TOKEN_KEY + refreshToken;
        String userIdStr = stringRedisTemplate.opsForValue().get(refreshTokenKey);

        if (StrUtil.isBlank(userIdStr)) {
            return Result.fail("Refresh Token已过期或无效，请重新登录");
        }

        // 3.根据用户ID查询用户信息
        Long userId = Long.valueOf(userIdStr);
        User user = getById(userId);

        if (user == null) {
            return Result.fail("用户不存在");
        }

        // 4.删除旧的Refresh Token（实现令牌轮换，提高安全性）
        stringRedisTemplate.delete(refreshTokenKey);

        // 5.生成新的双Token
        TokenPairDTO newTokenPair = generateTokenPair(user);

        log.debug("Token刷新成功，用户ID: {}", userId);
        return Result.ok(newTokenPair);
    }

    @Override
    public Result sign() {
        // 1.获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long userId = user.getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long userId = user.getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6.循环遍历
        int count = 0;
        while (true) {
            // 6.1.让这个数字与1做与运算，得到数字的最后一个bit位  // 判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 如果为0，说明未签到，结束
                break;
            }else {
                // 如果不为0，说明已签到，计数器+1
                count++;
            }
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        // 1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2.保存用户
        save(user);
        return user;
    }
}
