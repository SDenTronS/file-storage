package dev.dentron.filestorage.application.port.out;

import java.util.Optional;

public interface CrudRepo <T, ID>{
    T save(T domain);

    T saveAndFlush(T domain);

    Optional<T> findById(ID id);

    boolean existsById(ID id);

    void deleteById(ID id);

}
