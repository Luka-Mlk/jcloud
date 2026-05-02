package me.jcloud.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import me.jcloud.app.dto.*;
import me.jcloud.app.exception.ResourceNotFoundException;
import me.jcloud.app.model.FileMetadata;
import me.jcloud.app.model.UploadSession;
import me.jcloud.app.repository.FileMetadataRepository;
import me.jcloud.app.repository.UploadSessionRepository;
import me.jcloud.app.service.StorageService;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.springframework.core.io.Resource;
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
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileMetadataRepository repository;
    private final UploadSessionRepository uploadSessionRepository;
    private final StorageService storageService;

    public FileController(FileMetadataRepository repository,
                          UploadSessionRepository uploadSessionRepository,
                          StorageService storageService) {
        this.repository = repository;
        this.uploadSessionRepository = uploadSessionRepository;
        this.storageService = storageService;
    }

    @PostMapping
    public FileResponse upload(
            HttpServletRequest request,
            @RequestAttribute("authenticatedUserId") UUID userId) throws IOException {

        if (!JakartaServletFileUpload.isMultipartContent(request)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Multipart request expected");
        }

        JakartaServletFileUpload upload = new JakartaServletFileUpload();
        FileItemInputIterator iter = upload.getItemIterator(request);

        while (iter.hasNext()) {
            FileItemInput item = iter.next();
            if (!item.isFormField()) {
                // Found a file part
                FileMetadata metadata = new FileMetadata();
                metadata.setUserId(userId);
                metadata.setOriginalFilename(item.getName());
                metadata.setContentType(item.getContentType());
                metadata.setUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));
                metadata.setFileSize(0L); // Will update after saving

                FileMetadata saved = repository.save(metadata);

                try (InputStream inputStream = item.getInputStream()) {
                    long size = storageService.saveFile(saved.getId(), inputStream);
                    saved.setFileSize(size);
                    repository.save(saved);
                    return mapToResponse(saved);
                } catch (IOException e) {
                    repository.delete(saved);
                    throw e;
                }
            }
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file found in request");
    }

    @PostMapping("/init")
    public UploadInitResponse initUpload(
            @Valid @RequestBody UploadInitRequest request,
            @RequestAttribute("authenticatedUserId") UUID userId) {

        UploadSession session = UploadSession.builder()
                .userId(userId)
                .originalFilename(request.getFilename())
                .contentType(request.getContentType())
                .status("IN_PROGRESS")
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        UploadSession saved = uploadSessionRepository.save(session);
        return new UploadInitResponse(saved.getId());
    }

    @PutMapping("/{uploadId}/part/{partNumber}")
    public ResponseEntity<Void> uploadPart(
            @PathVariable UUID uploadId,
            @PathVariable int partNumber,
            HttpServletRequest request,
            @RequestAttribute("authenticatedUserId") UUID userId) throws IOException {

        uploadSessionRepository.findByIdAndUserId(uploadId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload session not found"));

        try (InputStream inputStream = request.getInputStream()) {
            storageService.savePart(uploadId, partNumber, inputStream);
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{uploadId}/complete")
    public FileResponse completeUpload(
            @PathVariable UUID uploadId,
            @Valid @RequestBody UploadCompleteRequest request,
            @RequestAttribute("authenticatedUserId") UUID userId) throws IOException {

        UploadSession session = uploadSessionRepository.findByIdAndUserId(uploadId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload session not found"));

        FileMetadata metadata = new FileMetadata();
        metadata.setUserId(userId);
        metadata.setOriginalFilename(session.getOriginalFilename());
        metadata.setContentType(session.getContentType());
        metadata.setUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));
        metadata.setFileSize(0L);

        FileMetadata saved = repository.save(metadata);

        try {
            long totalSize = storageService.mergeParts(uploadId, saved.getId(), request.getTotalParts());
            saved.setFileSize(totalSize);
            repository.save(saved);

            session.setStatus("COMPLETED");
            uploadSessionRepository.save(session);
            storageService.cleanupUpload(uploadId);

            return mapToResponse(saved);
        } catch (Exception e) {
            repository.delete(saved);
            throw e;
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable UUID id,
            @RequestAttribute("authenticatedUserId") UUID userId) {

        FileMetadata metadata = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + id));

        Resource resource = storageService.loadAsResource(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + metadata.getOriginalFilename() + "\"")
                .body(resource);
    }

    @GetMapping
    public Page<FileResponse> list(
            @RequestAttribute("authenticatedUserId") UUID userId,
            @PageableDefault(sort = "uploadedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return repository.findAllByUserId(userId, pageable).map(this::mapToResponse);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestAttribute("authenticatedUserId") UUID userId) {

        FileMetadata metadata = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + id));

        try {
            storageService.deleteFile(id);
            repository.delete(metadata);
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private FileResponse mapToResponse(FileMetadata file) {
        return FileResponse.builder()
                .id(file.getId())
                .userId(file.getUserId())
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getFileSize())
                .uploadedAt(file.getUploadedAt())
                .build();
    }
}