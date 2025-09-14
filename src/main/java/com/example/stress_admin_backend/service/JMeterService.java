package com.example.stress_admin_backend.service;

import com.example.stress_admin_backend.model.UseCase;
import com.example.stress_admin_backend.repository.UseCaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class JMeterService {

    private final FileStorageService storage;
    private final UseCaseRepository repo;

    @Value("${jmeter.path}")
    private String jmeterPath;

    private final Map<String, Process> runningProcesses = new HashMap<>();

    public JMeterService(FileStorageService storage, UseCaseRepository repo) {
        this.storage = storage;
        this.repo = repo;
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
        uc.setUserCount(users);
        repo.save(uc);
        
        System.out.println("Starting JMeter test for use case: " + uc.getName() + " (ID: " + useCaseId + ") with " + users + " users");

        // Validate JMeter installation
        Path jmeterExecutable = Paths.get(jmeterPath);
        if (!Files.exists(jmeterExecutable)) {
            uc.setStatus("FAILED");
            uc.setLastRunAt(LocalDateTime.now());
            repo.save(uc);
            System.err.println("JMeter executable not found at: " + jmeterPath);
            return CompletableFuture.completedFuture(null);
        }

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

            // Validate CSV file exists
            Path csvFile = Paths.get(csv);
            if (!Files.exists(csvFile)) {
                uc.setStatus("FAILED");
                uc.setLastRunAt(LocalDateTime.now());
                repo.save(uc);
                System.err.println("CSV file not found: " + csv);
                return CompletableFuture.completedFuture(null);
            }

            String stamp = String.valueOf(System.currentTimeMillis());
            Path resultJtl = storage.getResultsDir().resolve("result_" + useCaseId + "_" + stamp + ".jtl");
            Path reportDir = storage.getReportsDir().resolve("report_" + useCaseId + "_" + stamp);

            if (Files.exists(reportDir)) deleteRecursive(reportDir);
            Files.createDirectories(reportDir);

            List<String> cmd = buildJMeterCommand(
                    "-n",
                    "-t", jmx,
                    "-l", resultJtl.toString(),
                    "-Jusers=" + users,
                    "-JcsvPath=" + csv,
                    "-e",
                    "-o", reportDir.toString()
            );

            // Log the command being executed
            System.out.println("Executing JMeter command: " + String.join(" ", cmd));

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
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                 BufferedWriter bw = Files.newBufferedWriter(log, StandardOpenOption.CREATE)) {
                String line;
                while ((line = br.readLine()) != null) {
                    bw.write(line);
                    bw.newLine();
                }
            }

            int exit = p.waitFor();
            System.out.println("JMeter process exited with code: " + exit);
            
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
            
            // Remove from running processes
            runningProcesses.remove(useCaseId);

        } catch (Exception e) {
            uc.setStatus("FAILED");
            uc.setLastRunAt(LocalDateTime.now());
            repo.save(uc);
            
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
            cmd.add("-jar");
            cmd.add(jmeterPath);
            cmd.addAll(Arrays.asList(args));
        } else {
            cmd.add(jmeterPath);
            cmd.addAll(Arrays.asList(args));
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
}
