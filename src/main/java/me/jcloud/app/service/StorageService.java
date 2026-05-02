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

    public Resource loadAsResource(UUID fileId) {
        try {
            Path file = Paths.get(storageLocation).resolve(fileId.toString());
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

    public boolean deleteFile(UUID fileId) throws IOException {
        return Files.deleteIfExists(Paths.get(storageLocation).resolve(fileId.toString()));
    }
}