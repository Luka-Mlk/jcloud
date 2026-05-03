package me.jcloud.app.service;

import me.jcloud.app.model.Bucket;
import me.jcloud.app.repository.BucketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BucketServiceTest {

    @Mock
    private BucketRepository repository;

    @InjectMocks
    private BucketService bucketService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void createBucket_ShouldSuccess() {
        String bucketName = "test-bucket";
        when(repository.findByName(bucketName)).thenReturn(Optional.empty());
        when(repository.save(any(Bucket.class))).thenAnswer(i -> i.getArguments()[0]);

        Bucket result = bucketService.createBucket(bucketName, userId);

        assertNotNull(result);
        assertEquals(bucketName, result.getName());
        assertEquals(userId, result.getOwnerId());
        verify(repository).save(any(Bucket.class));
    }

    @Test
    void createBucket_WhenAlreadyExists_ShouldThrowException() {
        String bucketName = "existing";
        when(repository.findByName(bucketName)).thenReturn(Optional.of(new Bucket()));

        assertThrows(IllegalArgumentException.class, () -> bucketService.createBucket(bucketName, userId));
    }
}
