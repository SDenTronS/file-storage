package dev.dentron.filestorage.api.outbox;

import dev.dentron.filestorage.application.outbox.OutboxMessage;
import dev.dentron.filestorage.application.port.out.outbox.OutboxPort;
import dev.dentron.filestorage.messagingkafka.KafkaTopicsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor

@Component
public class OutboxPublisher {
    private final OutboxPort outboxPort;
    private final KafkaTemplate<String, OutboxMessage> kafkaTemplate;
    private final KafkaTopicsProperties kafkaTopicsProperties;

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    public void publishEvents() {
        var events = outboxPort.claimBatch(500);
        var futures = events.stream()
                .map(event -> {
                    var topic = resolveTopic(event);
                    return kafkaTemplate
                        .send(topic, event.aggregateId(), event)
                        .handle((r, t) -> {
                            if (t == null) {
                                return r;
                            }

                            log.warn("Failed to publish event eventId={}, aggregateId={}, topic={}", event.eventId(), event.aggregateId(), topic, t);
                            return null;
                        });
                }).toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        var succeed = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .map((r) -> r.getProducerRecord().value().eventId())
                .toList();

        outboxPort.markPublished(succeed, Instant.now());
    }

    private String resolveTopic(OutboxMessage event) {
        return kafkaTopicsProperties.topicName(event.eventType());
    }

}
