package me.jcloud.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import me.jcloud.app.dto.FileResponse;
import me.jcloud.app.exception.ResourceNotFoundException;
import me.jcloud.app.model.FileMetadata;
import me.jcloud.app.repository.FileMetadataRepository;
import me.jcloud.app.service.StorageService;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileMetadataRepository repository;
    private final StorageService storageService;

    public FileController(FileMetadataRepository repository, StorageService storageService) {
        this.repository = repository;
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

    @GetMapping("/{id}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable UUID id,
            @RequestAttribute("authenticatedUserId") UUID userId) {

        FileMetadata metadata = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + id));

        Resource file = storageService.loadAsResource(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.getContentType()))
                .contentLength(metadata.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + metadata.getOriginalFilename() + "\"")
                .body(file);
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