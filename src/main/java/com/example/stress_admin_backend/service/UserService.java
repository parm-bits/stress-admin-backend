package com.example.stress_admin_backend.service;

import com.example.stress_admin_backend.model.User;
import com.example.stress_admin_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    public Map<String, Object> createUser(String username, String password, String fullName, String role) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            System.out.println("User creation attempt for username: " + username + ", role: " + role);
            
            // Check if user already exists
            if (userRepository.existsByUsername(username)) {
                System.out.println("Username already exists: " + username);
                response.put("success", false);
                response.put("message", "Username already exists");
                return response;
            }
            
            // Validate role
            if (!role.equals("ADMIN") && !role.equals("USER")) {
                response.put("success", false);
                response.put("message", "Invalid role. Must be ADMIN or USER");
                return response;
            }
            
            // Create new user
            User user = User.builder()
                    .username(username)
                    .email(username + "@stress-admin.local") // Auto-generate email
                    .password(passwordEncoder.encode(password))
                    .fullName(fullName)
                    .role(role)
                    .status("ACTIVE")
                    .createdAt(LocalDateTime.now())
                    .build();
            
            System.out.println("Saving user to database...");
            User savedUser = userRepository.save(user);
            System.out.println("User saved successfully with ID: " + savedUser.getId());
            
            response.put("success", true);
            response.put("message", "User created successfully");
            response.put("username", username);
            response.put("role", role);
            
            return response;
            
        } catch (Exception e) {
            System.err.println("User creation error: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "User creation failed: " + e.getMessage());
            return response;
        }
    }
    
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    public Optional<User> getUserById(String id) {
        return userRepository.findById(id);
    }
    
    public Map<String, Object> updateUser(String id, Map<String, String> updateRequest) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOptional = userRepository.findById(id);
            
            if (userOptional.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }
            
            User user = userOptional.get();
            
            // Update fields if provided
            if (updateRequest.containsKey("username")) {
                String newUsername = updateRequest.get("username");
                if (!newUsername.equals(user.getUsername()) && userRepository.existsByUsername(newUsername)) {
                    response.put("success", false);
                    response.put("message", "Username already exists");
                    return response;
                }
                user.setUsername(newUsername);
            }
            
            if (updateRequest.containsKey("email")) {
                String newEmail = updateRequest.get("email");
                if (!newEmail.equals(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
                    response.put("success", false);
                    response.put("message", "Email already exists");
                    return response;
                }
                user.setEmail(newEmail);
            }
            
            if (updateRequest.containsKey("fullName")) {
                user.setFullName(updateRequest.get("fullName"));
            }
            
            if (updateRequest.containsKey("role")) {
                String role = updateRequest.get("role");
                if (!role.equals("ADMIN") && !role.equals("USER")) {
                    response.put("success", false);
                    response.put("message", "Invalid role. Must be ADMIN or USER");
                    return response;
                }
                user.setRole(role);
            }
            
            if (updateRequest.containsKey("status")) {
                String status = updateRequest.get("status");
                if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
                    response.put("success", false);
                    response.put("message", "Invalid status. Must be ACTIVE or INACTIVE");
                    return response;
                }
                user.setStatus(status);
            }
            
            if (updateRequest.containsKey("password") && !updateRequest.get("password").isEmpty()) {
                user.setPassword(passwordEncoder.encode(updateRequest.get("password")));
            }
            
            userRepository.save(user);
            
            response.put("success", true);
            response.put("message", "User updated successfully");
            response.put("username", user.getUsername());
            
            return response;
            
        } catch (Exception e) {
            System.err.println("User update error: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "User update failed: " + e.getMessage());
            return response;
        }
    }
    
    public Map<String, Object> deleteUser(String id) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOptional = userRepository.findById(id);
            
            if (userOptional.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }
            
            User user = userOptional.get();
            userRepository.delete(user);
            
            response.put("success", true);
            response.put("message", "User deleted successfully");
            response.put("username", user.getUsername());
            
            return response;
            
        } catch (Exception e) {
            System.err.println("User deletion error: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "User deletion failed: " + e.getMessage());
            return response;
        }
    }
}
