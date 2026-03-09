# 黑马点评微服务测试文档

## 基础环境检查

### 1. 检查中间件状态
```bash
# 在WSL中执行
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

### 2. 测试各中间件连通性
```bash
# MySQL
docker exec -it mysql mysql -uroot -proot -e "SELECT 1"

# Redis
docker exec -it redis redis-cli -a 123456 ping

# Elasticsearch
curl http://172.25.242.221:9200

# Nacos
curl http://172.25.242.221:8848/nacos/v1/ns/operator/metrics

# Kafka
docker exec -it kafka kafka-broker-api-versions.sh --bootstrap-server 172.25.242.221:9092
```

---

## 一、用户服务测试 (8081)

### 1. 发送验证码
```bash
curl -X POST "http://localhost:8090/user/code?phone=13800138000"
```

### 2. 用户登录
```bash
curl -X POST http://localhost:8090/user/login \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "13800138000",
    "code": "123456"
  }'
```

### 3. 查询用户信息
```bash
# 需要替换token
curl -H "authorization: token" \
  http://localhost:8090/user/me
```

### 4. 用户签到
```bash
curl -H "authorization: token" \
  http://localhost:8090/user/sign
```

### 5. 连续签到统计
```bash
curl -H "authorization: token" \
  http://localhost:8090/user/sign/count
```

### 6. 关注用户
```bash
curl -X PUT -H "authorization: token" \
  http://localhost:8090/follow/2
```

### 7. 取消关注
```bash
curl -X PUT -H "authorization: token" \
  http://localhost:8090/follow/2
```

### 8. 查询是否关注
```bash
curl -H "authorization: token" \
  http://localhost:8090/follow/or/not/2
```

### 9. 共同关注
```bash
curl -H "authorization: token" \
  http://localhost:8090/follow/common/2
```

---

## 二、商铺服务测试 (8082)

### 1. 查询商铺详情
```bash
curl http://localhost:8090/shop/1
```

### 2. 查询商铺类型列表
```bash
curl http://localhost:8090/shop-type/list
```

### 3. 按类型查询商铺
```bash
curl "http://localhost:8090/shop/of/type?typeId=1&current=1"
```

### 4. 附近商铺查询
```bash
# 需要经纬度参数
curl "http://localhost:8090/shop/of/type?typeId=1&current=1&x=121.47&y=31.23"
```

### 5. 商铺名称模糊查询
```bash
curl "http://localhost:8090/shop/of/name?name=火锅&current=1"
```

---

## 三、搜索服务测试 (8085) - Elasticsearch

### 商铺搜索

#### 1. 全文搜索商铺
```bash
curl "http://localhost:8090/search/shop?keyword=火锅&sortBy=score&current=1&size=10"
```

#### 2. 附近商铺搜索
```bash
curl "http://localhost:8090/search/shop/nearby?x=121.47&y=31.23&distance=5000&current=1&size=10"
```

#### 3. 附近+关键词搜索
```bash
curl "http://localhost:8090/search/shop/nearby?keyword=火锅&x=121.47&y=31.23&distance=5000&current=1&size=10"
```

#### 4. 拼音搜索
```bash
curl "http://localhost:8090/search/shop/pinyin?pinyin=hg&current=1&size=10"
```

#### 5. 搜索建议
```bash
curl "http://localhost:8090/search/shop/suggest?prefix=火&size=10"
```

#### 6. 热门搜索词
```bash
curl http://localhost:8090/search/shop/hot-keywords
```

### 笔记搜索

#### 7. 全文搜索笔记
```bash
curl "http://localhost:8090/search/blog?keyword=美食&sortBy=hot&current=1&size=10"
```

#### 8. 热门笔记
```bash
curl "http://localhost:8090/search/blog/hot?size=10"
```

#### 9. 笔记搜索建议
```bash
curl "http://localhost:8090/search/blog/suggest?prefix=美&size=10"
```

#### 10. 相关笔记推荐
```bash
curl "http://localhost:8090/search/blog/1/related?size=5"
```

#### 11. 查询用户笔记
```bash
curl "http://localhost:8090/search/blog/user/1?current=1&size=10"
```

#### 12. 查询商铺笔记
```bash
curl "http://localhost:8090/search/blog/shop/1?current=1&size=10"
```

---

## 四、推荐服务测试 (8085)

### 1. 推荐笔记（需要登录）
```bash
curl -H "authorization: token" \
  "http://localhost:8090/recommend/blog?size=10"
```

### 2. 推荐商铺（需要登录）
```bash
curl -H "authorization: token" \
  "http://localhost:8090/recommend/shop?size=10"
```

### 3. 记录用户行为
```bash
curl -X POST -H "authorization: token" \
  "http://localhost:8090/recommend/behavior?itemId=1&itemType=blog&behaviorType=like"
```

### 4. 基于ItemCF的推荐
```bash
curl -H "authorization: token" \
  "http://localhost:8090/recommend/itemcf/blog?size=10"
