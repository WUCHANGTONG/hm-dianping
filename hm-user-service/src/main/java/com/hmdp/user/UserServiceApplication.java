package com.hmdp.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 用户服务启动类
 * ============ 微服务架构：用户服务 ============
 * 功能：用户登录、验证码、签到、关注/取关
 * 端口：8081
 * 注册中心：Nacos
 */
@SpringBootApplication
@EnableDiscoveryClient  // 启用Nacos服务注册发现
@MapperScan("com.hmdp.user.mapper")  // 扫描Mapper接口（微服务拆分：修正为 com.hmdp.user.mapper）
@ComponentScan(basePackages = {"com.hmdp"})  // 扫描common模块的组件和本服务组件
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}