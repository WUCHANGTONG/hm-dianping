# 方案二改造实施文档

## 概述

本方案按照"方案二：搜索与推荐增强"对黑马点评项目进行全面改造，实现了以下核心功能：

1. **Elasticsearch搜索引擎** - 商铺/笔记全文检索、分词搜索、拼音搜索、高亮显示
2. **Canal数据同步** - 实时同步MySQL数据到Elasticsearch
3. **Kafka消息队列** - 替换Redis Stream，实现专业的秒杀订单异步处理
4. **推荐系统** - 基于用户行为的协同过滤推荐算法

---

## 一、项目结构变化

### 新增模块
```
hm-dianping/
├── hm-search-service/           # 新增：搜索服务（端口8085）
│   ├── config/                  # ES配置
│   ├── controller/              # 搜索、推荐接口
│   ├── entity/                  # ES文档实体（ShopDoc, BlogDoc）
│   ├── listener/                # Canal数据同步监听器
│   ├── recommend/               # 推荐算法
│   │   ├── algorithm/           # ItemCF算法
│   │   ├── entity/              # 行为/偏好实体
│   │   └── service/             # 推荐服务
│   └── service/                 # 搜索服务
└── [其他现有模块...]
```

---

## 二、Elasticsearch集成

### 2.1 索引设计

#### 商铺索引（shop_index）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 商铺ID |
| name | text | 商铺名称（IK分词+拼音） |
| namePinyin | completion | 自动补全 |
| typeId | long | 类型ID |
| area | text | 商圈 |
| address | text | 地址 |
| location | geo_point | 地理位置（经纬度） |
| avgPrice | long | 均价 |
| sold | integer | 销量 |
| comments | integer | 评论数 |
| score | integer | 评分 |

#### 笔记索引（blog_index）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 笔记ID |
| title | text | 标题（IK分词+拼音） |
| content | text | 内容（IK分词） |
| userId | long | 用户ID |
| shopId | long | 商铺ID |
| liked | integer | 点赞数 |
| comments | integer | 评论数 |
| hotScore | double | 热度分数 |
| isHot | boolean | 是否热门 |

### 2.2 搜索功能

#### 商铺搜索
- **全文搜索**：`GET /search/shop?keyword=火锅&sortBy=score`
- **附近搜索**：`GET /search/shop/nearby?x=121.47&y=31.23&distance=5000`
- **拼音搜索**：`GET /search/shop/pinyin?pinyin=hg`
- **自动补全**：`GET /search/shop/suggest?prefix=火&size=10`

#### 笔记搜索
- **全文搜索**：`GET /search/blog?keyword=美食&sortBy=hot`
- **热门笔记**：`GET /search/blog/hot?size=10`
- **相关推荐**：`GET /search/blog/{id}/related?size=5`

---

## 三、Canal数据同步

### 3.1 工作原理
1. Canal伪装成MySQL Slave，订阅binlog日志
2. 解析binlog中的数据变更（INSERT/UPDATE/DELETE）
3. 实时同步到Elasticsearch

### 3.2 配置说明
```yaml
canal:
  server:
    host: 127.0.0.1
    port: 11111
  destination: example
```

### 3.3 同步策略
- **商铺表**（tb_shop）：全字段同步
- **笔记表**（tb_blog）：全字段同步，自动计算热度分数

---

## 四、Kafka消息队列

### 4.1 改造对比

| 特性 | Redis Stream | Kafka |
|------|-------------|-------|
| 持久化 | 内存 | 磁盘 |
| 吞吐量 | 中等 | 高 |
| 消息重试 | 手动实现 | 内置支持 |
| 死信队列 | 无 | 支持 |
| 消费者组 | 支持 | 原生支持 |
| 消息顺序 | 单分区保证 | 按Key分区保证 |

### 4.2 Kafka配置

#### Topic设计
- **seckill-orders**：秒杀订单主题（3分区）
- **seckill-orders-dlq**：死信队列（处理失败订单）

#### 生产者配置
```yaml
spring.kafka.producer:
  acks: all          # 等待所有副本确认
  retries: 3         # 失败重试3次
  enable-idempotence: true  # 开启幂等性
```

#### 消费者配置
```yaml
spring.kafka.consumer:
  enable-auto-commit: false   # 手动ACK
  auto-offset-reset: earliest
  max-poll-records: 500
```

### 4.3 消息处理流程

