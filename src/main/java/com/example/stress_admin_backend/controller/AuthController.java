package com.example.stress_admin_backend.controller;

import com.example.stress_admin_backend.model.User;
import com.example.stress_admin_backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "APIs for user authentication and registration")
public class AuthController {
    
    private final AuthService authService;
    
    @Operation(summary = "User login", description = "Authenticate user with username and password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":true,\"token\":\"jwt_token\",\"username\":\"admin\"}"))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Invalid username or password\"}")))
    })
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Parameter(description = "Login request with username and password", required = true)
            @RequestBody Map<String, String> loginRequest) {
        
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");
        
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Username and password are required"
            ));
        }
        
        Map<String, Object> result = authService.authenticate(username, password);
        
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(401).body(result);
        }
    }
    
    @Operation(summary = "User registration", description = "Register a new user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registration successful",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":true,\"message\":\"User registered successfully\"}"))),
            @ApiResponse(responseCode = "400", description = "Registration failed",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Username already exists\"}")))
    })
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Parameter(description = "Registration request with user details", required = true)
            @RequestBody Map<String, String> registerRequest) {
        
        System.out.println("Registration request received: " + registerRequest);
        
        String username = registerRequest.get("username");
        String email = registerRequest.get("email");
        String password = registerRequest.get("password");
        String fullName = registerRequest.get("fullName");
        
        System.out.println("Parsed fields - Username: " + username + ", Email: " + email + ", FullName: " + fullName);
        
        if (username == null || email == null || password == null || fullName == null) {
            System.out.println("Missing required fields");
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "All fields are required"
            ));
        }
        
        Map<String, Object> result = authService.register(username, email, password, fullName);
        
        System.out.println("Registration result: " + result);
        
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @Operation(summary = "Validate token", description = "Validate JWT token and get user info")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token valid",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":true,\"username\":\"admin\"}"))),
            @ApiResponse(responseCode = "401", description = "Token invalid",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Invalid token\"}")))
    })
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(
            @Parameter(description = "JWT token to validate", required = true)
            @RequestBody Map<String, String> tokenRequest) {
        
        String token = tokenRequest.get("token");
        
        if (token == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Token is required"
            ));
        }
        
        Map<String, Object> result = authService.validateToken(token);
        
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(401).body(result);
        }
    }
    
    @Operation(summary = "Logout", description = "Logout user (client-side token removal)")
    @ApiResponse(responseCode = "200", description = "Logout successful")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Logout successful"
        ));
    }
    
    @Operation(summary = "Debug - Check user exists", description = "Debug endpoint to check if user exists")
    @GetMapping("/debug/user/{username}")
    public ResponseEntity<Map<String, Object>> debugUser(@PathVariable String username) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> user = authService.getUserByUsername(username);
            if (user.isPresent()) {
                response.put("success", true);
                response.put("user", Map.of(
                    "username", user.get().getUsername(),
                    "email", user.get().getEmail(),
                    "role", user.get().getRole(),
                    "status", user.get().getStatus(),
                    "hasPassword", user.get().getPassword() != null
                ));
            } else {
                response.put("success", false);
                response.put("message", "User not found");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
    
    @Operation(summary = "Debug - Test password", description = "Debug endpoint to test password matching")
    @PostMapping("/debug/test-password")
    public ResponseEntity<Map<String, Object>> testPassword(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String username = request.get("username");
            String password = request.get("password");
            
            Optional<User> user = authService.getUserByUsername(username);
            if (user.isPresent()) {
                boolean matches = authService.testPassword(password, user.get().getPassword());
                response.put("success", true);
                response.put("passwordMatches", matches);
                response.put("username", username);
            } else {
                response.put("success", false);
                response.put("message", "User not found");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
}
