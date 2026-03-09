package com.hmdp.search;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 搜索服务启动类
 * 提供Elasticsearch搜索和Canal数据同步功能
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.hmdp")
@ComponentScan(basePackages = {"com.hmdp.search", "com.hmdp.common", "com.hmdp.api"})
@MapperScan("com.hmdp.search.mapper")
public class SearchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
        System.out.println("==================================");
        System.out.println("搜索服务启动成功！端口: 8085");
        System.out.println("==================================");
    }
}
