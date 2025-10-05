package com.example.stress_admin_backend.config;

import com.example.stress_admin_backend.model.User;
import com.example.stress_admin_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Override
    public void run(String... args) throws Exception {
        // Create default admin user if it doesn't exist
        if (!userRepository.existsByUsername("admin")) {
            User admin = User.builder()
                    .username("admin")
                    .email("admin@stress-admin.com")
                    .password(passwordEncoder.encode("pyr@mid109"))
                    .fullName("Administrator")
                    .role("ADMIN")
                    .status("ACTIVE")
                    .build();
            
            userRepository.save(admin);
            System.out.println("Default admin user created: admin/pyr@mid109");
        }
        
        // Create default test user if it doesn't exist
        if (!userRepository.existsByUsername("user")) {
            User user = User.builder()
                    .username("user")
                    .email("user@stress-admin.com")
                    .password(passwordEncoder.encode("user123"))
                    .fullName("Test User")
                    .role("USER")
                    .status("ACTIVE")
                    .build();
            
            userRepository.save(user);
            System.out.println("Default test user created: user/user123");
        }
    }
}
