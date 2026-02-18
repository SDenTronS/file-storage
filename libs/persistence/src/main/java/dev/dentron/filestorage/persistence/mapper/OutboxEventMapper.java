package dev.dentron.filestorage.persistence.mapper;

import dev.dentron.filestorage.application.outbox.OutboxMessage;
import dev.dentron.filestorage.persistence.jpa.entity.OutboxEventEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ObjectFactory;

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
    OutboxMessage toDomain(OutboxEventEntity entity);

    @ObjectFactory
    default OutboxMessage restoreOutboxMessage(OutboxEventEntity entity) {
        return OutboxMessage.restore(
                entity.getId(),
                entity.getType(),
                entity.getPayloadJson(),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getCreatedAt()
        );
    }

    @Override
    @BeanMapping(ignoreUnmappedSourceProperties = {"eventId", "occurredAt"})
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "type", source = "eventType")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    OutboxEventEntity updateEntity(@MappingTarget OutboxEventEntity entity, OutboxMessage domain);
}
