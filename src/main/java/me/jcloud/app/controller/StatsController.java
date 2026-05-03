package me.jcloud.app.controller;

import lombok.RequiredArgsConstructor;
import me.jcloud.app.repository.BucketRepository;
import me.jcloud.app.repository.FileMetadataRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {
    private final FileMetadataRepository fileMetadataRepository;
    private final BucketRepository bucketRepository;

    @GetMapping("/summary")
    public Map<String, Object> getSummary(@RequestAttribute("authenticatedUserId") UUID userId) {
        return Map.of(
                "totalBytes", fileMetadataRepository.getTotalSizeByUserId(userId),
                "totalObjects", fileMetadataRepository.countByUserId(userId),
                "totalBuckets", bucketRepository.countByOwnerId(userId)
        );
    }
}
