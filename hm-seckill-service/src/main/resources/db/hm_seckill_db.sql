-- 黑马点评 - 秒杀服务数据库初始化脚本
-- 数据库: hm_seckill_db
-- 包含表: tb_voucher, tb_seckill_voucher, tb_voucher_order

CREATE DATABASE IF NOT EXISTS hm_seckill_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE hm_seckill_db;

-- 优惠券表
CREATE TABLE IF NOT EXISTS tb_voucher
(
    id           BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
    shop_id      BIGINT          NULL COMMENT '商铺id',
    title        VARCHAR(64)     NOT NULL COMMENT '代金券标题',
    sub_title    VARCHAR(128)    NULL COMMENT '副标题',
    rules        VARCHAR(1024)   NULL COMMENT '使用规则',
    pay_value    BIGINT          NULL COMMENT '支付金额',
    actual_value BIGINT          NULL COMMENT '抵扣金额',
    type         TINYINT         NULL DEFAULT 0 COMMENT '优惠券类型',
    status       TINYINT         NULL DEFAULT 1 COMMENT '优惠券状态，1：上架，2：下架',
    create_time  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_shop_id (shop_id),
    INDEX idx_status (status)
) COMMENT '优惠券表' ENGINE = InnoDB;

-- 秒杀优惠券表，与优惠券是一对一关系
CREATE TABLE IF NOT EXISTS tb_seckill_voucher
(
    voucher_id  BIGINT          NOT NULL COMMENT '关联的优惠券的id' PRIMARY KEY,
    stock       INT             NOT NULL COMMENT '库存',
    create_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    begin_time  TIMESTAMP       NOT NULL COMMENT '生效时间',
    end_time    TIMESTAMP       NOT NULL COMMENT '失效时间',
    update_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_begin_time (begin_time),
    INDEX idx_end_time (end_time)
) COMMENT '秒杀优惠券表' ENGINE = InnoDB;

-- 优惠券订单表
CREATE TABLE IF NOT EXISTS tb_voucher_order
(
    id          BIGINT          NOT NULL COMMENT '主键' PRIMARY KEY,
    user_id     BIGINT          NOT NULL COMMENT '下单的用户id',
    voucher_id  BIGINT          NOT NULL COMMENT '购买的代金券id',
    pay_type    TINYINT         NULL DEFAULT 1 COMMENT '支付方式 1：余额支付；2：支付宝；3：微信',
    status      TINYINT         NULL DEFAULT 1 COMMENT '订单状态，1：未支付；2：已支付；3：已核销；4：已取消；5：退款中；6：已退款',
    create_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    pay_time    TIMESTAMP       NULL COMMENT '支付时间',
    use_time    TIMESTAMP       NULL COMMENT '核销时间',
    refund_time TIMESTAMP       NULL COMMENT '退款时间',
    update_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_voucher_id (voucher_id),
    INDEX idx_status (status)
) COMMENT '优惠券订单表' ENGINE = InnoDB;
