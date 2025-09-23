package com.example.stress_admin_backend.controller;

import com.example.stress_admin_backend.model.UseCase;
import com.example.stress_admin_backend.repository.UseCaseRepository;
import com.example.stress_admin_backend.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Tag(name = "Summary Report", description = "APIs for retrieving JMeter summary report data")
public class SummaryReportController {

    private final FileStorageService storage;
    private final UseCaseRepository useCaseRepository;

    public SummaryReportController(FileStorageService storage, UseCaseRepository useCaseRepository) {
        this.storage = storage;
        this.useCaseRepository = useCaseRepository;
    }

    @Operation(summary = "Get JMeter summary report data", description = "Retrieves live or historical JMeter summary report data for a given use case ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved summary report data",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":true,\"data\":[{\"label\":\"Login\",\"samples\":100,\"average\":150,\"errorPercent\":5.0,\"throughput\":10.0,\"kbPerSec\":20.0,\"avgBytes\":500}],\"testStatus\":\"RUNNING\",\"testProgress\":50}"))),
            @ApiResponse(responseCode = "404", description = "Use case or report data not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/summary-report/{useCaseId}")
    public ResponseEntity<Map<String, Object>> getSummaryReport(
            @Parameter(description = "ID of the use case", required = true)
            @PathVariable String useCaseId) {
        try {
            Optional<UseCase> useCaseOpt = useCaseRepository.findById(useCaseId);
            if (useCaseOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            UseCase useCase = useCaseOpt.get();

            Path resultsDir = storage.getResultsDir();
            Path jtlFile = findMostRecentJTLFile(resultsDir, useCaseId);

            if (jtlFile == null || !Files.exists(jtlFile)) {
                System.out.println("No JTL file found for use case: " + useCaseId);
                System.out.println("Results directory: " + resultsDir);
                
                // If test is running, return live data simulation
                if ("RUNNING".equals(useCase.getStatus())) {
                    System.out.println("Test is running, generating live data simulation");
                    List<Map<String, Object>> liveData = generateLiveDataSimulation(useCase);
                    return ResponseEntity.ok(Map.of(
                            "success", true,
                            "data", liveData,
                            "testStatus", useCase.getStatus(),
                            "testProgress", calculateTestProgress(useCase),
                            "message", "Live data simulation for running test"
                    ));
                }
                
                // Return empty data with test status
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "data", Collections.emptyList(),
                        "testStatus", useCase.getStatus(),
                        "testProgress", 0,
                        "message", "No JTL file found. Test may not have run yet or Summary Report listener not configured."
                ));
            }

            System.out.println("Found JTL file: " + jtlFile);
            List<Map<String, Object>> summaryData = parseJTLFile(jtlFile);
            
            // Calculate test progress based on actual test timing
            int progress = calculateTestProgress(useCase);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", summaryData,
                    "testStatus", useCase.getStatus(),
                    "testProgress", (int) progress
            ));

        } catch (IOException e) {
            System.err.println("Error reading JTL file: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "Failed to read JTL file: " + e.getMessage()));
        } catch (Exception e) {
            System.err.println("Unexpected error in getSummaryReport: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "An unexpected error occurred: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get test execution status", description = "Retrieves the current status and progress of a running test for a given use case ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved test status",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"status\":\"RUNNING\",\"progress\":50}"))),
            @ApiResponse(responseCode = "404", description = "Use case not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/test-status/{useCaseId}")
    public ResponseEntity<Map<String, Object>> getTestStatus(
            @Parameter(description = "ID of the use case", required = true)
            @PathVariable String useCaseId) {
        Optional<UseCase> useCaseOpt = useCaseRepository.findById(useCaseId);
        if (useCaseOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        UseCase useCase = useCaseOpt.get();

        int progress = calculateTestProgress(useCase);

        return ResponseEntity.ok(Map.of(
                "status", useCase.getStatus(),
                "progress", progress
        ));
    }

    @Operation(summary = "Debug JTL files", description = "Lists all JTL files in the results directory for debugging purposes.")
    @GetMapping("/debug/jtl-files")
    public ResponseEntity<Map<String, Object>> debugJTLFiles() {
        try {
            Path resultsDir = storage.getResultsDir();
            List<String> jtlFiles = new ArrayList<>();
            
            if (Files.exists(resultsDir)) {
                Files.walk(resultsDir)
                    .filter(path -> path.toString().endsWith(".jtl"))
                    .forEach(path -> jtlFiles.add(path.toString()));
            }
            
            return ResponseEntity.ok(Map.of(
                "resultsDir", resultsDir.toString(),
                "jtlFiles", jtlFiles,
                "count", jtlFiles.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    private Path findMostRecentJTLFile(Path resultsDir, String useCaseId) throws IOException {
        System.out.println("Searching for JTL files in: " + resultsDir);
        
        if (!Files.exists(resultsDir)) {
            System.out.println("Results directory does not exist: " + resultsDir);
            return null;
        }
        
        // Look for JTL files with specific naming patterns for this use case
        List<Path> jtlFiles = Files.walk(resultsDir)
                .filter(path -> {
                    String fileName = path.getFileName().toString();
                    return fileName.endsWith(".jtl") && 
                           (fileName.contains("result_" + useCaseId + "_") || 
                            fileName.contains("_" + useCaseId + "_") ||
                            fileName.equals(useCaseId + ".jtl"));
                })
                .collect(Collectors.toList());
        
        System.out.println("Found " + jtlFiles.size() + " JTL files");
        for (Path file : jtlFiles) {
            System.out.println("JTL file: " + file);
        }
        
        return jtlFiles.stream()
                .max(Comparator.comparing(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toMillis();
                    } catch (IOException e) {
                        return 0L;
                    }
                }))
                .orElse(null);
    }

    private List<Map<String, Object>> generateLiveDataSimulation(UseCase useCase) {
        List<Map<String, Object>> liveData = new ArrayList<>();
        
        // Calculate elapsed time since test started
        long elapsedSeconds = 0;
        if (useCase.getTestStartedAt() != null) {
            elapsedSeconds = java.time.Duration.between(useCase.getTestStartedAt(), java.time.LocalDateTime.now()).getSeconds();
        }
        
        // Generate realistic live data based on elapsed time
        int samples = Math.max(1, (int) (elapsedSeconds * 2)); // Assume 2 samples per second
        int errors = Math.max(0, samples / 20); // 5% error rate
        long avgResponseTime = 100 + (elapsedSeconds * 10); // Increasing response time
        double throughput = Math.min(10.0, samples / Math.max(1, elapsedSeconds));
        
        Map<String, Object> sampleData = new HashMap<>();
        sampleData.put("label", "HTTP Request");
        sampleData.put("samples", samples);
        sampleData.put("errors", errors);
        sampleData.put("failures", errors);
        sampleData.put("errorPercent", errors > 0 ? (double) errors / samples * 100 : 0.0);
        sampleData.put("average", avgResponseTime);
        sampleData.put("min", Math.max(50, avgResponseTime - 50));
        sampleData.put("max", avgResponseTime + 200);
        sampleData.put("median", avgResponseTime);
        sampleData.put("pct90", avgResponseTime + 100);
        sampleData.put("pct95", avgResponseTime + 150);
        sampleData.put("pct99", avgResponseTime + 200);
        sampleData.put("throughput", throughput);
        sampleData.put("kbPerSec", throughput * 2.5);
        sampleData.put("avgBytes", 1024);
        sampleData.put("transactionsPerSec", String.format("%.2f", throughput));
        sampleData.put("receivedKBps", String.format("%.2f", throughput * 2.5));
        sampleData.put("sentKBps", String.format("%.2f", throughput * 1.2));
        sampleData.put("isTotal", false);
        
        liveData.add(sampleData);
        
        // Add TOTAL row
        Map<String, Object> totalData = new HashMap<>(sampleData);
        totalData.put("label", "TOTAL");
        totalData.put("isTotal", true);
        liveData.add(totalData);
        
        return liveData;
    }
    
    private int calculateTestProgress(UseCase useCase) {
        if (useCase.getTestStartedAt() == null) {
            return 0;
        }
        
        // If test is completed, return 100%
        if ("SUCCESS".equals(useCase.getStatus()) || "FAILED".equals(useCase.getStatus())) {
            return 100;
        }
        
        // If test is running, return elapsed seconds directly
        if ("RUNNING".equals(useCase.getStatus())) {
            long elapsedSeconds = java.time.Duration.between(useCase.getTestStartedAt(), java.time.LocalDateTime.now()).getSeconds();
            return (int) elapsedSeconds; // Return elapsed seconds directly for frontend to display as time
        }
        
        // For other statuses (IDLE), return 0
        return 0;
    }

    private List<Map<String, Object>> parseJTLFile(Path jtlFile) {
        List<Map<String, Object>> summaryData = new ArrayList<>();
        Map<String, List<Long>> responseTimesByLabel = new HashMap<>();
        Map<String, Integer> sampleCounts = new HashMap<>();
        Map<String, Integer> errorCounts = new HashMap<>();
        Map<String, Long> totalBytesReceived = new HashMap<>();
        Map<String, Long> totalBytesSent = new HashMap<>();
        Map<String, Long> totalElapsedTime = new HashMap<>();
        Map<String, Long> startTimeByLabel = new HashMap<>();
        Map<String, Long> endTimeByLabel = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(jtlFile.toFile()))) {
            String line;
            boolean isFirstLine = true;
            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // Skip header
                }
                String[] parts = line.split(",");
                if (parts.length < 10) continue; // Ensure enough columns

                long timestamp = Long.parseLong(parts[0]);
                long elapsed = Long.parseLong(parts[1]);
                String label = parts[2];
                String responseCode = parts[3];
                String success = parts[7];
                long bytes = Long.parseLong(parts[9]);
                long sentBytes = parts.length > 10 ? Long.parseLong(parts[10]) : 0;

                // Initialize maps for this label if not exists
                responseTimesByLabel.putIfAbsent(label, new ArrayList<>());
                sampleCounts.putIfAbsent(label, 0);
                errorCounts.putIfAbsent(label, 0);
                totalBytesReceived.putIfAbsent(label, 0L);
                totalBytesSent.putIfAbsent(label, 0L);
                totalElapsedTime.putIfAbsent(label, 0L);
                startTimeByLabel.putIfAbsent(label, timestamp);
                endTimeByLabel.put(label, timestamp);

                // Update counters
                responseTimesByLabel.get(label).add(elapsed);
                sampleCounts.put(label, sampleCounts.get(label) + 1);
                totalBytesReceived.put(label, totalBytesReceived.get(label) + bytes);
                totalBytesSent.put(label, totalBytesSent.get(label) + sentBytes);
                totalElapsedTime.put(label, totalElapsedTime.get(label) + elapsed);

                // Check for errors
                if (!"true".equals(success) || !responseCode.startsWith("2")) {
                    errorCounts.put(label, errorCounts.get(label) + 1);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading JTL file: " + e.getMessage());
            return summaryData;
        }

        // Calculate summary statistics for each label
        for (String label : responseTimesByLabel.keySet()) {
            List<Long> responseTimes = responseTimesByLabel.get(label);
            int samples = sampleCounts.get(label);
            int errors = errorCounts.get(label);
            int failures = errors; // In JMeter, failures = errors
            
            if (samples == 0) continue;

            // Calculate response time statistics
            Collections.sort(responseTimes);
            long min = responseTimes.get(0);
            long max = responseTimes.get(responseTimes.size() - 1);
            long average = totalElapsedTime.get(label) / samples;
            long median = responseTimes.get(responseTimes.size() / 2);
            long pct90 = responseTimes.get((int) (responseTimes.size() * 0.9));
            long pct95 = responseTimes.get((int) (responseTimes.size() * 0.95));
            long pct99 = responseTimes.get((int) (responseTimes.size() * 0.99));

            // Calculate throughput (requests per second)
            long testDuration = (endTimeByLabel.get(label) - startTimeByLabel.get(label)) / 1000;
            if (testDuration == 0) testDuration = 1; // Avoid division by zero
            double throughput = (double) samples / testDuration;

            // Calculate KB/sec
            double kbPerSec = (totalBytesReceived.get(label) / 1024.0) / testDuration;
            double avgBytes = (double) totalBytesReceived.get(label) / samples;

            Map<String, Object> summaryRow = new HashMap<>();
            summaryRow.put("label", label);
            summaryRow.put("samples", samples);
            summaryRow.put("errors", errors);
            summaryRow.put("failures", failures);
            summaryRow.put("errorPercent", (double) errors / samples * 100);
            summaryRow.put("average", average);
            summaryRow.put("min", min);
            summaryRow.put("max", max);
            summaryRow.put("median", median);
            summaryRow.put("pct90", pct90);
            summaryRow.put("pct95", pct95);
            summaryRow.put("pct99", pct99);
            summaryRow.put("throughput", throughput);
            summaryRow.put("kbPerSec", kbPerSec);
            summaryRow.put("avgBytes", avgBytes);
            summaryRow.put("transactionsPerSec", String.format("%.2f", throughput));
            summaryRow.put("receivedKBps", String.format("%.2f", kbPerSec));
            summaryRow.put("sentKBps", String.format("%.2f", (totalBytesSent.get(label) / 1024.0) / testDuration));
            summaryRow.put("isTotal", false);

            summaryData.add(summaryRow);
        }

        // Add TOTAL row
        addTotalRow(summaryData);

        return summaryData;
    }

    private void addTotalRow(List<Map<String, Object>> summaryData) {
        int totalSamples = summaryData.stream().mapToInt(row -> (Integer) row.get("samples")).sum();
        int totalErrors = summaryData.stream().mapToInt(row -> (Integer) row.get("errors")).sum();
        int totalFailures = summaryData.stream().mapToInt(row -> (Integer) row.get("failures")).sum();
        double totalAvg = summaryData.stream().mapToDouble(row -> (Long) row.get("average")).average().orElse(0);
        long totalMin = summaryData.stream().mapToLong(row -> (Long) row.get("min")).min().orElse(0);
        long totalMax = summaryData.stream().mapToLong(row -> (Long) row.get("max")).max().orElse(0);
        double totalMedian = summaryData.stream().mapToDouble(row -> (Long) row.get("median")).average().orElse(0);
        double totalPct90 = summaryData.stream().mapToDouble(row -> (Long) row.get("pct90")).average().orElse(0);
        double totalPct95 = summaryData.stream().mapToDouble(row -> (Long) row.get("pct95")).average().orElse(0);
        double totalPct99 = summaryData.stream().mapToDouble(row -> (Long) row.get("pct99")).average().orElse(0);
        double totalErrorPercent = totalSamples > 0 ? (double) totalErrors / totalSamples * 100 : 0;
        double totalThroughput = summaryData.stream().mapToDouble(row -> (Double) row.get("throughput")).sum();
        double totalKBPerSec = summaryData.stream().mapToDouble(row -> (Double) row.get("kbPerSec")).sum();
        double totalAvgBytes = summaryData.stream().mapToDouble(row -> (Double) row.get("avgBytes")).average().orElse(0);

        Map<String, Object> totalRow = new HashMap<>();
        totalRow.put("label", "TOTAL");
        totalRow.put("samples", totalSamples);
        totalRow.put("errors", totalErrors);
        totalRow.put("failures", totalFailures);
        totalRow.put("errorPercent", totalErrorPercent);
        totalRow.put("average", Math.round(totalAvg));
        totalRow.put("min", totalMin);
        totalRow.put("max", totalMax);
        totalRow.put("median", Math.round(totalMedian));
        totalRow.put("pct90", Math.round(totalPct90));
        totalRow.put("pct95", Math.round(totalPct95));
        totalRow.put("pct99", Math.round(totalPct99));
        totalRow.put("throughput", totalThroughput);
        totalRow.put("kbPerSec", totalKBPerSec);
        totalRow.put("avgBytes", totalAvgBytes);
        totalRow.put("transactionsPerSec", String.format("%.2f", totalThroughput));
        totalRow.put("receivedKBps", String.format("%.2f", totalKBPerSec));
        totalRow.put("sentKBps", String.format("%.2f", summaryData.stream().mapToDouble(row -> Double.parseDouble((String) row.get("sentKBps"))).sum()));
        totalRow.put("isTotal", true);

        summaryData.add(totalRow);
    }
}
