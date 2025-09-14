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
@Document(collection = "test_configurations")
@Schema(description = "Represents a test configuration with JMX and CSV files for load testing")
public class TestConfiguration {

    @Id
    @Schema(description = "Unique identifier for the test configuration", example = "config_123")
    private String id;

    @Schema(description = "Name of the test configuration", example = "Login Test Config", required = true)
    private String name;
    
    @Schema(description = "Description of what this configuration tests", example = "Configuration for login performance testing")
    private String description;

    @Schema(description = "Path to the JMeter test plan file (.jmx)", example = "/uploads/test.jmx")
    private String jmxPath;
    
    @Schema(description = "Path to the test data file (.csv)", example = "/uploads/data.csv")
    private String csvPath;

    @Schema(description = "Original filename of the JMX file", example = "login_test.jmx")
    private String jmxFileName;
    
    @Schema(description = "Original filename of the CSV file", example = "user_data.csv")
    private String csvFileName;

    @Schema(description = "File size of JMX file in bytes", example = "1024")
    private Long jmxFileSize;
    
    @Schema(description = "File size of CSV file in bytes", example = "2048")
    private Long csvFileSize;

    @Schema(description = "Timestamp when the configuration was created", example = "2024-01-15T10:30:00")
    private LocalDateTime createdAt;
    
    @Schema(description = "Timestamp when the configuration was last updated", example = "2024-01-15T10:30:00")
    private LocalDateTime updatedAt;
    
    @Schema(description = "Whether this configuration is active and can be used for testing", example = "true")
    private Boolean isActive;
}
