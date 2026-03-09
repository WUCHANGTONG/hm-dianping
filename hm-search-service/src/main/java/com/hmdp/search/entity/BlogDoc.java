package com.hmdp.search.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Elasticsearch笔记文档
 * 对应索引: blog_index
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogDoc {

    /**
     * 笔记ID
     */
    private Long id;

    /**
     * 商户ID
     */
    private Long shopId;

    /**
     * 商户名称（用于展示）
     */
    private String shopName;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userIcon;

    /**
     * 笔记标题（支持分词）
     */
    private String title;

    /**
     * 标题拼音（用于拼音搜索）
     */
    private String titlePinyin;

    /**
     * 标题拼音首字母
     */
    private String titleInitial;

    /**
     * 笔记内容（支持分词）
     */
    private String content;

    /**
     * 图片列表，多个以逗号分隔
     */
    private String images;

    /**
     * 点赞数量
     */
    private Integer liked;

    /**
     * 评论数量
     */
    private Integer comments;

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
     * 标签列表（用于分类和过滤）
     */
    private List<String> tags;

    /**
     * 是否为热门笔记（用于排序加权）
     */
    private Boolean isHot;

    /**
     * 热度评分（综合考虑点赞、评论、时间）
     */
    private Double hotScore;
}
