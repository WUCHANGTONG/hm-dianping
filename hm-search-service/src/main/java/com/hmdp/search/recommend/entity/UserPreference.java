package com.hmdp.search.recommend.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户偏好实体类
 * 存储用户对物品的综合偏好分数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreference {

    /**
     * 记录ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 物品ID
     */
    private Long itemId;

    /**
     * 物品类型
     */
    private String itemType;

    /**
     * 偏好分数（综合各种行为加权计算）
     */
    private Double preference;

    /**
     * 最后更新时间
     */
    private LocalDateTime updateTime;
}
