package me.jcloud.app.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
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
@JsonPropertyOrder({ "id", "bucketId", "path", "contentType", "fileSize", "uploadedAt" })
@Table(name = "file_metadata", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"bucket_id", "path"})
})
public class FileMetadata extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bucket_id", nullable = false)
    private Bucket bucket;

    @Column(nullable = false)
    private String path;

    private String contentType;

    private Long fileSize;

    @Column(nullable = false)
    private OffsetDateTime uploadedAt;

    public UUID getBucketId() {
        return bucket != null ? bucket.getId() : null;
    }
}