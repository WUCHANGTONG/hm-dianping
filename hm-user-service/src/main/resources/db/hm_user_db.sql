-- 黑马点评 - 用户服务数据库初始化脚本
-- 数据库: hm_user_db
-- 包含表: tb_user, tb_user_info, tb_follow

CREATE DATABASE IF NOT EXISTS hm_user_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE hm_user_db;

-- 用户表
CREATE TABLE IF NOT EXISTS tb_user
(
    id          BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
    phone       VARCHAR(11)     NOT NULL COMMENT '手机号码',
    password    VARCHAR(128)    NULL COMMENT '密码，加密存储',
    nick_name   VARCHAR(32)     NULL COMMENT '昵称，默认是随机字符',
    icon        VARCHAR(256)    NULL DEFAULT '' COMMENT '用户头像',
    create_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_phone UNIQUE (phone)
) COMMENT '用户表' ENGINE = InnoDB;

-- 用户信息表
CREATE TABLE IF NOT EXISTS tb_user_info
(
    user_id    BIGINT AUTO_INCREMENT COMMENT '主键，用户id' PRIMARY KEY,
    city       VARCHAR(32)     NULL COMMENT '城市名称',
    introduce  VARCHAR(128)    NULL COMMENT '个人介绍，不要超过128个字符',
    fans       INT             NULL DEFAULT 0 COMMENT '粉丝数量',
    followee   INT             NULL DEFAULT 0 COMMENT '关注的人的数量',
    gender     TINYINT(1)      NULL COMMENT '性别，0：男，1：女',
    birthday   DATE            NULL COMMENT '生日',
    credits    INT             NULL DEFAULT 0 COMMENT '积分',
    level      TINYINT(1)      NULL DEFAULT 0 COMMENT '会员级别，0~9级,0代表未开通会员',
    create_time TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT fk_user_info_user_id FOREIGN KEY (user_id) REFERENCES tb_user (id) ON DELETE CASCADE
) COMMENT '用户信息表' ENGINE = InnoDB;

-- 用户关注表
CREATE TABLE IF NOT EXISTS tb_follow
(
    id            BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
    user_id       BIGINT          NOT NULL COMMENT '用户id',
    follow_user_id BIGINT         NOT NULL COMMENT '关联的用户id',
    create_time   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    CONSTRAINT uk_user_follow UNIQUE (user_id, follow_user_id),
    INDEX idx_user_id (user_id),
    INDEX idx_follow_user_id (follow_user_id)
) COMMENT '用户关注表' ENGINE = InnoDB;
