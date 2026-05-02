package me.jcloud.app.controller;

import me.jcloud.app.model.Bucket;
import me.jcloud.app.service.BucketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/buckets")
public class BucketController {

    private final BucketService bucketService;

    public BucketController(BucketService bucketService) {
        this.bucketService = bucketService;
    }

    @GetMapping
    public List<Bucket> list(@RequestAttribute("authenticatedUserId") UUID userId) {
        return bucketService.listBuckets(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Bucket create(@RequestParam String name, @RequestAttribute("authenticatedUserId") UUID userId) {
        return bucketService.createBucket(name, userId);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name, @RequestAttribute("authenticatedUserId") UUID userId) {
        bucketService.deleteBucket(name, userId);
        return ResponseEntity.noContent().build();
    }
}
