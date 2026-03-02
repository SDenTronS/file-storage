package dev.dentron.filestorage.persistence;

import dev.dentron.filestorage.domain.UploadSession;
import dev.dentron.filestorage.persistence.adapter.UploadSessionPersistenceAdapter;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MediaType;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest(webEnvironment =  SpringBootTest.WebEnvironment.NONE, classes = TestJpaConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "logging.level.dev.dentron=DEBUG",
        "logging.level.org.hibernate=DEBUG",
        "logging.level.org.hibernate.orm.jdbc.bind=TRACE",
        "spring.jpa.properties.hibernate.jdbc.time-zone=UTC+0"
})
@Testcontainers
public class SessionPersistenceTest {

    @Autowired
    private UploadSessionPersistenceAdapter uploadSessionRepository;

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
    public void testTryMarkCompletingFromCreatedShouldTransition() {
        UploadSession session = createNewSession();

        var sessionViewOpt = uploadSessionRepository.tryMarkCompleting(session.getId());
        entityManager.flush();
        entityManager.clear();
        assertThat(sessionViewOpt).isPresent();

        var sessionOpt = uploadSessionRepository.findById(session.getId());
        assertThat(sessionOpt).isPresent();
        assertThat(sessionOpt.get().getStatus()).isEqualTo(UploadSession.Status.COMPLETING);
    }

    @Test
    @Transactional
    public void testTryMarkCompletedFromCreatedShouldNotTransition() {
        UploadSession session = createNewSession();

        var sessionViewOpt = uploadSessionRepository.tryMarkCompleted(session.getId());
        entityManager.flush();
        entityManager.clear();
        assertThat(sessionViewOpt).isEmpty();

        var sessionOpt = uploadSessionRepository.findById(session.getId());
        assertThat(sessionOpt).isPresent();
        assertThat(sessionOpt.get().getStatus()).isEqualTo(UploadSession.Status.CREATED);
    }

    @Test
    @Transactional
    public void testTryCompleteAfterMarkedAborted() {
        UploadSession session = createNewSession();

        int aborted = uploadSessionRepository.markAborted(List.of(session.getId()));
        entityManager.flush();
        entityManager.clear();
        assertThat(aborted).isPositive();

        var sessionOpt = uploadSessionRepository.findById(session.getId());
        assertThat(sessionOpt).isPresent();
        session = sessionOpt.get();

        assertThat(session.getStatus()).isEqualTo(UploadSession.Status.ABORTED);

        var sessionViewOpt = uploadSessionRepository.tryMarkCompleting(session.getId());
        entityManager.flush();
        entityManager.clear();
        assertThat(sessionViewOpt).isEmpty();

        sessionOpt = uploadSessionRepository.findById(session.getId());
        assertThat(sessionOpt).isPresent();
        session = sessionOpt.get();

        assertThat(session.getStatus()).isEqualTo(UploadSession.Status.ABORTED);
    }

    @AfterAll
    public static void afterAll() {
        // debug point
        return;
    }

    protected UploadSession createNewSession() {
        UUID sessionId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Instant expires = Instant.now().plusSeconds(3600);

        UploadSession session = new UploadSession(sessionId, "multipartId", fileId, expires, MediaType.IMAGE_PNG.toString());
        return uploadSessionRepository.save(session);
    }

}
