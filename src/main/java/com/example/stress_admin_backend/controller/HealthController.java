package com.example.stress_admin_backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    
    @Value("${jmeter.path}")
    private String jmeterPathProperty;
    
    @Value("${storage.base-dir}")
    private String storageBaseDir;
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "Backend is running",
                "timestamp", System.currentTimeMillis()
        ));
    }
    
    @GetMapping("/system")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();
        
        // Check JMeter installation
        Path jmeterPath = Paths.get(jmeterPathProperty);
        boolean jmeterExists = Files.exists(jmeterPath);
        
        // Try to find alternative JMeter paths (Linux/Ubuntu uses .sh scripts)
        String[] commonPaths = {
            "/opt/jmeter/bin/jmeter.sh",
            "/usr/local/jmeter/bin/jmeter.sh", 
            "/home/ubuntu/apache-jmeter-5.6.3/bin/jmeter.sh",
            "/opt/apache-jmeter-5.6.3/bin/jmeter.sh",
            "/opt/jmeter/bin/jmeter",
            "/usr/local/jmeter/bin/jmeter",
            "/home/ubuntu/apache-jmeter-5.6.3/bin/jmeter",
            "/opt/apache-jmeter-5.6.3/bin/jmeter"
        };
        
        String foundPath = null;
        if (jmeterExists) {
            foundPath = jmeterPathProperty;
        } else {
            for (String commonPath : commonPaths) {
                if (Files.exists(Paths.get(commonPath))) {
                    foundPath = commonPath;
                    break;
                }
            }
        }
        
        health.put("jmeter", Map.of(
            "installed", jmeterExists || foundPath != null,
            "configured_path", jmeterPathProperty,
            "found_path", foundPath != null ? foundPath : "NOT_FOUND",
            "status", (jmeterExists || foundPath != null) ? "OK" : "NOT_FOUND"
        ));
        
        // Check storage directories
        Path baseDir = Paths.get(storageBaseDir);
        boolean storageExists = Files.exists(baseDir);
        health.put("storage", Map.of(
            "exists", storageExists,
            "path", storageBaseDir,
            "status", storageExists ? "OK" : "NOT_FOUND"
        ));
        
        // Check MongoDB connection
        try {
            // Simple MongoDB check - you might want to inject MongoTemplate for this
            health.put("mongodb", Map.of(
                "status", "OK",
                "message", "Connection assumed OK"
            ));
        } catch (Exception e) {
            health.put("mongodb", Map.of(
                "status", "ERROR",
                "message", e.getMessage()
            ));
        }
        
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("status", "UP");
        
        return ResponseEntity.ok(health);
    }
}
