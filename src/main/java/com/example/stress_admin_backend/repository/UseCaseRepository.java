package com.example.stress_admin_backend.repository;

import com.example.stress_admin_backend.model.UseCase;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface UseCaseRepository extends MongoRepository<UseCase, String> {
    List<UseCase> findByUserId(String userId);
}
