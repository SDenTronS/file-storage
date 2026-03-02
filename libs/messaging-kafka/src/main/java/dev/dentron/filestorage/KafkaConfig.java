package dev.dentron.filestorage;

import dev.dentron.filestorage.application.outbox.OutboxMessage;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
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

@Configuration
@EnableKafka
@ConditionalOnBooleanProperty(prefix = "app.kafka", name = "enabled")
public class KafkaConfig {

    @Bean
    public NewTopic topic() {
        return TopicBuilder.name("file-storage-outbox")
                .partitions(1)
                .build();
    }

    @Bean
    public NewTopic dltTopic() {
        return TopicBuilder.name("file-storage-outbox-dlt")
                .partitions(1)
                .build();
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
            @Qualifier("outbox-error-handler") CommonErrorHandler errorHandler,
            ConsumerFactory<String, OutboxMessage> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, OutboxMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setPollTimeout(3000);
        return factory;
    }

    @Bean("outbox-error-handler")
    public CommonErrorHandler errorHandler(KafkaTemplate<String, OutboxMessage> outboxKafkaTemplate) {
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(OutboxMessage.class, outboxKafkaTemplate);
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(templates);

        DefaultErrorHandler handler = new DefaultErrorHandler(dlt, new FixedBackOff(1000L, 3));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        handler.addNotRetryableExceptions(IllegalStateException.class);
        handler.addNotRetryableExceptions(IOException.class);
        return handler;
    }
}
