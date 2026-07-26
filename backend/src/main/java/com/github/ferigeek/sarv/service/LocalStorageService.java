package com.github.ferigeek.sarv.service;

import com.github.ferigeek.sarv.dto.response.StoredObject;
import com.github.ferigeek.sarv.exception.StorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class LocalStorageService implements ObjectStorageService {

    private final Path storageDir;

    public LocalStorageService(@Value("${storage.local.dir:uploads}") String dir) {
        this.storageDir = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException e) {
            throw new StorageException("Could not create storage directory: " + this.storageDir, e);
        }
    }

    @Override
    public StoredObject uploadObject(byte[] bytes, String mimeType) {
        String sha256 = sha256(bytes);
        Path filePath = storageDir.resolve(sha256);

        try {
            Files.write(filePath, bytes, StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException e) {
            // same content already stored — dedup
        } catch (IOException e) {
            throw new StorageException("Failed to store file", e);
        }

        return new StoredObject(sha256, mimeType, bytes.length, sha256);
    }

    @Override
    public Resource download(String objectKey) {
        try {
            Path filePath = storageDir.resolve(objectKey).normalize();

            if (!filePath.startsWith(storageDir)) {
                throw new StorageException("Invalid object key: " + objectKey);
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new StorageException("File not found: " + objectKey);
            }
            return resource;
        } catch (IOException e) {
            throw new StorageException("Failed to read file: " + objectKey, e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Path filePath = storageDir.resolve(objectKey).normalize();

            if (!filePath.startsWith(storageDir)) {
                throw new StorageException("Invalid object key: " + objectKey);
            }

            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new StorageException("Failed to delete file: " + objectKey, e);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new StorageException("SHA-256 algorithm not available", e);
        }
    }
}
