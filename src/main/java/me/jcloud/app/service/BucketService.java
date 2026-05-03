package me.jcloud.app.service;

import me.jcloud.app.exception.ConflictException;
import me.jcloud.app.exception.ResourceNotFoundException;
import me.jcloud.app.model.Bucket;
import me.jcloud.app.repository.BucketRepository;
import me.jcloud.app.repository.FileMetadataRepository;
import me.jcloud.app.repository.UploadSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class BucketService {

    private final BucketRepository repository;
    private final FileMetadataRepository fileRepository;
    private final StorageService storageService;
    private final UploadSessionRepository uploadSessionRepository;

    public BucketService(BucketRepository repository,
                         FileMetadataRepository fileRepository,
                         StorageService storageService,
                         UploadSessionRepository uploadSessionRepository) {
        this.repository = repository;
        this.fileRepository = fileRepository;
        this.storageService = storageService;
        this.uploadSessionRepository = uploadSessionRepository;
    }

    public List<Bucket> listBuckets(UUID ownerId) {
        return repository.findAllByOwnerId(ownerId);
    }

    public Bucket createBucket(String name, UUID ownerId) {
        if (repository.findByName(name).isPresent()) {
            throw new ConflictException("Bucket already exists: " + name);
        }

        Bucket bucket = Bucket.builder()
                .name(name)
                .ownerId(ownerId)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        Bucket saved = repository.save(bucket);
        storageService.createBucketDirectory(name); // Ensure folder exists on disk
        return saved;
    }

    public Bucket getBucket(String name, UUID ownerId) {
        return repository.findByNameAndOwnerId(name, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Bucket not found: " + name));
    }

    @Transactional
    public void deleteBucket(String name, UUID ownerId) {
        Bucket bucket = getBucket(name, ownerId);
        if (fileRepository.existsByBucket_Id(bucket.getId())) {
            throw new ConflictException("Bucket is not empty: " + name);
        }
        uploadSessionRepository.deleteByBucketId(bucket.getId());
        repository.delete(bucket);
        storageService.deleteBucketDirectory(name);
    }
}
