package me.jcloud.app.repository;

import io.lettuce.core.dynamic.annotation.Param;
import me.jcloud.app.model.Bucket;
import me.jcloud.app.model.FileMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {
    Page<FileMetadata> findAllByUserId(UUID userId, Pageable pageable);
    Optional<FileMetadata> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM FileMetadata f WHERE f.userId = :userId")
    long getTotalSizeByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(f) FROM FileMetadata f WHERE f.userId = :userId")
    long countByUserId(@Param("userId") UUID userId);

    Optional<FileMetadata> findByBucketAndPath(Bucket bucket, String path);
    Page<FileMetadata> findAllByBucket(Bucket bucket, Pageable pageable);

    boolean existsByBucket_Id(UUID bucketId);
}
