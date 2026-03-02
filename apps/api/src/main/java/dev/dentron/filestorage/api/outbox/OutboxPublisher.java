package dev.dentron.filestorage.api.outbox;

import dev.dentron.filestorage.application.outbox.OutboxMessage;
import dev.dentron.filestorage.application.port.out.outbox.OutboxPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor

@Component
public class OutboxPublisher {
    private final OutboxPort outboxPort;
    private final KafkaTemplate<String, OutboxMessage> kafkaTemplate;

    @Scheduled(fixedDelay = 20, timeUnit = TimeUnit.SECONDS)
    @Transactional
    public void publishEvents() {
        var events = outboxPort.findNew(100);
        var futures = events.stream()
                .map(event -> kafkaTemplate
                        .send("file-storage-outbox", event.aggregateId(), event)
                        .handle((r, t) -> {
                            if (t == null) {
                                return r;
                            }

                            log.warn("Failed to publish event eventId={}, aggregateId={}", event.eventId(), event.aggregateId(), t);
                            return null;
                        })
                ).toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        var succeed = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .map((r) -> r.getProducerRecord().value().eventId())
                .toList();

        outboxPort.markPublished(succeed);
    }

}
