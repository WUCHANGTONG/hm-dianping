-- 黑马点评 - 商户服务数据库初始化脚本
-- 数据库: hm_shop_db
-- 包含表: tb_shop, tb_shop_type

CREATE DATABASE IF NOT EXISTS hm_shop_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE hm_shop_db;

-- 商户类型表
CREATE TABLE IF NOT EXISTS tb_shop_type
(
    id          BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
    name        VARCHAR(32)     NOT NULL COMMENT '类型名称',
    icon        VARCHAR(256)    NOT NULL COMMENT '图标',
    sort        INT             NOT NULL DEFAULT 0 COMMENT '顺序',
    create_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_sort (sort)
) COMMENT '商户类型表' ENGINE = InnoDB;

-- 商户表
CREATE TABLE IF NOT EXISTS tb_shop
(
    id          BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
    name        VARCHAR(64)     NOT NULL COMMENT '商铺名称',
    type_id     BIGINT          NOT NULL COMMENT '商铺类型的id',
    images      VARCHAR(1024)   NULL COMMENT '商铺图片，多个图片以","隔开',
    area        VARCHAR(64)     NULL COMMENT '商圈，例如陆家嘴',
    address     VARCHAR(256)    NULL COMMENT '地址',
    x           DOUBLE          NULL COMMENT '经度',
    y           DOUBLE          NULL COMMENT '纬度',
    avg_price   BIGINT          NULL COMMENT '均价，取整数',
    sold        INT             NULL DEFAULT 0 COMMENT '销量',
    comments    INT             NULL DEFAULT 0 COMMENT '评论数量',
    score       INT             NULL DEFAULT 50 COMMENT '评分，1~5分，乘10保存，避免小数',
    open_hours  VARCHAR(32)     NULL COMMENT '营业时间，例如 10:00-22:00',
    create_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_type_id (type_id),
    INDEX idx_area (area),
    INDEX idx_score (score)
) COMMENT '商户表' ENGINE = InnoDB;
