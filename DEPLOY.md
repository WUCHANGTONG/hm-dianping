# 部署指南

## 一、配置修改总结

已将所有服务的MySQL配置从 `localhost` 改为 `172.25.242.221`（WSL IP）：

| 服务 | MySQL地址 | Redis地址 | Nacos地址 | ES地址 | Kafka地址 | Canal地址 |
|------|-----------|-----------|-----------|--------|-----------|-----------|
| user-service | 172.25.242.221 | 172.25.242.221 | 172.25.242.221 | - | - | - |
| shop-service | 172.25.242.221 | 172.25.242.221 | 172.25.242.221 | - | - | - |
| comment-service | 172.25.242.221 | 172.25.242.221 | 172.25.242.221 | - | - | - |
| seckill-service | 172.25.242.221 | 172.25.242.221 | 172.25.242.221 | - | 172.25.242.221 | - |
| search-service | 172.25.242.221 | 172.25.242.221 | 172.25.242.221 | 172.25.242.221 | - | 172.25.242.221 |
| gateway | - | - | 172.25.242.221 | - | - | - |

---

## 二、Docker启动命令

在WSL中执行以下命令启动所有容器：

### 1. MySQL
```bash
docker run -d \
  --name mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=hmdp \
  -v mysql_data:/var/lib/mysql \
  --restart always \
  mysql:8.0 \
  --server-id=1 \
  --log-bin=mysql-bin \
  --binlog-format=ROW \
  --binlog-do-db=hmdp
```

### 2. Redis
```bash
docker run -d \
  --name redis \
  -p 6379:6379 \
  -e REDIS_PASSWORD=123456 \
  --restart always \
  redis:7-alpine \
  redis-server --requirepass 123456
```

### 3. Zookeeper (Kafka依赖)
```bash
docker run -d \
  --name zookeeper \
  -p 2181:2181 \
  -e ALLOW_ANONYMOUS_LOGIN=yes \
  --restart always \
  bitnami/zookeeper:latest
```

### 4. Kafka
```bash
docker run -d \
  --name kafka \
  -p 9092:9092 \
  --link zookeeper:zookeeper \
  -e KAFKA_BROKER_ID=1 \
  -e KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper:2181 \
  -e ALLOW_PLAINTEXT_LISTENER=yes \
  -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092 \
  -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://172.25.242.221:9092 \
  --restart always \
  bitnami/kafka:latest
```

### 5. Elasticsearch
```bash
docker run -d \
  --name elasticsearch \
  -p 9200:9200 \
  -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
  -e "ELASTIC_PASSWORD=elastic" \
  -e "xpack.security.enabled=false" \
  -v es_data:/usr/share/elasticsearch/data \
  --restart always \
  elasticsearch:8.11.0
```

### 6. Kibana
```bash
docker run -d \
  --name kibana \
  -p 5601:5601 \
  --link elasticsearch:elasticsearch \
  -e ELASTICSEARCH_HOSTS=http://elasticsearch:9200 \
  --restart always \
  kibana:8.11.0
```

### 7. Nacos
```bash
docker run -d \
  --name nacos \
  -p 8848:8848 \
  -p 9848:9848 \
  -e MODE=standalone \
  -e SPRING_DATASOURCE_PLATFORM=mysql \
  -e MYSQL_SERVICE_HOST=172.25.242.221 \
  -e MYSQL_SERVICE_PORT=3306 \
  -e MYSQL_SERVICE_DB_NAME=nacos \
  -e MYSQL_SERVICE_USER=root \
  -e MYSQL_SERVICE_PASSWORD=root \
  --restart always \
  nacos/nacos-server:v2.3.0
```

### 8. Canal
```bash
# 先创建Canal配置目录
mkdir -p ~/canal/conf

# 创建instance配置
cat > ~/canal/conf/instance.properties << 'EOF'
canal.instance.mysql.slaveId=1234
canal.instance.master.address=172.25.242.221:3306
canal.instance.dbUsername=canal
canal.instance.dbPassword=canal
canal.instance.connectionCharset=UTF-8
canal.instance.filter.regex=hmdp\\..*
canal.mq.topic=example
EOF

docker run -d \
  --name canal \
  -p 11111:11111 \
  -v ~/canal/conf:/home/admin/canal-server/conf \
  --restart always \
  canal/canal-server:v1.1.7
```