```

### 5. 发现相似用户
```bash
curl -H "authorization: token" \
  "http://localhost:8090/recommend/similar-users?size=10"
```

---

## 五、点评服务测试 (8083)

### 1. 发布笔记（需要登录）
```bash
curl -X POST -H "authorization: token" \
  -H "Content-Type: application/json" \
  http://localhost:8090/blog \
  -d '{
    "title": "测试笔记标题",
    "content": "这是一条测试笔记内容，非常好吃！",
    "shopId": 1,
    "images": "http://example.com/img1.jpg"
  }'
```

### 2. 查询热门笔记
```bash
curl "http://localhost:8090/blog/hot?current=1"
```

### 3. 查询笔记详情
```bash
curl http://localhost:8090/blog/1
```

### 4. 点赞笔记（需要登录）
```bash
curl -X PUT -H "authorization: token" \
  http://localhost:8090/blog/like/1
```

### 5. 查询笔记点赞列表
```bash
curl http://localhost:8090/blog/likes/1
```

### 6. 查询关注Feed流（需要登录）
```bash
curl -H "authorization: token" \
  "http://localhost:8090/blog/of/follow?lastId=0&offset=0"
```

### 7. 查询我的笔记（需要登录）
```bash
curl -H "authorization: token" \
  "http://localhost:8090/blog/of/me?current=1"
```

---

## 六、秒杀服务测试 (8084) - Kafka

### 1. 查询优惠券列表
```bash
curl http://localhost:8090/voucher/list/1
```

### 2. 查询秒杀优惠券
```bash
curl http://localhost:8090/voucher/seckill/list
```

### 3. 秒杀下单（需要登录）
```bash
curl -X POST -H "authorization: token" \
  http://localhost:8090/voucher-order/seckill/1
```

### 4. 查询我的订单（需要登录）
```bash
curl -H "authorization: token" \
  http://localhost:8090/voucher-order/list
```

### 5. 检查Kafka消息
```bash
# 在WSL中执行
# 查看Topic
docker exec -it kafka kafka-topics.sh --bootstrap-server 172.25.242.221:9092 --list

# 查看消费者组
docker exec -it kafka kafka-consumer-groups.sh \
  --bootstrap-server 172.25.242.221:9092 --list

# 查看消费进度
docker exec -it kafka kafka-consumer-groups.sh \
  --bootstrap-server 172.25.242.221:9092 \
  --describe --group seckill-order-group
```

---

## 七、Canal数据同步测试

### 1. 查看ES中的商铺数量
```bash
curl "http://172.25.242.221:9200/shop_index/_count"
```

### 2. 查看ES中的笔记数量
```bash
curl "http://172.25.242.221:9200/blog_index/_count"
```

### 3. 在MySQL中插入商铺数据
```bash
docker exec -it mysql mysql -uroot -proot -e "
USE hmdp;
INSERT INTO tb_shop (name, type_id, images, area, address, x, y, avg_price, sold, comments, score, open_hours, create_time, update_time)
VALUES ('测试商铺-Canal同步', 1, 'http://example.com/img.jpg', '陆家嘴', '测试地址', 121.47, 31.23, 100, 0, 0, 50, '09:00-22:00', NOW(), NOW());
"
```

### 4. 5秒后查询ES验证同步
```bash
curl "http://172.25.242.221:9200/shop_index/_search?q=Canal同步"
```

### 5. 更新数据测试
```bash
docker exec -it mysql mysql -uroot -proot -e "
USE hmdp;
UPDATE tb_shop SET name = '测试商铺-已更新' WHERE name = '测试商铺-Canal同步';
"
```

### 6. 删除数据测试
```bash
docker exec -it mysql mysql -uroot -proot -e "
USE hmdp;
DELETE FROM tb_shop WHERE name = '测试商铺-已更新';
"
```

---

## 八、压力测试脚本

### 秒杀压力测试（使用wrk或ab）
```bash
# 安装wrk（在WSL中）
sudo apt-get install wrk

# 准备Lua脚本（seckill.lua）
cat > /tmp/seckill.lua << 'EOF'
wrk.method = "POST"
wrk.headers["authorization"] = "your_token_here"
EOF

# 执行压力测试（需要替换token）
wrk -t10 -c100 -d30s -s /tmp/seckill.lua \
  http://localhost:8090/voucher-order/seckill/1
```

### 搜索压力测试
```bash
wrk -t4 -c20 -d30s \
  "http://localhost:8090/search/shop?keyword=火锅&current=1&size=20"
```

---

## 九、WebSocket测试（如果前端需要）

### 使用websocat测试
```bash
# 安装websocat
cargo install websocat

# 测试连接
websocat ws://localhost:8090/ws/echo
```

---

## 十、完整业务流程测试

### 场景：用户登录 -> 搜索商铺 -> 发布笔记 -> 秒杀下单

```bash
#!/bin/bash

