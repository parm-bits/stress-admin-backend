package com.example.stress_admin_backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
@Schema(description = "User entity for authentication")
public class User {
    
    @Id
    @Schema(description = "Unique identifier for the user", example = "507f1f77bcf86cd799439011")
    private String id;
    
    @Schema(description = "Username for login", example = "admin")
    private String username;
    
    @Schema(description = "User's email address", example = "admin@example.com")
    private String email;
    
    @Schema(description = "Encrypted password")
    private String password;
    
    @Schema(description = "User's full name", example = "Administrator")
    private String fullName;
    
    @Schema(description = "User role", example = "ADMIN")
    private String role;
    
    @Schema(description = "Account creation timestamp")
    private LocalDateTime createdAt;
    
    @Schema(description = "Last login timestamp")
    private LocalDateTime lastLoginAt;
    
    @Schema(description = "Account status", example = "ACTIVE")
    private String status;
    
}
