package dev.dentron.filestorage.persistence;

import dev.dentron.filestorage.domain.FileObject;
import dev.dentron.filestorage.persistence.adapter.FileObjectPersistenceAdapter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5");

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

        var readyView = fileRepository.tryMarkReady(file.getId(), "text/plain", 123L);
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

        var readyView = fileRepository.tryMarkReady(file.getId(), "text/plain", 123L);
        entityManager.flush();
        entityManager.clear();

        assertThat(readyView).isPresent();

        var reloaded = fileRepository.findById(file.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(FileObject.Status.READY);
        assertThat(reloaded.get().getContentType()).isEqualTo("text/plain");
        assertThat(reloaded.get().getSize()).isEqualTo(123L);
    }

    @Test
    @Transactional
    public void testTryMarkDeletedFromReadyShouldTransition() {
        FileObject file = createNewFile();
        fileRepository.tryMarkUploaded(file.getId(), "etag-3");
        fileRepository.tryMarkReady(file.getId(), "text/plain", 123L);

        Instant deletedAt = Instant.now();
        var deletedView = fileRepository.tryMarkDeleted(file.getId(), deletedAt);
        entityManager.flush();
        entityManager.clear();

        assertThat(deletedView).isPresent();

        var reloaded = fileRepository.findById(file.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(FileObject.Status.DELETED);
    }

    @Test
    @Transactional
    public void testScrollByOwnerReturnsNextCursor() {
        FileObject alpha = createNewFile("service-a", "alpha.txt");
        FileObject beta = createNewFile("service-a", "beta.txt");
        FileObject gamma = createNewFile("service-a", "gamma.txt");
        FileObject delta = createNewFile("service-a", "delta.txt");
        createNewFile("service-b", "foreign.txt");
        fileRepository.tryMarkDeleted(beta.getId(), Instant.now());

        entityManager.flush();
        entityManager.clear();

        var firstPage = fileRepository.scrollByOwner("service-a", null, 2);

        assertThat(firstPage.items()).extracting(FileObject::getId)
                .containsExactly(alpha.getId(), gamma.getId());
        assertThat(firstPage.nextCursor()).isNotNull();

        var secondPage = fileRepository.scrollByOwner("service-a", firstPage.nextCursor(), 2);

        assertThat(secondPage.items()).extracting(FileObject::getId)
                .containsExactly(delta.getId());
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    @Transactional
    public void testScrollByOwnerSupportsOffsetAndLimit() {
        createNewFile(numUuid(1_111),"service-b", "foreign.txt");
        FileObject alpha = createNewFile(numUuid(1), "service-a", "alpha.txt");
        FileObject beta = createNewFile(numUuid(2),"service-a", "beta.txt");
        FileObject gamma = createNewFile(numUuid(3), "service-a", "gamma.txt");
        FileObject delta = createNewFile(numUuid(4), "service-a", "delta.txt");
        fileRepository.tryMarkDeleted(beta.getId(), Instant.now());

        entityManager.flush();
        entityManager.clear();

        var page = fileRepository.findLimitByOwner("service-a", 1L, 2);

        assertThat(page).extracting(FileObject::getId)
                .containsExactly(gamma.getId(), delta.getId());
        assertThat(page).extracting(FileObject::getOwner)
                .containsOnly("service-a");
    }

    private static UUID numUuid(int num) {
        if (num < 0) {
            throw new IllegalArgumentException("num must be >= 0");
        }

        String s = "%032d".formatted(num);

        return UUID.fromString(
                s.substring(0, 8) + "-" +
                        s.substring(8, 12) + "-" +
                        s.substring(12, 16) + "-" +
                        s.substring(16, 20) + "-" +
                        s.substring(20)
        );
    }

    private FileObject createNewFile() {
        return createNewFile("service-a", "example.png");
    }

    private FileObject createNewFile(UUID fileId, String owner, String originalName) {
        FileObject file = new FileObject(fileId, owner, "obj/" + fileId, originalName, "bucket-main");
        return fileRepository.save(file);
    }

    private FileObject createNewFile(String owner, String originalName) {
        UUID fileId = UUID.randomUUID();
        return createNewFile(fileId, owner, originalName);
    }
}
