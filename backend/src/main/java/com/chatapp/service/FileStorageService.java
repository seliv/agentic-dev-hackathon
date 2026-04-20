package com.chatapp.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    private final Path uploadDir;

    public FileStorageService(@Value("${chatapp.upload-dir:./uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + uploadDir, e);
        }
    }

    public String store(MultipartFile file) {
        validateFileSize(file);

        String extension = getExtension(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + extension;
        LocalDate now = LocalDate.now();
        String relativePath = now.getYear() + "/" + String.format("%02d", now.getMonthValue()) + "/" + storedName;

        Path targetDir = uploadDir.resolve(now.getYear() + "/" + String.format("%02d", now.getMonthValue()));
        try {
            Files.createDirectories(targetDir);
            Path targetPath = uploadDir.resolve(relativePath);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }

        return relativePath;
    }

    public Resource load(String storagePath) {
        try {
            Path filePath = uploadDir.resolve(storagePath).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) {
                throw new RuntimeException("File not found: " + storagePath);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new RuntimeException("File not found: " + storagePath, e);
        }
    }

    public void delete(String storagePath) {
        try {
            Path filePath = uploadDir.resolve(storagePath).normalize();
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", storagePath, e);
        }
    }

    private void validateFileSize(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && contentType.startsWith("image/") && file.getSize() > 3 * 1024 * 1024) {
            throw new IllegalArgumentException("Image files must not exceed 3MB");
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

}
