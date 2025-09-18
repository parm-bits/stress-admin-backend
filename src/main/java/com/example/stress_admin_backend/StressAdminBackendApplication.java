package com.example.stress_admin_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class StressAdminBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(StressAdminBackendApplication.class, args);
	}

}





