package me.jcloud.app.repository;

import me.jcloud.app.model.FileMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {
    Page<FileMetadata> findAllByUserId(UUID userId, Pageable pageable);
    Optional<FileMetadata> findByIdAndUserId(UUID id, UUID userId);
}
