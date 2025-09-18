package com.example.stress_admin_backend.controller;

import com.example.stress_admin_backend.model.TestSession;
import com.example.stress_admin_backend.service.ConcurrentTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.example.stress_admin_backend.security.CustomUserPrincipal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/test-sessions")
@Tag(name = "Test Session Management", description = "APIs for managing concurrent test sessions")
public class TestSessionController {

    private final ConcurrentTestService concurrentTestService;

    public TestSessionController(ConcurrentTestService concurrentTestService) {
        this.concurrentTestService = concurrentTestService;
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal) {
            CustomUserPrincipal userPrincipal = (CustomUserPrincipal) authentication.getPrincipal();
            return userPrincipal.getUser().getId();
        }
        throw new RuntimeException("User not authenticated");
    }

    @Operation(summary = "Get all test sessions", description = "Retrieve a list of all test sessions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved test sessions",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TestSession.class),
                            examples = @ExampleObject(value = "[{\"id\":\"session_123\",\"name\":\"Concurrent Load Test\",\"status\":\"RUNNING\",\"useCaseCount\":3,\"totalUsers\":175}]")))
    })
    @GetMapping
    public List<TestSession> list() {
        String userId = getCurrentUserId();
        return concurrentTestService.getSessionsByUserId(userId);
    }

    @Operation(summary = "Get running test sessions", description = "Retrieve currently running test sessions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved running test sessions")
    })
    @GetMapping("/running")
    public List<TestSession> listRunning() {
        return concurrentTestService.getRunningSessions();
    }

    @Operation(summary = "Create a new test session", description = "Create a new test session with multiple use cases for concurrent execution")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Test session created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TestSession.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<?> create(
            @Parameter(description = "Name of the test session", required = true, example = "Concurrent Load Test Session")
            @RequestParam String name,
            
            @Parameter(description = "Description of the test session", example = "Running multiple use cases concurrently for load testing")
            @RequestParam(required = false) String description,
            
            @Parameter(description = "List of use case IDs to run concurrently", required = true, example = "[\"uc_1\", \"uc_2\", \"uc_3\"]")
            @RequestParam List<String> useCaseIds,
            
            @Parameter(description = "Map of use case ID to number of users (JSON format)", required = true, example = "{\"uc_1\": 50, \"uc_2\": 100, \"uc_3\": 25}")
            @RequestBody Map<String, Integer> userCounts) {
        
        try {
            // Validate inputs
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Name is required"));
            }
            
            if (useCaseIds == null || useCaseIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "At least one use case ID is required"));
            }
            
            if (userCounts == null || userCounts.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User counts are required"));
            }
            
            // Validate that all use case IDs have user counts
            for (String useCaseId : useCaseIds) {
                if (!userCounts.containsKey(useCaseId)) {
                    return ResponseEntity.badRequest().body(Map.of("error", "User count not specified for use case: " + useCaseId));
                }
                if (userCounts.get(useCaseId) <= 0) {
                    return ResponseEntity.badRequest().body(Map.of("error", "User count must be positive for use case: " + useCaseId));
                }
            }
            
            String userId = getCurrentUserId();
            TestSession session = concurrentTestService.createTestSession(
                name.trim(), 
                description != null ? description.trim() : "", 
                useCaseIds, 
                userCounts,
                userId
            );
            
            return ResponseEntity.ok(session);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create test session: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get test session by ID", description = "Retrieve a specific test session by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Test session found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TestSession.class))),
            @ApiResponse(responseCode = "404", description = "Test session not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TestSession> get(
            @Parameter(description = "Test session ID", required = true, example = "session_123")
            @PathVariable String id) {
        Optional<TestSession> session = concurrentTestService.getSession(id);
        return session.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Start a test session", description = "Start executing all use cases in the test session concurrently")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Test session started successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\":\"Test session started\",\"sessionId\":\"session_123\"}"))),
            @ApiResponse(responseCode = "404", description = "Test session not found"),
            @ApiResponse(responseCode = "400", description = "Test session cannot be started"),
            @ApiResponse(responseCode = "500", description = "Failed to start test session")
    })
    @PostMapping("/{id}/start")
    public ResponseEntity<?> start(
            @Parameter(description = "Test session ID", required = true, example = "session_123")
            @PathVariable String id) {
        
        Optional<TestSession> sessionOpt = concurrentTestService.getSession(id);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        TestSession session = sessionOpt.get();
        if (!session.getStatus().equals("IDLE")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Test session is not in IDLE status"));
        }
        
        try {
            concurrentTestService.runConcurrentTest(id);
            return ResponseEntity.accepted().body(Map.of("message", "Test session started", "sessionId", id));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to start test session: " + e.getMessage()));
        }
    }

    @Operation(summary = "Stop a test session", description = "Stop a running test session and all its use cases")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Test session stopped successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\":\"Test session stopped\",\"sessionId\":\"session_123\"}"))),
            @ApiResponse(responseCode = "404", description = "Test session not found"),
            @ApiResponse(responseCode = "400", description = "Test session is not running"),
            @ApiResponse(responseCode = "500", description = "Failed to stop test session")
    })
    @PostMapping("/{id}/stop")
    public ResponseEntity<?> stop(
            @Parameter(description = "Test session ID", required = true, example = "session_123")
            @PathVariable String id) {
        
        try {
            boolean stopped = concurrentTestService.stopSession(id);
            if (stopped) {
                return ResponseEntity.ok(Map.of("message", "Test session stopped", "sessionId", id));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Test session is not running or not found"));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to stop test session: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get test session status", description = "Get detailed status information for a test session")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"status\":\"RUNNING\",\"useCaseCount\":3,\"successCount\":1,\"failureCount\":0,\"totalUsers\":175,\"useCaseStatuses\":{\"uc_1\":\"RUNNING\",\"uc_2\":\"SUCCESS\",\"uc_3\":\"IDLE\"}}"))),
            @ApiResponse(responseCode = "404", description = "Test session not found")
    })
    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getStatus(
            @Parameter(description = "Test session ID", required = true, example = "session_123")
            @PathVariable String id) {
        
        Optional<TestSession> sessionOpt = concurrentTestService.getSession(id);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        TestSession session = sessionOpt.get();
        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put("status", session.getStatus());
        statusMap.put("useCaseCount", session.getUseCaseCount());
        statusMap.put("successCount", session.getSuccessCount());
        statusMap.put("failureCount", session.getFailureCount());
        statusMap.put("totalUsers", session.getTotalUsers());
        statusMap.put("useCaseStatuses", session.getUseCaseStatuses());
        statusMap.put("useCaseReportUrls", session.getUseCaseReportUrls());
        statusMap.put("startedAt", session.getStartedAt());
        statusMap.put("completedAt", session.getCompletedAt());
        
        return ResponseEntity.ok(statusMap);
    }
}
