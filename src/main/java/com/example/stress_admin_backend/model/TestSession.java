package com.example.stress_admin_backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "test_sessions")
@Schema(description = "Represents a test session that can run multiple use cases concurrently")
public class TestSession {

    @Id
    @Schema(description = "Unique identifier for the test session", example = "session_123")
    private String id;

    @Schema(description = "Name of the test session", example = "Concurrent Load Test Session", required = true)
    private String name;
    
    @Schema(description = "Description of the test session", example = "Running multiple use cases concurrently for load testing")
    private String description;

    @Schema(description = "List of use case IDs to run concurrently", example = "[\"uc_1\", \"uc_2\", \"uc_3\"]")
    private List<String> useCaseIds;
    
    @Schema(description = "Map of use case ID to number of users for that use case", example = "{\"uc_1\": 50, \"uc_2\": 100, \"uc_3\": 25}")
    private Map<String, Integer> userCounts;

    @Schema(description = "Current status of the test session", 
            example = "RUNNING", 
            allowableValues = {"IDLE", "RUNNING", "SUCCESS", "FAILED", "PARTIAL_SUCCESS"})
    private String status; // IDLE, RUNNING, SUCCESS, FAILED, PARTIAL_SUCCESS

    @Schema(description = "Timestamp when the session was created", example = "2024-01-15T10:30:00")
    private LocalDateTime createdAt;
    
    @Schema(description = "Timestamp when the session was last updated", example = "2024-01-15T10:30:00")
    private LocalDateTime updatedAt;
    
    @Schema(description = "Timestamp when the session started running", example = "2024-01-15T10:35:00")
    private LocalDateTime startedAt;
    
    @Schema(description = "Timestamp when the session completed", example = "2024-01-15T11:00:00")
    private LocalDateTime completedAt;

    @Schema(description = "Total number of concurrent users across all use cases", example = "175")
    private Integer totalUsers;
    
    @Schema(description = "Number of use cases in this session", example = "3")
    private Integer useCaseCount;
    
    @Schema(description = "Number of successfully completed use cases", example = "2")
    private Integer successCount;
    
    @Schema(description = "Number of failed use cases", example = "1")
    private Integer failureCount;

    @Schema(description = "Map of use case ID to its individual status", example = "{\"uc_1\": \"SUCCESS\", \"uc_2\": \"SUCCESS\", \"uc_3\": \"FAILED\"}")
    private Map<String, String> useCaseStatuses;
    
    @Schema(description = "Map of use case ID to its report URL", example = "{\"uc_1\": \"/reports/report_1/index.html\", \"uc_2\": \"/reports/report_2/index.html\"}")
    private Map<String, String> useCaseReportUrls;
    
    @Schema(description = "ID of the user who created this test session", example = "507f1f77bcf86cd799439011")
    private String userId;
}
