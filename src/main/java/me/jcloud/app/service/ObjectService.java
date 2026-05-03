package me.jcloud.app.service;

import lombok.RequiredArgsConstructor;
import me.jcloud.app.dto.ObjectResponse;
import me.jcloud.app.model.Bucket;
import me.jcloud.app.model.FileMetadata;
import me.jcloud.app.repository.FileMetadataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ObjectService {

    private final FileMetadataRepository repository;
    private final StorageService storageService;

    /**
     * Handles the atomic save of metadata and the physical file.
     */
    @Transactional
    public ObjectResponse storeObject(Bucket bucket, String path, String contentType, InputStream inputStream, UUID userId) throws IOException {
        FileMetadata record = repository.findByBucketAndPath(bucket, path)
                .orElseGet(() -> FileMetadata.builder()
                        .userId(userId)
                        .bucket(bucket)
                        .path(path)
                        .build());

        boolean isNew = (record.getId() == null);
        record.setContentType(contentType);
        record.setUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));
        record.setFileSize(0L);

        FileMetadata saved = repository.save(record);

        try {
            long size = storageService.saveFile(saved.getId(), inputStream);
            saved.setFileSize(size);
            repository.save(saved);
            return mapToResponse(saved);
        } catch (IOException e) {
            if (isNew) {
                storageService.deleteFileQuietly(saved.getId());
            }
            throw e;
        }
    }

    /**
     * Handles the atomic deletion of metadata and the physical file.
     */
    @Transactional
    public void deleteObject(FileMetadata metadata) {
        repository.delete(metadata);

        storageService.deleteFileQuietly(metadata.getId());
    }

    /**
     * Finalizes a multipart upload by merging segments and cleaning up.
     */
    @Transactional
    public ObjectResponse completeMultipartUpload(FileMetadata record, UUID uploadId, int totalParts) throws IOException {
        FileMetadata saved = repository.save(record);

        try {
            long totalSize = storageService.mergeParts(uploadId, saved.getId(), totalParts);
            saved.setFileSize(totalSize);
            repository.save(saved);

            storageService.cleanupUpload(uploadId);

            return mapToResponse(saved);
        } catch (IOException e) {
            storageService.deleteFileQuietly(saved.getId());
            throw e;
        }
    }

    private ObjectResponse mapToResponse(FileMetadata file) {
        return ObjectResponse.builder()
                .id(file.getId())
                .path(file.getPath())
                .contentType(file.getContentType())
                .fileSize(file.getFileSize())
                .uploadedAt(file.getUploadedAt())
                .build();
    }
}
