package me.jcloud.app.repository;

import me.jcloud.app.model.Bucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BucketRepository extends JpaRepository<Bucket, UUID> {
    Optional<Bucket> findByName(String name);
    List<Bucket> findAllByOwnerId(UUID ownerId);
    Optional<Bucket> findByNameAndOwnerId(String name, UUID ownerId);
    long countByOwnerId(UUID ownerId);
}
