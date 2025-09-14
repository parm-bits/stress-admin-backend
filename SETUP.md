# Stress Admin Backend - Setup Guide

## Prerequisites

1. **Java 17** - Make sure you have Java 17 installed
2. **MongoDB** - Install and start MongoDB locally (default port 27017)
3. **Apache JMeter** - Download and install JMeter 5.6.2 or later

## Quick Setup

### 1. Configure JMeter Path
Update `src/main/resources/application.properties`:
```properties
# Update this path to your JMeter installation
jmeter.path=C:/apache-jmeter-5.6.2/bin/jmeter.bat
```

### 2. Start MongoDB
```bash
# Windows
mongod

# Linux/Mac
sudo systemctl start mongod
```

### 3. Run the Application
```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8081`

## API Documentation

Once running, visit:
- **Swagger UI**: `http://localhost:8081/swagger-ui.html`
- **API Docs**: `http://localhost:8081/api-docs`

## Testing the Basic Functionality

### 1. Create a Use Case
```bash
curl -X POST "http://localhost:8081/api/usecases" \
  -H "Content-Type: multipart/form-data" \
  -F "name=Sample Test" \
  -F "description=Test HTTP endpoint" \
  -F "jmxFile=@src/main/resources/sample-test.jmx" \
  -F "csvFile=@src/main/resources/sample-data.csv"
```

### 2. List Use Cases
```bash
curl "http://localhost:8081/api/usecases"
```

### 3. Run a Test
```bash
curl -X POST "http://localhost:8081/api/usecases/{useCaseId}/run?users=5"
```

### 4. Check Test Status
```bash
curl "http://localhost:8081/api/usecases/{useCaseId}/status"
```

### 5. View Report
Once test completes, the report URL will be available in the use case status. Visit:
`http://localhost:8081/reports/{reportPath}/index.html`

## File Structure

- **JMX Files**: Stored in `{storage.base-dir}/jmx/`
- **CSV Files**: Stored in `{storage.base-dir}/csv/`
- **Test Results**: Stored in `{storage.base-dir}/results/`
- **Reports**: Stored in `{storage.base-dir}/reports/`

## Troubleshooting

1. **JMeter not found**: Update `jmeter.path` in application.properties
2. **MongoDB connection failed**: Ensure MongoDB is running on port 27017
3. **File upload fails**: Check storage directory permissions
4. **Test execution fails**: Verify JMX file is valid and CSV path is correct

## Next Steps

- Add authentication and authorization
- Create web UI for easier management
- Add test scheduling capabilities
- Implement test result comparison
- Add email notifications
