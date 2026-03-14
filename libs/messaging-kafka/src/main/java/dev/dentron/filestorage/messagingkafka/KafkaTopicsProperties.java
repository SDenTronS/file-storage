package dev.dentron.filestorage.messagingkafka;

import dev.dentron.filestorage.application.outbox.OutboxEventType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public record KafkaTopicsProperties(
        Topics topics,
        Listener listener,
        Retry retry
) {
    public String topicName(OutboxEventType eventType) {
        return switch (eventType) {
            case FILE_UPLOADED -> topics.fileUploaded().name();
            case FILE_DELETED -> topics.fileDeleted().name();
        };
    }

    public String dltTopicName(String topicName) {
        if (topics.fileUploaded().name().equals(topicName)) {
            return topics.fileUploaded().dltName();
        }

        if (topics.fileDeleted().name().equals(topicName)) {
            return topics.fileDeleted().dltName();
        }

        return topicName + "-dlt";
    }

    public record Topics(
            Topic fileUploaded,
            Topic fileDeleted
    ) {}

    public record Topic(
            String name,
            String dltName,
            int partitions,
            int replicas
    ) {}

    public record Listener(
            String groupId,
            int concurrency,
            long pollTimeoutMs
    ) {}

    public record Retry(
            long intervalMs,
            long maxAttempts
    ) {}
}
