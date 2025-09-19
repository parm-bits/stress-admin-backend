package com.example.stress_admin_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Settings Management", description = "APIs for managing application settings")
public class SettingsController {

    @Value("${jmeter.path}")
    private String jmeterPath;

    @Operation(summary = "Get application settings", description = "Retrieve current application settings including JMeter path")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Settings retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"jmeterPath\":\"/opt/jmeter/bin/jmeter.sh\"}")))
    })
    @GetMapping
    public ResponseEntity<Map<String, Object>> getSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("jmeterPath", jmeterPath);
        return ResponseEntity.ok(settings);
    }

    @Operation(summary = "Update application settings", description = "Update application settings including JMeter path")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Settings updated successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\":\"Settings updated successfully\"}"))),
            @ApiResponse(responseCode = "400", description = "Invalid settings data"),
            @ApiResponse(responseCode = "500", description = "Failed to update settings")
    })
    @PostMapping
    public ResponseEntity<?> updateSettings(
            @Parameter(description = "Settings object containing JMeter path", required = true)
            @RequestBody Map<String, String> settings) {
        
        try {
            String newJmeterPath = settings.get("jmeterPath");
            
            if (newJmeterPath == null || newJmeterPath.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "JMeter path is required"));
            }
            
            // Validate that the JMeter executable exists
            File jmeterFile = new File(newJmeterPath.trim());
            if (!jmeterFile.exists()) {
                return ResponseEntity.badRequest().body(Map.of("error", "JMeter executable not found at the specified path"));
            }
            
            // Accept both .jar files (Windows) and .sh scripts (Linux/Unix)
            String fileName = jmeterFile.getName().toLowerCase();
            if (!fileName.endsWith(".jar") && !fileName.endsWith(".sh") && !fileName.equals("jmeter")) {
                return ResponseEntity.badRequest().body(Map.of("error", "File must be a JAR file (.jar), shell script (.sh), or jmeter executable"));
            }
            
            // Update the JMeter path (in a real application, you'd persist this to a database or config file)
            this.jmeterPath = newJmeterPath.trim();
            
            return ResponseEntity.ok(Map.of("message", "Settings updated successfully", "jmeterPath", this.jmeterPath));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to update settings: " + e.getMessage()));
        }
    }

    @Operation(summary = "Validate JMeter path", description = "Validate if the provided JMeter path is correct")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Path validation result",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"valid\":true,\"message\":\"JMeter path is valid\"}"))),
            @ApiResponse(responseCode = "400", description = "Invalid path")
    })
    @PostMapping("/validate-jmeter")
    public ResponseEntity<?> validateJmeterPath(
            @Parameter(description = "JMeter path to validate", required = true)
            @RequestBody Map<String, String> request) {
        
        try {
            String path = request.get("path");
            
            if (path == null || path.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "Path is required"));
            }
            
            File jmeterFile = new File(path.trim());
            
            if (!jmeterFile.exists()) {
                return ResponseEntity.ok(Map.of("valid", false, "message", "File does not exist"));
            }
            
            // Accept both .jar files (Windows) and .sh scripts (Linux/Unix)
            String fileName = jmeterFile.getName().toLowerCase();
            if (!fileName.endsWith(".jar") && !fileName.endsWith(".sh") && !fileName.equals("jmeter")) {
                return ResponseEntity.ok(Map.of("valid", false, "message", "File must be a JAR file (.jar), shell script (.sh), or jmeter executable"));
            }
            
            return ResponseEntity.ok(Map.of("valid", true, "message", "JMeter path is valid"));
            
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("valid", false, "message", "Error validating path: " + e.getMessage()));
        }
    }
}
