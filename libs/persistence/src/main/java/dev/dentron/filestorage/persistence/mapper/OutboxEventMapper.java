package dev.dentron.filestorage.persistence.mapper;

import dev.dentron.filestorage.application.outbox.OutboxMessage;
import dev.dentron.filestorage.persistence.jpa.entity.OutboxEventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = JpaMapperConfig.class)
public interface OutboxEventMapper extends BaseMapper<OutboxMessage, OutboxEventEntity> {
    @Override
    @Mapping(target = "id", source = "eventId")
    @Mapping(target = "type", source = "eventType")
    @Mapping(target = "createdAt", source = "occurredAt")
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "status", constant = "NEW")
    OutboxEventEntity toEntity(OutboxMessage domain);

    @Override
    @Mapping(target = "eventId", source = "id")
    @Mapping(target = "eventType", source = "type")
    @Mapping(target = "occurredAt", source = "createdAt")
    OutboxMessage toDomain(OutboxEventEntity entity);

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "type", source = "eventType")
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    OutboxEventEntity updateEntity(@MappingTarget OutboxEventEntity entity, OutboxMessage domain);
}
