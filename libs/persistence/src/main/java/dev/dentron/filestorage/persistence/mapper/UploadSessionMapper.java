package dev.dentron.filestorage.persistence.mapper;

import dev.dentron.filestorage.domain.UploadSession;
import dev.dentron.filestorage.persistence.jpa.entity.UploadSessionEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ObjectFactory;

@Mapper(config = JpaMapperConfig.class)
public interface UploadSessionMapper extends BaseMapper<UploadSession, UploadSessionEntity> {

    @Override
    UploadSessionEntity toEntity(UploadSession domain);

    @Override
    UploadSession toDomain(UploadSessionEntity entity);

    @ObjectFactory
    default UploadSession restoreUploadSession(UploadSessionEntity entity) {
        return UploadSession.restore(
                entity.getId(),
                entity.getMultipartUploadId(),
                entity.getFileId(),
                entity.getExpiresAt(),
                entity.getExpectedSize(),
                entity.getExpectedContentType(),
                entity.getStatus()
        );
    }

    @Override
    @Mapping(target = "id", source = "id")
    UploadSessionEntity updateEntity(@MappingTarget UploadSessionEntity entity, UploadSession domain);
}
