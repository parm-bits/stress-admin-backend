package com.example.stress_admin_backend.repository;

import com.example.stress_admin_backend.model.UseCase;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UseCaseRepository extends MongoRepository<UseCase, String> {
}
