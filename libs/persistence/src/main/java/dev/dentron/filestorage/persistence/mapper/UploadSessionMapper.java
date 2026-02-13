package dev.dentron.filestorage.persistence.mapper;

import dev.dentron.filestorage.domain.UploadSession;
import dev.dentron.filestorage.persistence.jpa.entity.UploadSessionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(config = JpaMapperConfig.class)
public interface UploadSessionMapper extends BaseMapper<UploadSession, UploadSessionEntity> {

}
