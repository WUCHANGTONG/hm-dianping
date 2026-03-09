package com.hmdp.seckill.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka配置类
 * 用于秒杀订单消息的生产和消费
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * 秒杀订单Topic
     */
    public static final String SECKILL_ORDER_TOPIC = "seckill-orders";

    /**
     * 死信队列Topic（用于处理失败的消息）
     */
    public static final String SECKILL_ORDER_DLQ_TOPIC = "seckill-orders-dlq";

    /**
     * 延迟检查Topic（用于订单补偿，5分钟后检查订单状态）
     */
    public static final String SECKILL_DELAY_CHECK_TOPIC = "seckill-delay-check";

    /**
     * 缓存失效广播Topic（用于店铺缓存更新）
     * 解决Gemini提到的"薛定谔的更新"问题
     */
    public static final String CACHE_INVALIDATION_TOPIC = "cache-invalidation";

    /**
     * 创建秒杀订单Topic
     * 分区数：3，副本数：1（生产环境建议2-3）
     */
    @Bean
    public NewTopic seckillOrderTopic() {
        return new NewTopic(SECKILL_ORDER_TOPIC, 3, (short) 1);
    }

    /**
     * 创建死信队列Topic
     */
    @Bean
    public NewTopic seckillOrderDlqTopic() {
        return new NewTopic(SECKILL_ORDER_DLQ_TOPIC, 1, (short) 1);
    }

    /**
     * 创建延迟检查Topic
     * 用于5分钟后检查订单是否创建成功
     */
    @Bean
    public NewTopic seckillDelayCheckTopic() {
        return new NewTopic(SECKILL_DELAY_CHECK_TOPIC, 3, (short) 1);
    }

    /**
     * 创建缓存失效广播Topic
     * 用于店铺信息变更时通知所有节点清除本地缓存
     */
    @Bean
    public NewTopic cacheInvalidationTopic() {
        return new NewTopic(CACHE_INVALIDATION_TOPIC, 3, (short) 1);
    }

    /**
     * Kafka Producer配置
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // 可靠性配置
        config.put(ProducerConfig.ACKS_CONFIG, "all");  // 等待所有副本确认
        config.put(ProducerConfig.RETRIES_CONFIG, 3);    // 失败重试3次
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);  // 开启幂等性
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // 批量发送配置（提升吞吐量）
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        config.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
