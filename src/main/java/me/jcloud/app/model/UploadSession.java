package me.jcloud.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bucket_id", nullable = false)
    private Bucket bucket;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private String status; // IN_PROGRESS, COMPLETED, ABORTED

    @Column(nullable = false)
    private OffsetDateTime createdAt;
}
