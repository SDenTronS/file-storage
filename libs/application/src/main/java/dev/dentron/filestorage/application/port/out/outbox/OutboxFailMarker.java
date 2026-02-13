package dev.dentron.filestorage.application.port.out.outbox;

import java.util.List;
import java.util.UUID;

public interface OutboxFailMarker {
    void markFailed(List<UUID> messages);

    void markFailed(UUID id);
}
