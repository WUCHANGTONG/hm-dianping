package com.hmdp.search.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Elasticsearch商铺文档
 * 对应索引: shop_index
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopDoc {

    /**
     * 商铺ID
     */
    private Long id;

    /**
     * 商铺名称（支持分词）
     */
    private String name;

    /**
     * 商铺名称拼音（用于拼音搜索）
     */
    private String namePinyin;

    /**
     * 商铺名称拼音首字母
     */
    private String nameInitial;

    /**
     * 商铺类型ID
     */
    private Long typeId;

    /**
     * 商铺类型名称
     */
    private String typeName;

    /**
     * 商铺图片，多个以逗号分隔
     */
    private String images;

    /**
     * 商圈
     */
    private String area;

    /**
     * 详细地址
     */
    private String address;

    /**
     * 经度
     */
    private Double x;

    /**
     * 纬度
     */
    private Double y;

    /**
     * 地理位置（用于GEO查询）
     * 格式: "lat,lon"
     */
    private String location;

    /**
     * 均价
     */
    private Long avgPrice;

    /**
     * 销量
     */
    private Integer sold;

    /**
     * 评论数量
     */
    private Integer comments;

    /**
     * 评分（1-5分，乘以10存储）
     */
    private Integer score;

    /**
     * 营业时间
     */
    private String openHours;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 搜索建议字段（用于自动补全）
     */
    private String suggest;

    /**
     * 距离（非索引字段，查询时临时设置）
     */
    private Double distance;
}
