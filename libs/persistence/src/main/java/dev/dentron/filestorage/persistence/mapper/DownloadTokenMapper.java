package dev.dentron.filestorage.persistence.mapper;

import dev.dentron.filestorage.domain.DownloadToken;
import dev.dentron.filestorage.persistence.jpa.entity.DownloadTokenEntity;
import org.mapstruct.Mapper;

@Mapper(config = JpaMapperConfig.class)
public interface DownloadTokenMapper extends BaseMapper<DownloadToken, DownloadTokenEntity> {
}
