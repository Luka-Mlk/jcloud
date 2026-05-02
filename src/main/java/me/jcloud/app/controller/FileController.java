package me.jcloud.app.controller;

import me.jcloud.app.dto.FileResponse;
import me.jcloud.app.exception.ResourceNotFoundException;
import me.jcloud.app.model.FileMetadata;
import me.jcloud.app.repository.FileMetadataRepository;
import me.jcloud.app.service.StorageService;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
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
            @RequestParam("file") MultipartFile file,
            @RequestAttribute("authenticatedUserId") UUID userId) throws IOException {

        FileMetadata metadata = new FileMetadata();
        metadata.setUserId(userId);
        metadata.setOriginalFilename(file.getOriginalFilename());
        metadata.setFileSize(file.getSize());
        metadata.setContentType(file.getContentType());
        metadata.setUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));

        FileMetadata saved = repository.save(metadata);

        try {
            storageService.saveFile(saved.getId(), file.getInputStream());
        } catch (IOException e) {
            repository.delete(saved);
            throw e;
        }

        return mapToResponse(saved);
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