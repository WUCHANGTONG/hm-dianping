package com.hmdp.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.json.jackson.JacksonJsonpGenerator;
import com.hmdp.search.entity.BlogDoc;
import com.hmdp.search.entity.ShopDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

/**
 * Elasticsearch索引服务
 * 负责索引的创建、删除、数据增删改查
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexService {

    private final ElasticsearchClient esClient;

    // ==================== 商铺索引操作 ====================

    /**
     * 创建商铺索引
     */
    public void createShopIndex() throws IOException {
        String indexName = "shop_index";

        // 检查索引是否存在
        boolean exists = esClient.indices().exists(
                ExistsRequest.of(e -> e.index(indexName))
        ).value();

        if (exists) {
            log.info("商铺索引已存在，跳过创建");
            return;
        }

        // 创建索引并配置mapping
        CreateIndexRequest createIndexRequest = CreateIndexRequest.of(builder -> builder
                .index(indexName)
                .settings(settings -> settings
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                        .analysis(analysis -> analysis
                                .analyzer("ik_smart", a -> a
                                        .custom(ca -> ca.tokenizer("ik_smart"))
                                )
                                .analyzer("ik_max_word", a -> a
                                        .custom(ca -> ca.tokenizer("ik_max_word"))
                                )
                        )
                )
                .mappings(mappings -> mappings
                        .properties("id", p -> p.long_(l -> l))
                        .properties("name", p -> p.text(t -> t
                                .analyzer("ik_max_word")
                                .searchAnalyzer("ik_smart")
                                .fields("pinyin", f -> f
                                        .text(text -> text
                                                .analyzer("ik_max_word_pinyin")
                                                .searchAnalyzer("ik_smart_pinyin")
                                        )
                                )
                        ))
                        .properties("namePinyin", p -> p.completion(c -> c))
                        .properties("nameInitial", p -> p.keyword(k -> k))
                        .properties("typeId", p -> p.long_(l -> l))
                        .properties("typeName", p -> p.keyword(k -> k))
                        .properties("images", p -> p.keyword(k -> k))
                        .properties("area", p -> p.text(t -> t.analyzer("ik_max_word")))
                        .properties("address", p -> p.text(t -> t.analyzer("ik_max_word")))
                        .properties("x", p -> p.double_(d -> d))
                        .properties("y", p -> p.double_(d -> d))
                        .properties("location", p -> p.geoPoint(g -> g))
                        .properties("avgPrice", p -> p.long_(l -> l))
                        .properties("sold", p -> p.integer(i -> i))
                        .properties("comments", p -> p.integer(i -> i))
                        .properties("score", p -> p.integer(i -> i))
                        .properties("openHours", p -> p.keyword(k -> k))
                        .properties("createTime", p -> p.date(d -> d.format("strict_date_optional_time||epoch_millis")))
                        .properties("updateTime", p -> p.date(d -> d.format("strict_date_optional_time||epoch_millis")))
                        .properties("suggest", p -> p.completion(c -> c))
                )
        );

        CreateIndexResponse response = esClient.indices().create(createIndexRequest);
        if (response.acknowledged()) {
            log.info("商铺索引创建成功");
        } else {
            log.error("商铺索引创建失败");
        }
    }

    /**
     * 删除商铺索引
     */
    public void deleteShopIndex() throws IOException {
        DeleteIndexRequest request = DeleteIndexRequest.of(d -> d.index("shop_index"));
        esClient.indices().delete(request);
        log.info("商铺索引已删除");
    }

    /**
     * 添加/更新商铺文档
     */
    public void saveShopDocument(ShopDoc shopDoc) throws IOException {
        IndexRequest<ShopDoc> request = IndexRequest.of(i -> i
                .index("shop_index")
                .id(String.valueOf(shopDoc.getId()))
                .document(shopDoc)
        );
        esClient.index(request);
        log.debug("商铺文档已保存: {}", shopDoc.getId());
    }

    /**
     * 删除商铺文档
     */
    public void deleteShopDocument(Long id) throws IOException {
        esClient.delete(d -> d.index("shop_index").id(String.valueOf(id)));
        log.debug("商铺文档已删除: {}", id);
    }

    /**
     * 批量保存商铺文档
     */
    public void batchSaveShopDocuments(List<ShopDoc> shopDocs) throws IOException {
        if (shopDocs == null || shopDocs.isEmpty()) {
            return;
        }

        List<BulkOperation> operations = shopDocs.stream()
                .map(shop -> BulkOperation.of(b -> b
                        .index(IndexOperation.of(i -> i
                                .index("shop_index")
                                .id(String.valueOf(shop.getId()))
                                .document(shop)
                        ))
                ))
                .toList();

        esClient.bulk(b -> b.operations(operations));
        log.info("批量保存商铺文档: {} 条", shopDocs.size());
    }

    // ==================== 笔记索引操作 ====================

    /**
     * 创建笔记索引
     */
    public void createBlogIndex() throws IOException {
        String indexName = "blog_index";

        // 检查索引是否存在
        boolean exists = esClient.indices().exists(
                ExistsRequest.of(e -> e.index(indexName))
        ).value();

        if (exists) {
            log.info("笔记索引已存在，跳过创建");
            return;
        }

        // 创建索引并配置mapping
        CreateIndexRequest createIndexRequest = CreateIndexRequest.of(builder -> builder
                .index(indexName)
                .settings(settings -> settings
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                        .analysis(analysis -> analysis
                                .analyzer("ik_smart", a -> a
                                        .custom(ca -> ca.tokenizer("ik_smart"))
                                )
                                .analyzer("ik_max_word", a -> a
                                        .custom(ca -> ca.tokenizer("ik_max_word"))
                                )
                        )
                )
                .mappings(mappings -> mappings
                        .properties("id", p -> p.long_(l -> l))
                        .properties("shopId", p -> p.long_(l -> l))
                        .properties("shopName", p -> p.keyword(k -> k))
                        .properties("userId", p -> p.long_(l -> l))
                        .properties("userName", p -> p.keyword(k -> k))
                        .properties("userIcon", p -> p.keyword(k -> k))
                        .properties("title", p -> p.text(t -> t
                                .analyzer("ik_max_word")
                                .searchAnalyzer("ik_smart")
                                .fields("pinyin", f -> f
                                        .text(text -> text
                                                .analyzer("ik_max_word_pinyin")
                                                .searchAnalyzer("ik_smart_pinyin")
                                        )
                                )
                        ))
                        .properties("titlePinyin", p -> p.completion(c -> c))
                        .properties("titleInitial", p -> p.keyword(k -> k))
                        .properties("content", p -> p.text(t -> t
                                .analyzer("ik_max_word")
                                .searchAnalyzer("ik_smart")
                        ))
                        .properties("images", p -> p.keyword(k -> k))
                        .properties("liked", p -> p.integer(i -> i))
                        .properties("comments", p -> p.integer(i -> i))
                        .properties("createTime", p -> p.date(d -> d.format("strict_date_optional_time||epoch_millis")))
                        .properties("updateTime", p -> p.date(d -> d.format("strict_date_optional_time||epoch_millis")))
                        .properties("suggest", p -> p.completion(c -> c))
                        .properties("tags", p -> p.keyword(k -> k))
                        .properties("isHot", p -> p.boolean_(b -> b))
                        .properties("hotScore", p -> p.double_(d -> d))
                )
        );

        CreateIndexResponse response = esClient.indices().create(createIndexRequest);
        if (response.acknowledged()) {
            log.info("笔记索引创建成功");
        } else {
            log.error("笔记索引创建失败");
        }
    }

    /**
     * 删除笔记索引
     */
    public void deleteBlogIndex() throws IOException {
        DeleteIndexRequest request = DeleteIndexRequest.of(d -> d.index("blog_index"));
        esClient.indices().delete(request);
        log.info("笔记索引已删除");
    }

    /**
     * 添加/更新笔记文档
     */
    public void saveBlogDocument(BlogDoc blogDoc) throws IOException {
        IndexRequest<BlogDoc> request = IndexRequest.of(i -> i
                .index("blog_index")
                .id(String.valueOf(blogDoc.getId()))
                .document(blogDoc)
        );
        esClient.index(request);
        log.debug("笔记文档已保存: {}", blogDoc.getId());
    }

    /**
     * 批量保存笔记文档
     */
    public void batchSaveBlogDocuments(List<BlogDoc> blogDocs) throws IOException {
        if (blogDocs == null || blogDocs.isEmpty()) {
            return;
        }

        List<BulkOperation> operations = blogDocs.stream()
                .map(blog -> BulkOperation.of(b -> b
                        .index(IndexOperation.of(i -> i
                                .index("blog_index")
                                .id(String.valueOf(blog.getId()))
                                .document(blog)
                        ))
                ))
                .toList();

        esClient.bulk(b -> b.operations(operations));
        log.info("批量保存笔记文档: {} 条", blogDocs.size());
    }

    /**
     * 删除笔记文档
     */
    public void deleteBlogDocument(Long id) throws IOException {
        esClient.delete(d -> d.index("blog_index").id(String.valueOf(id)));
        log.debug("笔记文档已删除: {}", id);
    }
}
