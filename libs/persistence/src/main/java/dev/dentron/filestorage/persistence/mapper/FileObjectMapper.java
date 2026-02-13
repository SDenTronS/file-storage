package dev.dentron.filestorage.persistence.mapper;

import dev.dentron.filestorage.domain.FileObject;
import dev.dentron.filestorage.persistence.jpa.entity.FileEntity;
import org.mapstruct.Mapper;

@Mapper(config = JpaMapperConfig.class)
public interface FileObjectMapper extends BaseMapper<FileObject, FileEntity> {
}
