package com.example.stress_admin_backend.service;

import com.example.stress_admin_backend.model.TestSession;
import com.example.stress_admin_backend.model.UseCase;
import com.example.stress_admin_backend.repository.TestSessionRepository;
import com.example.stress_admin_backend.repository.UseCaseRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConcurrentTestService {

    private final JMeterService jMeterService;
    private final UseCaseRepository useCaseRepository;
    private final TestSessionRepository testSessionRepository;
    
    // Track running test sessions
    private final Map<String, TestSession> runningSessions = new ConcurrentHashMap<>();

    public ConcurrentTestService(JMeterService jMeterService, 
                               UseCaseRepository useCaseRepository, 
                               TestSessionRepository testSessionRepository) {
        this.jMeterService = jMeterService;
        this.useCaseRepository = useCaseRepository;
        this.testSessionRepository = testSessionRepository;
    }

    @Async
    public CompletableFuture<TestSession> runConcurrentTest(String sessionId) {
        Optional<TestSession> sessionOpt = testSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        TestSession session = sessionOpt.get();
        runningSessions.put(sessionId, session);
        
        try {
            // Update session status
            session.setStatus("RUNNING");
            session.setStartedAt(LocalDateTime.now());
            session.setUpdatedAt(LocalDateTime.now());
            testSessionRepository.save(session);

            // Get all use cases for this session
            List<UseCase> useCases = useCaseRepository.findAllById(session.getUseCaseIds());
            
            // Sort by priority (1 = highest priority)
            useCases.sort(Comparator.comparing(uc -> uc.getPriority() != null ? uc.getPriority() : Integer.MAX_VALUE));

            // Start all use cases concurrently
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (UseCase useCase : useCases) {
                // Update use case status
                useCase.setStatus("RUNNING");
                useCase.setTestSessionId(sessionId);
                useCase.setUserCount(session.getUserCounts().get(useCase.getId()));
                useCaseRepository.save(useCase);

                // Start JMeter test for this use case
                CompletableFuture<Void> future = jMeterService.runTest(useCase.getId(), useCase.getUserCount())
                    .thenAccept(result -> {
                        // Update use case status when test completes
                        Optional<UseCase> updatedUseCase = useCaseRepository.findById(useCase.getId());
                        if (updatedUseCase.isPresent()) {
                            UseCase uc = updatedUseCase.get();
                            updateSessionProgress(sessionId, uc.getId(), uc.getStatus());
                        }
                    });
                
                futures.add(future);
            }

            // Wait for all tests to complete
            CompletableFuture<Void> allTests = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );

            allTests.thenRun(() -> {
                completeSession(sessionId);
            });

            return CompletableFuture.completedFuture(session);

        } catch (Exception e) {
            session.setStatus("FAILED");
            session.setUpdatedAt(LocalDateTime.now());
            testSessionRepository.save(session);
            runningSessions.remove(sessionId);
            
            System.err.println("Concurrent test session failed: " + e.getMessage());
            e.printStackTrace();
            
            return CompletableFuture.completedFuture(session);
        }
    }

    private void updateSessionProgress(String sessionId, String useCaseId, String useCaseStatus) {
        Optional<TestSession> sessionOpt = testSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) return;

        TestSession session = sessionOpt.get();
        
        // Update use case status in session
        if (session.getUseCaseStatuses() == null) {
            session.setUseCaseStatuses(new HashMap<>());
        }
        session.getUseCaseStatuses().put(useCaseId, useCaseStatus);
        
        // Update counters
        if (useCaseStatus.equals("SUCCESS")) {
            session.setSuccessCount((session.getSuccessCount() != null ? session.getSuccessCount() : 0) + 1);
        } else if (useCaseStatus.equals("FAILED")) {
            session.setFailureCount((session.getFailureCount() != null ? session.getFailureCount() : 0) + 1);
        }
        
        // Check if all use cases are complete
        boolean allComplete = session.getUseCaseStatuses().values().stream()
            .allMatch(status -> status.equals("SUCCESS") || status.equals("FAILED"));
        
        if (allComplete) {
            completeSession(sessionId);
        } else {
            session.setUpdatedAt(LocalDateTime.now());
            testSessionRepository.save(session);
        }
    }

    private void completeSession(String sessionId) {
        Optional<TestSession> sessionOpt = testSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) return;

        TestSession session = sessionOpt.get();
        
        // Determine final session status
        int totalUseCases = session.getUseCaseIds().size();
        int successCount = session.getSuccessCount() != null ? session.getSuccessCount() : 0;
        int failureCount = session.getFailureCount() != null ? session.getFailureCount() : 0;
        
        if (successCount == totalUseCases) {
            session.setStatus("SUCCESS");
        } else if (failureCount == totalUseCases) {
            session.setStatus("FAILED");
        } else {
            session.setStatus("PARTIAL_SUCCESS");
        }
        
        session.setCompletedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        testSessionRepository.save(session);
        
        runningSessions.remove(sessionId);
        
        System.out.println("Concurrent test session completed: " + sessionId + 
                          " - Status: " + session.getStatus() + 
                          " - Success: " + successCount + 
                          " - Failed: " + failureCount);
    }

    public TestSession createTestSession(String name, String description, 
                                       List<String> useCaseIds, 
                                       Map<String, Integer> userCounts,
                                       String userId) {
        
        // Validate use cases exist
        List<UseCase> useCases = useCaseRepository.findAllById(useCaseIds);
        if (useCases.size() != useCaseIds.size()) {
            throw new IllegalArgumentException("Some use cases not found");
        }
        
        // Calculate total users
        int totalUsers = userCounts.values().stream().mapToInt(Integer::intValue).sum();
        
        TestSession session = TestSession.builder()
            .name(name)
            .description(description)
            .useCaseIds(useCaseIds)
            .userCounts(userCounts)
            .status("IDLE")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .totalUsers(totalUsers)
            .useCaseCount(useCaseIds.size())
            .successCount(0)
            .failureCount(0)
            .useCaseStatuses(new HashMap<>())
            .useCaseReportUrls(new HashMap<>())
            .userId(userId)
            .build();
        
        return testSessionRepository.save(session);
    }

    public List<TestSession> getRunningSessions() {
        return new ArrayList<>(runningSessions.values());
    }

    public Optional<TestSession> getSession(String sessionId) {
        return testSessionRepository.findById(sessionId);
    }

    public List<TestSession> getAllSessions() {
        return testSessionRepository.findAll();
    }
    
    public List<TestSession> getSessionsByUserId(String userId) {
        return testSessionRepository.findByUserId(userId);
    }

    public boolean stopSession(String sessionId) {
        Optional<TestSession> sessionOpt = testSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) return false;
        
        TestSession session = sessionOpt.get();
        if (!session.getStatus().equals("RUNNING")) return false;
        
        // Stop all running use cases in this session
        for (String useCaseId : session.getUseCaseIds()) {
            Optional<UseCase> useCaseOpt = useCaseRepository.findById(useCaseId);
            if (useCaseOpt.isPresent() && useCaseOpt.get().getStatus().equals("RUNNING")) {
                UseCase useCase = useCaseOpt.get();
                useCase.setStatus("FAILED");
                useCaseRepository.save(useCase);
            }
        }
        
        session.setStatus("FAILED");
        session.setUpdatedAt(LocalDateTime.now());
        testSessionRepository.save(session);
        
        runningSessions.remove(sessionId);
        return true;
    }
}
