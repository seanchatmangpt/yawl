# GRPO API Reference

**Document type:** API Reference
**Audience:** Integration developers, API consumers
**Purpose:** Complete reference for the GRPO (Group Resource Process Orchestrator) REST API endpoints used in YAWL v6.0.0-GA
**Version:** v6.0.0-GA

---

## Overview

The GRPO API provides endpoints for generating workflow specifications through reinforcement learning (RL) optimization. These endpoints allow you to:
- Generate workflow specifications using RL optimization
- Monitor the generation progress
- Score candidate workflows

All endpoints require authentication and follow RESTful principles.

---

## Authentication

### Required Headers

```http
Authorization: Bearer <jwt-token>
Content-Type: application/json
```

### JWT Token Format

Tokens are generated using the engine's authentication system with a default expiration of 24 hours.

```bash
# Example JWT payload
{
  "sub": "admin",
  "iat": 1704067200,
  "exp": 1704153600,
  "roles": ["ADMIN"]
}
```

---

## API Endpoints

### 1. POST /api/v1/rl/generate

Generate a workflow specification using reinforcement learning optimization.

**Endpoint:** `POST /api/v1/rl/generate`

#### Request Body

```json
{
  "specificationId": "holiday-booking-process",
  "stage": "optimization",
  "parameters": {
    "iterations": 100,
    "populationSize": 50,
    "mutationRate": 0.1,
    "crossoverRate": 0.7,
    "eliteSize": 5,
    "timeoutMs": 300000,
    "seed": 42
  }
}
```

#### Parameters

| Parameter | Type | Required | Default | Range | Description |
|-----------|------|----------|---------|-------|-------------|
| `specificationId` | string | Yes | - | - | Identifier for the specification to optimize |
| `stage` | string | Yes | - | optimization, refinement, validation | RL optimization stage |
| `parameters` | object | Yes | - | - | RL optimization parameters |
| `parameters.iterations` | integer | No | 100 | 10-1000 | Number of RL iterations to perform |
| `parameters.populationSize` | integer | No | 50 | 10-200 | Population size for genetic algorithm |
| `parameters.mutationRate` | float | No | 0.1 | 0.01-1.0 | Mutation probability for genetic operations |
| `parameters.crossoverRate` | float | No | 0.7 | 0.1-1.0 | Crossover probability for genetic operations |
| `parameters.eliteSize` | integer | No | 5 | 1-50 | Number of elite individuals to preserve |
| `parameters.timeoutMs` | integer | No | 300000 | 1000-3600000 | Timeout in milliseconds for optimization |
| `parameters.seed` | integer | No | random | - | Random seed for reproducible results |

#### Response

```json
{
  "jobId": "job-abc123-xyz789",
  "status": "QUEUED",
  "specificationId": "holiday-booking-process",
  "estimatedTime": 300,
  "parameters": {
    "iterations": 100,
    "populationSize": 50,
    "mutationRate": 0.1
  }
}
```

#### Response Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["jobId", "status", "specificationId", "estimatedTime"],
  "properties": {
    "jobId": {
      "type": "string",
      "pattern": "^job-[a-zA-Z0-9]{9}-[a-zA-Z0-9]{6}$"
    },
    "status": {
      "type": "string",
      "enum": ["QUEUED", "RUNNING", "COMPLETED", "FAILED"]
    },
    "specificationId": {
      "type": "string"
    },
    "estimatedTime": {
      "type": "integer",
      "minimum": 0
    },
    "parameters": {
      "type": "object",
      "properties": {
        "iterations": {"type": "integer", "minimum": 1},
        "populationSize": {"type": "integer", "minimum": 1},
        "mutationRate": {"type": "number", "minimum": 0, "maximum": 1},
        "crossoverRate": {"type": "number", "minimum": 0, "maximum": 1},
        "eliteSize": {"type": "integer", "minimum": 1},
        "timeoutMs": {"type": "integer", "minimum": 1},
        "seed": {"type": "integer"}
      }
    }
  }
}
```

#### Status Codes

| Code | Meaning |
|------|---------|
| 202 Accepted | Job queued successfully |
| 400 Bad Request | Invalid request parameters |
| 401 Unauthorized | Missing or invalid authentication |
| 403 Forbidden | Insufficient permissions |
| 429 Too Many Requests | Rate limit exceeded |
| 500 Internal Server Error | Server error during processing |

#### Examples

**cURL**
```bash
curl -X POST https://yawl.example.com/api/v1/rl/generate \
  -H "Authorization: Bearer $(cat jwt.txt)" \
  -H "Content-Type: application/json" \
  -d '{
    "specificationId": "holiday-booking-process",
    "stage": "optimization",
    "parameters": {
      "iterations": 200,
      "populationSize": 100,
      "mutationRate": 0.05
    }
  }'
