package com.hmdp.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import com.hmdp.dto.Result;
import com.hmdp.search.entity.BlogDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 笔记搜索服务
 * 支持全文检索、高亮显示、热门笔记推荐
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogSearchService {

    private final ElasticsearchClient esClient;

    /**
     * 搜索笔记（全文检索）
     *
     * @param keyword  搜索关键词（标题+内容）
     * @param shopId   商铺ID过滤
     * @param userId   用户ID过滤
     * @param sortBy   排序方式: default(默认)/hot(热度)/time(时间)/like(点赞)
     * @param current  当前页
     * @param size     每页大小
     */
    public Result searchBlogs(String keyword, Long shopId, Long userId,
                              String sortBy, Integer current, Integer size) {
        try {
            int from = (current - 1) * size;

            SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                    .index("blog_index")
                    .from(from)
                    .size(size);

            // 构建bool查询
            searchBuilder.query(q -> q.bool(bool -> {
                // 全文搜索（标题+内容）
                if (keyword != null && !keyword.isEmpty()) {
                    bool.must(m -> m.multiMatch(mm -> mm
                            .query(keyword)
                            .fields("title^3", "title.pinyin^2", "content", "shopName")
                    ));
                }

                // 过滤条件
                if (shopId != null) {
                    bool.filter(f -> f.term(t -> t.field("shopId").value(shopId)));
                }
                if (userId != null) {
                    bool.filter(f -> f.term(t -> t.field("userId").value(userId)));
                }

                return bool;
            }));

            // 高亮配置
            if (keyword != null && !keyword.isEmpty()) {
                searchBuilder.highlight(h -> h
                        .fields("title", HighlightField.of(hf -> hf
                                .preTags("<em class='highlight'>")
                                .postTags("</em>")
                                .requireFieldMatch(false)
                        ))
                        .fields("content", HighlightField.of(hf -> hf
                                .preTags("<em class='highlight'>")
                                .postTags("</em>")
                                .fragmentSize(150)
                                .numberOfFragments(2)
                        ))
                );
            }

            // 排序
            switch (sortBy) {
                case "hot" -> searchBuilder.sort(s -> s.field(f -> f.field("hotScore").order(SortOrder.Desc)));
                case "time" -> searchBuilder.sort(s -> s.field(f -> f.field("createTime").order(SortOrder.Desc)));
                case "like" -> searchBuilder.sort(s -> s.field(f -> f.field("liked").order(SortOrder.Desc)));
                default -> searchBuilder.sort(s -> s.field(f -> f.field("_score").order(SortOrder.Desc)));
            }

            SearchResponse<BlogDoc> response = esClient.search(searchBuilder.build(), BlogDoc.class);

            // 解析结果
            List<BlogDoc> blogs = new ArrayList<>();
            long total = response.hits().total() != null ? response.hits().total().value() : 0;

            for (Hit<BlogDoc> hit : response.hits().hits()) {
                BlogDoc blog = hit.source();
                if (blog != null) {
                    // 处理高亮
                    if (hit.highlight() != null && !hit.highlight().isEmpty()) {
                        List<String> titleHighlights = hit.highlight().get("title");
                        if (titleHighlights != null && !titleHighlights.isEmpty()) {
                            blog.setTitle(titleHighlights.get(0));
                        }
                        // 内容摘要
                        List<String> contentHighlights = hit.highlight().get("content");
                        if (contentHighlights != null && !contentHighlights.isEmpty()) {
                            blog.setContent(contentHighlights.get(0));
                        } else {
                            // 如果没有匹配到高亮，截取前150字符
                            String content = blog.getContent();
                            if (content != null && content.length() > 150) {
                                blog.setContent(content.substring(0, 150) + "...");
                            }
                        }
                    } else {
                        // 无高亮时截取内容
                        String content = blog.getContent();
                        if (content != null && content.length() > 150) {
                            blog.setContent(content.substring(0, 150) + "...");
                        }
                    }
                    blogs.add(blog);
                }
            }

            return Result.ok(blogs, total);

        } catch (IOException e) {
            log.error("搜索笔记失败", e);
            return Result.fail("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 获取热门笔记
     *
     * @param size 返回数量
     */
    public Result getHotBlogs(Integer size) {
        try {
            SearchRequest request = SearchRequest.of(s -> s
                    .index("blog_index")
                    .size(size)
                    .query(q -> q.bool(bool -> {
                        bool.filter(f -> f.term(t -> t.field("isHot").value(true)));
                        return bool;
                    }))
                    .sort(sort -> sort.field(f -> f.field("hotScore").order(SortOrder.Desc)))
            );

            SearchResponse<BlogDoc> response = esClient.search(request, BlogDoc.class);

            List<BlogDoc> blogs = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .peek(blog -> {
                        // 截取内容摘要
                        String content = blog.getContent();
                        if (content != null && content.length() > 150) {
                            blog.setContent(content.substring(0, 150) + "...");
                        }
                    })
                    .collect(Collectors.toList());

            return Result.ok(blogs);

        } catch (IOException e) {
            log.error("获取热门笔记失败", e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 笔记搜索建议
     *
     * @param prefix 输入前缀
     * @param size   返回数量
     */
    public Result getBlogSearchSuggestions(String prefix, Integer size) {
        try {
            SearchRequest request = SearchRequest.of(s -> s
                    .index("blog_index")
                    .suggest(sug -> sug
                            .suggesters("blog_suggest", sugBuilder -> sugBuilder
                                    .prefix(prefix)
                                    .completion(c -> c
                                            .field("titlePinyin")
                                            .size(size)
                                            .fuzzy(f -> f.fuzziness("AUTO"))
                                    )
                            )
                    )
            );

            SearchResponse<BlogDoc> response = esClient.search(request, BlogDoc.class);

            List<String> suggestions = new ArrayList<>();
            Map<String, List<Suggestion<BlogDoc>>> suggestMap = response.suggest();

            if (suggestMap != null && suggestMap.containsKey("blog_suggest")) {
                List<Suggestion<BlogDoc>> suggestionList = suggestMap.get("blog_suggest");
                for (Suggestion<BlogDoc> suggestion : suggestionList) {
                    suggestion.completion().options().forEach(option -> {
                        suggestions.add(option.text());
                    });
                }
            }

            return Result.ok(suggestions);

        } catch (IOException e) {
            log.error("获取笔记搜索建议失败", e);
            return Result.fail("获取建议失败: " + e.getMessage());
        }
    }

    /**
     * 获取相关笔记推荐
     *
     * @param blogId 当前笔记ID
     * @param size   返回数量
     */
    public Result getRelatedBlogs(Long blogId, Integer size) {
        try {
            // 先获取当前笔记的信息
            SearchResponse<BlogDoc> currentResponse = esClient.search(s -> s
                    .index("blog_index")
                    .query(q -> q.term(t -> t.field("id").value(blogId)))
                    .size(1),
                    BlogDoc.class
            );

            if (currentResponse.hits().hits().isEmpty()) {
                return Result.fail("笔记不存在");
            }

            BlogDoc currentBlog = currentResponse.hits().hits().get(0).source();
            if (currentBlog == null) {
                return Result.fail("笔记不存在");
            }

            // 使用more like this查询相似笔记
            SearchRequest request = SearchRequest.of(s -> s
                    .index("blog_index")
                    .size(size)
                    .query(q -> q.moreLikeThis(mlt -> mlt
                            .fields("title", "content", "tags")
                            .like(l -> l
                                    .document(d -> d
                                            .index("blog_index")
                                            .id(String.valueOf(blogId))
                                    )
                            )
                            .minTermFreq(1)
                            .minDocFreq(1)
                    ))
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
            log.error("获取相关笔记失败", e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 搜索特定用户的笔记
     */
    public Result searchUserBlogs(Long userId, Integer current, Integer size) {
        return searchBlogs(null, null, userId, "time", current, size);
    }

    /**
     * 搜索特定商铺的笔记
     */
    public Result searchShopBlogs(Long shopId, Integer current, Integer size) {
        return searchBlogs(null, shopId, null, "hot", current, size);
    }
}
