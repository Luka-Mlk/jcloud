package me.jcloud.app.service;

import me.jcloud.app.exception.ConflictException;
import me.jcloud.app.exception.ResourceNotFoundException;
import me.jcloud.app.model.Bucket;
import me.jcloud.app.repository.BucketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class BucketService {

    private final BucketRepository repository;

    public BucketService(BucketRepository repository) {
        this.repository = repository;
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

        return repository.save(bucket);
    }

    public Bucket getBucket(String name, UUID ownerId) {
        return repository.findByNameAndOwnerId(name, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Bucket not found: " + name));
    }

    @Transactional
    public void deleteBucket(String name, UUID ownerId) {
        Bucket bucket = getBucket(name, ownerId);
        // In a real S3 clone, we should check if the bucket is empty
        repository.delete(bucket);
    }
}
