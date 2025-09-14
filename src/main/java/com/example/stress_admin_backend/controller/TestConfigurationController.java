package com.example.stress_admin_backend.controller;

import com.example.stress_admin_backend.model.TestConfiguration;
import com.example.stress_admin_backend.repository.TestConfigurationRepository;
import com.example.stress_admin_backend.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/test-configurations")
@Tag(name = "Test Configuration Management", description = "APIs for managing test configurations with JMX and CSV files")
public class TestConfigurationController {

    private final TestConfigurationRepository repo;
    private final FileStorageService storage;

    public TestConfigurationController(TestConfigurationRepository repo, FileStorageService storage) {
        this.repo = repo;
        this.storage = storage;
    }

    @Operation(summary = "Get all test configurations", description = "Retrieve a list of all test configurations")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved test configurations",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TestConfiguration.class),
                            examples = @ExampleObject(value = "[{\"id\":\"config_123\",\"name\":\"Login Test Config\",\"description\":\"Configuration for login testing\",\"jmxPath\":\"/uploads/test.jmx\",\"csvPath\":\"/uploads/data.csv\",\"isActive\":true}]")))
    })
    @GetMapping
    public List<TestConfiguration> list() {
        return repo.findAll();
    }

    @Operation(summary = "Get active test configurations", description = "Retrieve only active test configurations")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved active test configurations")
    })
    @GetMapping("/active")
    public List<TestConfiguration> listActive() {
        return repo.findByIsActiveTrue();
    }

    @Operation(summary = "Create a new test configuration", description = "Create a new test configuration with JMX and CSV files")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Test configuration created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TestConfiguration.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @Parameter(description = "Name of the test configuration", required = true, example = "Login Test Config")
            @RequestParam String name,
            
            @Parameter(description = "Description of the test configuration", example = "Configuration for login performance testing")
            @RequestParam(required = false) String description,
            
            @Parameter(description = "JMeter test plan file (.jmx)", required = true)
            @RequestPart("jmxFile") MultipartFile jmxFile,
            
            @Parameter(description = "Test data file (.csv)", required = true)
            @RequestPart("csvFile") MultipartFile csvFile
    ) {
        try {
            // Validate inputs
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Name is required"));
            }
            
            if (jmxFile == null || jmxFile.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "JMX file is required"));
            }
            
            if (csvFile == null || csvFile.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "CSV file is required"));
            }
            
            // Validate file extensions
            String jmxFileName = jmxFile.getOriginalFilename();
            String csvFileName = csvFile.getOriginalFilename();
            
            if (jmxFileName == null || !jmxFileName.toLowerCase().endsWith(".jmx")) {
                return ResponseEntity.badRequest().body(Map.of("error", "JMX file must have .jmx extension"));
            }
            
            if (csvFileName == null || !csvFileName.toLowerCase().endsWith(".csv")) {
                return ResponseEntity.badRequest().body(Map.of("error", "CSV file must have .csv extension"));
            }
            
            String jmxPath = storage.storeJmx(jmxFile);
            String csvPath = storage.storeCsv(csvFile);

            TestConfiguration config = TestConfiguration.builder()
                    .name(name.trim())
                    .description(description != null ? description.trim() : "")
                    .jmxPath(jmxPath)
                    .csvPath(csvPath)
                    .jmxFileName(jmxFileName)
                    .csvFileName(csvFileName)
                    .jmxFileSize(jmxFile.getSize())
                    .csvFileSize(csvFile.getSize())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .isActive(true)
                    .build();

            TestConfiguration saved = repo.save(config);
            return ResponseEntity.created(new URI("/api/test-configurations/" + saved.getId())).body(saved);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create test configuration: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get test configuration by ID", description = "Retrieve a specific test configuration by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Test configuration found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TestConfiguration.class))),
            @ApiResponse(responseCode = "404", description = "Test configuration not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TestConfiguration> get(
            @Parameter(description = "Test configuration ID", required = true, example = "config_123")
            @PathVariable String id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update test configuration", description = "Update an existing test configuration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Test configuration updated successfully"),
            @ApiResponse(responseCode = "404", description = "Test configuration not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @Parameter(description = "Test configuration ID", required = true, example = "config_123")
            @PathVariable String id,
            
            @Parameter(description = "Updated test configuration data", required = true)
            @RequestBody TestConfiguration config) {
        
        Optional<TestConfiguration> existing = repo.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        TestConfiguration existingConfig = existing.get();
        existingConfig.setName(config.getName());
        existingConfig.setDescription(config.getDescription());
        existingConfig.setUpdatedAt(LocalDateTime.now());
        existingConfig.setIsActive(config.getIsActive());
        
        TestConfiguration saved = repo.save(existingConfig);
        return ResponseEntity.ok(saved);
    }

    @Operation(summary = "Delete test configuration", description = "Delete a test configuration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Test configuration deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Test configuration not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @Parameter(description = "Test configuration ID", required = true, example = "config_123")
            @PathVariable String id) {
        
        if (!repo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        
        repo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Test configuration deleted successfully"));
    }

    @Operation(summary = "Search test configurations", description = "Search test configurations by name, JMX filename, or CSV filename")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results retrieved successfully")
    })
    @GetMapping("/search")
    public ResponseEntity<List<TestConfiguration>> search(
            @Parameter(description = "Search term for name, JMX filename, or CSV filename", example = "login")
            @RequestParam String query) {
        
        List<TestConfiguration> results = repo.findByNameContainingIgnoreCase(query);
        results.addAll(repo.findByJmxFileNameContainingIgnoreCase(query));
        results.addAll(repo.findByCsvFileNameContainingIgnoreCase(query));
        
        return ResponseEntity.ok(results);
    }
}
