package com.hmdp.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.SuggestMode;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.search.entity.ShopDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商铺搜索服务
 * 支持分词搜索、拼音搜索、高亮显示、GEO地理位置查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopSearchService {

    private final ElasticsearchClient esClient;

    /**
     * 搜索商铺（支持分词、拼音、高亮）
     *
     * @param keyword   搜索关键词
     * @param typeId    商铺类型过滤
     * @param area      商圈过滤
     * @param sortBy    排序方式: default(默认)/score(评分)/sold(销量)/price(价格)
     * @param current   当前页
     * @param size      每页大小
     */
    public Result searchShops(String keyword, Long typeId, String area,
                              String sortBy, Integer current, Integer size) {
        try {
            int from = (current - 1) * size;

            // 构建查询
            SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                    .index("shop_index")
                    .from(from)
                    .size(size);

            // 构建bool查询
            searchBuilder.query(q -> q.bool(bool -> {
                // 必须匹配关键词（多字段匹配）
                if (keyword != null && !keyword.isEmpty()) {
                    bool.must(m -> m.multiMatch(mm -> mm
                            .query(keyword)
                            .fields("name^3", "name.pinyin^2", "area", "address")
                    ));
                }

                // 过滤条件
                if (typeId != null) {
                    bool.filter(f -> f.term(t -> t.field("typeId").value(typeId)));
                }
                if (area != null && !area.isEmpty()) {
                    bool.filter(f -> f.match(m -> m.field("area").query(area)));
                }

                return bool;
            }));

            // 高亮配置
            if (keyword != null && !keyword.isEmpty()) {
                searchBuilder.highlight(h -> h
                        .fields("name", HighlightField.of(hf -> hf
                                .preTags("<em class='highlight'>")
                                .postTags("</em>")
                                .requireFieldMatch(false)
                        ))
                        .fields("area", HighlightField.of(hf -> hf
                                .preTags("<em class='highlight'>")
                                .postTags("</em>")
                        ))
                );
            }

            // 排序
            switch (sortBy) {
                case "score" -> searchBuilder.sort(s -> s.field(f -> f.field("score").order(SortOrder.Desc)));
                case "sold" -> searchBuilder.sort(s -> s.field(f -> f.field("sold").order(SortOrder.Desc)));
                case "price" -> searchBuilder.sort(s -> s.field(f -> f.field("avgPrice").order(SortOrder.Asc)));
                default -> searchBuilder.sort(s -> s.field(f -> f.field("_score").order(SortOrder.Desc)));
            }

            SearchResponse<ShopDoc> response = esClient.search(searchBuilder.build(), ShopDoc.class);

            // 解析结果
            List<ShopDoc> shops = new ArrayList<>();
            long total = response.hits().total() != null ? response.hits().total().value() : 0;

            for (Hit<ShopDoc> hit : response.hits().hits()) {
                ShopDoc shop = hit.source();
                if (shop != null) {
                    // 处理高亮
                    if (hit.highlight() != null && !hit.highlight().isEmpty()) {
                        List<String> nameHighlights = hit.highlight().get("name");
                        if (nameHighlights != null && !nameHighlights.isEmpty()) {
                            shop.setName(nameHighlights.get(0));
                        }
                    }
                    shops.add(shop);
                }
            }

            return Result.ok(shops, total);

        } catch (IOException e) {
            log.error("搜索商铺失败", e);
            return Result.fail("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 搜索附近商铺（GEO查询 + 关键词）
     *
     * @param keyword   搜索关键词（可选）
     * @param x         经度
     * @param y         纬度
     * @param distance  距离（米）
     * @param current   当前页
     * @param size      每页大小
     */
    public Result searchNearbyShops(String keyword, Double x, Double y,
                                    Integer distance, Integer current, Integer size) {
        try {
            int from = (current - 1) * size;

            SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                    .index("shop_index")
                    .from(from)
                    .size(size);

            // 构建bool查询
            searchBuilder.query(q -> q.bool(bool -> {
                // GEO距离过滤
                bool.filter(f -> f.geoDistance(gd -> gd
                        .field("location")
                        .distance(distance + "m")
                        .location(loc -> loc.latlon(latlon -> latlon.lat(y).lon(x)))
                ));

                // 关键词匹配（可选）
                if (keyword != null && !keyword.isEmpty()) {
                    bool.must(m -> m.multiMatch(mm -> mm
                            .query(keyword)
                            .fields("name^3", "name.pinyin^2", "area", "address")
                    ));
                }

                return bool;
            }));

            // 按距离排序
            searchBuilder.sort(s -> s.geoDistance(gd -> gd
                    .field("location")
                    .location(loc -> loc.latlon(latlon -> latlon.lat(y).lon(x)))
                    .order(SortOrder.Asc)
                    .unit(co.elastic.clients.elasticsearch._types.DistanceUnit.Meters)
            ));

            SearchResponse<ShopDoc> response = esClient.search(searchBuilder.build(), ShopDoc.class);

            List<ShopDoc> shops = new ArrayList<>();
            long total = response.hits().total() != null ? response.hits().total().value() : 0;

            for (Hit<ShopDoc> hit : response.hits().hits()) {
                ShopDoc shop = hit.source();
                if (shop != null) {
                    // 设置距离
                    if (hit.sort() != null && !hit.sort().isEmpty()) {
                        shop.setDistance(hit.sort().get(0).doubleValue());
                    }
                    shops.add(shop);
                }
            }

            return Result.ok(shops, total);

        } catch (IOException e) {
            log.error("搜索附近商铺失败", e);
            return Result.fail("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 搜索建议（自动补全）
     *
     * @param prefix 输入前缀
     * @param size   返回数量
     */
    public Result getSearchSuggestions(String prefix, Integer size) {
        try {
            SearchRequest request = SearchRequest.of(s -> s
                    .index("shop_index")
                    .suggest(sug -> sug
                            .suggesters("shop_suggest", sugBuilder -> sugBuilder
                                    .prefix(prefix)
                                    .completion(c -> c
                                            .field("namePinyin")
                                            .size(size)
                                            .fuzzy(f -> f.fuzziness("AUTO"))
                                    )
                            )
                    )
            );

            SearchResponse<ShopDoc> response = esClient.search(request, ShopDoc.class);

            // 解析建议结果
            List<String> suggestions = new ArrayList<>();
            Map<String, List<Suggestion<ShopDoc>>> suggestMap = response.suggest();

            if (suggestMap != null && suggestMap.containsKey("shop_suggest")) {
                List<Suggestion<ShopDoc>> suggestionList = suggestMap.get("shop_suggest");
                for (Suggestion<ShopDoc> suggestion : suggestionList) {
                    suggestion.completion().options().forEach(option -> {
                        suggestions.add(option.text());
                    });
                }
            }

            return Result.ok(suggestions);

        } catch (IOException e) {
            log.error("获取搜索建议失败", e);
            return Result.fail("获取建议失败: " + e.getMessage());
        }
    }

    /**
     * 拼音搜索（支持首字母搜索）
     *
     * @param pinyin 拼音或首字母
     * @param current 当前页
     * @param size    每页大小
     */
    public Result searchByPinyin(String pinyin, Integer current, Integer size) {
        try {
            int from = (current - 1) * size;

            SearchRequest request = SearchRequest.of(s -> s
                    .index("shop_index")
                    .from(from)
                    .size(size)
                    .query(q -> q.bool(bool -> {
                        // 首字母精确匹配
                        bool.should(sq -> sq.term(t -> t.field("nameInitial").value(pinyin)));
                        // 拼音模糊匹配
                        bool.should(sq -> sq.match(m -> m.field("name.pinyin").query(pinyin)));
                        return bool.minimumShouldMatch("1");
                    }))
            );

            SearchResponse<ShopDoc> response = esClient.search(request, ShopDoc.class);

            List<ShopDoc> shops = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            long total = response.hits().total() != null ? response.hits().total().value() : 0;

            return Result.ok(shops, total);

        } catch (IOException e) {
            log.error("拼音搜索失败", e);
            return Result.fail("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 获取热门搜索词（基于搜索日志统计）
     * 这里简化实现，实际应该用聚合查询或日志分析
     */
    public Result getHotSearchKeywords() {
        // 模拟热门搜索词
        List<Map<String, Object>> hotKeywords = Arrays.asList(
                Map.of("keyword", "火锅", "count", 1250),
                Map.of("keyword", "烧烤", "count", 980),
                Map.of("keyword", "日料", "count", 850),
                Map.of("keyword", "奶茶店", "count", 720),
                Map.of("keyword", "咖啡店", "count", 680)
        );
        return Result.ok(hotKeywords);
    }
}
