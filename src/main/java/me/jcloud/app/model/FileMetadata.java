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
@JsonPropertyOrder({ "id", "originalFilename", "contentType", "fileSize", "uploadedAt" })
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String originalFilename;

    private String contentType;

    private Long fileSize;

    @Column(nullable = false)
    private OffsetDateTime uploadedAt;
}