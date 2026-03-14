package dev.dentron.filestorage.application.service;

import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.in.FileQueryUseCase;
import dev.dentron.filestorage.application.port.out.FileObjectRepository;
import dev.dentron.filestorage.domain.FileObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@RequiredArgsConstructor
@Service
public class FileQueryService implements FileQueryUseCase {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final FileObjectRepository fileRepository;
    private final FileAccessService fileAccessService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public ListFilesResult listFiles(NamespaceContext ns, ListFilesRequest request) {
        int limit = resolveLimit(request.limit());
        var cursor = decode(request.cursor());
        var page = fileRepository.scrollByOwner(ns.serviceId(), cursor, limit);

        List<FileMetadata> items = page.items().stream()
                .map(this::toFileMetadata)
                .toList();

        return new ListFilesResult(items, encode(page.nextCursor()));
    }

    @Override
    @Transactional(readOnly = true)
    public FileMetadata getFile(NamespaceContext ns, GetFileRequest request) {
        FileObject file = fileAccessService.getByIdOwnedBy(request.fileId(), ns);
        return toFileMetadata(file);
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }

        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }

        return limit;
    }

    private FileMetadata toFileMetadata(FileObject file) {
        return new FileMetadata(
                file.getId(),
                file.getOwner(),
                file.getBucket(),
                file.getObjectKey(),
                file.getOriginalName(),
                file.getSize(),
                file.getSha256(),
                file.getEtag(),
                file.getContentType(),
                file.getStatus(),
                file.getCreatedAt()
        );
    }

    public String encode(FileObjectRepository.FileObjectScrollCursor cursor) {
        if (cursor == null) {
            return null;
        }


        byte[] json = objectMapper.writeValueAsBytes(cursor);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json);

    }

    public FileObjectRepository.FileObjectScrollCursor decode(String encodedCursor) {
        if (!StringUtils.hasText(encodedCursor)) {
            return null;
        }

        try {
            byte[] json = Base64.getUrlDecoder().decode(encodedCursor.getBytes(StandardCharsets.UTF_8));
            return objectMapper.readValue(json, FileObjectRepository.FileObjectScrollCursor.class);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cursor", e);
        }
    }
}
