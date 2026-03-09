-- =====================================================
-- 黑马点评数据库初始化脚本
-- =====================================================

-- 创建nacos数据库
CREATE DATABASE IF NOT EXISTS nacos CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户服务数据库
CREATE DATABASE IF NOT EXISTS hm_user_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建商户服务数据库
CREATE DATABASE IF NOT EXISTS hm_shop_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建评论服务数据库
CREATE DATABASE IF NOT EXISTS hm_comment_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建秒杀服务数据库
CREATE DATABASE IF NOT EXISTS hm_seckill_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 切换到用户数据库
USE hm_user_db;

-- 用户表
CREATE TABLE IF NOT EXISTS `tb_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone` varchar(11) NOT NULL COMMENT '手机号码',
  `password` varchar(128) DEFAULT '' COMMENT '密码，加密存储',
  `nick_name` varchar(32) DEFAULT '' COMMENT '昵称',
  `icon` varchar(255) DEFAULT '' COMMENT '用户头像',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 用户信息表
CREATE TABLE IF NOT EXISTS `tb_user_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键，用户id',
  `city` varchar(20) DEFAULT NULL COMMENT '城市名称',
  `introduce` varchar(100) DEFAULT NULL COMMENT '个人介绍，不要超过128个字符',
  `fans` int(8) unsigned DEFAULT '0' COMMENT '粉丝数量',
  `followee` int(8) unsigned DEFAULT '0' COMMENT '关注的人的数量',
  `gender` tinyint(1) unsigned DEFAULT '0' COMMENT '性别，0：男，1：女',
  `birthday` date DEFAULT NULL COMMENT '生日',
  `credits` int(10) unsigned DEFAULT '0' COMMENT '积分',
  `level` tinyint(1) DEFAULT '1' COMMENT '会员级别，0~9级,0代表未开通会员',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

-- 关注表
CREATE TABLE IF NOT EXISTS `tb_follow` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `user_id` bigint(20) unsigned NOT NULL COMMENT '用户id',
  `follow_user_id` bigint(20) unsigned NOT NULL COMMENT '关联的用户id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_follow` (`user_id`,`follow_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注表';

-- 切换到商户数据库
USE hm_shop_db;

-- 商铺类型表
CREATE TABLE IF NOT EXISTS `tb_shop_type` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) NOT NULL COMMENT '类型名称',
  `icon` varchar(255) NOT NULL COMMENT '图标',
  `sort` int(8) unsigned NOT NULL DEFAULT '0' COMMENT '顺序',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_sort` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺类型表';

-- 商铺表
CREATE TABLE IF NOT EXISTS `tb_shop` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) NOT NULL COMMENT '商铺名称',
  `type_id` bigint(20) NOT NULL COMMENT '商铺类型的id',
  `images` varchar(1024) DEFAULT NULL COMMENT '商铺图片，多个图片以','隔开',
  `area` varchar(32) DEFAULT NULL COMMENT '商圈',
  `address` varchar(255) NOT NULL COMMENT '地址',
  `x` double(10,7) NOT NULL COMMENT '经度',
  `y` double(10,7) NOT NULL COMMENT '纬度',
  `avg_price` bigint(10) DEFAULT NULL COMMENT '均价，消费金额',
  `sold` int(10) unsigned DEFAULT '0' COMMENT '销量',
  `comments` int(10) unsigned DEFAULT '0' COMMENT '评论数量',
  `score` int(2) unsigned DEFAULT '50' COMMENT '评分，1~5分，乘10保存，避免小数',
  `open_hours` varchar(32) DEFAULT NULL COMMENT '营业时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_type_id` (`type_id`),
  KEY `idx_area` (`area`),
  KEY `idx_score` (`score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户表';

-- 插入商铺类型数据
INSERT INTO `tb_shop_type` (`name`, `icon`, `sort`) VALUES
('美食', '/types/1.png', 1),
('KTV', '/types/2.png', 2),
('足疗/按摩', '/types/3.png', 3),
('酒吧', '/types/4.png', 4),
('美发/理发', '/types/5.png', 5),
('美容SPA', '/types/6.png', 6),
('桌游/电玩', '/types/7.png', 7),
('健身运动', '/types/8.png', 8),
('洗浴', '/types/9.png', 9),
('民宿/酒店', '/types/10.png', 10)
ON DUPLICATE KEY UPDATE `name`=`name`;

-- 插入示例商铺数据
INSERT INTO `tb_shop` (`name`, `type_id`, `images`, `area`, `address`, `x`, `y`, `avg_price`, `sold`, `comments`, `score`, `open_hours`) VALUES
('茶百道(三里屯店)', 1, '/shops/1.png', '三里屯', '三里屯路19号三里屯太古里南区B1', 116.4551, 39.9372, 2500, 3650, 890, 48, '10:00-22:00'),
('海底捞火锅(国贸店)', 1, '/shops/2.png', '国贸', '建国门外大街1号国贸商城北区L4', 116.4605, 39.9143, 15000, 5280, 1560, 49, '10:00-03:00'),
('星巴克臻选(望京SOHO店)', 1, '/shops/3.png', '望京', '望京街9号望京国际中心E座', 116.4878, 39.9991, 4500, 8920, 2100, 47, '07:00-22:00'),
('纯K(工体店)', 2, '/shops/4.png', '工体', '工人体育场北路58号', 116.4473, 39.9331, 8000, 2340, 560, 46, '13:00-06:00')
ON DUPLICATE KEY UPDATE `name`=`name`;

-- 切换到评论数据库
USE hm_comment_db;

-- 笔记/博客表
CREATE TABLE IF NOT EXISTS `tb_blog` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint(20) DEFAULT NULL COMMENT '商户id',
  `user_id` bigint(20) unsigned NOT NULL COMMENT '用户id',
  `title` varchar(255) DEFAULT NULL COMMENT '标题',
  `images` varchar(2048) DEFAULT NULL COMMENT '探店的照片，最多9张，多张以","隔开',
  `content` varchar(2048) DEFAULT NULL COMMENT '探店的文字描述',
  `liked` int(8) unsigned DEFAULT '0' COMMENT '点赞数量',
  `comments` int(8) unsigned DEFAULT '0' COMMENT '评论数量',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记表';

-- 评论表
CREATE TABLE IF NOT EXISTS `tb_blog_comments` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) NOT NULL COMMENT '用户id',
  `blog_id` bigint(20) NOT NULL COMMENT '笔记id',
  `parent_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '关联的1级评论id，如果是一级评论，则值为0',
  `answer_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '回复的评论id',
  `content` varchar(512) NOT NULL COMMENT '评论内容',
  `liked` int(8) unsigned DEFAULT '0' COMMENT '点赞数',
  `status` tinyint(1) unsigned DEFAULT '0' COMMENT '状态，0：正常，1：被举报，2：禁止查看',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_blog_id` (`blog_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记评论表';

-- 插入示例笔记数据
INSERT INTO `tb_blog` (`shop_id`, `user_id`, `title`, `images`, `content`, `liked`, `comments`) VALUES
(1, 1, '三里屯这家茶百道太好喝了！', '/blogs/1.jpg', '今天来三里屯逛街，发现这家茶百道。招牌芋圆奶茶真的超级好喝，芋圆很Q弹，奶茶甜度刚刚好。环境也很棒，适合和朋友聊天休息。推荐给大家！', 128, 23),
(2, 2, '海底捞服务真的没得说', '/blogs/2.jpg', '又来吃海底捞了，服务还是一如既往的好。等位的时候有免费的小吃和饮料，还会帮忙做美甲。火锅味道也很棒，最爱番茄锅底！', 256, 45),
(3, 1, '望京SOHO这家星巴克风景绝了', '/blogs/3.jpg', '今天来望京SOHO办事，发现这家星巴克的位置特别好。坐在窗边可以看到整个SOHO的建筑，设计感满满。咖啡品质在线，值得来打卡。', 89, 12)
ON DUPLICATE KEY UPDATE `title`=`title`;

-- 切换到秒杀数据库
USE hm_seckill_db;

-- 优惠券表
CREATE TABLE IF NOT EXISTS `tb_voucher` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint(20) unsigned DEFAULT NULL COMMENT '关联的商铺id',
  `title` varchar(60) NOT NULL COMMENT '代金券标题',
  `sub_title` varchar(128) DEFAULT NULL COMMENT '副标题',
  `rules` varchar(1024) DEFAULT NULL COMMENT '使用规则',
  `pay_value` bigint(10) unsigned NOT NULL COMMENT '支付金额',
  `actual_value` bigint(10) unsigned NOT NULL COMMENT '抵扣金额',
  `type` tinyint(1) unsigned DEFAULT '0' COMMENT '0,普通券；1,秒杀券',
  `status` tinyint(1) unsigned DEFAULT '1' COMMENT '1,上架; 2,下架; 3,过期',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券表';

-- 秒杀优惠券表
CREATE TABLE IF NOT EXISTS `tb_seckill_voucher` (
  `voucher_id` bigint(20) unsigned NOT NULL COMMENT '关联的优惠券的id',
  `stock` int(8) unsigned NOT NULL COMMENT '库存',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `begin_time` datetime NOT NULL COMMENT '生效时间',
  `end_time` datetime NOT NULL COMMENT '失效时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`voucher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀优惠券表，与优惠券是一对一关系';

-- 优惠券订单表
CREATE TABLE IF NOT EXISTS `tb_voucher_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) unsigned NOT NULL COMMENT '下单的用户id',
  `voucher_id` bigint(20) unsigned NOT NULL COMMENT '购买的代金券id',
  `pay_type` tinyint(1) unsigned DEFAULT '1' COMMENT '支付方式 1：余额支付；2：支付宝；3：微信',
  `status` tinyint(1) unsigned DEFAULT '1' COMMENT '订单状态，1：未付款；2：已付款；3：已核销；4：已取消；5：退款中；6：已退款',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `pay_time` timestamp DEFAULT NULL COMMENT '支付时间',
  `use_time` timestamp DEFAULT NULL COMMENT '核销时间',
  `refund_time` timestamp DEFAULT NULL COMMENT '退款时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_voucher` (`user_id`,`voucher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券订单表';

-- 插入示例优惠券数据
INSERT INTO `tb_voucher` (`id`, `shop_id`, `title`, `sub_title`, `rules`, `pay_value`, `actual_value`, `type`) VALUES
(1, 1, '茶百道5折优惠券', '全场通用', '满20元可用', 1000, 2000, 0),
(2, 2, '海底捞100元代金券', '满300可用', '仅限工作日使用', 5000, 10000, 0)
ON DUPLICATE KEY UPDATE `title`=`title`;

-- 插入示例秒杀券数据
INSERT INTO `tb_seckill_voucher` (`voucher_id`, `stock`, `begin_time`, `end_time`) VALUES
(3, 100, DATE_ADD(NOW(), INTERVAL -1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY))
ON DUPLICATE KEY UPDATE `voucher_id`=`voucher_id`;

-- 插入示例用户数据
USE hm_user_db;
INSERT INTO `tb_user` (`id`, `phone`, `nick_name`) VALUES
(1, '13800138000', '用户_abc123'),
(2, '13800138001', '用户_def456')
ON DUPLICATE KEY UPDATE `phone`=`phone`;

-- Nacos初始化表结构（简化版）
USE nacos;
CREATE TABLE IF NOT EXISTS `config_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id` varchar(255) NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) DEFAULT NULL COMMENT 'group_id',
  `content` longtext NOT NULL COMMENT 'content',
  `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfo_datagrouptenant` (`data_id`,`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='config_info';
