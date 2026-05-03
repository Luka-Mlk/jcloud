package me.jcloud.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import me.jcloud.app.dto.*;
import me.jcloud.app.exception.ResourceNotFoundException;
import me.jcloud.app.model.Bucket;
import me.jcloud.app.model.FileMetadata;
import me.jcloud.app.model.UploadSession;
import me.jcloud.app.repository.FileMetadataRepository;
import me.jcloud.app.repository.UploadSessionRepository;
import me.jcloud.app.service.BucketService;
import me.jcloud.app.service.StorageService;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/objects/{bucketName}")
public class ObjectController {

    private final FileMetadataRepository repository;
    private final UploadSessionRepository uploadSessionRepository;
    private final StorageService storageService;
    private final BucketService bucketService;

    public ObjectController(FileMetadataRepository repository,
            UploadSessionRepository uploadSessionRepository,
            StorageService storageService,
            BucketService bucketService) {
        this.repository = repository;
        this.uploadSessionRepository = uploadSessionRepository;
        this.storageService = storageService;
        this.bucketService = bucketService;
    }

    /**
     * Multipart form upload.
     * POST /api/v1/objects/{bucketName}
     */
    @PostMapping
    public ObjectResponse uploadMultipart(
            @PathVariable String bucketName,
            HttpServletRequest request,
            @RequestAttribute("authenticatedUserId") UUID userId) throws IOException {

        Bucket bucket = bucketService.getBucket(bucketName, userId);

        if (!JakartaServletFileUpload.isMultipartContent(request)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Multipart request expected");
        }

        JakartaServletFileUpload upload = new JakartaServletFileUpload();
        FileItemInputIterator iter = upload.getItemIterator(request);

        while (iter.hasNext()) {
            FileItemInput item = iter.next();
            if (!item.isFormField()) {
                String path = item.getName();
                FileMetadata record = repository.findByBucketAndPath(bucket, path)
                        .orElseGet(() -> FileMetadata.builder()
                                .userId(userId)
                                .bucket(bucket)
                                .path(path)
                                .build());

                record.setContentType(item.getContentType());
                record.setUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));
                record.setFileSize(0L);

                FileMetadata saved = repository.save(record);

                try (InputStream inputStream = item.getInputStream()) {
                    long size = storageService.saveFile(saved.getId(), inputStream);
                    saved.setFileSize(size);
                    repository.save(saved);
                    return mapToResponse(saved);
                } catch (IOException e) {
                    // Only delete if this was a brand-new record (no pre-existing id)
                    if (record.getId() == null)
                        repository.delete(saved);
                    throw e;
                }
            }
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file found in request");
    }

    /**
     * Raw body upload (PutObject).
     * PUT /api/v1/objects/{bucketName}/{key}
     */
    @PutMapping("/{*key}")
    public ObjectResponse uploadRaw(
            @PathVariable String bucketName,
            @PathVariable String key,
            HttpServletRequest request,
            @RequestAttribute("authenticatedUserId") UUID userId) throws IOException {

        Bucket bucket = bucketService.getBucket(bucketName, userId);
        String cleanedKey = cleanKey(key);

        boolean isNew = false;
        FileMetadata record = repository.findByBucketAndPath(bucket, cleanedKey)
                .orElseGet(() -> {
                    return FileMetadata.builder()
                            .userId(userId)
                            .bucket(bucket)
                            .path(cleanedKey)
                            .build();
                });

        isNew = record.getId() == null;
        record.setContentType(request.getContentType());
        record.setUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));
        record.setFileSize(0L);

        FileMetadata saved = repository.save(record);

        try (InputStream inputStream = request.getInputStream()) {
            long size = storageService.saveFile(saved.getId(), inputStream);
            saved.setFileSize(size);
            repository.save(saved);
            return mapToResponse(saved);
        } catch (IOException e) {
            if (isNew)
                repository.delete(saved);
            throw e;
        }
    }

    /**
     * Download object.
     * GET /api/v1/objects/{bucketName}/{key}
     */
    @GetMapping("/{*key}")
    public ResponseEntity<Resource> download(
            @PathVariable String bucketName,
            @PathVariable String key,
            @RequestAttribute("authenticatedUserId") UUID userId) {

        Bucket bucket = bucketService.getBucket(bucketName, userId);
        String cleanedKey = cleanKey(key);

        FileMetadata metadata = repository.findByBucketAndPath(bucket, cleanedKey)
                .orElseThrow(() -> new ResourceNotFoundException("Object not found: " + cleanedKey));

        Resource resource = storageService.loadAsResource(metadata.getId());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + metadata.getPath() + "\"")
                .body(resource);
    }

    /**
     * List objects in bucket.
     * GET /api/v1/objects/{bucketName}
     */
    @GetMapping
    public Page<ObjectResponse> list(
            @PathVariable String bucketName,
            @RequestAttribute("authenticatedUserId") UUID userId,
            @PageableDefault(sort = "uploadedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Bucket bucket = bucketService.getBucket(bucketName, userId);
        return repository.findAllByBucket(bucket, pageable).map(this::mapToResponse);
    }

    /**
     * Delete object.
     * DELETE /api/v1/objects/{bucketName}/{key}
     */
    @DeleteMapping("/{*key}")
    public ResponseEntity<Void> delete(
            @PathVariable String bucketName,
            @PathVariable String key,
            @RequestAttribute("authenticatedUserId") UUID userId) {

        Bucket bucket = bucketService.getBucket(bucketName, userId);
        String cleanedKey = cleanKey(key);

        FileMetadata metadata = repository.findByBucketAndPath(bucket, cleanedKey)
                .orElseThrow(() -> new ResourceNotFoundException("Object not found: " + cleanedKey));

        try {
            storageService.deleteFile(metadata.getId());
            repository.delete(metadata);
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // --- Multipart Session Endpoints ---

    @PostMapping("/sessions")
    public UploadInitResponse initUpload(
            @PathVariable String bucketName,
            @Valid @RequestBody UploadInitRequest request,
            @RequestAttribute("authenticatedUserId") UUID userId) {

        Bucket bucket = bucketService.getBucket(bucketName, userId);

        UploadSession session = UploadSession.builder()
                .userId(userId)
                .bucket(bucket)
                .path(request.getPath())
                .contentType(request.getContentType())
                .status("IN_PROGRESS")
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        UploadSession saved = uploadSessionRepository.save(session);
        return new UploadInitResponse(saved.getId());
    }

    @PutMapping("/sessions/{uploadId}/parts/{partNumber}")
    public ResponseEntity<Void> uploadPart(
            @PathVariable String bucketName,
            @PathVariable UUID uploadId,
            @PathVariable int partNumber,
            HttpServletRequest request,
            @RequestAttribute("authenticatedUserId") UUID userId) throws IOException {

        bucketService.getBucket(bucketName, userId);

        uploadSessionRepository.findByIdAndUserId(uploadId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload session not found"));

        try (InputStream inputStream = request.getInputStream()) {
            storageService.savePart(uploadId, partNumber, inputStream);
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/sessions/{uploadId}/completion")
    public ObjectResponse completeUpload(
            @PathVariable String bucketName,
            @PathVariable UUID uploadId,
            @Valid @RequestBody UploadCompleteRequest request,
            @RequestAttribute("authenticatedUserId") UUID userId) throws IOException {

        Bucket bucket = bucketService.getBucket(bucketName, userId);
        UploadSession session = uploadSessionRepository.findByIdAndUserId(uploadId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload session not found"));

        boolean isNew = false;
        FileMetadata record = repository.findByBucketAndPath(bucket, session.getPath())
                .orElseGet(() -> FileMetadata.builder()
                        .userId(userId)
                        .bucket(bucket)
                        .path(session.getPath())
                        .build());

        isNew = record.getId() == null;
        record.setContentType(session.getContentType());
        record.setUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));
        record.setFileSize(0L);

        FileMetadata saved = repository.save(record);

        try {
            long totalSize = storageService.mergeParts(uploadId, saved.getId(), request.getTotalParts());
            saved.setFileSize(totalSize);
            repository.save(saved);

            session.setStatus("COMPLETED");
            uploadSessionRepository.save(session);
            storageService.cleanupUpload(uploadId);

            return mapToResponse(saved);
        } catch (Exception e) {
            if (isNew)
                repository.delete(saved);
            throw e;
        }
    }

    private String cleanKey(String key) {
        if (key == null)
            return "";
        // Remove leading slash if present (Spring's wildcard sometimes includes it)
        return key.startsWith("/") ? key.substring(1) : key;
    }

    private ObjectResponse mapToResponse(FileMetadata file) {
        return ObjectResponse.builder()
                .id(file.getId())
                .userId(file.getUserId())
                .bucketName(file.getBucket().getName())
                .path(file.getPath())
                .contentType(file.getContentType())
                .fileSize(file.getFileSize())
                .uploadedAt(file.getUploadedAt())
                .build();
    }
}