package dev.dentron.filestorage.api;

import dev.dentron.filestorage.api.config.DevNoSecurityConfig;
import dev.dentron.filestorage.api.controller.FileController;
import dev.dentron.filestorage.application.port.NamespaceContext;
import dev.dentron.filestorage.application.port.in.DeleteFileUseCase;
import dev.dentron.filestorage.application.port.in.FileQueryUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = FileController.class, properties = {
        "app.security.enabled=false"
})
@Import(DevNoSecurityConfig.class)
class FileControllerTest extends AbstractControllerWebMvcTestSupport {
    @MockitoBean private DeleteFileUseCase deleteFileUseCase;
    @MockitoBean private FileQueryUseCase fileQueryUseCase;

    @Test
    void getFileReturnsReducedMetadata() {
        var fileId = UUID.randomUUID();
        var createdAt = Instant.parse("2000-01-01T00:00:00Z");

        when(fileQueryUseCase.getFile(any(), any()))
                .thenReturn(new FileQueryUseCase.FileMetadata(
                        fileId,
                        SERVICE_ID,
                        "bucket-main",
                        "uploads/" + fileId,
                        "document.txt",
                        128L,
                        "sha-256",
                        "etag-123",
                        MediaType.TEXT_PLAIN_VALUE,
                        dev.dentron.filestorage.domain.FileObject.Status.READY,
                        createdAt
                ));

        var request = mockMvc.get()
                .uri("/v1/files/{fileId}", fileId)
                .with(serviceAuthentication());

        var bodyJson = assertThat(request)
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson();

        bodyJson.extractingPath("$.fileId").isEqualTo(fileId.toString());
        bodyJson.extractingPath("$.originalName").isEqualTo("document.txt");
        bodyJson.extractingPath("$.size").isEqualTo(128);
        bodyJson.extractingPath("$.contentType").isEqualTo(MediaType.TEXT_PLAIN_VALUE);
        bodyJson.extractingPath("$.status").isEqualTo("READY");
        bodyJson.extractingPath("$.createdAt").isEqualTo(createdAt.toString());

        var nsCaptor = ArgumentCaptor.forClass(NamespaceContext.class);
        var requestCaptor = ArgumentCaptor.forClass(FileQueryUseCase.GetFileRequest.class);

        verify(fileQueryUseCase).getFile(nsCaptor.capture(), requestCaptor.capture());
        assertThat(nsCaptor.getValue().serviceId()).isEqualTo(SERVICE_ID);
        assertThat(requestCaptor.getValue().fileId()).isEqualTo(fileId);
    }

    @Test
    void getFilesReturnsReducedMetadataList() {
        var fileId = UUID.randomUUID();
        var createdAt = Instant.parse("2000-01-02T00:00:00Z");
        var nextCursor = "bmV4dC1jdXJzb3I";

        when(fileQueryUseCase.listFiles(any(), any()))
                .thenReturn(new FileQueryUseCase.ListFilesResult(
                        List.of(
                                new FileQueryUseCase.FileMetadata(
                                        fileId,
                                        SERVICE_ID,
                                        "bucket-main",
                                        "uploads/" + fileId,
                                        "document.txt",
                                        128L,
                                        "sha-256",
                                        "etag-123",
                                        MediaType.TEXT_PLAIN_VALUE,
                                        dev.dentron.filestorage.domain.FileObject.Status.READY,
                                        createdAt
                                )
                        ),
                        nextCursor
                ));

        var request = mockMvc.get()
                .uri("/v1/files")
                .with(serviceAuthentication());

        var bodyJson = assertThat(request)
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson();

        bodyJson.extractingPath("$.items[0].fileId").isEqualTo(fileId.toString());
        bodyJson.extractingPath("$.items[0].originalName").isEqualTo("document.txt");
        bodyJson.extractingPath("$.items[0].status").isEqualTo("READY");
        bodyJson.extractingPath("$.items[0].createdAt").isEqualTo(createdAt.toString());
        bodyJson.extractingPath("$.nextCursor").isEqualTo(nextCursor);

        var nsCaptor = ArgumentCaptor.forClass(NamespaceContext.class);
        var requestCaptor = ArgumentCaptor.forClass(FileQueryUseCase.ListFilesRequest.class);

        verify(fileQueryUseCase).listFiles(nsCaptor.capture(), requestCaptor.capture());
        assertThat(nsCaptor.getValue().serviceId()).isEqualTo(SERVICE_ID);
        assertThat(requestCaptor.getValue().cursor()).isNull();
        assertThat(requestCaptor.getValue().limit()).isNull();
    }

    @Test
    void getFilesPassesCursorAndLimit() {
        when(fileQueryUseCase.listFiles(any(), any()))
                .thenReturn(new FileQueryUseCase.ListFilesResult(List.of(), null));

        var request = mockMvc.get()
                .uri("/v1/files?cursor={cursor}&limit={limit}", "encoded-cursor", 25)
                .with(serviceAuthentication());

        assertThat(request).hasStatusOk();

        var nsCaptor = ArgumentCaptor.forClass(NamespaceContext.class);
        var requestCaptor = ArgumentCaptor.forClass(FileQueryUseCase.ListFilesRequest.class);

        verify(fileQueryUseCase).listFiles(nsCaptor.capture(), requestCaptor.capture());
        assertThat(nsCaptor.getValue().serviceId()).isEqualTo(SERVICE_ID);
        assertThat(requestCaptor.getValue().cursor()).isEqualTo("encoded-cursor");
        assertThat(requestCaptor.getValue().limit()).isEqualTo(25);
    }

    @Test
    void deleteFileReturnsNoContent() {
        var fileId = UUID.randomUUID();

        var request = mockMvc.delete()
                .uri("/v1/files/{fileId}", fileId)
                .with(serviceAuthentication());

        assertThat(request)
                .hasStatus(HttpStatus.NO_CONTENT)
                .body().isEmpty();

        var nsCaptor = ArgumentCaptor.forClass(NamespaceContext.class);
        var requestCaptor = ArgumentCaptor.forClass(DeleteFileUseCase.DeleteFileRequest.class);

        verify(deleteFileUseCase).deleteFile(nsCaptor.capture(), requestCaptor.capture());
        assertThat(nsCaptor.getValue().serviceId()).isEqualTo(SERVICE_ID);
        assertThat(requestCaptor.getValue().fileId()).isEqualTo(fileId);
    }
}
