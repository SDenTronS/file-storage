package dev.dentron.filestorage.persistence.jpa.entity;

import dev.dentron.filestorage.domain.UploadSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)

@Entity
@Table(name = "upload_session")
public class UploadSessionEntity {


    @Id
    @Column(name = "id", nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "multipart_upload_id", nullable = false)
    private String multipartUploadId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "expected_size")
    private Long expectedSize;

    @Column(name = "expected_content_type")
    private String expectedContentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UploadSession.Status status;
}
