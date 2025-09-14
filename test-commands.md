# Testing Commands for Your Stress Admin

## 1. Upload Your Files (Replace paths with your actual file locations)

```bash
curl -X POST "http://localhost:8081/api/usecases" \
  -H "Content-Type: multipart/form-data" \
  -F "name=My Load Test" \
  -F "description=Testing with my JMX and CSV files" \
  -F "jmxFile=@C:\path\to\your\Test.jmx" \
  -F "csvFile=@C:\path\to\your\user.csv"
```

## 2. List All Use Cases

```bash
curl "http://localhost:8081/api/usecases"
```

## 3. Run a Test (Replace {useCaseId} with actual ID from step 2)

```bash
curl -X POST "http://localhost:8081/api/usecases/{useCaseId}/run?users=10"
```

## 4. Check Test Status

```bash
curl "http://localhost:8081/api/usecases/{useCaseId}/status"
```

## 5. Get Specific Use Case Details

```bash
curl "http://localhost:8081/api/usecases/{useCaseId}"
```

## Example Workflow:

1. Upload files → Get useCaseId (e.g., "507f1f77bcf86cd799439011")
2. Run test → Get "Run started" message
3. Check status → See "RUNNING" then "SUCCESS" or "FAILED"
4. When SUCCESS, get reportUrl from status
5. Visit reportUrl in browser to see results
