# 黑马点评 - 微服务高并发架构项目

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.8-green.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.0-blue.svg)](https://spring.io/projects/spring-cloud)
[![JDK](https://img.shields.io/badge/JDK-21-orange.svg)](https://openjdk.java.net/)
[![License](https://img.shields.io/badge/License-Apache%202.0-red.svg)](https://opensource.org/licenses/Apache-2.0)

## 项目简介

黑马点评是一个仿大众点评的高并发微服务项目，采用Spring Cloud Alibaba技术栈，实现了完整的用户系统、商户系统、秒杀系统、搜索推荐系统等功能。

## 技术架构

### 核心技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.8 | 基础框架 |
| Spring Cloud | 2023.0.0 | 微服务治理 |
| Spring Cloud Alibaba | 2023.0.1.0 | 阿里微服务组件 |
| Nacos | 2.3.0 | 服务注册/配置中心 |
| Sentinel | 1.8.6 | 流量控制/熔断降级 |
| MySQL | 8.0.33 | 关系型数据库 |
| Redis | 7.x | 缓存/分布式锁 |
| Redisson | 3.23.5 | 分布式锁/限流 |
| Elasticsearch | 8.11.0 | 搜索引擎 |
| Kafka | 3.6.0 | 消息队列（异步处理） |
| RabbitMQ | 3.12.x | 消息队列（延时消息） |
| Caffeine | 3.1.8 | 本地缓存 |
| Canal | 1.1.7 | MySQL数据同步 |

### 微服务架构

```
┌─────────────────────────────────────────────────────────────┐
│                         用户端                                │
│                    (Web / App / H5)                          │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                      API Gateway                             │
│                    (端口: 8090)                               │
│           统一入口 / 路由转发 / 限流 / 跨域                   │
└──────────────────────────┬──────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
┌───────▼────────┐ ┌──────▼────────┐ ┌───────▼─────────┐
│  hm-user       │ │  hm-shop      │ │  hm-comment     │
│  用户服务      │ │  商户服务     │ │  点评服务       │
│  端口: 8081    │ │  端口: 8082   │ │  端口: 8083     │
└────────────────┘ └───────────────┘ └─────────────────┘
        │                  │                  │
┌───────▼──────────────────▼──────────────────▼─────────────┐
│              基础设施层 (Docker Compose)                    │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐         │
│  │  MySQL  │ │  Redis  │ │  Nacos  │ │   ES    │         │
│  │ 3307    │ │  6379   │ │  8848   │ │  9200   │         │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘         │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐         │
│  │  Kafka  │ │RabbitMQ │ │ Sentinel│ │  Canal  │         │
│  │ 9092    │ │  5672   │ │  8858   │ │  11111  │         │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘         │
└─────────────────────────────────────────────────────────────┘
```

### 服务列表

| 服务名称 | 端口 | 功能描述 | 数据库 |
|---------|------|---------|--------|
| hm-gateway | 8090 | API网关 | - |
| hm-user-service | 8081 | 用户/关注/签到 | hm_user_db |
| hm-shop-service | 8082 | 商户/商铺类型 | hm_shop_db |
| hm-comment-service | 8083 | 笔记/评论/Feed流 | hm_comment_db |
| hm-seckill-service | 8084 | 秒杀/优惠券/订单 | hm_seckill_db |
| hm-search-service | 8085 | 搜索/推荐/ES同步 | - |

## 核心功能

### 1. 用户系统
- 手机验证码登录（双Token机制）
- Token刷新与登出
- 用户签到（Redis BitMap）
- 连续签到统计

### 2. 关注系统
- 关注/取消关注
- 共同关注查询（Redis Set交集）
- 粉丝列表
- Feed流推送（推模式）

### 3. 商户系统
- 商铺信息查询（多级缓存：Caffeine + Redis）
- 商铺类型列表
- 附近商铺（Redis GEO）
- 缓存一致性保障

### 4. 笔记系统
- 发布/查询笔记
- 点赞/取消点赞（Sorted Set）
- 点赞排行榜
- Feed流（滚动分页）

### 5. 秒杀系统
- 秒杀优惠券管理
- 异步秒杀（Redis Lua + Kafka）
- 库存预热
- 订单异步处理
- 死信队列（处理失败订单）
- Sentinel限流保护

### 6. 搜索推荐
- Elasticsearch全文搜索
- 拼音搜索/首字母搜索
- 附近商铺GEO查询
- 自动补全建议
- 个性化推荐（协同过滤）
- 用户行为记录

## 高并发解决方案

| 场景 | 技术方案 | 实现方式 |
|------|---------|---------|
| 缓存 | 多级缓存 | Caffeine(L1) + Redis(L2) |
| 分布式锁 | Redisson | 可重入锁/看门狗机制 |
| 限流 | Sentinel | 接口级限流/熔断降级 |
| 异步处理 | Kafka | 秒杀订单异步处理 |
| 延时消息 | RabbitMQ | 订单超时取消 |
| 数据同步 | Canal | MySQL → ES 实时同步 |
| 防重复提交 | Redis + Lua | SETNX原子操作 |

## 快速开始

### 方式一：Docker Compose一键启动（推荐）

#### 1. 克隆项目
```bash
git clone https://github.com/your-repo/hm-dianping.git
cd hm-dianping
```

#### 2. 启动基础设施
```bash
# 启动所有中间件（MySQL、Redis、Nacos、ES、Kafka等）
docker-compose up -d

# 查看启动状态
docker-compose ps
```

#### 3. 初始化数据库
```bash
# 进入MySQL容器
docker exec -it mysql bash

# 执行初始化脚本
mysql -uroot -proot < /docker-entrypoint-initdb.d/init.sql
```

#### 4. 启动微服务
```bash
# 方式1：使用IDEA启动各个服务
# 方式2：使用Maven打包后启动
mvn clean install -DskipTests
cd hm-gateway && mvn spring-boot:run
cd hm-user-service && mvn spring-boot:run
# ... 其他服务
```

#### 5. 访问服务
- API Gateway: http://localhost:8090
- Nacos控制台: http://localhost:8848/nacos (nacos/nacos)
- Sentinel控制台: http://localhost:8858
- RabbitMQ管理: http://localhost:15672 (guest/guest)

### 方式二：手动启动中间件

如果你已经安装了所需中间件，可以修改`application.yml`中的连接地址后启动。

## 测试

### 运行测试类

```bash
# 用户服务测试
cd hm-user-service
mvn test -Dtest=UserServiceTest
mvn test -Dtest=FollowServiceTest

# 商铺服务测试
cd hm-shop-service
mvn test -Dtest=ShopServiceTest
mvn test -Dtest=ShopTypeServiceTest

# 评论服务测试
cd hm-comment-service
mvn test -Dtest=BlogServiceTest

# 秒杀服务测试
cd hm-seckill-service
mvn test -Dtest=SeckillKafkaTest
mvn test -Dtest=VoucherServiceTest

# 搜索服务测试
cd hm-search-service
mvn test -Dtest=SearchServiceTest
mvn test -Dtest=RecommendServiceTest
```

### API接口文档

启动服务后访问：
- Swagger文档: http://localhost:8090/swagger-ui.html

## 项目结构

```
hm-dianping/
├── hm-common/           # 公共模块（工具类、配置、常量）
├── hm-api/              # Feign接口定义
├── hm-gateway/          # API网关
├── hm-user-service/     # 用户服务
├── hm-shop-service/     # 商户服务
├── hm-comment-service/  # 点评服务
├── hm-seckill-service/  # 秒杀服务
├── hm-search-service/   # 搜索服务
├── docker-compose.yml   # Docker编排文件
└── README.md           # 项目说明
```

## 环境要求

- JDK 21+
- Maven 3.9+
- Docker 24+ & Docker Compose 2+
- MySQL 8.0+
- Redis 7.0+
- Elasticsearch 8.11+
- Kafka 3.6+

## 性能指标

| 指标 | 数值 | 说明 |
|------|------|------|
| QPS | 10,000+ | 秒杀接口峰值 |
| 接口延迟 | < 50ms | 缓存命中情况下 |
| 缓存命中率 | > 95% | 多级缓存 |
| 订单处理 | 5,000/秒 | Kafka异步处理 |

## 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/xxx`)
3. 提交更改 (`git commit -am 'Add some feature'`)
4. 推送到分支 (`git push origin feature/xxx`)
5. 创建 Pull Request

## 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。

## 联系方式

- 项目作者：黑马程序员
- 课程地址：https://www.itheima.com

---

**如果这个项目对你有帮助，请给个Star ⭐**