```

**Java**
```java
import java.net.http.*;
import java.net.URI;
import java.util.*;

public class GrpoClient {
    private final HttpClient httpClient;

    public GrpoClient(String baseUrl) {
        this.httpClient = HttpClient.newHttpClient();
    }

    public HttpResponse<String> generateWorkflow(String jwtToken, String specId) {
        String json = String.format("""
            {
                "specificationId": "%s",
                "stage": "optimization",
                "parameters": {
                    "iterations": 100,
                    "populationSize": 50,
                    "mutationRate": 0.1
                }
            }
            """, specId);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://yawl.example.com/api/v1/rl/generate"))
            .header("Authorization", "Bearer " + jwtToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
```

**Python**
```python
import requests
import json

class GrpoClient:
    def __init__(self, base_url, jwt_token):
        self.base_url = base_url
        self.jwt_token = jwt_token

    def generate_workflow(self, specification_id):
        url = f"{self.base_url}/api/v1/rl/generate"
        payload = {
            "specificationId": specification_id,
            "stage": "optimization",
            "parameters": {
                "iterations": 100,
                "populationSize": 50,
                "mutationRate": 0.1
            }
        }

        headers = {
            "Authorization": f"Bearer {self.jwt_token}",
            "Content-Type": "application/json"
        }

        response = requests.post(url, json=payload, headers=headers)
        response.raise_for_status()
        return response.json()
```

---

### 2. GET /api/v1/rl/status/{jobId}

Check the status of a RL generation job.

**Endpoint:** `GET /api/v1/rl/status/{jobId}`

#### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `jobId` | string | Yes | Job ID from the initial request |

#### Response

```json
{
  "jobId": "job-abc123-xyz789",
  "status": "RUNNING",
  "specificationId": "holiday-booking-process",
  "currentIteration": 75,
  "totalIterations": 100,
  "currentScore": 0.85,
  "bestScore": 0.91,
  "elapsedMs": 150000,
  "estimatedTimeRemaining": 50000,
  "error": null
}
```

#### Response Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["jobId", "status", "specificationId"],
  "properties": {
    "jobId": {
      "type": "string",
      "pattern": "^job-[a-zA-Z0-9]{9}-[a-zA-Z0-9]{6}$"
    },
    "status": {
      "type": "string",
      "enum": ["QUEUED", "RUNNING", "COMPLETED", "FAILED"]
    },
    "specificationId": {
      "type": "string"
    },
    "currentIteration": {
      "type": "integer",
      "minimum": 0
    },
    "totalIterations": {
      "type": "integer",
      "minimum": 0
    },
    "currentScore": {
      "type": "number",
      "minimum": 0,
      "maximum": 1
    },
    "bestScore": {
      "type": "number",
      "minimum": 0,
      "maximum": 1
    },
    "elapsedMs": {
      "type": "integer",
      "minimum": 0
    },
    "estimatedTimeRemaining": {
      "type": "integer",
      "minimum": 0
    },
    "error": {
      "type": ["string", "null"]
    }
  }
}
```

#### Status Codes

| Code | Meaning |
|------|---------|
| 200 OK | Status retrieved successfully |
| 404 Not Found | Job ID not found |
| 500 Internal Server Error | Error retrieving status |

#### Examples

**cURL**
```bash
curl -X GET https://yawl.example.com/api/v1/rl/status/job-abc123-xyz789 \
  -H "Authorization: Bearer $(cat jwt.txt)"
```

**Java**
```java
public HttpResponse<String> checkStatus(String jobId) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://yawl.example.com/api/v1/rl/status/" + jobId))
        .header("Authorization", "Bearer " + jwtToken)
        .GET()
        .build();

    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
}
```

**Python**
```python
def check_status(self, job_id):
    url = f"{self.base_url}/api/v1/rl/status/{job_id}"
    headers = {
        "Authorization": f"Bearer {self.jwt_token}"
    }

    response = requests.get(url, headers=headers)
    response.raise_for_status()
    return response.json()
```

---

### 3. POST /api/v1/rl/score

Score a candidate workflow specification.

**Endpoint:** `POST /api/v1/rl/score`

#### Request Body

```json
{
  "specificationId": "holiday-booking-process",
  "candidateId": "candidate-abc123",
  "workflowXml": "<?xml version='1.0' encoding='UTF-8'?>\n...",
  "metrics": {
    "executionTime": 2.5,
    "resourceUtilization": 0.75,
    "errorRate": 0.02,
    "userSatisfaction": 0.88
  }
}
```

#### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `specificationId` | string | Yes | Identifier for the specification |
| `candidateId` | string | Yes | Unique identifier for the candidate |
| `workflowXml` | string | Yes | Complete workflow specification XML |
| `metrics` | object | Yes | Performance metrics for scoring |

#### Response

```json
{
  "candidateId": "candidate-abc123",
  "score": 0.91,
  "ranking": 3,
  "breakdown": {
    "efficiency": 0.95,
    "robustness": 0.87,
    "complexity": 0.92,
    "maintainability": 0.89
  },
  "feedback": {
    "strengths": ["High resource utilization", "Low error rate"],
    "improvements": ["Reduce execution time by 15%"]
  }
}
```

#### Response Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["candidateId", "score", "ranking", "breakdown"],
  "properties": {
    "candidateId": {
      "type": "string"
    },
    "score": {
      "type": "number",
      "minimum": 0,
      "maximum": 1
    },
    "ranking": {
      "type": "integer",
      "minimum": 1
    },
    "breakdown": {
      "type": "object",
      "properties": {
        "efficiency": {"type": "number", "minimum": 0, "maximum": 1},
        "robustness": {"type": "number", "minimum": 0, "maximum": 1},
        "complexity": {"type": "number", "minimum": 0, "maximum": 1},
        "maintainability": {"type": "number", "minimum": 0, "maximum": 1}
      }
    },
    "feedback": {
      "type": "object",
      "properties": {
        "strengths": {
          "type": "array",
          "items": {"type": "string"}
        },
        "improvements": {
          "type": "array",
          "items": {"type": "string"}
        }
      }
    }
  }
}
```

#### Status Codes

| Code | Meaning |
|------|---------|
| 200 OK | Score calculated successfully |
| 400 Bad Request | Invalid request or malformed XML |
| 401 Unauthorized | Missing or invalid authentication |
| 500 Internal Server Error | Error during scoring |

#### Examples

**cURL**
```bash
curl -X POST https://yawl.example.com/api/v1/rl/score \
  -H "Authorization: Bearer $(cat jwt.txt)" \
  -H "Content-Type: application/json" \
  -d '{
    "specificationId": "holiday-booking-process",
    "candidateId": "candidate-abc123",
    "workflowXml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>...",
    "metrics": {
      "executionTime": 2.5,
      "resourceUtilization": 0.75,
      "errorRate": 0.02,
      "userSatisfaction": 0.88
    }
  }'
```

---

## Error Handling

### Error Response Format

```json
{
  "error": {
    "code": "INVALID_SPECIFICATION",
    "message": "The provided specification contains invalid elements",
    "details": {
      "line": 42,
      "element": "task",
      "issue": "missing name attribute"
    }
  }
}
```

### Common Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `INVALID_SPECIFICATION` | 400 | Invalid workflow XML format or content |
| `JOB_NOT_FOUND` | 404 | Job ID does not exist |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests in time period |
| `AUTHENTICATION_FAILED` | 401 | Invalid JWT token |
| `PERMISSION_DENIED` | 403 | Insufficient privileges |
| `INTERNAL_ERROR` | 500 | Server-side error |

---

## Rate Limiting

### Rate Limits

| Endpoint | Limit | Window |
|----------|-------|--------|
| POST /api/v1/rl/generate | 10 | minute |
| GET /api/v1/rl/status | 60 | minute |
| POST /api/v1/rl/score | 100 | minute |

### Rate Limit Response Headers

```http
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 8
X-RateLimit-Reset: 1704067200
X-RateLimit-Retry-After: 30
```

---

## Webhook Notifications

### Subscribe to Job Status Changes

```bash
# Create webhook subscription
curl -X POST https://yawl.example.com/api/v1/rl/webhooks \
  -H "Authorization: Bearer $(cat jwt.txt)" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://your-app.com/webhooks/grpo",
    "events": ["job_started", "job_completed", "job_failed"],
    "secret": "your-webhook-secret"
  }'
