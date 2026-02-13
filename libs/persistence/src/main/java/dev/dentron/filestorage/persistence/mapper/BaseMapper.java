package dev.dentron.filestorage.persistence.mapper;

import org.mapstruct.MappingTarget;

public interface BaseMapper <D, E> {
    E toEntity(D domain);
    D toDomain(E entity);

    E updateEntity(@MappingTarget E entity, D domain);
}
