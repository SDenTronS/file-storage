package dev.dentron.filestorage.api.outbox;

import dev.dentron.filestorage.application.outbox.OutboxMessage;
import dev.dentron.filestorage.application.port.out.outbox.OutboxPort;
import dev.dentron.filestorage.common.util.ExceptionUtils;
import dev.dentron.filestorage.messagingkafka.KafkaTopicsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final int BATCH_SIZE = 500;

    private final OutboxPort outboxPort;
    private final KafkaTemplate<String, OutboxMessage> kafkaTemplate;
    private final KafkaTopicsProperties kafkaTopicsProperties;

    @Scheduled(fixedDelay = 500, timeUnit = TimeUnit.MILLISECONDS)
    public void publishEvents() {
        List<OutboxMessage> events = outboxPort.claimBatch(BATCH_SIZE);
        if (events.isEmpty()) {
            return;
        }

        var futures = events.stream()
                .map(this::publishTracked)
                .toList();

        List<PublishResult> results = futures
                .stream()
                .map(CompletableFuture::join)
                .toList();

        List<UUID> published = new ArrayList<>(BATCH_SIZE);
        List<UUID> failed = new ArrayList<>();
        List<UUID> retryable = new ArrayList<>();

        for (PublishResult result : results) {
            switch (result.status) {
                case FAILED:
                    failed.add(result.eventId);
                    break;
                case RETRYABLE:
                    retryable.add(result.eventId);
                    break;
                case PUBLISHED:
                    published.add(result.eventId);
                    break;
                default:
                    log.warn("Unhandled outbox publish result status: {}", result.status);
            }
        }

        Instant completedAt = Instant.now();
        outboxPort.completeBatch(published, failed, retryable, completedAt);

        if (!retryable.isEmpty()) {
            log.warn("{} outbox messages will be retried" , retryable.size());
        }

        log.debug(
                "Outbox batch processed: total={}, published={}, failed={}, retryable={}",
                events.size(), published.size(), failed.size(), retryable.size()
        );
    }

    private CompletableFuture<PublishResult> publishTracked(OutboxMessage event) {
        return publishOne(event)
                .handle((eventId, throwable) -> {
                    if (throwable == null) {
                        return PublishResult.published(eventId);
                    }

                    Throwable cause = ExceptionUtils.unwrap(throwable);

                    if (cause instanceof TopicNotResolvedException) {
                        log.error(
                                "Outbox event {} failed permanently: topic not resolved for eventType={}",
                                event.eventId(), event.eventType()
                        );
                        return PublishResult.failed(event.eventId());
                    }

                    log.debug(
                            "Outbox event {} failed, will be retried",
                            event.eventId(),
                            cause
                    );
                    return PublishResult.retryable(event.eventId());
                });
    }

    private CompletableFuture<UUID> publishOne(OutboxMessage event) {
        String topic = resolveTopic(event);

        if (topic == null) {
            return CompletableFuture.failedFuture(
                    new TopicNotResolvedException(event.eventType().getEventType())
            );
        }

        return kafkaTemplate
                .send(topic, event.aggregateId(), event)
                .thenApply(sendResult -> event.eventId());
    }

    private String resolveTopic(OutboxMessage event) {
        return kafkaTopicsProperties.topicName(event.eventType());
    }

    private enum PublishStatus {
        PUBLISHED,
        FAILED,
        RETRYABLE
    }

    private record PublishResult(UUID eventId, PublishStatus status) {
        static PublishResult published(UUID eventId) {
            return new PublishResult(eventId, PublishStatus.PUBLISHED);
        }

        static PublishResult failed(UUID eventId) {
            return new PublishResult(eventId, PublishStatus.FAILED);
        }

        static PublishResult retryable(UUID eventId) {
            return new PublishResult(eventId, PublishStatus.RETRYABLE);
        }
    }

    private static final class TopicNotResolvedException extends RuntimeException {
        private TopicNotResolvedException(String eventType) {
            super("Topic is not resolved for eventType=" + eventType);
        }
    }
}