```

### Webhook Payload

```json
{
  "eventId": "evt-abc123",
  "event": "job_completed",
  "timestamp": "2024-01-01T12:00:00Z",
  "jobId": "job-abc123-xyz789",
  "specificationId": "holiday-booking-process",
  "result": {
    "finalScore": 0.95,
    "bestCandidate": {
      "id": "candidate-xyz789",
      "xml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>..."
    }
  }
}
```

---

## Monitoring

### Response Metrics

| Metric | Description |
|--------|-------------|
| `rl_generation_duration` | Total time for generation |
| `rl_iterations_completed` | Number of iterations completed |
| `rl_best_score` | Best score achieved |
| `rl_candidates_evaluated` | Total candidates evaluated |

### Example Prometheus Query

```promql
# Average generation time by specification
rate(rl_generation_duration_seconds_sum{specification!=""}[5m]) /
rate(rl_generation_duration_seconds_count{specification!=""}[5m])

# Best score trend
increase(rl_best_score[1h])
```

---

## Security Considerations

1. **Authentication**: All endpoints require valid JWT tokens
2. **Authorization**: Ensure proper RBAC implementation
3. **Input Validation**: All XML inputs are validated against YAWL schema
4. **Rate Limiting**: Protect against DoS attacks
5. **HTTPS**: Always use HTTPS in production

---

## Best Practices

### Performance Optimization

1. **Batch Operations**: Score multiple candidates in parallel
2. **Caching**: Cache frequently accessed specifications
3. **Timeout**: Set appropriate timeouts for long-running operations
4. **Monitoring**: Track generation quality and performance metrics

### Error Recovery

1. **Retry Logic**: Implement exponential backoff for transient errors
2. **Logging**: Log all API interactions for debugging
3. **Validation**: Validate input before making requests
4. **Fallback**: Provide fallback mechanisms for critical operations

---

## Migration from v5.x

### Breaking Changes

1. **Authentication**: JWT tokens are now required for all endpoints
2. **Rate Limiting**: Implemented rate limiting on all endpoints
3. **Webhook Format**: Webhook payloads have been standardized

### Migration Guide

```bash
# Update authentication header
# Before: curl -X POST https://yawl.example.com/api/v1/rl/generate
# After: curl -X POST https://yawl.example.com/api/v1/rl/generate \
  -H "Authorization: Bearer $(cat jwt.txt)"
```

---

**Related Documentation:**
- [GRPO Configuration Reference](../rl-config-reference.md)
- [Virtual Thread Configuration](../virtual-threads.md)
- [YAWL Security Policy](../security-policy.md)
- [Environment Variables](../environment-variables.md)

**Support:**
For API support, contact the YAWL engineering team or check the community forums.