package dev.dentron.filestorage.persistence.mapper;

import dev.dentron.filestorage.domain.DownloadToken;
import dev.dentron.filestorage.persistence.jpa.entity.DownloadTokenEntity;
import org.mapstruct.*;

@Mapper(config = JpaMapperConfig.class)
public interface DownloadTokenMapper extends BaseMapper<DownloadToken, DownloadTokenEntity> {
    @Override
    @BeanMapping(ignoreUnmappedSourceProperties = "used")
    @Mapping(target = "file.id", source = "fileId")
    DownloadTokenEntity toEntity(DownloadToken domain);

    @Override
    @BeanMapping(unmappedSourcePolicy = ReportingPolicy.IGNORE)
    DownloadToken toDomain(DownloadTokenEntity entity);

    @ObjectFactory
    default DownloadToken restoreDownloadToken(DownloadTokenEntity entity) {
        return DownloadToken.restore(
                entity.getId(),
                entity.getTokenHash(),
                entity.getFile() == null ? null : entity.getFile().getId(),
                entity.getIssuedByService(),
                entity.getAudienceService(),
                entity.getExpiresAt(),
                entity.getRedeemedAt(),
                entity.getRedeemedByService(),
                entity.getCreatedAt(),
                entity.getRevokedAt()
        );
    }

    @Override
    @BeanMapping(ignoreUnmappedSourceProperties = {"used"})
    @Mapping(target = "file.id", source = "fileId")
    DownloadTokenEntity updateEntity(@MappingTarget DownloadTokenEntity entity, DownloadToken domain);
}
