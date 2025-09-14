package com.example.stress_admin_backend.repository;

import com.example.stress_admin_backend.model.TestConfiguration;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestConfigurationRepository extends MongoRepository<TestConfiguration, String> {
    
    List<TestConfiguration> findByIsActiveTrue();
    
    List<TestConfiguration> findByNameContainingIgnoreCase(String name);
    
    List<TestConfiguration> findByJmxFileNameContainingIgnoreCase(String jmxFileName);
    
    List<TestConfiguration> findByCsvFileNameContainingIgnoreCase(String csvFileName);
}
