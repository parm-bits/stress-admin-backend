package com.example.stress_admin_backend.service;

import com.example.stress_admin_backend.model.UseCase;
import com.example.stress_admin_backend.repository.UseCaseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import jakarta.annotation.PostConstruct;

@Service
public class JMeterService {

    private final FileStorageService storage;
    private final UseCaseRepository repo;
    private final JmxModificationService jmxModificationService;

    @Value("${jmeter.path}")
    private String defaultJmeterPath;
    
    private String jmeterPath;

    @Value("${jmeter.remote.enabled:false}")
    private boolean remoteEnabled;

    @Value("${jmeter.remote.host:3.128.170.155}")
    private String remoteHost;
    
    @Value("${jmeter.alternative.paths:}")
    private String alternativePaths;

    private final Map<String, Process> runningProcesses = new HashMap<>();

    public JMeterService(FileStorageService storage, UseCaseRepository repo, JmxModificationService jmxModificationService) {
        this.storage = storage;
        this.repo = repo;
        this.jmxModificationService = jmxModificationService;
        // Don't initialize jmeterPath here - it will be set by @PostConstruct
    }
    
    /**
     * Initialize jmeterPath after dependency injection
     */
    @PostConstruct
    public void init() {
        this.jmeterPath = defaultJmeterPath;
        System.out.println("JMeterService initialized with path: " + jmeterPath);
    }
    
    /**
     * Updates the JMeter path (called from SettingsController)
     */
    public void updateJmeterPath(String newPath) {
        this.jmeterPath = newPath;
        System.out.println("JMeter path updated to: " + newPath);
    }

