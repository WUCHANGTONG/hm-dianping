package com.hmdp.search.listener;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Shop;
import com.hmdp.search.entity.BlogDoc;
import com.hmdp.search.entity.ShopDoc;
import com.hmdp.search.service.ElasticsearchIndexService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

/**
 * Canal数据变更监听器
 * 监听MySQL binlog，实时同步数据到Elasticsearch
 * 包含连接断开自动重连机制
 */
@Slf4j
@Component
public class CanalDataChangeListener {

    private final ElasticsearchIndexService indexService;
    private CanalConnector connector;
    private ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${canal.server.host:127.0.0.1}")
    private String canalHost;

    @Value("${canal.server.port:11111}")
    private Integer canalPort;

    @Value("${canal.destination:example}")
    private String destination;

    public CanalDataChangeListener(ElasticsearchIndexService indexService) {
        this.indexService = indexService;
    }

    @PostConstruct
    public void start() {
        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "canal-listener-thread");
            t.setDaemon(true);
            return t;
        });
        running.set(true);
        executorService.submit(this::processWithReconnect);
        log.info("Canal监听器启动，目标: {}:{}, destination: {}", canalHost, canalPort, destination);
    }

    @PreDestroy
    public void stop() {
        log.info("正在停止Canal监听器...");
        running.set(false);

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        disconnect();
        log.info("Canal监听器已停止");
    }

    /**
     * 断开连接
     */
    private void disconnect() {
        try {
            if (connector != null) {
                connector.disconnect();
                connector = null;
                log.debug("Canal连接已断开");
            }
        } catch (Exception e) {
            log.warn("断开Canal连接时出错", e);
        }
    }

    /**
     * 创建并连接Canal
     */
    private boolean connect() {
        try {
            // 先断开旧连接
            disconnect();

            // 创建新连接
            connector = CanalConnectors.newSingleConnector(
                    new InetSocketAddress(canalHost, canalPort),
                    destination, "", "");

            connector.connect();
            connector.subscribe("hmdp.tb_shop,hmdp.tb_blog");
            connector.rollback();

            log.info("Canal连接成功，监听数据库: hmdp.tb_shop, hmdp.tb_blog");
            return true;
        } catch (Exception e) {
            log.error("Canal连接失败: {}", e.getMessage());
            connector = null;
            return false;
        }
    }

    /**
     * 带重连机制的消息处理
     */
    private void processWithReconnect() {
        // 初始连接
        while (running.get() && !connect()) {
            log.warn("初始连接失败，5秒后重试...");
            sleep(5000);
        }

        while (running.get()) {
            try {
                // 检查连接是否有效
                if (connector == null) {
                    log.warn("连接为null，尝试重新连接...");
                    if (!connect()) {
                        sleep(5000);
                        continue;
                    }
                }

                // 获取消息
                Message message = connector.getWithoutAck(1000);

                // 空值检查
                if (message == null) {
                    log.warn("收到空消息，可能连接已断开");
                    disconnect();
                    sleep(1000);
                    continue;
                }

                long batchId = message.getId();

                // batchId为-1表示连接问题
                if (batchId == -1) {
                    sleep(1000);
                    continue;
                }

                // 处理消息条目
                if (message.getEntries() != null && !message.getEntries().isEmpty()) {
                    processEntries(message.getEntries());
                    connector.ack(batchId);
                }

            } catch (Exception e) {
                log.error("处理Canal消息异常: {}", e.getMessage());
                disconnect();
                sleep(3000);
            }
        }
    }

    /**
     * 处理消息条目
     */
    private void processEntries(List<CanalEntry.Entry> entries) {
        for (CanalEntry.Entry entry : entries) {
            try {
                // 跳过事务开始/结束
                if (entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONBEGIN
                        || entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONEND) {
                    continue;
                }

                // 解析行变更
                CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
                String tableName = entry.getHeader().getTableName();
                CanalEntry.EventType eventType = rowChange.getEventType();

                log.debug("处理数据变更: 表={}, 事件类型={}", tableName, eventType);

                // 处理商铺表变更
                if ("tb_shop".equals(tableName)) {
                    handleShopChange(rowChange, eventType);
                }
                // 处理笔记表变更
                else if ("tb_blog".equals(tableName)) {
                    handleBlogChange(rowChange, eventType);
                }
            } catch (Exception e) {
                log.error("处理条目异常", e);
            }
        }
    }

    /**
     * 处理商铺数据变更
     */
    private void handleShopChange(CanalEntry.RowChange rowChange, CanalEntry.EventType eventType) {
        if (rowChange == null || rowChange.getRowDatasList() == null) {
            return;
        }

        for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
            try {
                if (eventType == CanalEntry.EventType.DELETE) {
                    Long id = getColumnValue(rowData.getBeforeColumnsList(), "id");
                    if (id != null) {
                        indexService.deleteShopDocument(id);
                        log.info("商铺文档已删除: {}", id);
                    }
                } else if (eventType == CanalEntry.EventType.INSERT
                        || eventType == CanalEntry.EventType.UPDATE) {
                    Shop shop = parseShop(rowData.getAfterColumnsList());
                    if (shop != null) {
                        ShopDoc shopDoc = convertToShopDoc(shop);
                        indexService.saveShopDocument(shopDoc);
                        log.info("商铺文档已同步: {}", shop.getId());
                    }
                }
            } catch (Exception e) {
                log.error("处理商铺变更失败", e);
            }
        }
    }

    /**
     * 处理笔记数据变更
     */
    private void handleBlogChange(CanalEntry.RowChange rowChange, CanalEntry.EventType eventType) {
        if (rowChange == null || rowChange.getRowDatasList() == null) {
            return;
        }

        for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
            try {
                if (eventType == CanalEntry.EventType.DELETE) {
                    Long id = getColumnValue(rowData.getBeforeColumnsList(), "id");
                    if (id != null) {
                        indexService.deleteBlogDocument(id);
                        log.info("笔记文档已删除: {}", id);
                    }
                } else if (eventType == CanalEntry.EventType.INSERT
                        || eventType == CanalEntry.EventType.UPDATE) {
                    Blog blog = parseBlog(rowData.getAfterColumnsList());
                    if (blog != null) {
                        BlogDoc blogDoc = convertToBlogDoc(blog);
                        indexService.saveBlogDocument(blogDoc);
                        log.info("笔记文档已同步: {}", blog.getId());
                    }
                }
            } catch (Exception e) {
                log.error("处理笔记变更失败", e);
            }
        }
    }

    /**
     * 休眠指定毫秒
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 从列列表中获取指定列的值
     */
    private Long getColumnValue(List<CanalEntry.Column> columns, String columnName) {
        if (columns == null) return null;
        for (CanalEntry.Column column : columns) {
            if (columnName.equals(column.getName())) {
                String value = column.getValue();
                if (value != null && !value.isEmpty()) {
                    try {
                        return Long.valueOf(value);
                    } catch (NumberFormatException e) {
                        log.warn("转换ID失败: {}", value);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 解析商铺数据
     */
    private Shop parseShop(List<CanalEntry.Column> columns) {
        if (columns == null) return null;
        Shop shop = new Shop();
        for (CanalEntry.Column column : columns) {
            String name = column.getName();
            String value = column.getValue();
            if (value == null || value.isEmpty()) continue;

            try {
                switch (name) {
                    case "id" -> shop.setId(Long.valueOf(value));
                    case "name" -> shop.setName(value);
                    case "type_id" -> shop.setTypeId(Long.valueOf(value));
                    case "images" -> shop.setImages(value);
                    case "area" -> shop.setArea(value);
                    case "address" -> shop.setAddress(value);
                    case "x" -> shop.setX(Double.valueOf(value));
                    case "y" -> shop.setY(Double.valueOf(value));
                    case "avg_price" -> shop.setAvgPrice(Long.valueOf(value));
                    case "sold" -> shop.setSold(Integer.valueOf(value));
                    case "comments" -> shop.setComments(Integer.valueOf(value));
                    case "score" -> shop.setScore(Integer.valueOf(value));
                    case "open_hours" -> shop.setOpenHours(value);
                }
            } catch (NumberFormatException e) {
                log.warn("解析商铺字段失败: {} = {}", name, value);
            }
        }
        return shop.getId() != null ? shop : null;
    }

    /**
     * 解析笔记数据
     */
    private Blog parseBlog(List<CanalEntry.Column> columns) {
        if (columns == null) return null;
        Blog blog = new Blog();
        for (CanalEntry.Column column : columns) {
            String name = column.getName();
            String value = column.getValue();
            if (value == null || value.isEmpty()) continue;

            try {
                switch (name) {
                    case "id" -> blog.setId(Long.valueOf(value));
                    case "shop_id" -> blog.setShopId(Long.valueOf(value));
                    case "user_id" -> blog.setUserId(Long.valueOf(value));
                    case "title" -> blog.setTitle(value);
                    case "images" -> blog.setImages(value);
                    case "content" -> blog.setContent(value);
                    case "liked" -> blog.setLiked(Integer.valueOf(value));
                    case "comments" -> blog.setComments(Integer.valueOf(value));
                }
            } catch (NumberFormatException e) {
                log.warn("解析笔记字段失败: {} = {}", name, value);
            }
        }
        return blog.getId() != null ? blog : null;
    }

    /**
     * 将Shop转换为ShopDoc
     */
    private ShopDoc convertToShopDoc(Shop shop) {
        ShopDoc doc = new ShopDoc();
        doc.setId(shop.getId());
        doc.setName(shop.getName());
        doc.setTypeId(shop.getTypeId());
        doc.setImages(shop.getImages());
        doc.setArea(shop.getArea());
        doc.setAddress(shop.getAddress());
        doc.setX(shop.getX());
        doc.setY(shop.getY());
        doc.setLocation(shop.getY() + "," + shop.getX());
        doc.setAvgPrice(shop.getAvgPrice());
        doc.setSold(shop.getSold());
        doc.setComments(shop.getComments());
        doc.setScore(shop.getScore());
        doc.setOpenHours(shop.getOpenHours());

        if (shop.getName() != null) {
            doc.setNamePinyin(shop.getName());
            doc.setNameInitial(shop.getName());
            doc.setSuggest(shop.getName());
        }

        return doc;
    }

    /**
     * 将Blog转换为BlogDoc
     */
    private BlogDoc convertToBlogDoc(Blog blog) {
        BlogDoc doc = new BlogDoc();
        doc.setId(blog.getId());
        doc.setShopId(blog.getShopId());
        doc.setUserId(blog.getUserId());
        doc.setTitle(blog.getTitle());
        doc.setImages(blog.getImages());
        doc.setContent(blog.getContent());
        doc.setLiked(blog.getLiked());
        doc.setComments(blog.getComments());

        if (blog.getTitle() != null) {
            doc.setTitlePinyin(blog.getTitle());
            doc.setTitleInitial(blog.getTitle());
            doc.setSuggest(blog.getTitle());
        }

        double hotScore = (blog.getLiked() != null ? blog.getLiked() : 0) * 2.0
                + (blog.getComments() != null ? blog.getComments() : 0) * 3.0;
        doc.setHotScore(hotScore);
        doc.setIsHot(hotScore > 100);

        return doc;
    }
}
