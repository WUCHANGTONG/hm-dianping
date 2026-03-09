package com.hmdp.search.recommend.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.hmdp.dto.Result;
import com.hmdp.search.entity.BlogDoc;
import com.hmdp.search.entity.ShopDoc;
import com.hmdp.search.recommend.algorithm.ItemCollaborativeFilter;
import com.hmdp.search.recommend.entity.ItemSimilarity;
import com.hmdp.search.recommend.entity.UserBehavior;
import com.hmdp.search.recommend.entity.UserPreference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐服务
 * 提供个性化内容推荐功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {

    private final ElasticsearchClient esClient;
    private final StringRedisTemplate redisTemplate;
    private final ItemCollaborativeFilter itemCF;

    private static final String USER_BEHAVIOR_KEY = "recommend:behavior:";
    private static final String USER_PREFERENCE_KEY = "recommend:pref:";
    private static final String ITEM_SIMILARITY_KEY = "recommend:similarity:";

    /**
     * 记录用户行为
     *
     * @param userId    用户ID
     * @param itemId    物品ID
     * @param itemType  物品类型
     * @param behaviorType 行为类型
     */
    public void recordBehavior(Long userId, Long itemId, String itemType, String behaviorType) {
        String key = USER_BEHAVIOR_KEY + userId + ":" + itemType;
        String field = itemId.toString();

        // 获取行为权重
        double weight = UserBehavior.getBehaviorWeight(behaviorType);

        // 累加权重（使用Redis Hash存储）
        redisTemplate.opsForHash().increment(key, field, weight);

        // 设置过期时间（30天）
        redisTemplate.expire(key, java.time.Duration.ofDays(30));

        log.debug("记录用户行为: userId={}, itemId={}, type={}, weight={}",
                userId, itemId, behaviorType, weight);
    }

    /**
     * 获取用户对某物品的偏好分数
     */
    public Double getUserPreference(Long userId, Long itemId, String itemType) {
        String key = USER_BEHAVIOR_KEY + userId + ":" + itemType;
        Object value = redisTemplate.opsForHash().get(key, itemId.toString());
        return value != null ? Double.parseDouble(value.toString()) : 0.0;
    }

    /**
     * 获取用户的所有偏好
     */
    public List<UserPreference> getUserPreferences(Long userId, String itemType) {
        String key = USER_BEHAVIOR_KEY + userId + ":" + itemType;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        return entries.entrySet().stream()
                .map(e -> UserPreference.builder()
                        .userId(userId)
                        .itemId(Long.valueOf(e.getKey().toString()))
                        .itemType(itemType)
                        .preference(Double.parseDouble(e.getValue().toString()))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 推荐笔记（基于协同过滤）
     *
     * @param userId 用户ID
     * @param topN   推荐数量
     */
    public Result recommendBlogs(Long userId, int topN) {
        try {
            // 1. 获取用户偏好
            List<UserPreference> userPrefs = getUserPreferences(userId, "blog");

            List<Long> recommendIds;

            if (userPrefs.isEmpty()) {
                // 冷启动：返回热门笔记
                log.info("用户 {} 冷启动，返回热门笔记", userId);
                SearchRequest request = SearchRequest.of(s -> s
                        .index("blog_index")
                        .size(topN)
                        .sort(sort -> sort.field(f -> f.field("hotScore").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
                );

                SearchResponse<BlogDoc> response = esClient.search(request, BlogDoc.class);
                List<BlogDoc> blogs = response.hits().hits().stream()
                        .map(Hit::source)
                        .filter(Objects::nonNull)
                        .peek(blog -> {
                            String content = blog.getContent();
                            if (content != null && content.length() > 150) {
                                blog.setContent(content.substring(0, 150) + "...");
                            }
                        })
                        .collect(Collectors.toList());

                return Result.ok(blogs);
            }

            // 2. 获取所有用户的偏好（简化版：实际应用应该分批获取或预先计算）
            // 这里使用用户的偏好直接进行基于内容的推荐
            Set<Long> userItemIds = userPrefs.stream()
                    .map(UserPreference::getItemId)
                    .collect(Collectors.toSet());

            // 3. 基于ES的more_like_this查询推荐相似笔记
            if (userItemIds.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }

            // 取用户最近喜欢的3个笔记作为种子
            List<Long> seedIds = userPrefs.stream()
                    .sorted((a, b) -> Double.compare(b.getPreference(), a.getPreference()))
                    .limit(3)
                    .map(UserPreference::getItemId)
                    .collect(Collectors.toList());

            // 使用ES的More Like This查询
            SearchRequest request = SearchRequest.of(s -> s
                    .index("blog_index")
                    .size(topN)
                    .query(q -> q.bool(bool -> {
                        // 使用More Like This查询相似笔记
                        bool.should(should -> should
                                .moreLikeThis(mlt -> mlt
                                        .fields("title", "content", "tags")
                                        .like(l -> l
                                                .document(d -> d.index("blog_index").id(seedIds.get(0).toString()))
                                        )
                                        .minTermFreq(1)
                                        .minDocFreq(1)
                                )
                        );

                        // 排除用户已经看过的笔记
                        for (Long itemId : userItemIds) {
                            bool.mustNot(mn -> mn.term(t -> t.field("id").value(itemId)));
                        }

                        return bool;
                    }))
            );

            SearchResponse<BlogDoc> response = esClient.search(request, BlogDoc.class);
            List<BlogDoc> blogs = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .peek(blog -> {
                        String content = blog.getContent();
                        if (content != null && content.length() > 150) {
                            blog.setContent(content.substring(0, 150) + "...");
                        }
                    })
                    .collect(Collectors.toList());

            return Result.ok(blogs);

        } catch (IOException e) {
            log.error("推荐笔记失败", e);
            return Result.fail("推荐失败: " + e.getMessage());
        }
    }

    /**
     * 推荐商铺（基于协同过滤）
     *
     * @param userId 用户ID
     * @param topN   推荐数量
     */
    public Result recommendShops(Long userId, int topN) {
        try {
            // 1. 获取用户偏好
            List<UserPreference> userPrefs = getUserPreferences(userId, "shop");

            if (userPrefs.isEmpty()) {
                // 冷启动：返回热门商铺
                log.info("用户 {} 冷启动，返回热门商铺", userId);
                SearchRequest request = SearchRequest.of(s -> s
                        .index("shop_index")
                        .size(topN)
                        .sort(sort -> sort.field(f -> f.field("score").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
                );

                SearchResponse<ShopDoc> response = esClient.search(request, ShopDoc.class);
                List<ShopDoc> shops = response.hits().hits().stream()
                        .map(Hit::source)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                return Result.ok(shops);
            }

            // 2. 获取用户喜欢的商铺类型
            Set<Long> userShopIds = userPrefs.stream()
                    .map(UserPreference::getItemId)
                    .collect(Collectors.toSet());

            // 3. 先获取用户喜欢的商铺详情（获取类型信息）
            if (userShopIds.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }

            // 4. 推荐相似类型的热门商铺
            SearchRequest request = SearchRequest.of(s -> s
                    .index("shop_index")
                    .size(topN)
                    .query(q -> q.bool(bool -> {
                        // 排除用户已经浏览过的商铺
                        for (Long itemId : userShopIds) {
                            bool.mustNot(mn -> mn.term(t -> t.field("id").value(itemId)));
                        }
                        return bool;
                    }))
                    .sort(sort -> sort.field(f -> f.field("score").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
            );

            SearchResponse<ShopDoc> response = esClient.search(request, ShopDoc.class);
            List<ShopDoc> shops = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            return Result.ok(shops);

        } catch (IOException e) {
            log.error("推荐商铺失败", e);
            return Result.fail("推荐失败: " + e.getMessage());
        }
    }

    /**
     * 获取相似用户（基于用户行为的相似度）
     * 用于社交推荐或群体分析
     */
    public List<Long> findSimilarUsers(Long userId, int topN) {
        // 获取当前用户的行为
        List<UserPreference> userPrefs = getUserPreferences(userId, "blog");
        Set<Long> userItems = userPrefs.stream()
                .map(UserPreference::getItemId)
                .collect(Collectors.toSet());

        if (userItems.isEmpty()) {
            return Collections.emptyList();
        }

        // 使用Redis获取所有用户的行为（简化实现）
        // 实际应用中应该使用更高效的方式，如预先计算相似用户
        Set<String> keys = redisTemplate.keys(USER_BEHAVIOR_KEY + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Double> userSimilarities = new HashMap<>();

        for (String key : keys) {
            // 解析用户ID
            String[] parts = key.replace(USER_BEHAVIOR_KEY, "").split(":");
            if (parts.length < 1) continue;

            try {
                Long otherUserId = Long.valueOf(parts[0]);
                if (otherUserId.equals(userId)) {
                    continue;
                }

                // 获取其他用户的偏好
                Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
                Set<Long> otherItems = entries.keySet().stream()
                        .map(k -> Long.valueOf(k.toString()))
                        .collect(Collectors.toSet());

                // 计算Jaccard相似度
                Set<Long> intersection = new HashSet<>(userItems);
                intersection.retainAll(otherItems);

                Set<Long> union = new HashSet<>(userItems);
                union.addAll(otherItems);

                if (!union.isEmpty()) {
                    double similarity = (double) intersection.size() / union.size();
                    if (similarity > 0) {
                        userSimilarities.put(otherUserId, similarity);
                    }
                }

            } catch (NumberFormatException e) {
                log.warn("解析用户ID失败: {}", key);
            }
        }

        // 返回相似度最高的用户
        return userSimilarities.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 基于物品的协同过滤推荐
     * 使用预计算的物品相似度
     */
    public Result recommendBasedOnItemCF(Long userId, String itemType, int topN) {
        // 1. 获取用户历史偏好
        List<UserPreference> userPrefs = getUserPreferences(userId, itemType);

        if (userPrefs.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 2. 获取物品相似度（从Redis或内存）
        String simKey = ITEM_SIMILARITY_KEY + itemType;
        Map<Object, Object> simEntries = redisTemplate.opsForHash().entries(simKey);

        List<ItemSimilarity> similarities = simEntries.entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().toString().split(":");
                    if (parts.length == 2) {
                        return ItemSimilarity.builder()
                                .itemIdA(Long.valueOf(parts[0]))
                                .itemIdB(Long.valueOf(parts[1]))
                                .similarity(Double.parseDouble(e.getValue().toString()))
                                .build();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (similarities.isEmpty()) {
            // 如果没有预计算的相似度，使用基于内容的推荐
            return "blog".equals(itemType) ? recommendBlogs(userId, topN) : recommendShops(userId, topN);
        }

        // 3. 使用协同过滤算法生成推荐
        List<Long> recommendIds = itemCF.recommendItems(userId, userPrefs, similarities, topN);

        // 4. 从ES获取推荐物品的详情
        try {
            if ("blog".equals(itemType)) {
                return getBlogsByIds(recommendIds);
            } else {
                return getShopsByIds(recommendIds);
            }
        } catch (IOException e) {
            log.error("获取推荐物品详情失败", e);
            return Result.fail("推荐失败");
        }
    }

    private Result getBlogsByIds(List<Long> ids) throws IOException {
        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        SearchRequest request = SearchRequest.of(s -> s
                .index("blog_index")
                .query(q -> q.terms(t -> t.field("id").terms(terms -> {
                    List<FieldValue> values = ids.stream()
                            .map(id -> FieldValue.of(id))
                            .collect(Collectors.toList());
                    return terms.value(values);
                })))
                .size(ids.size())
        );

        SearchResponse<BlogDoc> response = esClient.search(request, BlogDoc.class);
        List<BlogDoc> blogs = response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return Result.ok(blogs);
    }

    private Result getShopsByIds(List<Long> ids) throws IOException {
        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        SearchRequest request = SearchRequest.of(s -> s
                .index("shop_index")
                .query(q -> q.terms(t -> t.field("id").terms(terms -> {
                    List<FieldValue> values = ids.stream()
                            .map(id -> FieldValue.of(id))
                            .collect(Collectors.toList());
                    return terms.value(values);
                })))
                .size(ids.size())
        );

        SearchResponse<ShopDoc> response = esClient.search(request, ShopDoc.class);
        List<ShopDoc> shops = response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return Result.ok(shops);
    }
}
