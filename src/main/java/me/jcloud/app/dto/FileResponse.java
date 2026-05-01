package me.jcloud.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@JsonPropertyOrder({ "id", "original_filename", "content_type", "file_size", "uploaded_at" })
public class FileResponse {
    private UUID id;

    @JsonProperty("user_id")
    private UUID userId;

    @JsonProperty("original_filename")
    private String originalFilename;

    @JsonProperty("content_type")
    private String contentType;

    @JsonProperty("file_size")
    private Long fileSize;

    @JsonProperty("uploaded_at")
    private OffsetDateTime uploadedAt;
}