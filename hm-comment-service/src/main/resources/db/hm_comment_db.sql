-- 黑马点评 - 评论服务数据库初始化脚本
-- 数据库: hm_comment_db
-- 包含表: tb_blog, tb_blog_comments

CREATE DATABASE IF NOT EXISTS hm_comment_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE hm_comment_db;

-- 用户探店笔记表
CREATE TABLE IF NOT EXISTS tb_blog
(
    id          BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
    shop_id     BIGINT          NULL COMMENT '商户id',
    user_id     BIGINT          NOT NULL COMMENT '用户id',
    title       VARCHAR(128)    NULL COMMENT '标题',
    images      VARCHAR(2048)   NULL COMMENT '探店的照片，最多9张，多张以","隔开',
    content     VARCHAR(2048)   NULL COMMENT '探店的文字描述',
    liked       INT             NULL DEFAULT 0 COMMENT '点赞数量',
    comments    INT             NULL DEFAULT 0 COMMENT '评论数量',
    create_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_shop_id (shop_id),
    INDEX idx_user_id (user_id)
) COMMENT '用户探店笔记' ENGINE = InnoDB;

-- 用户探店笔记评论表
CREATE TABLE IF NOT EXISTS tb_blog_comments
(
    id          BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
    user_id     BIGINT          NOT NULL COMMENT '用户id',
    blog_id     BIGINT          NOT NULL COMMENT '探店id',
    parent_id   BIGINT          NOT NULL DEFAULT 0 COMMENT '关联的1级评论id，如果是一级评论，则值为0',
    answer_id   BIGINT          NULL COMMENT '回复的评论id',
    content     VARCHAR(512)    NOT NULL COMMENT '回复的内容',
    liked       INT             NULL DEFAULT 0 COMMENT '点赞数',
    status      TINYINT(1)      NULL DEFAULT 0 COMMENT '状态，0：正常，1：被举报，2：禁止查看',
    create_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_blog_id (blog_id),
    INDEX idx_user_id (user_id),
    INDEX idx_parent_id (parent_id)
) COMMENT '用户探店笔记评论表' ENGINE = InnoDB;