```
用户请求
  ↓
Lua脚本（扣库存 + 标记用户）
  ↓
发送消息到Kafka（异步）
  ↓
立即返回订单ID（响应<100ms）
  ↓
Kafka Consumer（异步处理）
  ↓
创建订单（分布式锁保护）
```

---

## 五、推荐系统

### 5.1 算法设计

#### 基于物品的协同过滤（ItemCF）

**相似度计算**：余弦相似度
```
similarity(i,j) = N(i)∩N(j) / sqrt(N(i) * N(j))
```

**推荐公式**：
```
P(u,i) = Σ sim(i,j) * r(u,j)
```

### 5.2 用户行为权重

| 行为类型 | 权重 | 说明 |
|---------|------|------|
| view | 1.0 | 浏览 |
| like | 3.0 | 点赞 |
| comment | 4.0 | 评论 |
| collect | 5.0 | 收藏 |
| share | 6.0 | 分享 |
| follow | 8.0 | 关注 |

### 5.3 推荐接口

#### 个性化推荐
- **推荐笔记**：`GET /recommend/blog?size=10`
- **推荐商铺**：`GET /recommend/shop?size=10`

#### 行为记录
- **记录行为**：`POST /recommend/behavior?itemId=1&itemType=blog&behaviorType=like`

#### 协同过滤
- **ItemCF推荐**：`GET /recommend/itemcf/blog?size=10`

#### 相似用户
- **发现相似用户**：`GET /recommend/similar-users?size=10`

---

## 六、部署说明

### 6.1 依赖服务

| 服务 | 端口 | 说明 |
|------|------|------|
| Elasticsearch | 9200 | 搜索引擎 |
| Kibana | 5601 | ES可视化（可选） |
| Kafka | 9092 | 消息队列 |
| Zookeeper | 2181 | Kafka依赖 |
| Canal | 11111 | 数据同步 |
| MySQL | 3306 | 开启binlog |

### 6.2 MySQL配置

需要开启binlog：
```ini
[mysqld]
server-id=1
log-bin=mysql-bin
binlog-format=ROW
binlog-do-db=hmdp
```

### 6.3 Elasticsearch插件

需要安装IK分词器和拼音插件：
```bash
# IK分词器
./bin/elasticsearch-plugin install https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v8.11.0/elasticsearch-analysis-ik-8.11.0.zip

# 拼音插件（可选）
./bin/elasticsearch-plugin install https://github.com/medcl/elasticsearch-analysis-pinyin/releases/download/v8.11.0/elasticsearch-analysis-pinyin-8.11.0.zip
```

---

## 七、API汇总

### 搜索服务（8085）

| 接口 | 方法 | 说明 |
|------|------|------|
| /search/shop | GET | 搜索商铺 |
| /search/shop/nearby | GET | 附近商铺 |
| /search/shop/pinyin | GET | 拼音搜索 |
| /search/shop/suggest | GET | 搜索建议 |
| /search/blog | GET | 搜索笔记 |
| /search/blog/hot | GET | 热门笔记 |
| /search/blog/{id}/related | GET | 相关笔记 |

### 推荐服务（8085）

| 接口 | 方法 | 说明 |
|------|------|------|
| /recommend/blog | GET | 推荐笔记 |
| /recommend/shop | GET | 推荐商铺 |
| /recommend/behavior | POST | 记录行为 |
| /recommend/itemcf/{type} | GET | 协同过滤推荐 |
| /recommend/similar-users | GET | 相似用户 |

---

## 八、性能优化建议

### 8.1 Elasticsearch优化
1. **分页优化**：使用search_after替代深分页
2. **索引优化**：合理设置分片数和副本数
3. **查询优化**：使用filter缓存，避免高频计算

### 8.2 Kafka优化
1. **批量发送**：提高吞吐量
2. **压缩传输**：减少网络开销
3. **分区策略**：按用户ID分区保证顺序

### 8.3 推荐系统优化
1. **离线计算**：使用Spark/Flink预计算相似度
2. **缓存策略**：Redis缓存推荐结果
3. **冷启动**：新用户使用热门内容填充

---

## 九、总结

本次改造实现了企业级的搜索和推荐功能：

1. **搜索能力**：支持全文检索、拼音搜索、地理位置搜索、自动补全
2. **数据同步**：Canal实现毫秒级数据同步
3. **消息队列**：Kafka替代Redis Stream，提升可靠性和吞吐量
4. **个性化推荐**：基于协同过滤的推荐算法，支持冷启动

所有新增功能已集成到网关，对外提供统一的API接口。
