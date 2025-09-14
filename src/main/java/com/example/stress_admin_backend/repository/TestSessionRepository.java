package com.example.stress_admin_backend.repository;

import com.example.stress_admin_backend.model.TestSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestSessionRepository extends MongoRepository<TestSession, String> {
    
    List<TestSession> findByStatus(String status);
    
    List<TestSession> findByNameContainingIgnoreCase(String name);
    
    List<TestSession> findByCreatedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
}
