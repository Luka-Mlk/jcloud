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
import me.jcloud.app.service.ObjectService;
import me.jcloud.app.service.StorageService;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.springframework.core.io.support.ResourceRegion;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/objects/{bucketName}")
public class ObjectController {

    private final FileMetadataRepository repository;
    private final UploadSessionRepository uploadSessionRepository;
    private final StorageService storageService;
    private final BucketService bucketService;
    private final ObjectService objectService;

    public ObjectController(FileMetadataRepository repository,
                            UploadSessionRepository uploadSessionRepository,
                            StorageService storageService,
                            BucketService bucketService,
                            ObjectService objectService) {
        this.repository = repository;
        this.uploadSessionRepository = uploadSessionRepository;
        this.storageService = storageService;
        this.bucketService = bucketService;
        this.objectService = objectService;
    }

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
                try (InputStream inputStream = item.getInputStream()) {
                    return objectService.storeObject(bucket, item.getName(), item.getContentType(), inputStream, userId);
                }
            }
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file found in request");
    }

    @PutMapping("/{*key}")
    public ObjectResponse uploadRaw(
            @PathVariable String bucketName,
            @PathVariable String key,
            HttpServletRequest request,
            @RequestAttribute("authenticatedUserId") UUID userId) throws IOException {

        Bucket bucket = bucketService.getBucket(bucketName, userId);
        String cleanedKey = cleanKey(key);

        try (InputStream inputStream = request.getInputStream()) {
            return objectService.storeObject(bucket, cleanedKey, request.getContentType(), inputStream, userId);
        }
    }

    @GetMapping("/{*key}")
    public ResponseEntity<ResourceRegion> download(
            @PathVariable String bucketName,
            @PathVariable String key,
            @RequestHeader HttpHeaders headers,
            @RequestAttribute("authenticatedUserId") UUID userId) throws IOException {

        Bucket bucket = bucketService.getBucket(bucketName, userId);
        String cleanedKey = cleanKey(key);

        FileMetadata metadata = repository.findByBucketAndPath(bucket, cleanedKey)
                .orElseThrow(() -> new ResourceNotFoundException("Object not found"));

        ResourceRegion region = storageService.getResourceRegion(metadata.getId(), headers);

        // If client asked for a range, return 206, otherwise 200.
        HttpStatus status = headers.getRange().isEmpty() ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT;

        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType(metadata.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.getPath() + "\"")
                .body(region);
    }

    @DeleteMapping("/{*key}")
    public ResponseEntity<Void> delete(
            @PathVariable String bucketName,
            @PathVariable String key,
            @RequestAttribute("authenticatedUserId") UUID userId) {

        Bucket bucket = bucketService.getBucket(bucketName, userId);
        String cleanedKey = cleanKey(key);

        FileMetadata metadata = repository.findByBucketAndPath(bucket, cleanedKey)
                .orElseThrow(() -> new ResourceNotFoundException("Object not found: " + cleanedKey));

        objectService.deleteObject(metadata);
        return ResponseEntity.noContent().build();
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

        FileMetadata record = repository.findByBucketAndPath(bucket, session.getPath())
                .orElseGet(() -> FileMetadata.builder()
                        .userId(userId)
                        .bucket(bucket)
                        .path(session.getPath())
                        .build());

        record.setContentType(session.getContentType());
        record.setUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));

        ObjectResponse response = objectService.completeMultipartUpload(record, uploadId, request.getTotalParts());

        session.setStatus("COMPLETED");
        uploadSessionRepository.save(session);

        return response;
    }

    @GetMapping
    public Page<ObjectResponse> list(
            @PathVariable String bucketName,
            @RequestAttribute("authenticatedUserId") UUID userId,
            @PageableDefault(sort = "uploadedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Bucket bucket = bucketService.getBucket(bucketName, userId);
        return repository.findAllByBucket(bucket, pageable).map(this::mapToResponse);
    }

    @PostMapping("/sessions")
    public UploadInitResponse initUpload(
            @PathVariable String bucketName,
            @Valid @RequestBody UploadInitRequest request,
            @RequestAttribute("authenticatedUserId") UUID userId) {
        Bucket bucket = bucketService.getBucket(bucketName, userId);

        // Ensure path is clean before checking sessions
        String path = cleanKey(request.getPath());

        return uploadSessionRepository
                .findByUserIdAndBucketAndPathAndStatus(userId, bucket, path, "IN_PROGRESS")
                .map(existing -> {
                    try {
                        List<Integer> parts = storageService.getExistingPartNumbers(existing.getId());
                        return new UploadInitResponse(existing.getId(), parts);
                    } catch (IOException e) {
                        return new UploadInitResponse(existing.getId(), List.of());
                    }
                })
                .orElseGet(() -> {
                    UploadSession session = UploadSession.builder()
                            .userId(userId)
                            .bucket(bucket)
                            .path(path)
                            .contentType(request.getContentType())
                            .status("IN_PROGRESS")
                            .build();
                    UploadSession saved = uploadSessionRepository.save(session);
                    return new UploadInitResponse(saved.getId(), List.of());
                });
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

    private String cleanKey(String key) {
        if (key == null) return "";
        // Removes leading slash and normalizes
        String cleaned = org.springframework.util.StringUtils.cleanPath(key);
        return cleaned.startsWith("/") ? cleaned.substring(1) : cleaned;
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
