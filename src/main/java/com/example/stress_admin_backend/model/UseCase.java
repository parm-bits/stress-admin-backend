package com.example.stress_admin_backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "use_cases")
@Schema(description = "Represents a stress testing use case with JMeter test configuration")
public class UseCase {

    @Id
    @Schema(description = "Unique identifier for the use case", example = "123")
    private String id;

    @Schema(description = "Name of the use case", example = "Login Performance Test", required = true)
    private String name;
    
    @Schema(description = "Description of what this use case tests", example = "Test the performance of user login functionality")
    private String description;

    @Schema(description = "Path to the JMeter test plan file (.jmx)", example = "/uploads/test.jmx")
    private String jmxPath;
    
    @Schema(description = "Path to the test data file (.csv)", example = "/uploads/data.csv")
    private String csvPath;

    @Schema(description = "URL to the last test execution report", example = "http://localhost:8080/reports/report_123.html")
    private String lastReportUrl;
    
    @Schema(description = "Timestamp of the last test execution", example = "2024-01-15T10:30:00")
    private LocalDateTime lastRunAt;
    
    @Schema(description = "Timestamp when the test started", example = "2024-01-15T10:30:00")
    private LocalDateTime testStartedAt;
    
    @Schema(description = "Timestamp when the test completed", example = "2024-01-15T10:35:00")
    private LocalDateTime testCompletedAt;
    
    @Schema(description = "Duration of the last test execution in seconds", example = "300")
    private Long testDurationSeconds;
    
    @Schema(description = "Current status of the use case", 
            example = "IDLE", 
            allowableValues = {"IDLE", "RUNNING", "SUCCESS", "FAILED"})
    private String status; // IDLE, RUNNING, SUCCESS, FAILED
    
    @Schema(description = "ID of the test session this use case belongs to", example = "session_123")
    private String testSessionId;
    
    @Schema(description = "Number of users for this specific use case", example = "50")
    private Integer userCount;
    
    @Schema(description = "Priority of this use case in concurrent execution (1=highest)", example = "1")
    private Integer priority;
    
    @Schema(description = "Thread group configuration as JSON string", example = "{\"numberOfThreads\":10,\"rampUpPeriod\":60}")
    private String threadGroupConfig;
    
    @Schema(description = "Server configuration as JSON string", example = "{\"protocol\":\"http\",\"server\":\"localhost\",\"port\":\"8080\"}")
    private String serverConfig;
    
    @Schema(description = "Whether this use case requires a CSV data file", example = "true")
    private Boolean requiresCsv;
    
    @Schema(description = "ID of the user who created this use case", example = "507f1f77bcf86cd799439011")
    private String userId;
}
