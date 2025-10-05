package com.example.stress_admin_backend.controller;

import com.example.stress_admin_backend.model.UseCase;
import com.example.stress_admin_backend.model.TestConfiguration;
import com.example.stress_admin_backend.model.TestSession;
import com.example.stress_admin_backend.repository.UseCaseRepository;
import com.example.stress_admin_backend.repository.TestConfigurationRepository;
import com.example.stress_admin_backend.service.FileStorageService;
import com.example.stress_admin_backend.service.JMeterService;
import com.example.stress_admin_backend.service.ConcurrentTestService;
import com.example.stress_admin_backend.service.JmxModificationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.stress_admin_backend.security.CustomUserPrincipal;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/usecases")
@Tag(name = "Use Case Management", description = "APIs for managing stress testing use cases")
public class UseCaseController {

    private final UseCaseRepository repo;
    private final TestConfigurationRepository configRepo;
    private final FileStorageService storage;
    private final JMeterService jMeterService;
    private final ConcurrentTestService concurrentTestService;
    private final JmxModificationService jmxModificationService;

    public UseCaseController(UseCaseRepository repo, TestConfigurationRepository configRepo, 
                           FileStorageService storage, JMeterService jMeterService, 
                           ConcurrentTestService concurrentTestService, JmxModificationService jmxModificationService) {
        this.repo = repo;
        this.configRepo = configRepo;
        this.storage = storage;
        this.jMeterService = jMeterService;
        this.concurrentTestService = concurrentTestService;
        this.jmxModificationService = jmxModificationService;
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal) {
            CustomUserPrincipal userPrincipal = (CustomUserPrincipal) authentication.getPrincipal();
            return userPrincipal.getUser().getId();
        }
        throw new RuntimeException("User not authenticated");
    }

    @Operation(summary = "Get all use cases", description = "Retrieve a list of all stress testing use cases")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved use cases",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UseCase.class),
                            examples = @ExampleObject(value = "[{\"id\":\"123\",\"name\":\"Login Test\",\"description\":\"Test user login functionality\",\"status\":\"IDLE\",\"jmxPath\":\"/uploads/test.jmx\",\"csvPath\":\"/uploads/data.csv\"}]")))
    })
    @GetMapping
    public List<UseCase> list() {
        String userId = getCurrentUserId();
        return repo.findByUserId(userId);
    }

    @Operation(summary = "Create a new use case", description = "Create a new stress testing use case with JMX and CSV files")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Use case created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UseCase.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @Parameter(description = "Name of the use case", required = true, example = "Login Performance Test")
            @RequestParam String name,
            
            @Parameter(description = "Description of the use case", example = "Test the performance of user login functionality")
            @RequestParam(required = false) String description,
            
            @Parameter(description = "JMeter test plan file (.jmx)", required = true)
            @RequestPart("jmxFile") MultipartFile jmxFile,
            
            @Parameter(description = "Test data file (.csv)", required = false)
            @RequestPart(value = "csvFile", required = false) MultipartFile csvFile,
            
            @Parameter(description = "Whether this use case requires a CSV file", required = false)
            @RequestParam(value = "requiresCsv", required = false, defaultValue = "false") String requiresCsv,
            
            @Parameter(description = "Thread group configuration as JSON string", required = false)
            @RequestParam(value = "threadGroupConfig", required = false) String threadGroupConfig
    ) {
        try {
            // Validate inputs
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Name is required"));
            }
            
            if (jmxFile == null || jmxFile.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "JMX file is required"));
            }
            
            boolean requiresCsvBool = Boolean.parseBoolean(requiresCsv);
            if (requiresCsvBool && (csvFile == null || csvFile.isEmpty())) {
                return ResponseEntity.badRequest().body(Map.of("error", "CSV file is required for this use case"));
            }
            
            // Validate file extensions
            String jmxFileName = jmxFile.getOriginalFilename();
            String csvFileName = csvFile != null ? csvFile.getOriginalFilename() : null;
            
            if (jmxFileName == null || !jmxFileName.toLowerCase().endsWith(".jmx")) {
                return ResponseEntity.badRequest().body(Map.of("error", "JMX file must have .jmx extension"));
            }
            
            if (requiresCsvBool && (csvFileName == null || !csvFileName.toLowerCase().endsWith(".csv"))) {
                return ResponseEntity.badRequest().body(Map.of("error", "CSV file must have .csv extension"));
            }
            
            String jmxPath = storage.storeJmx(jmxFile);
            String csvPath = csvFile != null ? storage.storeCsv(csvFile) : null;

            String userId = getCurrentUserId();
            
            // Build use case with optional thread group configuration
            UseCase.UseCaseBuilder builder = UseCase.builder()
                    .name(name.trim())
                    .description(description != null ? description.trim() : "")
                    .jmxPath(jmxPath)
                    .csvPath(csvPath)
                    .status("IDLE")
                    .userCount(50) // Set default user count
                    .requiresCsv(requiresCsvBool)
                    .userId(userId);
            
            // Add thread group configuration if provided
            if (threadGroupConfig != null && !threadGroupConfig.trim().isEmpty()) {
                builder.threadGroupConfig(threadGroupConfig.trim());
                System.out.println("\n🔧 UPLOAD PAGE: Thread Group Configuration Received");
                System.out.println("========================================");
                System.out.println("📋 Use Case Name: " + name.trim());
                
                // Parse and analyze thread group configuration
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> config = mapper.readValue(threadGroupConfig, new TypeReference<Map<String, Object>>() {});
                    analyzeThreadConfiguration(config);
                } catch (Exception e) {
                    System.err.println("⚠️ WARNING: Invalid thread group configuration JSON: " + e.getMessage());
                    System.out.println("⚠️ Thread group config will be stored as string");
                }
                
                System.out.println("✅ Thread Group Configuration will be applied during test execution");
                System.out.println("🎯 Duration issue prevention: ACTIVE for upload creation");
                System.out.println("========================================");
            } else {
                System.out.println("ℹ️ UPLOAD PAGE: No Thread Group Configuration provided");
                System.out.println("ℹ️ Use case created with default thread settings");
            }
            
            UseCase saved = repo.save(builder.build());
            
            // Log CSV configuration status after use case creation
            System.out.println("✅ Use Case Created Successfully!");
            System.out.println("📋 Use Case Details:");
            System.out.println("   - ID: " + saved.getId());
            System.out.println("   - Name: " + saved.getName());
            System.out.println("   - JMX Path: " + saved.getJmxPath());
            System.out.println("   - CSV Path: " + saved.getCsvPath());
            System.out.println("   - Requires CSV: " + saved.getRequiresCsv());
            System.out.println("   - Thread Group Config: " + (saved.getThreadGroupConfig() != null ? "PROVIDED ✅" : "NOT PROVIDED ℹ️"));
            
            if (saved.getCsvPath() != null && !saved.getCsvPath().isEmpty()) {
                System.out.println("✅ CSV Configuration: PROPERLY CONFIGURED");
                System.out.println("   - CSV file will be automatically mapped to server path during test execution");
                System.out.println("   - Original JMX CSV paths will be replaced with: " + saved.getCsvPath());
            } else {
                System.out.println("ℹ️ CSV Configuration: NO CSV FILE REQUIRED");
                System.out.println("   - This use case does not require CSV data");
            }
            
            return ResponseEntity.created(new URI("/api/usecases/" + saved.getId())).body(saved);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create use case: " + e.getMessage()));
        }
    }

    @Operation(summary = "Run a use case test", description = "Execute a JMeter test for the specified use case with the given number of users")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Test execution started successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\":\"Run started\",\"useCaseId\":\"123\"}"))),
            @ApiResponse(responseCode = "404", description = "Use case not found"),
            @ApiResponse(responseCode = "500", description = "Failed to start test execution")
    })
    @PostMapping("/{id}/run")
    public ResponseEntity<?> run(
            @Parameter(description = "Use case ID", required = true, example = "123")
            @PathVariable String id, 
            
            @Parameter(description = "Number of concurrent users for the test", example = "50")
            @RequestParam(defaultValue = "50") int users) {
        
        if (users <= 0 || users > 10000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Users must be between 1 and 10000"));
        }
        
        if (!repo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            jMeterService.runTest(id, users);
            return ResponseEntity.accepted().body(Map.of("message", "Run started", "useCaseId", id));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to start test: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get use case by ID", description = "Retrieve a specific use case by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Use case found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UseCase.class),
                            examples = @ExampleObject(value = "{\"id\":\"123\",\"name\":\"Login Test\",\"description\":\"Test user login functionality\",\"status\":\"IDLE\",\"jmxPath\":\"/uploads/test.jmx\",\"csvPath\":\"/uploads/data.csv\"}"))),
            @ApiResponse(responseCode = "404", description = "Use case not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<UseCase> get(
            @Parameter(description = "Use case ID", required = true, example = "123")
            @PathVariable String id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update use case", description = "Update an existing use case's name, description, and configurations")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Use case updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UseCase.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "404", description = "Use case not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @Parameter(description = "Use case ID", required = true, example = "123")
            @PathVariable String id,
            
            @Parameter(description = "Updated use case data", required = true)
            @RequestBody Map<String, Object> updateData) {
        
        try {
            Optional<UseCase> useCaseOpt = repo.findById(id);
            if (useCaseOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            UseCase useCase = useCaseOpt.get();
            
            // Update name if provided
            if (updateData.containsKey("name")) {
                String name = (String) updateData.get("name");
                if (name != null && !name.trim().isEmpty()) {
                    useCase.setName(name.trim());
                } else {
                    return ResponseEntity.badRequest().body(Map.of("error", "Name cannot be empty"));
                }
            }
            
            // Update description if provided
            if (updateData.containsKey("description")) {
                String description = (String) updateData.get("description");
                useCase.setDescription(description != null ? description.trim() : "");
            }
            
            // Update thread group configuration if provided
            if (updateData.containsKey("threadGroupConfig")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> threadGroupConfig = (Map<String, Object>) updateData.get("threadGroupConfig");
                if (threadGroupConfig != null && !threadGroupConfig.isEmpty()) {
                    // Convert to JSON string and store in dedicated field
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        useCase.setThreadGroupConfig(mapper.writeValueAsString(threadGroupConfig));
                        
                        // 📊 DURATION ISSUE ANALYSIS & FIX LOGGING
                        System.out.println("\n🔧 USE CASE: Thread Group Configuration Applied");
                        System.out.println("========================================");
                        System.out.println("📋 Use Case Name: " + useCase.getName());
                        System.out.println("🆔 Use Case ID: " + useCase.getId());
                        
                        // Analyze thread configuration for duration conflicts
                        analyzeThreadConfiguration(threadGroupConfig);
                        
                        System.out.println("✅ Thread Group Configuration successfully saved");
                        System.out.println("🎯 Duration issue prevention: ACTIVE");
                        System.out.println("========================================");
                        
                    } catch (Exception e) {
                        System.err.println("Error converting thread group config to JSON: " + e.getMessage());
                        useCase.setThreadGroupConfig(threadGroupConfig.toString());
                        
                        System.out.println("⚠️ WARNING: Thread Group Config converted as string due to parsing error");
                    }
                } else {
                    System.out.println("ℹ️ No Thread Group Configuration provided for: " + useCase.getName());
                }
            }
            
            // Update server configuration if provided
            if (updateData.containsKey("serverConfig")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> serverConfig = (Map<String, Object>) updateData.get("serverConfig");
                if (serverConfig != null && !serverConfig.isEmpty()) {
                    // Convert to JSON string and store in dedicated field
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        useCase.setServerConfig(mapper.writeValueAsString(serverConfig));
                    } catch (Exception e) {
                        System.err.println("Error converting server config to JSON: " + e.getMessage());
                        useCase.setServerConfig(serverConfig.toString());
                    }
                }
            }
            
            UseCase updatedUseCase = repo.save(useCase);
            return ResponseEntity.ok(updatedUseCase);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to update use case: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get test execution status", description = "Check the current status of a test execution")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"status\":\"RUNNING\",\"lastRunAt\":\"2024-01-15T10:30:00\",\"lastReportUrl\":\"/reports/report_123/index.html\"}"))),
            @ApiResponse(responseCode = "404", description = "Use case not found")
    })
    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getStatus(
            @Parameter(description = "Use case ID", required = true, example = "123")
            @PathVariable String id) {
        return repo.findById(id)
                .map(uc -> {
                    Map<String, Object> statusMap = new HashMap<>();
                    statusMap.put("status", uc.getStatus());
                    statusMap.put("lastRunAt", uc.getLastRunAt());
                    statusMap.put("lastReportUrl", uc.getLastReportUrl() != null ? uc.getLastReportUrl() : "");
                    return ResponseEntity.ok(statusMap);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Create use case from test configuration", description = "Create a new use case using an existing test configuration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Use case created successfully from test configuration",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UseCase.class))),
            @ApiResponse(responseCode = "404", description = "Test configuration not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    @PostMapping("/from-config/{configId}")
    public ResponseEntity<?> createFromConfig(
            @Parameter(description = "Test configuration ID", required = true, example = "config_123")
            @PathVariable String configId,
            
            @Parameter(description = "Name for the use case", required = true, example = "Login Performance Test")
            @RequestParam String name,
            
            @Parameter(description = "Description for the use case", example = "Test the performance of user login functionality")
            @RequestParam(required = false) String description,
            
            @Parameter(description = "Number of concurrent users for this use case", example = "50")
            @RequestParam(defaultValue = "50") int users,
            
            @Parameter(description = "Priority for concurrent execution (1=highest)", example = "1")
            @RequestParam(defaultValue = "1") int priority) {
        
        try {
            Optional<TestConfiguration> configOpt = configRepo.findById(configId);
            if (configOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            TestConfiguration config = configOpt.get();
            if (!config.getIsActive()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Test configuration is not active"));
            }
            
            if (users <= 0 || users > 10000) {
                return ResponseEntity.badRequest().body(Map.of("error", "Users must be between 1 and 10000"));
            }
            
            String userId = getCurrentUserId();
            UseCase useCase = UseCase.builder()
                    .name(name.trim())
                    .description(description != null ? description.trim() : "")
                    .jmxPath(config.getJmxPath())
                    .csvPath(config.getCsvPath())
                    .status("IDLE")
                    .userCount(users)
                    .priority(priority)
                    .requiresCsv(false) // Default to CSV optional
                    .userId(userId)
                    .build();
            
            UseCase saved = repo.save(useCase);
            
            // Log CSV configuration status after use case creation
            System.out.println("✅ Use Case Created Successfully!");
            System.out.println("📋 Use Case Details:");
            System.out.println("   - ID: " + saved.getId());
            System.out.println("   - Name: " + saved.getName());
            System.out.println("   - JMX Path: " + saved.getJmxPath());
            System.out.println("   - CSV Path: " + saved.getCsvPath());
            System.out.println("   - Requires CSV: " + saved.getRequiresCsv());
            
            if (saved.getCsvPath() != null && !saved.getCsvPath().isEmpty()) {
                System.out.println("✅ CSV Configuration: PROPERLY CONFIGURED");
                System.out.println("   - CSV file will be automatically mapped to server path during test execution");
                System.out.println("   - Original JMX CSV paths will be replaced with: " + saved.getCsvPath());
            } else {
                System.out.println("ℹ️ CSV Configuration: NO CSV FILE REQUIRED");
                System.out.println("   - This use case does not require CSV data");
            }
            
            return ResponseEntity.created(new URI("/api/usecases/" + saved.getId())).body(saved);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create use case: " + e.getMessage()));
        }
    }

    @Operation(summary = "Create multiple use cases from test configurations", description = "Create multiple use cases from different test configurations for concurrent execution")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Use cases created successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "[{\"id\":\"uc_1\",\"name\":\"Login Test\",\"status\":\"IDLE\"},{\"id\":\"uc_2\",\"name\":\"Search Test\",\"status\":\"IDLE\"}]"))),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/batch-create")
    public ResponseEntity<?> createBatch(
            @Parameter(description = "List of use case creation requests", required = true)
            @RequestBody List<UseCaseCreationRequest> requests) {
        
        try {
            if (requests == null || requests.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "At least one use case request is required"));
            }
            
            List<UseCase> createdUseCases = new ArrayList<>();
            String userId = getCurrentUserId();
            
            for (UseCaseCreationRequest request : requests) {
                Optional<TestConfiguration> configOpt = configRepo.findById(request.getConfigId());
                if (configOpt.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Test configuration not found: " + request.getConfigId()));
                }
                
                TestConfiguration config = configOpt.get();
                if (!config.getIsActive()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Test configuration is not active: " + request.getConfigId()));
                }
                
                if (request.getUsers() <= 0 || request.getUsers() > 10000) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Users must be between 1 and 10000 for: " + request.getName()));
                }
                
                UseCase useCase = UseCase.builder()
                        .name(request.getName().trim())
                        .description(request.getDescription() != null ? request.getDescription().trim() : "")
                        .jmxPath(config.getJmxPath())
                        .csvPath(config.getCsvPath())
                        .status("IDLE")
                        .userCount(request.getUsers())
                        .priority(request.getPriority() != null ? request.getPriority() : 1)
                        .requiresCsv(false) // Default to CSV optional
                        .userId(userId)
                        .build();
                
                UseCase saved = repo.save(useCase);
                createdUseCases.add(saved);
                
                // Log CSV configuration status for each use case
                System.out.println("✅ Use Case Created Successfully!");
                System.out.println("📋 Use Case Details:");
                System.out.println("   - ID: " + saved.getId());
                System.out.println("   - Name: " + saved.getName());
                System.out.println("   - JMX Path: " + saved.getJmxPath());
                System.out.println("   - CSV Path: " + saved.getCsvPath());
                System.out.println("   - Requires CSV: " + saved.getRequiresCsv());
                
                if (saved.getCsvPath() != null && !saved.getCsvPath().isEmpty()) {
                    System.out.println("✅ CSV Configuration: PROPERLY CONFIGURED");
                    System.out.println("   - CSV file will be automatically mapped to server path during test execution");
                    System.out.println("   - Original JMX CSV paths will be replaced with: " + saved.getCsvPath());
                } else {
                    System.out.println("ℹ️ CSV Configuration: NO CSV FILE REQUIRED");
                    System.out.println("   - This use case does not require CSV data");
                }
            }
            
            System.out.println("🎉 Batch Use Case Creation Completed!");
            System.out.println("📊 Total Use Cases Created: " + createdUseCases.size());
            
            return ResponseEntity.ok(createdUseCases);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create use cases: " + e.getMessage()));
        }
    }

    @Operation(summary = "Create and start concurrent test session", description = "Create multiple use cases and start them concurrently")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Concurrent test session created and started successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\":\"Concurrent test session started\",\"sessionId\":\"session_123\",\"useCaseIds\":[\"uc_1\",\"uc_2\",\"uc_3\"]}"))),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/concurrent-test")
    public ResponseEntity<?> createAndStartConcurrentTest(
            @Parameter(description = "Name of the test session", required = true, example = "Concurrent Load Test")
            @RequestParam String sessionName,
            
            @Parameter(description = "Description of the test session", example = "Running multiple use cases concurrently")
            @RequestParam(required = false) String sessionDescription,
            
            @Parameter(description = "List of use case creation requests", required = true)
            @RequestBody List<UseCaseCreationRequest> requests) {
        
        try {
            if (requests == null || requests.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "At least one use case request is required"));
            }
            
            // Create use cases first
            List<UseCase> createdUseCases = new ArrayList<>();
            Map<String, Integer> userCounts = new HashMap<>();
            String userId = getCurrentUserId();
            
            for (UseCaseCreationRequest request : requests) {
                Optional<TestConfiguration> configOpt = configRepo.findById(request.getConfigId());
                if (configOpt.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Test configuration not found: " + request.getConfigId()));
                }
                
                TestConfiguration config = configOpt.get();
                if (!config.getIsActive()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Test configuration is not active: " + request.getConfigId()));
                }
                
                if (request.getUsers() <= 0 || request.getUsers() > 10000) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Users must be between 1 and 10000 for: " + request.getName()));
                }
                
                UseCase useCase = UseCase.builder()
                        .name(request.getName().trim())
                        .description(request.getDescription() != null ? request.getDescription().trim() : "")
                        .jmxPath(config.getJmxPath())
                        .csvPath(config.getCsvPath())
                        .status("IDLE")
                        .userCount(request.getUsers())
                        .priority(request.getPriority() != null ? request.getPriority() : 1)
                        .requiresCsv(false) // Default to CSV optional
                        .userId(userId)
                        .build();
                
                UseCase saved = repo.save(useCase);
                createdUseCases.add(saved);
                userCounts.put(saved.getId(), saved.getUserCount());
                
                // Log CSV configuration status for each use case
                System.out.println("✅ Use Case Created Successfully!");
                System.out.println("📋 Use Case Details:");
                System.out.println("   - ID: " + saved.getId());
                System.out.println("   - Name: " + saved.getName());
                System.out.println("   - JMX Path: " + saved.getJmxPath());
                System.out.println("   - CSV Path: " + saved.getCsvPath());
                System.out.println("   - Requires CSV: " + saved.getRequiresCsv());
                
                if (saved.getCsvPath() != null && !saved.getCsvPath().isEmpty()) {
                    System.out.println("✅ CSV Configuration: PROPERLY CONFIGURED");
                    System.out.println("   - CSV file will be automatically mapped to server path during test execution");
                    System.out.println("   - Original JMX CSV paths will be replaced with: " + saved.getCsvPath());
                } else {
                    System.out.println("ℹ️ CSV Configuration: NO CSV FILE REQUIRED");
                    System.out.println("   - This use case does not require CSV data");
                }
            }
            
            // Create test session
            List<String> useCaseIds = createdUseCases.stream()
                    .map(UseCase::getId)
                    .collect(Collectors.toList());
            
            TestSession session = concurrentTestService.createTestSession(
                sessionName.trim(),
                sessionDescription != null ? sessionDescription.trim() : "",
                useCaseIds,
                userCounts,
                userId
            );
            
            // Log concurrent test session summary
            System.out.println("🎉 Concurrent Test Session Created Successfully!");
            System.out.println("📊 Session Summary:");
            System.out.println("   - Session ID: " + session.getId());
            System.out.println("   - Session Name: " + session.getName());
            System.out.println("   - Total Use Cases: " + createdUseCases.size());
            System.out.println("   - Total Users: " + session.getTotalUsers());
            
            // Count CSV configurations
            long csvConfiguredCount = createdUseCases.stream()
                    .filter(uc -> uc.getCsvPath() != null && !uc.getCsvPath().isEmpty())
                    .count();
            
            System.out.println("📋 CSV Configuration Summary:");
            System.out.println("   - Use Cases with CSV: " + csvConfiguredCount);
            System.out.println("   - Use Cases without CSV: " + (createdUseCases.size() - csvConfiguredCount));
            
            if (csvConfiguredCount > 0) {
                System.out.println("✅ All CSV configurations will be automatically applied during test execution");
            }
            
            // Start the concurrent test
            concurrentTestService.runConcurrentTest(session.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Concurrent test session started");
            response.put("sessionId", session.getId());
            response.put("useCaseIds", useCaseIds);
            response.put("totalUsers", session.getTotalUsers());
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create and start concurrent test: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get running processes", description = "Get information about currently running JMeter processes (for debugging)")
    @GetMapping("/running-processes")
    public ResponseEntity<?> getRunningProcesses() {
        try {
            Map<String, Process> runningProcesses = jMeterService.getRunningProcesses();
            Map<String, Object> response = new HashMap<>();
            
            for (Map.Entry<String, Process> entry : runningProcesses.entrySet()) {
                String useCaseId = entry.getKey();
                Process process = entry.getValue();
                
                Map<String, Object> processInfo = new HashMap<>();
                processInfo.put("useCaseId", useCaseId);
                processInfo.put("isAlive", process.isAlive());
                processInfo.put("pid", process.pid());
                
                response.put(useCaseId, processInfo);
            }
            
            return ResponseEntity.ok(Map.of(
                "runningProcesses", response,
                "count", runningProcesses.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to get running processes: " + e.getMessage()));
        }
    }

    @Operation(summary = "Stop a running use case test", description = "Stop a currently running JMeter test for the specified use case")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Test stopped successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\":\"Test stopped\",\"useCaseId\":\"123\"}"))),
            @ApiResponse(responseCode = "404", description = "Use case not found"),
            @ApiResponse(responseCode = "400", description = "Test is not running"),
            @ApiResponse(responseCode = "500", description = "Failed to stop test")
    })
    @PostMapping("/{id}/stop")
    public ResponseEntity<?> stop(
            @Parameter(description = "Use case ID", required = true, example = "123")
            @PathVariable String id) {
        
        Optional<UseCase> useCaseOpt = repo.findById(id);
        if (useCaseOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        UseCase useCase = useCaseOpt.get();
        if (!"RUNNING".equals(useCase.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Test is not currently running"));
        }
        
        try {
            // Stop the JMeter process (this would need to be implemented in JMeterService)
            jMeterService.stopTest(id);
            
            // Update use case status
            useCase.setStatus("STOPPED");
            useCase.setLastRunAt(LocalDateTime.now());
            repo.save(useCase);
            
            return ResponseEntity.ok(Map.of("message", "Test stopped", "useCaseId", id));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to stop test: " + e.getMessage()));
        }
    }

    @Operation(summary = "Download JMX file", description = "Download the JMX file for a specific use case")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "JMX file downloaded successfully",
                    content = @Content(mediaType = "application/xml")),
            @ApiResponse(responseCode = "404", description = "Use case not found"),
            @ApiResponse(responseCode = "500", description = "Failed to download JMX file")
    })
    @GetMapping("/{id}/download/jmx")
    public ResponseEntity<byte[]> downloadJmx(
            @Parameter(description = "Use case ID", required = true, example = "123")
            @PathVariable String id) {
        
        Optional<UseCase> useCaseOpt = repo.findById(id);
        if (useCaseOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        UseCase useCase = useCaseOpt.get();
        try {
            // Check if the original JMX file exists
            Resource resource = storage.loadJmxAsResource(useCase.getJmxPath());
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            // Modify JMX content with configuration if available
            String modifiedJmxContent;
            if ((useCase.getThreadGroupConfig() != null && !useCase.getThreadGroupConfig().isEmpty()) ||
                (useCase.getServerConfig() != null && !useCase.getServerConfig().isEmpty())) {
                // Extract duration from Thread Group Configuration, same as JMeterService does
                int duration = extractDurationFromThreadGroupConfig(useCase);
                System.out.println("\n📥 JMX DOWNLOAD REQUEST");
                System.out.println("========================================");
                System.out.println("📋 Use Case: " + useCase.getName() + " (ID: " + id + ")");
                System.out.println("⏱️  Extracted Duration: " + duration + " seconds from Thread Group Configuration");
                System.out.println("🔄 Applying configurations to JMX file...");
                System.out.println("========================================");
                
                modifiedJmxContent = jmxModificationService.modifyJmxWithConfiguration(useCase.getJmxPath(), useCase, duration);
                
                System.out.println("✅ JMX Download prepared with timing configuration applied");
                System.out.println("========================================");
            } else {
                // Read original content if no configuration to apply
                modifiedJmxContent = new String(resource.getInputStream().readAllBytes());
            }
            
            String filename = useCase.getJmxPath().substring(useCase.getJmxPath().lastIndexOf("/") + 1);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/xml")
                    .body(modifiedJmxContent.getBytes());
                    
        } catch (Exception e) {
            System.err.println("Error downloading JMX file: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "Download CSV file", description = "Download the CSV file for a specific use case")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "CSV file downloaded successfully",
                    content = @Content(mediaType = "text/csv")),
            @ApiResponse(responseCode = "404", description = "Use case not found"),
            @ApiResponse(responseCode = "500", description = "Failed to download CSV file")
    })
    @GetMapping("/{id}/download/csv")
    public ResponseEntity<Resource> downloadCsv(
            @Parameter(description = "Use case ID", required = true, example = "123")
            @PathVariable String id) {
        
        Optional<UseCase> useCaseOpt = repo.findById(id);
        if (useCaseOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        UseCase useCase = useCaseOpt.get();
        try {
            Resource resource = storage.loadCsvAsResource(useCase.getCsvPath());
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            String filename = useCase.getCsvPath().substring(useCase.getCsvPath().lastIndexOf("/") + 1);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                    .body(resource);
                    
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "Delete a use case", description = "Delete a use case by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Use case deleted successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\":\"Use case deleted successfully\"}"))),
            @ApiResponse(responseCode = "404", description = "Use case not found"),
            @ApiResponse(responseCode = "400", description = "Cannot delete running use case")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @Parameter(description = "Use case ID", required = true, example = "123")
            @PathVariable String id) {
        
        Optional<UseCase> useCaseOpt = repo.findById(id);
        if (useCaseOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        UseCase useCase = useCaseOpt.get();
        if ("RUNNING".equals(useCase.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete a running use case"));
        }
        
        try {
            repo.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Use case deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to delete use case: " + e.getMessage()));
        }
    }

    // Inner class for use case creation requests
    public static class UseCaseCreationRequest {
        private String name;
        private String description;
        private String configId;
        private Integer users;
        private Integer priority;
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getConfigId() { return configId; }
        public void setConfigId(String configId) { this.configId = configId; }
        
        public Integer getUsers() { return users; }
        public void setUsers(Integer users) { this.users = users; }
        
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
    }

    /**
     * Extracts duration from Thread Group Configuration
     */
    private int extractDurationFromThreadGroupConfig(UseCase useCase) {
        try {
            if (useCase.getThreadGroupConfig() != null && !useCase.getThreadGroupConfig().isEmpty()) {
                ObjectMapper objectMapper = new ObjectMapper();
                Map<String, Object> config = objectMapper.readValue(useCase.getThreadGroupConfig(), new TypeReference<Map<String, Object>>() {});
                
                if (config.containsKey("duration")) {
                    int duration = Integer.parseInt(config.get("duration").toString());
                    System.out.println("🎯 DURATION EXTRACTED: " + duration + " seconds from Thread Group Configuration");
                    System.out.println("✅ This duration will be used to prevent infinite runtime");
                    return duration;
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting duration from Thread Group Configuration: " + e.getMessage());
        }
        
        // Default duration if not specified in Thread Group Configuration
        System.out.println("No duration found in Thread Group Configuration, using default: 300 seconds");
        return 300;
    }

    /**
     * Analyzes thread group configuration for duration conflicts and logs potential issues
     */
    private void analyzeThreadConfiguration(Map<String, Object> threadGroupConfig) {
        System.out.println("🔍 ANALYZING Thread Group Configuration for Duration Issues:");
        System.out.println("-----------------------------------------------------------");
        
        // Check for infinite loop setting
        boolean infiniteLoop = false;
        if (threadGroupConfig.containsKey("infiniteLoop")) {
            infiniteLoop = Boolean.parseBoolean(threadGroupConfig.get("infiniteLoop").toString());
            System.out.println("🔄 Infinite Loop: " + (infiniteLoop ? "✅ ENABLED" : "❌ DISABLED"));
        } else {
            System.out.println("🔄 Infinite Loop: ℹ️ NOT SPECIFIED (defaulting to finite)");
        }
        
        // Check for duration setting
        boolean hasDuration = false;
        String duration = null;
        if (threadGroupConfig.containsKey("duration")) {
            hasDuration = true;
            duration = threadGroupConfig.get("duration").toString();
            System.out.println("⏱️  Duration: ✅ " + duration + " seconds");
        } else {
            System.out.println("⏱️  Duration: ❌ NOT SPECIFIED");
        }
        
        // Check for startup delay setting
        boolean hasStartupDelay = false;
        String startupDelay = null;
        if (threadGroupConfig.containsKey("startupDelay")) {
            hasStartupDelay = true;
            startupDelay = threadGroupConfig.get("startupDelay").toString();
            System.out.println("🚀 Startup Delay: ✅ " + startupDelay + " seconds");
        } else {
            System.out.println("🚀 Startup Delay: ❌ NOT SPECIFIED");
        }
        
        // Check for specify thread lifetime setting
        boolean specifyThreadLifetime = false;
        if (threadGroupConfig.containsKey("specifyThreadLifetime")) {
            specifyThreadLifetime = Boolean.parseBoolean(threadGroupConfig.get("specifyThreadLifetime").toString());
            System.out.println("📅 Specify Thread Lifetime: " + (specifyThreadLifetime ? "✅ ENABLED" : "❌ DISABLED"));
        } else {
            System.out.println("📅 Specify Thread Lifetime: ℹ️ NOT SPECIFIED");
        }
        
        System.out.println("-----------------------------------------------------------");
        
        // Analyze potential conflicts
        boolean hasDurationOrDelay = hasDuration || hasStartupDelay;
        
        if (hasDurationOrDelay && infiniteLoop) {
            System.out.println("🚨 CONFLICT DETECTED: Infinite Loop + Duration/Delay");
            System.out.println("🛡️  FIX APPLIED: Infinite loop will be overridden during JMX modification");
            System.out.println("✅ RESULT: Test will STOP after specified duration instead of running forever");
            System.out.println("🎯 PREVENTION: Duration issue automatically fixed in backend");
        } else if (hasDurationOrDelay) {
            System.out.println("✅ SAFE CONFIGURATION: Duration/Delay without infinite loop");
            System.out.println("🎯 RESULT: Test will properly stop after specified time");
            System.out.println("🛡️  DURATION PROTECTION: Automatically active");
        } else if (infiniteLoop) {
            System.out.println("⚡ INFINITE SETTING: No duration constraints");
            System.out.println("⚠️  WARNING: Test will run indefinitely until manually stopped");
            System.out.println("💡 SUGGESTION: Consider adding duration for controlled testing");
        } else {
            System.out.println("📊 STANDARD CONFIGURATION: Finite loops without duration");
            System.out.println("✅ RESULT: Test will run for specified loop count");
        }
        
        System.out.println("-----------------------------------------------------------");
        System.out.println("🔧 BACKEND STATUS: Duration issue prevention is ACTIVE");
        System.out.println("✅ JMX modification will handle conflicts automatically");
        System.out.println("========================================");
    }
}
