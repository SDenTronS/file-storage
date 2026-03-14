package dev.dentron.filestorage.application.service;

import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.out.FileObjectRepository;
import dev.dentron.filestorage.domain.FileObject;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
@Service
public class FileAccessService {
    private final FileObjectRepository fileRepository;

    public FileObject getById(UUID fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found with id: " + fileId));
    }

    public FileObject getByIdOwnedBy(UUID fileId, NamespaceContext ns) {
        FileObject file = getById(fileId);

        if (!file.isOwnedBy(ns.serviceId())) {
            log.warn(
                    "file belongs to different owner. fileId={}, fileOwner={}, callerService={}",
                    file.getId(),
                    file.getOwner(),
                    ns.serviceId()
            );
            throw new AccessDeniedException("Forbidden");
        }

        return file;
    }
}