    @Async
    public CompletableFuture<Void> runTest(String useCaseId, int users) {
        Optional<UseCase> opt = repo.findById(useCaseId);
        if (opt.isEmpty()) {
            System.err.println("Use case not found: " + useCaseId);
            return CompletableFuture.completedFuture(null);
        }

        UseCase uc = opt.get();
        uc.setStatus("RUNNING");
        uc.setLastRunAt(LocalDateTime.now());
        uc.setTestStartedAt(LocalDateTime.now());
        uc.setTestCompletedAt(null); // Clear previous completion time
        uc.setTestDurationSeconds(null); // Clear previous duration
        
        // Extract duration from Thread Group Configuration
        int durationSeconds = extractDurationFromThreadGroupConfig(uc);
        uc.setExpectedDurationSeconds((long) durationSeconds); // Store expected duration from Thread Group Config
        uc.setUserCount(users);
        repo.save(uc);
        
        System.out.println("Starting JMeter test for use case: " + uc.getName() + " (ID: " + useCaseId + ") with " + users + " users");

        // Validate JMeter installation and try alternative paths
        Path jmeterExecutable = findValidJmeterPath();
        if (jmeterExecutable == null) {
            uc.setStatus("FAILED");
            uc.setLastRunAt(LocalDateTime.now());
            repo.save(uc);
            System.err.println("JMeter executable not found at: " + jmeterPath);
            System.err.println("Tried alternative paths but none were valid.");
            return CompletableFuture.completedFuture(null);
        }
        
        // Check if JMeter is executable
        if (!Files.isReadable(jmeterExecutable)) {
            uc.setStatus("FAILED");
            uc.setLastRunAt(LocalDateTime.now());
            repo.save(uc);
            System.err.println("JMeter executable is not readable at: " + jmeterPath);
            return CompletableFuture.completedFuture(null);
        }

        String modifiedJmxPath = null;
        try {
            String jmx = uc.getJmxPath();
            String csv = uc.getCsvPath();

            // Validate JMX file exists
            Path jmxFile = Paths.get(jmx);
            if (!Files.exists(jmxFile)) {
                uc.setStatus("FAILED");
                uc.setLastRunAt(LocalDateTime.now());
                repo.save(uc);
                System.err.println("JMX file not found: " + jmx);
                return CompletableFuture.completedFuture(null);
            }

            // Validate CSV file exists (only if CSV is required)
            if (csv != null && !csv.trim().isEmpty()) {
                Path csvFile = Paths.get(csv);
                if (!Files.exists(csvFile)) {
                    uc.setStatus("FAILED");
                    uc.setLastRunAt(LocalDateTime.now());
                    repo.save(uc);
                    System.err.println("CSV file not found: " + csv);
                    return CompletableFuture.completedFuture(null);
                }
            }

            String stamp = String.valueOf(System.currentTimeMillis());
            Path resultJtl = storage.getResultsDir().resolve("result_" + useCaseId + "_" + stamp + ".jtl");
            Path reportDir = storage.getReportsDir().resolve("report_" + useCaseId + "_" + stamp);

            if (Files.exists(reportDir)) deleteRecursive(reportDir);
            Files.createDirectories(reportDir);

            // Create modified JMX file with updated configurations (including duration from UI)
            modifiedJmxPath = createModifiedJmxFile(uc, stamp, durationSeconds);
            
            List<String> cmd = buildJMeterCommand(
                    "-n",
                    "-t", modifiedJmxPath,
                    "-l", resultJtl.toString(),
                    "-Jusers=" + users,
                    "-e",
                    "-o", reportDir.toString(),
                    "-Jrampup=" + Math.max(60, durationSeconds / 5), // Ramp up over 20% of duration, minimum 60 seconds
                    "-Jjmeter.save.saveservice.output_format=csv",
                    "-Jjmeter.save.saveservice.response_data=false",
                    "-Jjmeter.save.saveservice.samplerData=false",
                    "-Jjmeter.save.saveservice.response_data.on_error=false",
                    "-Jjmeter.save.saveservice.autoflush=true",
                    "-Jjmeter.save.saveservice.print_field_names=false"
            );
            
            // Add CSV path only if CSV file exists
            if (csv != null && !csv.trim().isEmpty()) {
                cmd.add("-JcsvPath=" + csv);
            }

            // Log the command being executed
            System.out.println("Executing JMeter command-->: " + String.join(" ", cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            Path jmeterBin = Paths.get(jmeterPath).getParent();
            if (jmeterBin != null) {
                pb.directory(jmeterBin.toFile());
                System.out.println("Working directory: " + jmeterBin.toString());
            }

            Process p = pb.start();
            
            // Store the process for potential stopping
            runningProcesses.put(useCaseId, p);

            Path log = storage.getResultsDir().resolve("jmeter_exec_" + useCaseId + "_" + stamp + ".log");
            StringBuilder outputBuffer = new StringBuilder();
            
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                 BufferedWriter bw = Files.newBufferedWriter(log, StandardOpenOption.CREATE)) {
                String line;
                while ((line = br.readLine()) != null) {
                    bw.write(line);
                    bw.newLine();
                    outputBuffer.append(line).append("\n");
                    System.out.println("JMeter: " + line); // Log JMeter output to console
                }
            }

            // Wait for process with timeout (30 minutes)
            boolean finished = p.waitFor(30, java.util.concurrent.TimeUnit.MINUTES);
            int exit = finished ? p.exitValue() : -1;
            
            if (!finished) {
                System.err.println("JMeter process timed out after 30 minutes");
                p.destroyForcibly();
                exit = -1;
            }
            System.out.println("JMeter process exited with code: " + exit);
            
            // Calculate test duration
            LocalDateTime testEndTime = LocalDateTime.now();
            uc.setTestCompletedAt(testEndTime);
            
            if (uc.getTestStartedAt() != null) {
                long actualDurationSeconds = java.time.Duration.between(uc.getTestStartedAt(), testEndTime).getSeconds();
                uc.setTestDurationSeconds(actualDurationSeconds);
                System.out.println("Test duration: " + actualDurationSeconds + " seconds (" + formatDuration(actualDurationSeconds) + ")");
            }
            
            if (exit == 0) {
                String reportRel = "/reports/" + reportDir.getFileName().toString() + "/index.html";
                uc.setLastReportUrl(reportRel);
                uc.setStatus("SUCCESS");
                System.out.println("JMeter test completed successfully for use case: " + uc.getName() + " (ID: " + useCaseId + ")");
            } else {
                uc.setStatus("FAILED");
                System.err.println("JMeter test failed with exit code: " + exit + " for use case: " + uc.getName() + " (ID: " + useCaseId + ")");
            }

            uc.setLastRunAt(LocalDateTime.now());
            repo.save(uc);
            
            // Clean up temporary modified JMX file if it was created
            cleanupModifiedJmxFile(modifiedJmxPath, uc.getJmxPath());
            
            // Remove from running processes
            runningProcesses.remove(useCaseId);

        } catch (Exception e) {
            uc.setStatus("FAILED");
            uc.setLastRunAt(LocalDateTime.now());
            
            // Calculate test duration even for failed tests
            LocalDateTime testEndTime = LocalDateTime.now();
            uc.setTestCompletedAt(testEndTime);
            
            if (uc.getTestStartedAt() != null) {
                long actualDurationSeconds = java.time.Duration.between(uc.getTestStartedAt(), testEndTime).getSeconds();
                uc.setTestDurationSeconds(actualDurationSeconds);
                System.out.println("Test failed after: " + actualDurationSeconds + " seconds (" + formatDuration(actualDurationSeconds) + ")");
            }
            
            repo.save(uc);
            
            // Clean up temporary modified JMX file if it was created
            try {
                cleanupModifiedJmxFile(modifiedJmxPath, uc.getJmxPath());
            } catch (Exception cleanupError) {
                System.err.println("Error cleaning up modified JMX file: " + cleanupError.getMessage());
            }
            
            // Log detailed error information
            System.err.println("JMeter test failed for use case: " + uc.getName() + " (ID: " + useCaseId + ")");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            
            // Write error details to a log file
            try {
                Path errorLog = storage.getResultsDir().resolve("error_" + useCaseId + "_" + System.currentTimeMillis() + ".log");
                Files.createDirectories(errorLog.getParent());
                try (BufferedWriter writer = Files.newBufferedWriter(errorLog, StandardOpenOption.CREATE)) {
                    writer.write("JMeter Test Execution Failed\n");
                    writer.write("Use Case ID: " + useCaseId + "\n");
                    writer.write("Timestamp: " + LocalDateTime.now() + "\n");
                    writer.write("Error: " + e.getMessage() + "\n");
                    writer.write("Stack Trace:\n");
                    e.printStackTrace(new PrintWriter(writer));
                }
            } catch (IOException logError) {
                System.err.println("Failed to write error log: " + logError.getMessage());
            }
            
            // Remove from running processes
            runningProcesses.remove(useCaseId);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Creates a modified JMX file with the updated configurations from the use case
     */
    private String createModifiedJmxFile(UseCase useCase, String stamp, int durationSeconds) throws IOException {
        String originalJmxPath = useCase.getJmxPath();
        
        // Always create a modified JMX file to fix CSV paths and apply UI duration
        System.out.println("Creating modified JMX file to fix CSV paths and apply duration: " + durationSeconds + " seconds");
        
        // Create modified JMX file
        String modifiedJmxPath = storage.getResultsDir().resolve("modified_" + useCase.getId() + "_" + stamp + ".jmx").toString();
        
        try {
            // Apply modifications using JmxModificationService (pass duration to override JMX)
            String modifiedJmxContent = jmxModificationService.modifyJmxWithConfiguration(originalJmxPath, useCase, durationSeconds);
            
            // Write modified content to new file
            Files.write(Paths.get(modifiedJmxPath), modifiedJmxContent.getBytes());
            
            System.out.println("Created modified JMX file: " + modifiedJmxPath);
            System.out.println("Applied duration: " + durationSeconds + " seconds (from UI config)");
            System.out.println("Applied thread group config: " + (useCase.getThreadGroupConfig() != null && !useCase.getThreadGroupConfig().isEmpty()));
            System.out.println("Applied server config: " + (useCase.getServerConfig() != null && !useCase.getServerConfig().isEmpty()));
            
            return modifiedJmxPath;
            
        } catch (Exception e) {
            System.err.println("Error creating modified JMX file: " + e.getMessage());
            e.printStackTrace();
            // Fallback to original file if modification fails
            return originalJmxPath;
        }
    }

    /**
     * Cleans up temporary modified JMX file if it was created
     */
    private void cleanupModifiedJmxFile(String modifiedJmxPath, String originalJmxPath) {
        if (modifiedJmxPath != null && !modifiedJmxPath.equals(originalJmxPath)) {
            try {
                Path modifiedFile = Paths.get(modifiedJmxPath);
                if (Files.exists(modifiedFile)) {
                    Files.delete(modifiedFile);
                    System.out.println("Cleaned up temporary modified JMX file: " + modifiedJmxPath);
                }
            } catch (IOException e) {
                System.err.println("Error cleaning up modified JMX file: " + e.getMessage());
            }
        }
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
                    System.out.println("Extracted duration from Thread Group Configuration: " + duration + " seconds");
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
     * Formats duration in seconds to human-readable format (e.g., "5m 30s", "2h 15m")
     */
    private String formatDuration(long durationSeconds) {
        if (durationSeconds < 60) {
            return durationSeconds + "s";
        } else if (durationSeconds < 3600) {
            long minutes = durationSeconds / 60;
            long seconds = durationSeconds % 60;
            return minutes + "m " + seconds + "s";
        } else {
            long hours = durationSeconds / 3600;
            long minutes = (durationSeconds % 3600) / 60;
            long seconds = durationSeconds % 60;
            return hours + "h " + minutes + "m " + seconds + "s";
        }
    }

    public void stopTest(String useCaseId) {
        Process process = runningProcesses.get(useCaseId);
        if (process != null && process.isAlive()) {
            try {
                System.out.println("Stopping JMeter test for use case: " + useCaseId);
                process.destroyForcibly();
                runningProcesses.remove(useCaseId);
                System.out.println("JMeter test stopped for use case: " + useCaseId);
            } catch (Exception e) {
                System.err.println("Error stopping JMeter test for use case: " + useCaseId + ": " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("No running process found for use case: " + useCaseId);
        }
    }

    private List<String> buildJMeterCommand(String... args) {
        List<String> cmd = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win") && jmeterPath.toLowerCase().endsWith(".bat")) {
            cmd.add("cmd.exe");
            cmd.add("/c");
            cmd.add(jmeterPath);
            cmd.addAll(Arrays.asList(args));
        } else if (jmeterPath.toLowerCase().endsWith(".jar")) {
            // For JAR file execution, use java -jar
            cmd.add("java");
            cmd.add("-Xmx1024m"); // Set JVM heap size
            cmd.add("-jar");
            cmd.add(jmeterPath);
            cmd.addAll(Arrays.asList(args));
        } else if (jmeterPath.toLowerCase().endsWith(".sh")) {
            // For shell script execution on Linux/Unix
            cmd.add("bash");
            cmd.add(jmeterPath);
            cmd.addAll(Arrays.asList(args));
        } else {
            // Direct executable (like jmeter without .sh extension)
            cmd.add(jmeterPath);
            cmd.addAll(Arrays.asList(args));
        }
        
        // Add remote execution if enabled
        if (remoteEnabled) {
            cmd.add("-r");  // Run remote
            cmd.add("-R");  // Remote hosts
            cmd.add(remoteHost);
        }
        
        return cmd;
    }

    private void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
    
    /**
     * Finds a valid JMeter executable path by trying the configured path and alternatives
     */
    private Path findValidJmeterPath() {
        // Try the configured path first
        Path configuredPath = Paths.get(jmeterPath);
        if (Files.exists(configuredPath) && Files.isReadable(configuredPath)) {
            System.out.println("Using configured JMeter path: " + jmeterPath);
            return configuredPath;
        }
        
        // Try alternative paths if configured
        if (alternativePaths != null && !alternativePaths.trim().isEmpty()) {
            String[] paths = alternativePaths.split(",");
            for (String path : paths) {
                path = path.trim();
                if (!path.isEmpty()) {
                    Path altPath = Paths.get(path);
                    if (Files.exists(altPath) && Files.isReadable(altPath)) {
                        System.out.println("Using alternative JMeter path: " + path);
                        return altPath;
                    }
                }
            }
        }
        
        // Try common JMeter installation paths (Linux/Ubuntu uses .sh scripts)
        String[] commonPaths = {
            "/opt/jmeter/bin/jmeter.sh",
            "/usr/local/jmeter/bin/jmeter.sh",
            "/home/ubuntu/apache-jmeter-5.6.3/bin/jmeter.sh",
            "/opt/apache-jmeter-5.6.3/bin/jmeter.sh",
            "/usr/share/jmeter/bin/jmeter.sh",
            "/opt/jmeter/bin/jmeter",
            "/usr/local/jmeter/bin/jmeter",
            "/home/ubuntu/apache-jmeter-5.6.3/bin/jmeter",
            "/opt/apache-jmeter-5.6.3/bin/jmeter"
        };
        
        for (String commonPath : commonPaths) {
            Path path = Paths.get(commonPath);
            if (Files.exists(path) && Files.isReadable(path)) {
                System.out.println("Using common JMeter path: " + commonPath);
                return path;
            }
        }
        
        System.err.println("No valid JMeter executable found. Tried:");
        System.err.println("  Configured: " + jmeterPath);
        if (alternativePaths != null && !alternativePaths.trim().isEmpty()) {
            System.err.println("  Alternatives: " + alternativePaths);
        }
        System.err.println("  Common paths: " + String.join(", ", commonPaths));
        
        return null;
    }
}
