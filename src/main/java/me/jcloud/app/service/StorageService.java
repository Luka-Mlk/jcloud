package me.jcloud.app.service;

import me.jcloud.app.exception.ResourceNotFoundException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.MalformedURLException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;

@Service
public class StorageService {

    @Value("${storage.location:./data/storage}")
    private String storageLocation;

    public long saveFile(UUID fileId, InputStream inputStream) throws IOException {
        Path root = Paths.get(storageLocation);
        if (!Files.exists(root)) {
            Files.createDirectories(root);
        }

        return Files.copy(inputStream, root.resolve(fileId.toString()), StandardCopyOption.REPLACE_EXISTING);
    }

    public void savePart(UUID uploadId, int partNumber, InputStream inputStream) throws IOException {
        Path uploadDir = Paths.get(storageLocation).resolve("uploads").resolve(uploadId.toString());
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        Path partFile = uploadDir.resolve(String.valueOf(partNumber));
        Files.copy(inputStream, partFile, StandardCopyOption.REPLACE_EXISTING);
    }

    public long mergeParts(UUID uploadId, UUID fileId, int totalParts) throws IOException {
        Path uploadDir = Paths.get(storageLocation).resolve("uploads").resolve(uploadId.toString());
        Path finalFile = Paths.get(storageLocation).resolve(fileId.toString());

        if (!Files.exists(Paths.get(storageLocation))) {
            Files.createDirectories(Paths.get(storageLocation));
        }

        try (java.io.OutputStream out = Files.newOutputStream(finalFile)) {
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

    public void cleanupUpload(UUID uploadId) throws IOException {
        Path uploadDir = Paths.get(storageLocation).resolve("uploads").resolve(uploadId.toString());
        if (Files.exists(uploadDir)) {
            try (java.util.stream.Stream<Path> walk = Files.walk(uploadDir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors or log them
                        }
                    });
            }
        }
    }

    public Resource loadAsResource(UUID fileId) {
        try {
            Path file = getFilePath(fileId);
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

    public Path getFilePath(UUID fileId) {
        return Paths.get(storageLocation).resolve(fileId.toString());
    }

    public boolean deleteFile(UUID fileId) throws IOException {
        return Files.deleteIfExists(getFilePath(fileId));
    }
}