### 9. Sentinel (可选)
```bash
docker run -d \
  --name sentinel \
  -p 8858:8858 \
  --restart always \
  bladex/sentinel-dashboard:latest
```

---

## 三、一键启动脚本

创建 `start-all.sh`：

```bash
#!/bin/bash

echo "=== 启动基础中间件 ==="

# MySQL
docker start mysql 2>/dev/null || docker run -d \
  --name mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=hmdp \
  -v mysql_data:/var/lib/mysql \
  --restart always mysql:8.0 \
  --server-id=1 --log-bin=mysql-bin --binlog-format=ROW --binlog-do-db=hmdp

# Redis
docker start redis 2>/dev/null || docker run -d \
  --name redis -p 6379:6379 \
  --restart always redis:7-alpine redis-server --requirepass 123456

# Zookeeper
docker start zookeeper 2>/dev/null || docker run -d \
  --name zookeeper -p 2181:2181 \
  -e ALLOW_ANONYMOUS_LOGIN=yes \
  --restart always bitnami/zookeeper:latest

# Kafka
docker start kafka 2>/dev/null || docker run -d \
  --name kafka -p 9092:9092 \
  --link zookeeper:zookeeper \
  -e KAFKA_BROKER_ID=1 \
  -e KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper:2181 \
  -e ALLOW_PLAINTEXT_LISTENER=yes \
  -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092 \
  -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://172.25.242.221:9092 \
  --restart always bitnami/kafka:latest

# Elasticsearch
docker start elasticsearch 2>/dev/null || docker run -d \
  --name elasticsearch -p 9200:9200 \
  -e "discovery.type=single-node" \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
  -e "xpack.security.enabled=false" \
  --restart always elasticsearch:8.11.0

# Kibana
docker start kibana 2>/dev/null || docker run -d \
  --name kibana -p 5601:5601 \
  --link elasticsearch:elasticsearch \
  -e ELASTICSEARCH_HOSTS=http://elasticsearch:9200 \
  --restart always kibana:8.11.0

# Nacos
docker start nacos 2>/dev/null || docker run -d \
  --name nacos -p 8848:8848 -p 9848:9848 \
  -e MODE=standalone \
  --restart always nacos/nacos-server:v2.3.0

echo "=== 等待服务启动 ==="
sleep 30

echo "=== 检查服务状态 ==="
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo "=== 服务启动完成 ==="
echo "MySQL: 172.25.242.221:3306"
echo "Redis: 172.25.242.221:6379"
echo "Kafka: 172.25.242.221:9092"
echo "ES: http://172.25.242.221:9200"
echo "Kibana: http://172.25.242.221:5601"
echo "Nacos: http://172.25.242.221:8848/nacos"
```

---

## 四、MySQL初始化

连接MySQL并执行：

```bash
docker exec -it mysql mysql -uroot -proot
```

```sql
-- 创建业务数据库
CREATE DATABASE IF NOT EXISTS hmdp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建Canal用户（用于数据同步）
CREATE USER IF NOT EXISTS 'canal'@'%' IDENTIFIED BY 'canal';
GRANT SELECT, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'canal'@'%';
FLUSH PRIVILEGES;

-- 创建Nacos数据库（如果需要）
CREATE DATABASE IF NOT EXISTS nacos CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 导入hmdp数据（如果你有SQL文件）
-- source /path/to/hmdp.sql;
```

---

## 五、Elasticsearch初始化

### 安装IK分词器
```bash
docker exec -it elasticsearch bash

# 在容器内执行
./bin/elasticsearch-plugin install https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v8.11.0/elasticsearch-analysis-ik-8.11.0.zip

# 退出并重启
exit
docker restart elasticsearch
```

### 创建索引
启动search-service后，调用接口创建索引：
```bash
curl -X POST http://localhost:8085/search/init
```

---

## 六、服务启动顺序

```
1. 启动基础中间件（MySQL/Redis/Kafka/ES/Nacos）
   ↓
2. 等待30秒（等服务完全启动）
   ↓
3. 初始化MySQL数据
   ↓
4. 初始化ES索引
   ↓
5. 启动业务服务（按顺序）
   - hm-user-service (8081)
   - hm-shop-service (8082)
   - hm-comment-service (8083)
   - hm-seckill-service (8084)
   - hm-search-service (8085)
   ↓
6. 启动网关
   - hm-gateway (8090)
```

