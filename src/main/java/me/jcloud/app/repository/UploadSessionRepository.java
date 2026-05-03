package me.jcloud.app.repository;

import me.jcloud.app.model.Bucket;
import me.jcloud.app.model.UploadSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {
    Optional<UploadSession> findByIdAndUserId(UUID id, UUID userId);
    Optional<UploadSession> findByUserIdAndBucketAndPathAndStatus(
            UUID userId, Bucket bucket, String path, String status
    );
}
