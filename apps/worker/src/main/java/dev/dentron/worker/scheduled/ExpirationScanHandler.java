package dev.dentron.worker.scheduled;

import dev.dentron.filestorage.application.port.out.FileObjectRepository;
import dev.dentron.filestorage.application.port.out.ObjectStoragePort;
import dev.dentron.filestorage.application.port.out.UploadSessionRepository;
import dev.dentron.filestorage.domain.FileObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor

@Component
public class ExpirationScanHandler {

    private final UploadSessionRepository uploadSessionRepository;
    private final FileObjectRepository fileRepository;
    private final ObjectStoragePort storage;

//    @Scheduled(cron = "0 0 0 * * ?")
//    public void scanExpiredFiles() {
//
//    }

//    @Scheduled
//    public void scanExpiredTokens() {
//
//    }


    //TODO сделать подтверждение после аборта
    @Scheduled(fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
    @Transactional
    public void scanExpiredUploadSessions() {
        List<UploadSessionRepository.AbortRow> expired = uploadSessionRepository.findExpiredNonAbortedByTimeForUpdate(100);
        uploadSessionRepository.markAborted(expired.stream().map(UploadSessionRepository.AbortRow::getSessionId).toList());

        List<FileObject> expiredFiles = fileRepository.findAllByIds(expired.stream().map(UploadSessionRepository.AbortRow::getFileId).toList());
        Map<UUID, FileObject> fileMap = expiredFiles.stream().collect(Collectors.toMap(FileObject::getId, f -> f));

        for (var entry : expired) {
            FileObject file = fileMap.get(entry.getFileId());
            var request = new ObjectStoragePort.AbortMultipartUploadRequest(file.getBucket(), file.getObjectKey(), entry.getMultipartUploadId());

            storage.abortMultipartUploadAsync(request).whenComplete((r, t) -> {
                if (t != null) {
                    log.warn("Abort multipart upload failed for file {}", file.getObjectKey(), t);
                }
            });

        }
    }

//    @Scheduled
//    @Transactional
//    public void scanExpiredMultipartUploads() {
//
//    }
}
