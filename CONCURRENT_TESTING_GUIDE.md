# Concurrent Load Testing Guide

This guide explains how to use the enhanced stress testing backend to run multiple use cases concurrently with different JMX and CSV files.

## Overview

The system now supports:
- **Test Configurations**: Manage JMX and CSV file combinations
- **Concurrent Test Sessions**: Run multiple use cases simultaneously
- **Individual Use Cases**: Create and run single use cases
- **Batch Operations**: Create multiple use cases at once

## API Endpoints

### 1. Test Configuration Management

#### Create Test Configuration
```http
POST /api/test-configurations
Content-Type: multipart/form-data

name: "Login Test Config"
description: "Configuration for login performance testing"
jmxFile: [JMX file]
csvFile: [CSV file]
```

#### Get All Test Configurations
```http
GET /api/test-configurations
```

#### Get Active Test Configurations
```http
GET /api/test-configurations/active
```

### 2. Use Case Management

#### Create Use Case from Test Configuration
```http
POST /api/usecases/from-config/{configId}?name=Login Test&users=50&priority=1
```

#### Create Multiple Use Cases (Batch)
```http
POST /api/usecases/batch-create
Content-Type: application/json

[
  {
    "name": "Login Test",
    "description": "Test user login",
    "configId": "config_123",
    "users": 50,
    "priority": 1
  },
  {
    "name": "Search Test", 
    "description": "Test search functionality",
    "configId": "config_456",
    "users": 100,
    "priority": 2
  }
]
```

#### Create and Start Concurrent Test Session
```http
POST /api/usecases/concurrent-test?sessionName=Concurrent Load Test&sessionDescription=Running multiple tests
Content-Type: application/json

[
  {
    "name": "Login Test",
    "configId": "config_123",
    "users": 50,
    "priority": 1
  },
  {
    "name": "Search Test",
    "configId": "config_456", 
    "users": 100,
    "priority": 2
  }
]
```

### 3. Test Session Management

#### Get All Test Sessions
```http
GET /api/test-sessions
```

#### Get Running Test Sessions
```http
GET /api/test-sessions/running
```

#### Start Test Session
```http
POST /api/test-sessions/{sessionId}/start
```

#### Stop Test Session
```http
POST /api/test-sessions/{sessionId}/stop
```

#### Get Test Session Status
```http
GET /api/test-sessions/{sessionId}/status
```

## Usage Examples

### Example 1: Create Test Configurations

1. **Upload Login Test Configuration:**
```bash
curl -X POST "http://localhost:8082/api/test-configurations" \
  -F "name=Login Test Config" \
  -F "description=Configuration for login performance testing" \
  -F "jmxFile=@login_test.jmx" \
  -F "csvFile=@login_users.csv"
```

2. **Upload Search Test Configuration:**
```bash
curl -X POST "http://localhost:8082/api/test-configurations" \
  -F "name=Search Test Config" \
  -F "description=Configuration for search performance testing" \
  -F "jmxFile=@search_test.jmx" \
  -F "csvFile=@search_data.csv"
```

### Example 2: Run Concurrent Load Test

1. **Create and Start Concurrent Test:**
```bash
curl -X POST "http://localhost:8082/api/usecases/concurrent-test?sessionName=Concurrent Load Test" \
  -H "Content-Type: application/json" \
  -d '[
    {
      "name": "Login Performance Test",
      "configId": "config_123",
      "users": 50,
      "priority": 1
    },
    {
      "name": "Search Performance Test", 
      "configId": "config_456",
      "users": 100,
      "priority": 2
    },
    {
      "name": "Checkout Performance Test",
      "configId": "config_789", 
      "users": 25,
      "priority": 3
    }
  ]'
```

2. **Monitor Test Session Status:**
```bash
curl "http://localhost:8082/api/test-sessions/{sessionId}/status"
```

### Example 3: Individual Use Case Management

1. **Create Use Case from Configuration:**
```bash
curl -X POST "http://localhost:8082/api/usecases/from-config/config_123?name=Login Test&users=50&priority=1"
```

2. **Run Individual Use Case:**
```bash
curl -X POST "http://localhost:8082/api/usecases/{useCaseId}/run?users=50"
```

## Data Models

### TestConfiguration
- `id`: Unique identifier
- `name`: Configuration name
- `description`: Configuration description
- `jmxPath`: Path to JMX file
- `csvPath`: Path to CSV file
- `jmxFileName`: Original JMX filename
- `csvFileName`: Original CSV filename
- `jmxFileSize`: JMX file size in bytes
- `csvFileSize`: CSV file size in bytes
- `isActive`: Whether configuration is active
- `createdAt`: Creation timestamp
- `updatedAt`: Last update timestamp

### TestSession
- `id`: Unique identifier
- `name`: Session name
- `description`: Session description
- `useCaseIds`: List of use case IDs
- `userCounts`: Map of use case ID to user count
- `status`: Session status (IDLE, RUNNING, SUCCESS, FAILED, PARTIAL_SUCCESS)
- `totalUsers`: Total users across all use cases
- `useCaseCount`: Number of use cases
- `successCount`: Number of successful use cases
- `failureCount`: Number of failed use cases
- `useCaseStatuses`: Map of use case ID to status
- `useCaseReportUrls`: Map of use case ID to report URL

### UseCase (Enhanced)
- `id`: Unique identifier
- `name`: Use case name
- `description`: Use case description
- `jmxPath`: Path to JMX file
- `csvPath`: Path to CSV file
- `status`: Use case status
- `testSessionId`: ID of test session (if part of concurrent test)
- `userCount`: Number of users for this use case
- `priority`: Priority for concurrent execution (1=highest)

## Workflow

### 1. Setup Phase
1. Upload JMX and CSV files as test configurations
2. Verify configurations are active
3. Create use cases from configurations (optional)

### 2. Execution Phase
1. Create test session with multiple use cases
2. Start concurrent execution
3. Monitor progress and status
4. Stop if needed

### 3. Analysis Phase
1. Check individual use case results
2. Review test session summary
3. Access generated reports

## Best Practices

1. **File Organization**: Use descriptive names for JMX and CSV files
2. **User Count Planning**: Plan user counts based on system capacity
3. **Priority Setting**: Set priorities based on test importance
4. **Monitoring**: Regularly check test session status
5. **Resource Management**: Don't exceed system limits with too many concurrent tests

## Error Handling

- All endpoints return appropriate HTTP status codes
- Error messages are included in response body
- Failed tests are logged with detailed error information
- Test sessions can be stopped if issues occur

## Monitoring and Logging

- Console logs show test execution progress
- Individual test logs are saved to results directory
- Error logs are created for failed tests
- Test session status is tracked in real-time

## Swagger Documentation

Access the interactive API documentation at:
- Swagger UI: `http://localhost:8082/swagger-ui.html`
- API Docs: `http://localhost:8082/api-docs`
