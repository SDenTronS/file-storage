package dev.dentron.filestorage.messagingkafka;

import dev.dentron.filestorage.application.outbox.OutboxMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableKafka
@ConditionalOnBooleanProperty(prefix = "app.kafka", name = "enabled")
public class KafkaConfig {

    @Bean
    public KafkaAdmin.NewTopics kafkaTopics(KafkaTopicsProperties properties) {
        return new KafkaAdmin.NewTopics(
                buildTopic(properties.topics().fileUploaded()),
                buildDltTopic(properties.topics().fileUploaded()),
                buildTopic(properties.topics().fileDeleted()),
                buildDltTopic(properties.topics().fileDeleted())
        );
    }

    @Bean("producer-configs")
    public Map<String, Object> producerConfigs(KafkaConnectionDetails details) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, details.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        props.put(JacksonJsonSerializer.TYPE_MAPPINGS, "outbox:dev.dentron.filestorage.application.outbox.OutboxMessage");

        return props;
    }

    @Bean
    public ProducerFactory<String, OutboxMessage> producerFactory(
            @Qualifier("producer-configs") Map<String, Object> producerConfigs
    ) {
        return new DefaultKafkaProducerFactory<>(producerConfigs);
    }

    @Bean
    public KafkaTemplate<String, OutboxMessage> kafkaTemplate(ProducerFactory<String, OutboxMessage> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }


    @Bean("consumer-configs")
    public Map<String, Object> consumerConfigs(KafkaConnectionDetails details) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, details.getBootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
        props.put(JacksonJsonDeserializer.TYPE_MAPPINGS, "outbox:dev.dentron.filestorage.application.outbox.OutboxMessage");
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "dev.dentron.filestorage");

        return props;
    }

    @Bean
    public ConsumerFactory<String, OutboxMessage> consumerFactory(
            @Qualifier("consumer-configs") Map<String, Object> consumerConfigs
    ) {
        return new DefaultKafkaConsumerFactory<>(consumerConfigs);
    }

    @Bean("outbox-container-factory")
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, OutboxMessage>> outboxKafkaListenerContainerFactory(
            KafkaTopicsProperties properties,
            @Qualifier("outbox-error-handler") CommonErrorHandler errorHandler,
            ConsumerFactory<String, OutboxMessage> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, OutboxMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(properties.listener().concurrency());
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setPollTimeout(properties.listener().pollTimeoutMs());
        return factory;
    }

    @Bean("outbox-error-handler")
    public CommonErrorHandler errorHandler(
            KafkaTopicsProperties properties,
            KafkaTemplate<String, OutboxMessage> outboxKafkaTemplate
    ) {
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(OutboxMessage.class, outboxKafkaTemplate);
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(
                templates,
                (record, exception) -> new TopicPartition(
                        properties.dltTopicName(record.topic()),
                        record.partition()
                )
        );

        DefaultErrorHandler handler = new DefaultErrorHandler(
                dlt,
                new FixedBackOff(properties.retry().intervalMs(), properties.retry().maxAttempts())
        );
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        handler.addNotRetryableExceptions(IllegalStateException.class);
        handler.addNotRetryableExceptions(IOException.class);
        return handler;
    }

    @Bean("log-listener-error-handler")
    public KafkaListenerErrorHandler kafkaListenerErrorHandler() {
        return (message, exception) -> {
            log.error("KafkaListenerErrorHandler error occurred while processing outbox message", exception);
            throw exception;
        };
    }

    private NewTopic buildTopic(KafkaTopicsProperties.Topic topic) {
        return TopicBuilder.name(topic.name())
                .partitions(topic.partitions())
                .replicas(topic.replicas())
                .build();
    }

    private NewTopic buildDltTopic(KafkaTopicsProperties.Topic topic) {
        return TopicBuilder.name(topic.dltName())
                .partitions(topic.partitions())
                .replicas(topic.replicas())
                .build();
    }
}
