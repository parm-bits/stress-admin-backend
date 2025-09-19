package com.example.stress_admin_backend.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object errorMessage = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        
        Map<String, Object> errorResponse = new HashMap<>();
        
        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            errorResponse.put("status", statusCode);
            errorResponse.put("error", HttpStatus.valueOf(statusCode).getReasonPhrase());
        } else {
            errorResponse.put("status", 500);
            errorResponse.put("error", "Internal Server Error");
        }
        
        errorResponse.put("message", errorMessage != null ? errorMessage.toString() : "An error occurred");
        errorResponse.put("path", requestUri != null ? requestUri.toString() : "Unknown");
        errorResponse.put("timestamp", java.time.LocalDateTime.now().toString());
        
        // Special handling for report paths
        if (requestUri != null && requestUri.toString().startsWith("/reports/")) {
            errorResponse.put("message", "Report not found. The test may not have completed yet or the report path is incorrect.");
            errorResponse.put("suggestion", "Please check if the test has completed successfully and try again.");
        }
        
        return ResponseEntity.status(status != null ? Integer.parseInt(status.toString()) : 500)
                .body(errorResponse);
    }
}
