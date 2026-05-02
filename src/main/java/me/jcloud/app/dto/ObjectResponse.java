package me.jcloud.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({ "id", "bucket_name", "path", "content_type", "file_size", "uploaded_at" })
public class ObjectResponse {
    private UUID id;

    @JsonProperty("user_id")
    private UUID userId;

    @JsonProperty("bucket_name")
    private String bucketName;

    @JsonProperty("path")
    private String path;

    @JsonProperty("content_type")
    private String contentType;

    @JsonProperty("file_size")
    private Long fileSize;

    @JsonProperty("uploaded_at")
    private OffsetDateTime uploadedAt;
}