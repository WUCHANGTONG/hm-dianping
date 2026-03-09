package com.hmdp.comment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 点评服务启动类
 * ============ 微服务架构：点评服务 ============
 * 功能：发布点评、点赞、评论、关注Feed流
 * 端口：8083
 * 跨服务调用：通过Feign调用user-service和shop-service
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.hmdp.api")
@ComponentScan(basePackages = {"com.hmdp.comment", "com.hmdp.api.fallback", "com.hmdp.utils", "com.hmdp.dto", "com.hmdp.entity"})
@MapperScan("com.hmdp.comment.mapper")
public class CommentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommentServiceApplication.class, args);
    }
}
