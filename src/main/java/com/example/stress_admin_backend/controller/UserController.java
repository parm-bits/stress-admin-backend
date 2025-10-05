package com.example.stress_admin_backend.controller;

import com.example.stress_admin_backend.model.User;
import com.example.stress_admin_backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Admin-only user management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    
    private final UserService userService;
    
    @Operation(summary = "Create new user", description = "Create a new user account (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User created successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":true,\"message\":\"User created successfully\"}"))),
            @ApiResponse(responseCode = "400", description = "User creation failed",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Username already exists\"}"))),
            @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    })
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createUser(
            @Parameter(description = "User creation request with user details", required = true)
            @RequestBody Map<String, String> createUserRequest) {
        
        System.out.println("User creation request received: " + createUserRequest);
        
        String username = createUserRequest.get("username");
        String password = createUserRequest.get("password");
        String fullName = createUserRequest.get("fullName");
        String role = createUserRequest.getOrDefault("role", "USER");
        
        System.out.println("Parsed fields - Username: " + username + ", FullName: " + fullName + ", Role: " + role);
        
        if (username == null || password == null || fullName == null) {
            System.out.println("Missing required fields");
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Username, password, and full name are required"
            ));
        }
        
        Map<String, Object> result = userService.createUser(username, password, fullName, role);
        
        System.out.println("User creation result: " + result);
        
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @Operation(summary = "Get all users", description = "Get list of all users (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }
    
    @Operation(summary = "Get user by ID", description = "Get user details by ID (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        return userService.getUserById(id)
                .map(user -> ResponseEntity.ok(user))
                .orElse(ResponseEntity.notFound().build());
    }
    
    @Operation(summary = "Update user", description = "Update user details (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable String id,
            @RequestBody Map<String, String> updateRequest) {
        
        Map<String, Object> result = userService.updateUser(id, updateRequest);
        
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @Operation(summary = "Delete user", description = "Delete user account (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User deleted successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable String id) {
        Map<String, Object> result = userService.deleteUser(id);
        
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
}
