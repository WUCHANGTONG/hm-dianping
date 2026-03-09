package com.hmdp.search.controller;

import com.hmdp.dto.Result;
import com.hmdp.search.service.BlogSearchService;
import com.hmdp.search.service.ShopSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 搜索服务Controller
 * 提供商铺和笔记的搜索接口
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final ShopSearchService shopSearchService;
    private final BlogSearchService blogSearchService;

    // ==================== 商铺搜索接口 ====================

    /**
     * 搜索商铺
     *
     * @param keyword  搜索关键词
     * @param typeId   商铺类型过滤
     * @param area     商圈过滤
     * @param sortBy   排序方式: default/score/sold/price
     * @param current  当前页
     * @param size     每页大小
     */
    @GetMapping("/shop")
    public Result searchShops(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "typeId", required = false) Long typeId,
            @RequestParam(value = "area", required = false) String area,
            @RequestParam(value = "sortBy", defaultValue = "default") String sortBy,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return shopSearchService.searchShops(keyword, typeId, area, sortBy, current, size);
    }

    /**
     * 搜索附近商铺
     *
     * @param keyword  搜索关键词（可选）
     * @param x        经度
     * @param y        纬度
     * @param distance 搜索半径（米），默认5000
     * @param current  当前页
     * @param size     每页大小
     */
    @GetMapping("/shop/nearby")
    public Result searchNearbyShops(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam("x") Double x,
            @RequestParam("y") Double y,
            @RequestParam(value = "distance", defaultValue = "5000") Integer distance,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return shopSearchService.searchNearbyShops(keyword, x, y, distance, current, size);
    }

    /**
     * 拼音搜索商铺（支持首字母搜索）
     *
     * @param pinyin  拼音或首字母
     * @param current 当前页
     * @param size    每页大小
     */
    @GetMapping("/shop/pinyin")
    public Result searchShopsByPinyin(
            @RequestParam("pinyin") String pinyin,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return shopSearchService.searchByPinyin(pinyin, current, size);
    }

    /**
     * 获取商铺搜索建议
     *
     * @param prefix 输入前缀
     * @param size   返回数量
     */
    @GetMapping("/shop/suggest")
    public Result getShopSuggestions(
            @RequestParam("prefix") String prefix,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return shopSearchService.getSearchSuggestions(prefix, size);
    }

    /**
     * 获取热门搜索词
     */
    @GetMapping("/shop/hot-keywords")
    public Result getHotKeywords() {
        return shopSearchService.getHotSearchKeywords();
    }

    // ==================== 笔记搜索接口 ====================

    /**
     * 搜索笔记
     *
     * @param keyword 搜索关键词
     * @param shopId  商铺ID过滤
     * @param userId  用户ID过滤
     * @param sortBy  排序方式: default/hot/time/like
     * @param current 当前页
     * @param size    每页大小
     */
    @GetMapping("/blog")
    public Result searchBlogs(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "shopId", required = false) Long shopId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "sortBy", defaultValue = "default") String sortBy,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return blogSearchService.searchBlogs(keyword, shopId, userId, sortBy, current, size);
    }

    /**
     * 获取热门笔记
     *
     * @param size 返回数量
     */
    @GetMapping("/blog/hot")
    public Result getHotBlogs(
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return blogSearchService.getHotBlogs(size);
    }

    /**
     * 获取笔记搜索建议
     *
     * @param prefix 输入前缀
     * @param size   返回数量
     */
    @GetMapping("/blog/suggest")
    public Result getBlogSuggestions(
            @RequestParam("prefix") String prefix,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return blogSearchService.getBlogSearchSuggestions(prefix, size);
    }

    /**
     * 获取相关笔记推荐
     *
     * @param blogId 当前笔记ID
     * @param size   返回数量
     */
    @GetMapping("/blog/{id}/related")
    public Result getRelatedBlogs(
            @PathVariable("id") Long blogId,
            @RequestParam(value = "size", defaultValue = "5") Integer size) {
        return blogSearchService.getRelatedBlogs(blogId, size);
    }

    /**
     * 搜索用户笔记
     *
     * @param userId  用户ID
     * @param current 当前页
     * @param size    每页大小
     */
    @GetMapping("/blog/user/{userId}")
    public Result searchUserBlogs(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return blogSearchService.searchUserBlogs(userId, current, size);
    }

    /**
     * 搜索商铺笔记
     *
     * @param shopId  商铺ID
     * @param current 当前页
     * @param size    每页大小
     */
    @GetMapping("/blog/shop/{shopId}")
    public Result searchShopBlogs(
            @PathVariable("shopId") Long shopId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return blogSearchService.searchShopBlogs(shopId, current, size);
    }
}
