package com.example.stress_admin_backend.service;

import com.example.stress_admin_backend.model.User;
import com.example.stress_admin_backend.repository.UserRepository;
import com.example.stress_admin_backend.security.CustomUserPrincipal;
import com.example.stress_admin_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    
    
    public Map<String, Object> authenticate(String username, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            
            CustomUserPrincipal userPrincipal = (CustomUserPrincipal) authentication.getPrincipal();
            String token = jwtUtil.generateToken(userPrincipal);
            
            // Update last login
            User authUser = userPrincipal.getUser();
            if (authUser != null) {
                authUser.setLastLoginAt(LocalDateTime.now());
                userRepository.save(authUser);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("token", token);
            response.put("username", userPrincipal.getUsername());
            response.put("authorities", userPrincipal.getAuthorities());
            response.put("message", "Authentication successful");
            
            return response;
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Invalid username or password");
            return response;
        }
    }
    
    
    public Map<String, Object> validateToken(String token) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String username = jwtUtil.extractUsername(token);
            Optional<User> userOptional = userRepository.findByUsername(username);
            
            if (userOptional.isPresent()) {
                CustomUserPrincipal userPrincipal = new CustomUserPrincipal(userOptional.get());
                if (jwtUtil.validateToken(token, userPrincipal)) {
                    response.put("success", true);
                    response.put("username", username);
                    response.put("message", "Token is valid");
                } else {
                    response.put("success", false);
                    response.put("message", "Invalid or expired token");
                }
            } else {
                response.put("success", false);
                response.put("message", "Invalid or expired token");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Token validation failed");
        }
        
        return response;
    }
    
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    public boolean testPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}

