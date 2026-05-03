package me.jcloud.app.service;

import me.jcloud.app.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);
    private final Path rootLocation;

    public StorageService(@Value("${storage.location:./data/storage}") String location) {
        this.rootLocation = Paths.get(location).toAbsolutePath().normalize();
    }

    public long saveFile(UUID fileId, InputStream inputStream) throws IOException {
        Path destination = resolveSafePath(fileId.toString());
        ensureDirectoryExists(destination.getParent());
        return Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    public List<Integer> getExistingPartNumbers(UUID uploadId) throws IOException {
        Path uploadDir = resolveSafePath("uploads/" + uploadId.toString());
        if (!Files.exists(uploadDir)) return Collections.emptyList();

        try (Stream<Path> stream = Files.list(uploadDir)) {
            return stream
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.matches("\\d+"))
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
        }
    }

    public void savePart(UUID uploadId, int partNumber, InputStream inputStream) throws IOException {
        Path uploadDir = resolveSafePath("uploads/" + uploadId.toString());
        ensureDirectoryExists(uploadDir);

        Path partFile = uploadDir.resolve(String.valueOf(partNumber));
        Files.copy(inputStream, partFile, StandardCopyOption.REPLACE_EXISTING);
    }

    public long mergeParts(UUID uploadId, UUID fileId, int totalParts) throws IOException {
        Path uploadDir = resolveSafePath("uploads/" + uploadId.toString());
        Path finalFile = resolveSafePath(fileId.toString());

        ensureDirectoryExists(rootLocation);

        try (OutputStream out = Files.newOutputStream(finalFile)) {
            for (int i = 1; i <= totalParts; i++) {
                Path partFile = uploadDir.resolve(String.valueOf(i));
                if (!Files.exists(partFile)) {
                    throw new IOException("Missing part " + i + " for upload " + uploadId);
                }
                Files.copy(partFile, out);
            }
        }
        return Files.size(finalFile);
    }

    public void cleanupUpload(UUID uploadId) {
        Path uploadDir = resolveSafePath("uploads/" + uploadId.toString());
        deleteRecursiveQuietly(uploadDir);
    }

    public void deleteFileQuietly(UUID fileId) {
        try {
            Files.deleteIfExists(resolveSafePath(fileId.toString()));
        } catch (IOException e) {
            log.error("Failed to delete orphaned file: {}", fileId, e);
        }
    }

    public Resource loadAsResource(UUID fileId) {
        try {
            Path file = resolveSafePath(fileId.toString());
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Could not read file: " + fileId);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Error: " + e.getMessage());
        }
    }

    /**
     * Sanitizes the input and ensures the resolved path is within the rootLocation.
     */
    private Path resolveSafePath(String key) {
        String cleanedKey = StringUtils.cleanPath(key);
        Path resolvedPath = rootLocation.resolve(cleanedKey).normalize().toAbsolutePath();

        if (!resolvedPath.startsWith(rootLocation)) {
            throw new SecurityException("Path traversal attempt detected: " + key);
        }
        return resolvedPath;
    }

    private void ensureDirectoryExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }

    private void deleteRecursiveQuietly(Path path) {
        if (Files.exists(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                log.warn("Could not delete path during cleanup: {}", p);
                            }
                        });
            } catch (IOException e) {
                log.error("Failed to walk directory for cleanup: {}", path, e);
            }
        }
    }

    public ResourceRegion getResourceRegion(UUID fileId, HttpHeaders headers) throws IOException {
        Resource resource = loadAsResource(fileId);
        long contentLength = resource.contentLength();
        List<HttpRange> ranges = headers.getRange();

        if (!ranges.isEmpty()) {
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = end - start + 1;
            return new ResourceRegion(resource, start, rangeLength);
        } else {
            return new ResourceRegion(resource, 0, contentLength);
        }
    }

    public void createBucketDirectory(String bucketName) {
        try {
            Files.createDirectories(rootLocation.resolve(bucketName));
        } catch (IOException e) {
            throw new RuntimeException("Could not create bucket directory", e);
        }
    }

    public void deleteBucketDirectory(String bucketName) {
        Path path = rootLocation.resolve(bucketName);
        try {
            if (Files.exists(path)) {
                try (var stream = Files.walk(path)) {
                    stream.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not delete physical storage for bucket", e);
        }
    }
}
