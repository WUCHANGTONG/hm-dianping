package com.hmdp.search.controller;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.search.recommend.service.RecommendService;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 推荐服务Controller
 * 提供个性化推荐接口
 */
@RestController
@RequestMapping("/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    /**
     * 推荐笔记（个性化Feed）
     *
     * @param size 推荐数量
     */
    @GetMapping("/blog")
    public Result recommendBlogs(
            @RequestParam(value = "size", defaultValue = "10") int size) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        return recommendService.recommendBlogs(user.getId(), size);
    }

    /**
     * 推荐商铺
     *
     * @param size 推荐数量
     */
    @GetMapping("/shop")
    public Result recommendShops(
            @RequestParam(value = "size", defaultValue = "10") int size) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        return recommendService.recommendShops(user.getId(), size);
    }

    /**
     * 记录用户行为（用于优化推荐）
     *
     * @param itemId       物品ID
     * @param itemType     物品类型（blog/shop）
     * @param behaviorType 行为类型（view/like/collect）
     */
    @PostMapping("/behavior")
    public Result recordBehavior(
            @RequestParam("itemId") Long itemId,
            @RequestParam("itemType") String itemType,
            @RequestParam("behaviorType") String behaviorType) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        recommendService.recordBehavior(user.getId(), itemId, itemType, behaviorType);
        return Result.ok();
    }

    /**
     * 基于协同过滤的推荐（使用预计算相似度）
     *
     * @param itemType 物品类型
     * @param size     推荐数量
     */
    @GetMapping("/itemcf/{itemType}")
    public Result recommendByItemCF(
            @PathVariable("itemType") String itemType,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        return recommendService.recommendBasedOnItemCF(user.getId(), itemType, size);
    }

    /**
     * 发现相似用户（用于社交推荐）
     *
     * @param size 返回数量
     */
    @GetMapping("/similar-users")
    public Result findSimilarUsers(
            @RequestParam(value = "size", defaultValue = "10") int size) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(recommendService.findSimilarUsers(user.getId(), size));
    }
}
