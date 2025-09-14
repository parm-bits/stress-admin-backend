package com.example.stress_admin_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${storage.base-dir}")
    private String baseDir;

    private Path jmxDir;
    private Path csvDir;
    private Path resultsDir;
    private Path reportsDir;

    @PostConstruct
    public void init() throws IOException {
        Path base = Paths.get(baseDir);
        Files.createDirectories(base);

        jmxDir = base.resolve("jmx"); Files.createDirectories(jmxDir);
        csvDir = base.resolve("csv"); Files.createDirectories(csvDir);
        resultsDir = base.resolve("results"); Files.createDirectories(resultsDir);
        reportsDir = base.resolve("reports"); Files.createDirectories(reportsDir);
    }

    public String storeJmx(MultipartFile file) throws IOException {
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path target = jmxDir.resolve(filename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toString();
    }

    public String storeCsv(MultipartFile file) throws IOException {
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path target = csvDir.resolve(filename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toString();
    }

    public Path getReportsDir() { return reportsDir; }
    public Path getResultsDir() { return resultsDir; }

    public Resource loadJmxAsResource(String jmxPath) throws MalformedURLException {
        Path file = Paths.get(jmxPath);
        Resource resource = new UrlResource(file.toUri());
        if (resource.exists() || resource.isReadable()) {
            return resource;
        } else {
            throw new RuntimeException("Could not read JMX file: " + jmxPath);
        }
    }

    public Resource loadCsvAsResource(String csvPath) throws MalformedURLException {
        Path file = Paths.get(csvPath);
        Resource resource = new UrlResource(file.toUri());
        if (resource.exists() || resource.isReadable()) {
            return resource;
        } else {
            throw new RuntimeException("Could not read CSV file: " + csvPath);
        }
    }
}