BASE_URL="http://localhost:8090"
PHONE="13800138000"

echo "=== 1. 发送验证码 ==="
curl -X POST "${BASE_URL}/user/code?phone=${PHONE}"

echo -e "\n=== 2. 登录（手动输入验证码） ==="
read -p "请输入验证码: " CODE
TOKEN=$(curl -s -X POST ${BASE_URL}/user/login \
  -H "Content-Type: application/json" \
  -d "{\"phone\":\"${PHONE}\",\"code\":\"${CODE}\"}" | grep -o '"data":"[^"]*"' | cut -d'"' -f4)
echo "Token: ${TOKEN}"

echo -e "\n=== 3. 搜索商铺 ==="
curl -s "${BASE_URL}/search/shop?keyword=火锅&current=1&size=5"

echo -e "\n=== 4. 发布笔记 ==="
curl -s -X POST -H "authorization: ${TOKEN}" \
  -H "Content-Type: application/json" \
  ${BASE_URL}/blog \
  -d '{
    "title": "自动化测试笔记",
    "content": "这是通过脚本发布的测试笔记",
    "shopId": 1
  }'

echo -e "\n=== 5. 秒杀下单 ==="
ORDER_RESULT=$(curl -s -X POST -H "authorization: ${TOKEN}" \
  ${BASE_URL}/voucher-order/seckill/1)
echo "下单结果: ${ORDER_RESULT}"

echo -e "\n=== 6. 查询推荐 ==="
curl -s -H "authorization: ${TOKEN}" \
  "${BASE_URL}/recommend/blog?size=5"

echo -e "\n=== 测试完成 ==="
```

---

## 测试数据准备

### 初始化测试数据SQL
```sql
-- 插入测试商铺
INSERT INTO tb_shop_type (name, icon, sort) VALUES
('美食', 'food.png', 1),
('KTV', 'ktv.png', 2),
('酒店', 'hotel.png', 3);

INSERT INTO tb_shop (name, type_id, images, area, address, x, y, avg_price, sold, comments, score, open_hours) VALUES
('海底捞火锅', 1, 'https://example.com/hdl.jpg', '陆家嘴', '浦东新区陆家嘴环路1000号', 121.495, 31.240, 150, 1000, 500, 48, '10:00-22:00'),
('喜茶', 1, 'https://example.com/heytea.jpg', '静安寺', '静安区南京西路1266号', 121.445, 31.230, 35, 5000, 2000, 50, '09:00-23:00'),
('星巴克', 1, 'https://example.com/starbucks.jpg', '人民广场', '黄浦区南京东路800号', 121.475, 31.235, 45, 3000, 1500, 49, '07:00-23:00');

INSERT INTO tb_blog (shop_id, user_id, title, content, images, liked, comments) VALUES
(1, 1, '海底捞打卡', '服务真的很好，推荐毛肚和鸭肠！', 'https://example.com/blog1.jpg', 100, 20),
(2, 1, '喜茶新品评测', '多肉葡萄 yyds!', 'https://example.com/blog2.jpg', 200, 50);

INSERT INTO tb_seckill_voucher (voucher_id, stock, begin_time, end_time) VALUES
(1, 100, DATE_ADD(NOW(), INTERVAL -1 HOUR), DATE_ADD(NOW(), INTERVAL 2 HOUR));

INSERT INTO tb_voucher (shop_id, title, sub_title, rules, pay_value, actual_value, type) VALUES
(1, '海底捞100元代金券', '满200可用', '全场通用', 10000, 9500, 1);
```

---

## 监控和日志

### 查看服务日志
```bash
# 在IDEA中查看各服务的控制台输出
# 或查看日志文件（如果配置了）
```

### 查看Nacos服务注册
```bash
curl http://172.25.242.221:8848/nacos/v1/ns/instance/list?serviceName=hm-user-service
```

### 查看Sentinel监控
```bash
# 访问 http://172.25.242.221:8858
# 账号: sentinel
# 密码: sentinel
```

### 查看Kibana（ES监控）
```bash
# 访问 http://172.25.242.221:5601
```

---

## 常见问题排查

### 1. 连接被拒绝
- 检查Docker容器是否启动: `docker ps`
- 检查IP地址是否正确（WSL IP可能会变化）

### 2. Token失效
- 重新登录获取新token
- 检查token格式: `Bearer token` 或直接使用 `token`

### 3. ES搜索无结果
- 检查索引是否存在: `curl http://172.25.242.221:9200/_cat/indices`
- 检查数据是否同步

### 4. Kafka消息未消费
- 检查消费者组: `kafka-consumer-groups.sh --bootstrap-server 172.25.242.221:9092 --describe --group seckill-order-group`
- 检查消息是否发送到Topic

### 5. Canal未同步
- 检查Canal日志: `docker logs canal`
- 检查MySQL binlog是否开启
- 检查Canal用户权限
