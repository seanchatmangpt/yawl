# YAWL REST API Documentation

## Table of Contents

1. [Overview](#overview)
2. [Authentication](#authentication)
3. [API Endpoints](#api-endpoints)
4. [Core Resources](#core-resources)
5. [Request and Response Formats](#request-and-response-formats)
6. [Error Handling](#error-handling)
7. [Examples](#examples)
8. [Rate Limiting](#rate-limiting)
9. [Webhooks](#webhooks)
10. [SDK and Libraries](#sdk-and-libraries)

## Overview

The YAWL REST API provides programmatic access to YAWL workflow engine capabilities. The API follows RESTful principles and uses XML/JSON for data exchange.

### Base URL

```
Production: https://yawl.example.com:8080
Development: http://localhost:8080
```

### API Version

```
Current Version: 5.2
Supported Versions: 4.0, 4.1, 4.2, 4.3, 4.5, 5.0, 5.1, 5.2
```

### Key Features

- **Workflow Management**: Deploy, enable, disable workflows
- **Case Management**: Create, manage, and monitor workflow cases
- **Task Management**: Allocate, execute, and complete work items
- **Resource Management**: Manage users, roles, and organizational units
- **Monitoring**: Query case status, metrics, and history
- **Integration**: Web service invocation, external system integration

## Authentication

### Username/Password Authentication

```bash
# Basic HTTP Authentication
curl -u username:password http://localhost:8080/engine/workflows

# Or using Authorization header
curl -H "Authorization: Basic $(echo -n 'username:password' | base64)" \
  http://localhost:8080/engine/workflows
```

### Token-Based Authentication (OAuth 2.0)

```bash
# 1. Get access token
curl -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=myclient&client_secret=mysecret"

# Response:
# {
#   "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
#   "token_type": "Bearer",
#   "expires_in": 3600
# }

# 2. Use token in requests
curl -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  http://localhost:8080/engine/workflows
```

### Session-Based Authentication

```bash
# 1. Login and get session
curl -c cookies.txt -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=admin" \
  http://localhost:8080/resourceService/login

# 2. Use session in subsequent requests
curl -b cookies.txt http://localhost:8080/engine/workflows
```

## API Endpoints

### Engine Service

**Base Path**: `/engine`

#### Workflow Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/workflows` | List all workflows |
| GET | `/workflows/{id}` | Get workflow details |
| POST | `/workflows` | Deploy new workflow |
| DELETE | `/workflows/{id}` | Unload workflow |
| PUT | `/workflows/{id}/status` | Change workflow status |
| POST | `/workflows/{id}/enable` | Enable workflow |
| POST | `/workflows/{id}/disable` | Disable workflow |

#### Case Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/cases` | List cases |
| GET | `/cases/{id}` | Get case details |
| POST | `/cases` | Start new case |
| POST | `/cases/{id}/suspend` | Suspend case |
| POST | `/cases/{id}/resume` | Resume case |
| POST | `/cases/{id}/cancel` | Cancel case |
| DELETE | `/cases/{id}` | Delete case |

#### Work Item Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/workitems` | List work items |
| GET | `/workitems/{id}` | Get work item details |
| POST | `/workitems/{id}/execute` | Execute work item |
| POST | `/workitems/{id}/complete` | Complete work item |
| POST | `/workitems/{id}/skip` | Skip work item |

### Resource Service

**Base Path**: `/resourceService`

#### User Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/users` | List users |
| GET | `/users/{id}` | Get user details |
| POST | `/users` | Create user |
| PUT | `/users/{id}` | Update user |
| DELETE | `/users/{id}` | Delete user |
| PUT | `/users/{id}/password` | Change password |
| POST | `/users/{id}/roles` | Assign role |

#### Role Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/roles` | List roles |
| POST | `/roles` | Create role |
| PUT | `/roles/{id}` | Update role |
| DELETE | `/roles/{id}` | Delete role |

#### Work Queue

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/workqueue` | Get current user's work items |
| GET | `/workqueue/{userid}` | Get user's work items |

### Monitoring Service

**Base Path**: `/monitoring`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/cases/stats` | Case statistics |
| GET | `/cases/{id}/history` | Case execution history |
| GET | `/performance` | Performance metrics |
| GET | `/resources/utilization` | Resource utilization |

## Core Resources

### Workflow Resource

**Schema:**
```xml
<workflow>
  <id>order_fulfillment_1</id>
  <name>Order Fulfillment Process</name>
  <version>1.0</version>
  <status>ACTIVE</status>
  <specversion>4.0</specversion>
  <documentation>
    <description>Handles order processing workflow</description>
  </documentation>
  <tasks>
    <task id="Receive_Order">
      <name>Receive Order</name>
      <type>ATOMIC</type>
      <resources>
        <participant id="order_team"/>
      </resources>
    </task>
  </tasks>
  <net>
    <!-- Flow definition -->
  </net>
</workflow>
```

### Case Resource

**Schema:**
```xml
<case>
  <id>case_20260214_001</id>
  <workflowid>order_fulfillment_1</workflowid>
  <workflowversion>1.0</workflowversion>
  <status>ACTIVE</status>
  <createtime>2026-02-14T08:00:00Z</createtime>
  <completiontime/>
  <data>
    <order>
      <id>ORD-12345</id>
      <customer>John Doe</customer>
      <amount>1500.00</amount>
      <items>
        <item>
          <sku>PROD-001</sku>
          <quantity>2</quantity>
        </item>
      </items>
    </order>
  </data>
  <workitems count="3">
    <workitem id="wi_001"/>
  </workitems>
</case>
```

### Work Item Resource

**Schema:**
```xml
<workitem>
  <id>wi_20260214_001</id>
  <taskid>Receive_Order</taskid>
  <caseid>case_20260214_001</caseid>
  <status>ALLOCATED</status>
  <allocatedto>john.doe</allocatedto>
  <createtime>2026-02-14T08:00:00Z</createtime>
  <enabledtime>2026-02-14T08:00:00Z</enabledtime>
  <starttime/>
  <completiontime/>
  <datainput>
    <order>
      <id>ORD-12345</id>
    </order>
  </datainput>
  <dataoutput/>
  <documentation>
    <description>Review and receive order</description>
  </documentation>
</workitem>
```

### User Resource

**Schema:**
```xml
<user>
  <userid>john.doe</userid>
  <firstname>John</firstname>
  <lastname>Doe</lastname>
  <email>john.doe@example.com</email>
  <password/>
  <description>Order fulfillment specialist</description>
  <roles>
    <role>order_team</role>
    <role>approver</role>
  </roles>
  <status>ACTIVE</status>
  <updatetime>2026-02-14T08:00:00Z</updatetime>
</user>
```

## Request and Response Formats

### XML Request Example

```bash
curl -X POST http://localhost:8080/engine/cases \
  -H "Content-Type: application/xml" \
  -H "Authorization: Basic admin:admin" \
  -d '<?xml version="1.0" encoding="UTF-8"?>
<case>
  <workflowid>order_fulfillment_1</workflowid>
  <workflowversion>1.0</workflowversion>
  <data>
    <order>
      <id>ORD-12345</id>
      <customer>Jane Smith</customer>
      <amount>2500.00</amount>
      <items>
        <item>
          <sku>PROD-002</sku>
          <quantity>1</quantity>
          <price>2500.00</price>
        </item>
      </items>
    </order>
  </data>
</case>'
```

### JSON Request Example

```bash
curl -X POST http://localhost:8080/engine/cases \
  -H "Content-Type: application/json" \
  -H "Authorization: Basic admin:admin" \
  -d '{
  "workflowid": "order_fulfillment_1",
  "workflowversion": "1.0",
  "data": {
    "order": {
      "id": "ORD-12345",
      "customer": "Jane Smith",
      "amount": 2500.00,
      "items": [
        {
          "sku": "PROD-002",
          "quantity": 1,
          "price": 2500.00
        }
      ]
    }
  }
}'
```

### Successful Response (201 Created)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<response>
  <status>success</status>
  <message>Case created successfully</message>
  <case>
    <id>case_20260214_002</id>
    <workflowid>order_fulfillment_1</workflowid>
    <status>ACTIVE</status>
    <createtime>2026-02-14T09:15:00Z</createtime>
  </case>
</response>
```

## Error Handling

### Error Response Format

```xml
<?xml version="1.0" encoding="UTF-8"?>
<response>
  <status>error</status>
  <error>
    <code>400</code>
    <message>Bad Request: Invalid workflow specification</message>
    <details>
      <field>workflowid</field>
      <reason>Workflow not found or not enabled</reason>
    </details>
    <timestamp>2026-02-14T09:20:00Z</timestamp>
  </error>
</response>
```

### HTTP Status Codes

| Code | Meaning | Example |
|------|---------|---------|
| 200 | OK | GET request successful |
| 201 | Created | New case/workflow created |
| 204 | No Content | DELETE successful |
| 400 | Bad Request | Invalid XML/JSON |
| 401 | Unauthorized | Missing/invalid credentials |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Workflow/case doesn't exist |
| 409 | Conflict | Case already exists |
| 422 | Unprocessable Entity | Validation error |
| 500 | Server Error | Internal error |
| 503 | Service Unavailable | System maintenance |

### Common Error Scenarios

```bash
# Error: Unauthorized
# Response: 401 Unauthorized
# Solution: Check credentials

# Error: Workflow not found
# Response: 404 Not Found
# Solution: Verify workflow ID and that it's deployed

# Error: Invalid input data
# Response: 422 Unprocessable Entity
# Solution: Validate XML schema against YAWL_Schema4.0.xsd

# Error: Database connection failed
# Response: 500 Internal Server Error
# Solution: Check database connectivity
```

## Examples

### Complete Workflow: Order Processing

```bash
#!/bin/bash
# Complete workflow example: Process customer order

BASE_URL="http://localhost:8080"
AUTH="admin:admin"

# 1. Check if workflow exists
echo "1. Checking workflow status..."
WORKFLOW=$(curl -s -u $AUTH \
  "$BASE_URL/engine/workflows/order_fulfillment_1" | \
  grep -o '<status>[^<]*' | cut -d'>' -f2)

if [ "$WORKFLOW" != "ACTIVE" ]; then
  echo "ERROR: Workflow not active"
  exit 1
fi

# 2. Create case
echo "2. Creating new case..."
CASE=$(curl -s -u $AUTH -X POST \
  -H "Content-Type: application/xml" \
  "$BASE_URL/engine/cases" \
  -d '<?xml version="1.0"?>
<case>
  <workflowid>order_fulfillment_1</workflowid>
  <workflowversion>1.0</workflowversion>
  <data>
    <order>
      <id>ORD-54321</id>
      <customer>Alice Johnson</customer>
      <amount>5000.00</amount>
    </order>
  </data>
</case>' | grep -oP '(?<=<id>)[^<]*' | head -1)

echo "Case created: $CASE"

# 3. Get work items
echo "3. Retrieving work items..."
curl -s -u $AUTH \
  "$BASE_URL/resourceService/workqueue?caseid=$CASE" | \
  xmllint --format -

# 4. Get first work item
echo "4. Processing work item..."
WORKITEM=$(curl -s -u $AUTH \
  "$BASE_URL/engine/cases/$CASE/workitems" | \
  grep -oP '(?<=<id>)[^<]*' | head -1)

# 5. Complete work item
echo "5. Completing work item..."
curl -s -u $AUTH -X POST \
  -H "Content-Type: application/xml" \
  "$BASE_URL/engine/workitems/$WORKITEM/complete" \
  -d '<?xml version="1.0"?>
<workitem>
  <id>'$WORKITEM'</id>
  <datainput>
    <order>
      <id>ORD-54321</id>
      <status>RECEIVED</status>
    </order>
  </datainput>
</workitem>'

# 6. Monitor case progress
echo "6. Monitoring case..."
for i in {1..10}; do
  STATUS=$(curl -s -u $AUTH \
    "$BASE_URL/engine/cases/$CASE" | \
    grep -oP '(?<=<status>)[^<]*')

  echo "Check $i: Case status = $STATUS"

  if [ "$STATUS" = "COMPLETED" ]; then
    echo "Case completed successfully!"
    break
  fi

  sleep 5
done

# 7. Get final case data
echo "7. Retrieving final case data..."
curl -s -u $AUTH \
  "$BASE_URL/engine/cases/$CASE" | \
  xmllint --format -
```

### Batch Case Creation

```bash
#!/bin/bash
# Batch create multiple cases

BASE_URL="http://localhost:8080"
AUTH="admin:admin"

# CSV file: orders.csv
# ORDER_ID,CUSTOMER,AMOUNT
# ORD-001,Customer A,1000
# ORD-002,Customer B,2000

while IFS=',' read -r order_id customer amount; do
  [ "$order_id" = "ORDER_ID" ] && continue

  echo "Creating case for order: $order_id"

  curl -s -u $AUTH -X POST \
    -H "Content-Type: application/xml" \
    "$BASE_URL/engine/cases" \
    -d "<?xml version=\"1.0\"?>
<case>
  <workflowid>order_fulfillment_1</workflowid>
  <data>
    <order>
      <id>$order_id</id>
      <customer>$customer</customer>
      <amount>$amount</amount>
    </order>
  </data>
</case>" > /dev/null

  echo "Success"
done < orders.csv
```

### Dynamic Workflow Deployment

```bash
#!/bin/bash
# Deploy workflow from YAWL file

BASE_URL="http://localhost:8080"
AUTH="admin:admin"

WORKFLOW_FILE="process_definition.yawl"

# 1. Validate workflow schema
xmllint --noout --schema schema/YAWL_Schema4.0.xsd "$WORKFLOW_FILE"

if [ $? -ne 0 ]; then
  echo "ERROR: Workflow validation failed"
  exit 1
fi

# 2. Deploy workflow
echo "Deploying workflow..."
RESPONSE=$(curl -s -u $AUTH -X POST \
  -H "Content-Type: application/xml" \
  "$BASE_URL/engine/workflows" \
  -d @"$WORKFLOW_FILE")

# 3. Check response
if echo "$RESPONSE" | grep -q '<status>success</status>'; then
  WORKFLOW_ID=$(echo "$RESPONSE" | grep -oP '(?<=<id>)[^<]*' | head -1)
  echo "Workflow deployed successfully: $WORKFLOW_ID"

  # 4. Enable workflow
  curl -s -u $AUTH -X POST \
    "$BASE_URL/engine/workflows/$WORKFLOW_ID/enable"
  echo "Workflow enabled"
else
  echo "ERROR: Deployment failed"
  echo "$RESPONSE"
  exit 1
fi
```

## Rate Limiting

### Rate Limit Headers

```
X-RateLimit-Limit: 1000        # Requests per hour
X-RateLimit-Remaining: 950     # Remaining requests
X-RateLimit-Reset: 1613038800  # Unix timestamp when limit resets
```

### Rate Limit Policy

- **Default**: 1000 requests per hour per user
- **Burst**: Up to 50 requests per minute
- **Retry-After**: Returned when limit exceeded

### Handling Rate Limits

```bash
#!/bin/bash
# Script with rate limit handling

handle_rate_limit() {
  local response_code=$1

  if [ "$response_code" = "429" ]; then
    echo "Rate limit exceeded"
    sleep 60  # Wait 1 minute
    return 1
  fi
  return 0
}

retry_with_backoff() {
  local n=1
  local max=5

  while true; do
    local response=$(curl -s -w "%{http_code}" -o response.json \
      "http://localhost:8080/engine/cases")

    local status_code="${response: -3}"

    if handle_rate_limit "$status_code"; then
      return 0
    fi

    if [ "$n" -lt "$max" ]; then
      n=$((n + 1))
      echo "Attempt $n of $max, waiting..."
      sleep $((2 ** n))
    else
      echo "Max retries exceeded"
      return 1
    fi
  done
}

retry_with_backoff
```

## Webhooks

### Registering Webhooks

```bash
# Register webhook for case completion
curl -X POST http://localhost:8080/webhooks \
  -H "Content-Type: application/json" \
  -d '{
  "event": "case.completed",
  "url": "https://example.com/webhooks/case-completed",
  "active": true,
  "headers": {
    "Authorization": "Bearer webhook_token_123"
  }
}'
```

### Webhook Events

| Event | Trigger | Payload |
|-------|---------|---------|
| `case.created` | New case started | Case ID, workflow ID |
| `case.suspended` | Case suspended | Case ID, reason |
| `case.resumed` | Case resumed | Case ID |
| `case.completed` | Case finished | Case ID, completion time |
| `case.cancelled` | Case cancelled | Case ID, reason |
| `workitem.created` | Work item assigned | Work item ID, assignee |
| `workitem.completed` | Work item done | Work item ID, completion time |

### Webhook Payload Example

```json
{
  "event": "case.completed",
  "timestamp": "2026-02-14T09:30:00Z",
  "case": {
    "id": "case_20260214_001",
    "workflowid": "order_fulfillment_1",
    "status": "COMPLETED",
    "createtime": "2026-02-14T08:00:00Z",
    "completiontime": "2026-02-14T09:30:00Z",
    "duration_seconds": 5400
  }
}
```

### Processing Webhooks

```bash
#!/bin/bash
# Webhook server example

# Using netcat for simplicity (production: use proper HTTP server)
listen_for_webhooks() {
  nc -l -p 8888 -q 1 | while read -r line; do
    if [[ "$line" == *"POST"* ]]; then
      # Parse webhook payload
      read -r payload

      # Extract event type
      event=$(echo "$payload" | jq -r '.event')
      case_id=$(echo "$payload" | jq -r '.case.id')

      echo "Received webhook: $event for case $case_id"

      # Handle event
      case "$event" in
        "case.completed")
          echo "Case $case_id completed"
          # Send notification, update external system, etc.
          ;;
        *)
          echo "Unknown event: $event"
          ;;
      esac

      # Send response
      echo "HTTP/1.1 200 OK"
      echo "Content-Type: application/json"
      echo ""
      echo '{"status":"received"}'
    fi
  done
}

listen_for_webhooks
```

## SDK and Libraries

### Python SDK

```python
# Installation
pip install yawl-sdk

# Usage
from yawl import YawlClient

client = YawlClient(
    base_url="http://localhost:8080",
    username="admin",
    password="admin"
)

# List workflows
workflows = client.list_workflows()
for workflow in workflows:
    print(f"Workflow: {workflow['id']} - {workflow['status']}")

# Create case
case = client.create_case(
    workflow_id="order_fulfillment_1",
    workflow_version="1.0",
    data={
        "order": {
            "id": "ORD-12345",
            "customer": "Jane Smith",
            "amount": 2500.00
        }
    }
)
print(f"Case created: {case['id']}")

# Get work items
work_items = client.get_work_items(user_id="john.doe")
for item in work_items:
    print(f"Work Item: {item['id']} - {item['status']}")

# Complete work item
client.complete_work_item(
    work_item_id="wi_001",
    data={
        "order": {
            "status": "APPROVED"
        }
    }
)
```

### Java Client

```java
// Maven dependency
// <dependency>
//   <groupId>org.yawlfoundation</groupId>
//   <artifactId>yawl-client</artifactId>
//   <version>5.2.0</version>
// </dependency>

import org.yawlfoundation.yawl.client.YawlClient;

public class YawlWorkflow {
    public static void main(String[] args) throws Exception {
        YawlClient client = new YawlClient(
            "http://localhost:8080",
            "admin",
            "admin"
        );

        // List workflows
        List<Workflow> workflows = client.listWorkflows();
        for (Workflow workflow : workflows) {
            System.out.println("Workflow: " + workflow.getId());
        }

        // Create case
        Case newCase = client.createCase(
            "order_fulfillment_1",
            "1.0",
            orderData
        );
        System.out.println("Case created: " + newCase.getId());

        // Close client
        client.close();
    }
}
```

### cURL Reference

```bash
# List all available endpoints
curl -X OPTIONS http://localhost:8080/engine -v

# Test connectivity
curl -I http://localhost:8080/resourceService/

# Pretty print XML response
curl -s http://localhost:8080/engine/workflows | xmllint --format -

# Save response to file
curl -u admin:admin http://localhost:8080/engine/cases -o cases.xml

# Follow redirects
curl -L http://localhost:8080/engine/cases

# Include response headers
curl -i http://localhost:8080/engine/cases

# Verbose output (for debugging)
curl -v http://localhost:8080/engine/cases

# Set custom headers
curl -H "X-Custom-Header: value" http://localhost:8080/engine/cases

# Upload file
curl -F "file=@workflow.yawl" http://localhost:8080/engine/workflows
```

---

**API Documentation Version**: 1.0
**Last Updated**: 2026-02-14
**Maintained By**: YAWL Foundation
