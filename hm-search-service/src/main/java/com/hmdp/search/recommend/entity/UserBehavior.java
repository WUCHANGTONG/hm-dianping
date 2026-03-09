package com.hmdp.search.recommend.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户行为实体类
 * 用于记录用户对内容的互动行为
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehavior {

    /**
     * 行为ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 内容ID（笔记ID或商铺ID）
     */
    private Long itemId;

    /**
     * 内容类型：blog/shop
     */
    private String itemType;

    /**
     * 行为类型：
     * - view: 浏览
     * - like: 点赞
     * - collect: 收藏
     * - comment: 评论
     * - share: 分享
     * - follow: 关注
     */
    private String behaviorType;

    /**
     * 行为权重（用于计算偏好分数）
     */
    private Double weight;

    /**
     * 行为时间
     */
    private LocalDateTime createTime;

    /**
     * 获取行为权重
     */
    public static double getBehaviorWeight(String behaviorType) {
        return switch (behaviorType) {
            case "view" -> 1.0;
            case "like" -> 3.0;
            case "collect" -> 5.0;
            case "comment" -> 4.0;
            case "share" -> 6.0;
            case "follow" -> 8.0;
            default -> 1.0;
        };
    }
}
