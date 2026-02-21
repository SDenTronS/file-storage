package dev.dentron.filestorage.persistence;

import dev.dentron.filestorage.domain.FileObject;
import dev.dentron.filestorage.persistence.adapter.FileObjectPersistenceAdapter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = TestJpaConfiguration.class)
@AutoConfigureTestDatabase(replace =  AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "logging.level.dev.dentron=DEBUG",
        "logging.level.org.hibernate=DEBUG",
        "logging.level.org.hibernate.orm.jdbc.bind=TRACE",
        "spring.jpa.properties.hibernate.jdbc.time-zone=UTC+0"
})
@Testcontainers
public class FilePersistenceTest {

    @Autowired
    private FileObjectPersistenceAdapter fileRepository;

    @Autowired
    private EntityManager entityManager;

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:latest");
//
//    @DynamicPropertySource
//    static void dynamicProperties(DynamicPropertyRegistry registry) {
//        registry.add("spring.flyway.locations", () -> "filesystem:db/migration");
//    }

    @Test
    @Transactional
    public void testTryMarkUploadedFromUploadingShouldTransition() {
        FileObject file = createNewFile();

        var updatedView = fileRepository.tryMarkUploaded(file.getId(), "etag-1");
        entityManager.flush();
        entityManager.clear();

        assertThat(updatedView).isPresent();

        var reloaded = fileRepository.findById(file.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(FileObject.Status.UPLOADED);
        assertThat(reloaded.get().getEtag()).isEqualTo("etag-1");
    }

    @Test
    @Transactional
    public void testTryMarkReadyFromUploadingShouldNotTransition() {
        FileObject file = createNewFile();

        var readyView = fileRepository.tryMarkReady(file.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(readyView).isEmpty();

        var reloaded = fileRepository.findById(file.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(FileObject.Status.UPLOADING);
    }

    @Test
    @Transactional
    public void testTryMarkReadyAfterUploadedShouldTransition() {
        FileObject file = createNewFile();

        var uploadedView = fileRepository.tryMarkUploaded(file.getId(), "etag-2");
        assertThat(uploadedView).isPresent();

        var readyView = fileRepository.tryMarkReady(file.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(readyView).isPresent();

        var reloaded = fileRepository.findById(file.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(FileObject.Status.READY);
    }

    @Test
    @Transactional
    public void testTryMarkDeletedFromReadyShouldTransition() {
        FileObject file = createNewFile();
        fileRepository.tryMarkUploaded(file.getId(), "etag-3");
        fileRepository.tryMarkReady(file.getId());

        Instant deletedAt = Instant.now();
        var deletedView = fileRepository.tryMarkDeleted(file.getId(), deletedAt);
        entityManager.flush();
        entityManager.clear();

        assertThat(deletedView).isPresent();

        var reloaded = fileRepository.findById(file.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(FileObject.Status.DELETED);
    }

    protected FileObject createNewFile() {
        UUID fileId = UUID.randomUUID();
        FileObject file = new FileObject(fileId, "service-a", "obj/" + fileId, "example.png", "bucket-main");
        return fileRepository.save(file);
    }
}
