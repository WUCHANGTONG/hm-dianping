package com.hmdp.shop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 商户服务启动类
 * ============ 微服务架构：商户服务 ============
 * 功能：商铺信息、商铺类型、附近商铺（Redis GEO）
 * 端口：8082
 * 注册中心：Nacos
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.hmdp.shop.mapper")
@ComponentScan(basePackages = {"com.hmdp"})
public class ShopServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopServiceApplication.class, args);
    }
}