---

## 七、测试方法

### 1. 基础连通性测试

```bash
# 测试MySQL
docker exec -it mysql mysql -uroot -proot -e "SELECT 1"

# 测试Redis
docker exec -it redis redis-cli -a 123456 ping

# 测试ES
curl http://172.25.242.221:9200

# 测试Kafka
docker exec -it kafka kafka-broker-api-versions.sh --bootstrap-server 172.25.242.221:9092

# 测试Nacos
curl http://172.25.242.221:8848/nacos/v1/ns/operator/metrics
```

### 2. 业务接口测试

#### 用户服务
```bash
# 发送验证码
curl -X POST "http://localhost:8090/user/code?phone=13800138000"

# 登录（需要先发送验证码）
curl -X POST http://localhost:8090/user/login \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800138000","code":"123456"}'
```

#### 商铺搜索（ES）
```bash
# 搜索商铺
curl "http://localhost:8090/search/shop?keyword=火锅&sortBy=score"

# 附近商铺
curl "http://localhost:8090/search/shop/nearby?x=121.47&y=31.23&distance=5000"

# 搜索建议
curl "http://localhost:8090/search/shop/suggest?prefix=火&size=10"
```

#### 笔记搜索（ES）
```bash
# 搜索笔记
curl "http://localhost:8090/search/blog?keyword=美食&sortBy=hot"

# 热门笔记
curl "http://localhost:8090/search/blog/hot?size=10"
```

#### 推荐系统
```bash
# 需要先登录获取token

# 推荐笔记
curl -H "authorization: token" \
  "http://localhost:8090/recommend/blog?size=10"

# 推荐商铺
curl -H "authorization: token" \
  "http://localhost:8090/recommend/shop?size=10"

# 记录用户行为
curl -X POST -H "authorization: token" \
  "http://localhost:8090/recommend/behavior?itemId=1&itemType=blog&behaviorType=like"
```

#### 秒杀（Kafka）
```bash
# 需要先登录

# 秒杀下单
curl -X POST -H "authorization: token" \
  "http://localhost:8090/voucher-order/seckill/1"

# 查看订单
curl -H "authorization: token" \
  "http://localhost:8090/voucher-order/list"
```

### 3. Canal数据同步测试

```bash
# 1. 查看ES中的商铺数量
curl "http://172.25.242.221:9200/shop_index/_count"

# 2. 在MySQL中插入一条商铺数据
docker exec -it mysql mysql -uroot -proot -e "
INSERT INTO hmdp.tb_shop (name, type_id, area, address)
VALUES ('测试商铺', 1, '陆家嘴', '测试地址');
"

# 3. 5秒后查看ES中是否同步
curl "http://172.25.242.221:9200/shop_index/_search?q=测试商铺"
```

### 4. Kafka消息测试

```bash
# 查看Topic列表
docker exec -it kafka kafka-topics.sh --bootstrap-server 172.25.242.221:9092 --list

# 查看消费者组
docker exec -it kafka kafka-consumer-groups.sh \
  --bootstrap-server 172.25.242.221:9092 --list

# 查看消息积压
docker exec -it kafka kafka-consumer-groups.sh \
  --bootstrap-server 172.25.242.221:9092 \
  --describe --group seckill-order-group
```

---

## 八、常见问题

### 1. WSL IP变化
如果WSL重启后IP变化，执行：
```bash
# 获取新IP
ip addr show eth0 | grep "inet " | awk '{print $2}' | cut -d/ -f1

# 然后修改所有application.yml中的IP
```

### 2. MySQL连接失败
```bash
# 检查MySQL是否允许远程连接
docker exec -it mysql mysql -uroot -proot -e "
SELECT user, host FROM mysql.user;
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
"
```

### 3. Kafka连接失败
确保 `advertised.listeners` 配置正确，使用WSL的IP。

### 4. ES无法启动
可能是内存不足，调整ES内存：
```bash
docker update --memory=1g --memory-swap=1g elasticsearch
```

---

## 九、监控地址

| 服务 | 地址 | 账号/密码 |
|------|------|-----------|
| Nacos | http://172.25.242.221:8848/nacos | nacos/nacos |
| Kibana | http://172.25.242.221:5601 | - |
| Sentinel | http://172.25.242.221:8858 | sentinel/sentinel |
| ES | http://172.25.242.221:9200 | - |
