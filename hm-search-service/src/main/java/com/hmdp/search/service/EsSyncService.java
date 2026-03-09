package com.hmdp.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

/**
 * ES同步服务（带版本控制）
 * 解决Gemini提到的"乱序覆写"问题
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsSyncService {

    private final ElasticsearchClient esClient;

    /**
     * 同步店铺数据到ES（带版本号检查）
     * 只有当incomingVersion > esCurrentVersion时才更新
     *
     * @param shopId 店铺ID
     * @param shopData 店铺数据（包含version字段）
     * @param version 数据版本号（来自MySQL的update_time或version字段）
     */
    public void syncShopWithVersion(Long shopId, Map<String, Object> shopData, Long version) {
        try {
            shopData.put("version", version);

            // 使用ES的外部版本控制
            // version_type=external: 只有当传入版本大于ES当前版本时才更新
            IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                    .index("shop")
                    .id(String.valueOf(shopId))
                    .document(shopData)
                    .version(version)  // 外部版本号
                    .versionType(co.elastic.clients.elasticsearch._types.VersionType.External)
            );

            esClient.index(request);
            log.info("ES同步成功[版本{}]: shopId={}", version, shopId);

        } catch (Exception e) {
            // 如果版本冲突（低版本试图覆盖高版本），ES会抛异常
            if (e.getMessage() != null && e.getMessage().contains("version_conflict")) {
                log.warn("ES版本冲突，丢弃低版本消息: shopId={}, version={}", shopId, version);
            } else {
                log.error("ES同步失败: shopId={}", shopId, e);
                throw new RuntimeException("ES同步失败", e);
            }
        }
    }

    /**
     * 检查版本是否可以更新
     * 如果ES中不存在或版本号小于传入版本，返回true
     */
    public boolean canUpdate(Long shopId, Long incomingVersion) {
        try {
            var response = esClient.get(g -> g
                    .index("shop")
                    .id(String.valueOf(shopId)),
                    Map.class
            );

            if (!response.found()) {
                return true; // ES中不存在，可以更新
            }

            Map<String, Object> source = response.source();
            Long currentVersion = ((Number) source.getOrDefault("version", 0L)).longValue();

            return incomingVersion > currentVersion;

        } catch (IOException e) {
            log.error("检查ES版本失败: shopId={}", shopId, e);
            return false;
        }
    }
}
