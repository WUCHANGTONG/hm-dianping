package com.hmdp.search.recommend.algorithm;

import com.hmdp.search.recommend.entity.ItemSimilarity;
import com.hmdp.search.recommend.entity.UserPreference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于物品的协同过滤算法（ItemCF）
 *
 * 算法原理：
 * 1. 计算物品之间的相似度（基于用户对物品的偏好）
 * 2. 根据用户历史喜欢的物品，找到相似物品推荐
 *
 * 相似度计算：余弦相似度
 * similarity(i,j) = N(i)∩N(j) / sqrt(N(i) * N(j))
 * 其中N(i)表示喜欢物品i的用户集合
 */
@Slf4j
@Component
public class ItemCollaborativeFilter {

    /**
     * 计算物品相似度矩阵
     *
     * @param userPreferences 用户偏好列表
     * @return 物品相似度列表
     */
    public List<ItemSimilarity> calculateItemSimilarity(List<UserPreference> userPreferences) {
        log.info("开始计算物品相似度矩阵，用户偏好记录数: {}", userPreferences.size());

        // 1. 构建用户-物品偏好矩阵
        // Map<userId, Map<itemId, preference>>
        Map<Long, Map<Long, Double>> userItemPrefs = new HashMap<>();
        for (UserPreference pref : userPreferences) {
            userItemPrefs
                    .computeIfAbsent(pref.getUserId(), k -> new HashMap<>())
                    .put(pref.getItemId(), pref.getPreference());
        }

        // 2. 构建物品-用户倒排表
        // Map<itemId, Set<userId>>
        Map<Long, Set<Long>> itemUsers = new HashMap<>();
        for (UserPreference pref : userPreferences) {
            itemUsers
                    .computeIfAbsent(pref.getItemId(), k -> new HashSet<>())
                    .add(pref.getUserId());
        }

        // 3. 计算物品相似度
        List<ItemSimilarity> similarities = new ArrayList<>();
        List<Long> itemIds = new ArrayList<>(itemUsers.keySet());

        for (int i = 0; i < itemIds.size(); i++) {
            Long itemIdA = itemIds.get(i);
            Set<Long> usersA = itemUsers.get(itemIdA);

            for (int j = i + 1; j < itemIds.size(); j++) {
                Long itemIdB = itemIds.get(j);
                Set<Long> usersB = itemUsers.get(itemIdB);

                // 计算共同用户数
                Set<Long> commonUsers = new HashSet<>(usersA);
                commonUsers.retainAll(usersB);

                if (commonUsers.isEmpty()) {
                    continue;
                }

                // 计算余弦相似度
                double similarity = calculateCosineSimilarity(
                        itemIdA, itemIdB, commonUsers, userItemPrefs);

                if (similarity > 0) {
                    // 添加两个方向的相似度（对称矩阵）
                    similarities.add(ItemSimilarity.builder()
                            .itemIdA(itemIdA)
                            .itemIdB(itemIdB)
                            .similarity(similarity)
                            .build());

                    similarities.add(ItemSimilarity.builder()
                            .itemIdA(itemIdB)
                            .itemIdB(itemIdA)
                            .similarity(similarity)
                            .build());
                }
            }
        }

        log.info("物品相似度计算完成，共 {} 对相似物品", similarities.size() / 2);
        return similarities;
    }

    /**
     * 计算余弦相似度
     *
     * @param itemIdA      物品A
     * @param itemIdB      物品B
     * @param commonUsers  共同用户
     * @param userItemPrefs 用户-物品偏好矩阵
     * @return 相似度分数
     */
    private double calculateCosineSimilarity(Long itemIdA, Long itemIdB,
                                             Set<Long> commonUsers,
                                             Map<Long, Map<Long, Double>> userItemPrefs) {
        double sumProduct = 0.0;
        double sumSquareA = 0.0;
        double sumSquareB = 0.0;

        for (Long userId : commonUsers) {
            double prefA = userItemPrefs.get(userId).getOrDefault(itemIdA, 0.0);
            double prefB = userItemPrefs.get(userId).getOrDefault(itemIdB, 0.0);

            sumProduct += prefA * prefB;
        }

        // 计算所有用户对物品A的偏好平方和
        for (Map<Long, Double> userPrefs : userItemPrefs.values()) {
            sumSquareA += Math.pow(userPrefs.getOrDefault(itemIdA, 0.0), 2);
        }

        // 计算所有用户对物品B的偏好平方和
        for (Map<Long, Double> userPrefs : userItemPrefs.values()) {
            sumSquareB += Math.pow(userPrefs.getOrDefault(itemIdB, 0.0), 2);
        }

        // 余弦相似度
        double denominator = Math.sqrt(sumSquareA) * Math.sqrt(sumSquareB);
        if (denominator == 0) {
            return 0.0;
        }

        return sumProduct / denominator;
    }

    /**
     * 为用户推荐物品
     *
     * @param userId              用户ID
     * @param userPreferences     所有用户偏好
     * @param itemSimilarities    物品相似度矩阵
     * @param topN                推荐数量
     * @return 推荐物品ID列表（按分数排序）
     */
    public List<Long> recommendItems(Long userId,
                                     List<UserPreference> userPreferences,
                                     List<ItemSimilarity> itemSimilarities,
                                     int topN) {
        log.info("为用户 {} 生成推荐", userId);

        // 1. 获取用户的历史偏好物品
        Set<Long> userItems = userPreferences.stream()
                .filter(p -> p.getUserId().equals(userId))
                .map(UserPreference::getItemId)
                .collect(Collectors.toSet());

        if (userItems.isEmpty()) {
            log.info("用户 {} 没有历史行为，无法推荐", userId);
            return Collections.emptyList();
        }

        // 2. 构建物品相似度Map
        Map<Long, List<ItemSimilarity>> itemSimilarityMap = itemSimilarities.stream()
                .collect(Collectors.groupingBy(ItemSimilarity::getItemIdA));

        // 3. 计算用户对候选物品的偏好分数
        // Map<itemId, score>
        Map<Long, Double> candidateScores = new HashMap<>();

        for (Long userItem : userItems) {
            // 获取与该物品相似的物品
            List<ItemSimilarity> similarItems = itemSimilarityMap.getOrDefault(userItem, Collections.emptyList());

            for (ItemSimilarity sim : similarItems) {
                // 跳过用户已经喜欢的物品
                if (userItems.contains(sim.getItemIdB())) {
                    continue;
                }

                // 累加分数：相似度 * 用户对历史物品的偏好（简化处理，假设偏好为1）
                candidateScores.merge(sim.getItemIdB(), sim.getSimilarity(), Double::sum);
            }
        }

        // 4. 按分数排序，返回TopN
        return candidateScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 基于内容的推荐（冷启动策略）
     * 当用户没有历史行为时，推荐热门内容
     */
    public List<Long> recommendPopularItems(List<UserPreference> allPreferences, int topN) {
        // 按物品被喜欢的次数排序
        Map<Long, Long> itemPopularity = allPreferences.stream()
                .collect(Collectors.groupingBy(UserPreference::getItemId, Collectors.counting()));

        return itemPopularity.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
