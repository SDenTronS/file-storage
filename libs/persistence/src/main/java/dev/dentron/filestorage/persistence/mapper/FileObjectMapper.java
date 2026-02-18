package dev.dentron.filestorage.persistence.mapper;

import dev.dentron.filestorage.domain.FileObject;
import dev.dentron.filestorage.persistence.jpa.entity.FileEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ObjectFactory;

@Mapper(config = JpaMapperConfig.class)
public interface FileObjectMapper extends BaseMapper<FileObject, FileEntity> {

    @Override
    @BeanMapping(ignoreUnmappedSourceProperties = {"uploading"})
    FileEntity toEntity(FileObject domain);

    @Override
    FileObject toDomain(FileEntity entity);

    @ObjectFactory
    default FileObject restoreFileObject(FileEntity entity) {
        return FileObject.restore(
                entity.getId(),
                entity.getBucket(),
                entity.getOwner(),
                entity.getObjectKey(),
                entity.getOriginalName(),
                entity.getSize(),
                entity.getSha256(),
                entity.getEtag(),
                entity.getContentType(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getDeletedAt()
        );
    }

    @Override
    @BeanMapping(ignoreUnmappedSourceProperties = {"uploading"})
    FileEntity updateEntity(@MappingTarget FileEntity entity, FileObject domain);
}
