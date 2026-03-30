package dev.dentron.filestorage.api;

import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.in.AbortUploadUseCase;
import dev.dentron.filestorage.application.port.in.FileQueryUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = ApiApplication.class, properties = {
        "app.minio.enabled=false",
        "app.security.enabled=false",
        "app.kafka.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Import(PostgresPerTestConfig.class)
class FileQueryServiceIT extends AbstractFileStorageITSupport {
    @Autowired
    private AbortUploadUseCase abortUploadUseCase;

    @Autowired
    private FileQueryUseCase fileQueryUseCase;

    @Test
    void listFilesReturnsOnlyNonDeletedOwnedFiles() {
        NamespaceContext ns = new NamespaceContext("svc-a");
        UploadFixture uploadingFixture = createSessionAndFile("svc-a");
        UploadFixture deletedFixture = createSessionAndFile("svc-a");
        UploadFixture foreignFixture = createSessionAndFile("svc-b");

        abortUploadUseCase.abortUpload(ns, new AbortUploadUseCase.AbortUploadRequest(deletedFixture.sessionId()));

        var page = fileQueryUseCase.listFiles(ns, new FileQueryUseCase.ListFilesRequest(null, 10));
        List<FileQueryUseCase.FileMetadata> files = page.items();

        assertThat(files).hasSize(1);
        assertThat(page.nextCursor()).isNull();
        assertThat(files).extracting(FileQueryUseCase.FileMetadata::fileId)
                .containsExactly(uploadingFixture.fileId());
        assertThat(files).extracting(FileQueryUseCase.FileMetadata::owner)
                .containsOnly("svc-a");
        assertThat(files).extracting(FileQueryUseCase.FileMetadata::fileId)
                .doesNotContain(deletedFixture.fileId())
                .doesNotContain(foreignFixture.fileId());
    }

    @Test
    void listFilesSupportsCursorPagination() {
        NamespaceContext ns = new NamespaceContext("svc-a");
        UploadFixture first = createSessionAndFile("svc-a");
        UploadFixture second = createSessionAndFile("svc-a");
        UploadFixture third = createSessionAndFile("svc-a");

        var firstPage = fileQueryUseCase.listFiles(ns, new FileQueryUseCase.ListFilesRequest(null, 2));
        var secondPage = fileQueryUseCase.listFiles(ns, new FileQueryUseCase.ListFilesRequest(firstPage.nextCursor(), 2));

        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.nextCursor()).isNotNull();
        assertThat(secondPage.items()).hasSize(1);
        assertThat(secondPage.nextCursor()).isNull();

        Set<Object> ids = new HashSet<>();
        ids.addAll(firstPage.items().stream().map(FileQueryUseCase.FileMetadata::fileId).toList());
        ids.addAll(secondPage.items().stream().map(FileQueryUseCase.FileMetadata::fileId).toList());

        assertThat(ids).containsExactlyInAnyOrder(first.fileId(), second.fileId(), third.fileId());
    }

    @Test
    void getFileRejectsForeignOwner() {
        UploadFixture fixture = createSessionAndFile("svc-a");

        assertThatThrownBy(() ->
                fileQueryUseCase.getFile(
                        new NamespaceContext("svc-b"),
                        new FileQueryUseCase.GetFileRequest(fixture.fileId())
                )
        ).isInstanceOf(AccessDeniedException.class);
    }
}
