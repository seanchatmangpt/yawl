# Demo Health Check

This document describes the health check feature for YAWL pattern demo services.

## Overview

The health check feature provides real-time monitoring of pattern demo execution status through:

- `DemoHealthCheck` class - Core health status tracking
- `DemoHealthController` - REST endpoint for health queries
- Integration with `PatternDemoRunner` for live updates

## Components

### DemoHealthCheck

The core health status tracker with the following capabilities:

- **Initialization tracking**: Monitors when demo runner is ready
- **Progress monitoring**: Tracks completed vs total patterns
- **Failure detection**: Identifies high failure rates (10%+)
- **Shutdown detection**: Reports when shutdown is requested
- **Status classification**:
  - `STARTING` - Initializing
  - `HEALTHY` - Normal operation
  - `DEGRADED` - High failure rate
  - `STOPPING` - Shutdown in progress

### DemoHealthController

Spring Boot REST controller exposing health endpoint:

```java
@RestController
@RequestMapping("/demo")
public class DemoHealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health()
}
```

## API Usage

### Health Check Endpoint

**GET** `/demo/health`

Returns JSON response with health status:

```json
{
  "status": "HEALTHY",
  "patterns_total": 10,
  "patterns_completed": 7,
  "patterns_failed": 0,
  "progress_percent": "70.0",
  "last_pattern_time": 1672531200000
}
```

#### Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `status` | String | Overall health status |
| `patterns_total` | Integer | Total patterns to execute |
| `patterns_completed` | Integer | Successfully completed patterns |
| `patterns_failed` | Integer | Failed patterns |
| `progress_percent` | String | Completion percentage |
| `last_pattern_time` | Long | Timestamp of last pattern execution |

#### Status Codes

- `200 OK` - Healthy or initializing
- `503 Service Unavailable` - Degraded or error state

### Integration Example

```java
// Using REST template
RestTemplate restTemplate = new RestTemplate();
ResponseEntity<Map> response = restTemplate.getForEntity(
    "http://localhost:8080/demo/health",
    Map.class
);

Map<String, Object> health = response.getBody();
String status = (String) health.get("status");
double progress = Double.parseDouble((String) health.get("progress_percent"));
```

## Monitoring

### Progress Tracking

The health check automatically updates as patterns execute:

1. **Start**: Status = "INITIALIZING"
2. **Running**: Progress percentage increases with each completion
3. **Failure**: Failed patterns counted and failure rate monitored
4. **Shutdown**: Status changes to "SHUTTING_DOWN"

### Failure Threshold

- **Warning**: Triggers when failures exceed 10% of completed patterns
- **Response**: HTTP 503 with warning message in response body

## Testing

### Unit Tests

Located in `src/test/java/org/yawlfoundation/yawl/mcp/a2a/demo/health/`:

- `DemoHealthCheckTest` - Core health logic
- `DemoHealthControllerIntegrationTest` - REST endpoint testing

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=DemoHealthCheckTest
```

## Configuration

### Application Properties

No special configuration required for health check. It's enabled by default when the demo runner is active.

### Metrics Integration

The health check works with Spring Boot Actuator if configured:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

## Error Handling

### Common Scenarios

1. **Service Unavailable (503)**
   - High failure rate detected
   - System in degraded state

2. **OK (200) with "SHUTTING_DOWN"**
   - Normal shutdown in progress
   - Patterns still completing

## Example Usage

### Monitoring Script

```bash
#!/bin/bash
# health-monitor.sh

URL="http://localhost:8080/demo/health"

while true; do
    response=$(curl -s "$URL")
    status=$(echo "$response" | jq -r '.status')
    progress=$(echo "$response" | jq -r '.progress_percent')

    echo "Status: $status, Progress: $progress%"

    if [ "$status" = "SHUTTING_DOWN" ]; then
        echo "Demo is shutting down"
        break
    fi

    sleep 5
done
```

### Log Integration

Health status is logged at appropriate levels:

```java
// Log current health status
LOGGER.info("Current health: {}", healthCheck.check().details());
```

## Development

### Extending Health Checks

1. Add new metrics to `DemoHealthCheck`
2. Update `check()` method logic
3. Test with new scenarios
4. Update documentation

### Adding New Endpoints

Create additional endpoints in `DemoHealthController` as needed for specific monitoring requirements.