package com.hmdp.search.recommend.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 物品相似度实体类
 * 存储物品之间的相似度（基于物品的协同过滤）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemSimilarity {

    /**
     * 记录ID
     */
    private Long id;

    /**
     * 物品A的ID
     */
    private Long itemIdA;

    /**
     * 物品B的ID
     */
    private Long itemIdB;

    /**
     * 物品类型
     */
    private String itemType;

    /**
     * 相似度分数（0-1之间）
     */
    private Double similarity;

    /**
     * 最后更新时间
     */
    private LocalDateTime updateTime;
}
