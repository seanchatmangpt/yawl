# How-To: Implement Worklet Service (Advanced)

Set up and configure the YAWL Worklet Service for production workflow adaptation via Ripple Down Rules.

## Prerequisites

- YAWL v6.0+ running
- Understanding of RDR algorithm (from [Tutorial](../tutorials/yawl-worklet-getting-started.md))
- Experience with YAWL specifications
- Database access (PostgreSQL/MySQL recommended for RDR storage)

---

## Task 1: Choose RDR Storage Backend

### Option A: Memory (Development Only)

```yaml
yawl:
  worklet:
    enabled: true
    storage: memory
    cache-size: 100
```

**Advantages:**
- No database needed
- Fast access

**Disadvantages:**
- RDR rules lost on restart
- Single instance only

### Option B: Database (Production Recommended)

```yaml
yawl:
  worklet:
    enabled: true
    storage: database
    database:
      url: jdbc:postgresql://db.production:5432/yawl_worklets
      username: ${DB_USER}
      password: ${DB_PASSWORD}
      dialect: postgresql
    cache-size: 1000
    cache-ttl-minutes: 30
```

**Advantages:**
- Persistent across restarts
- Scales to multiple instances
- Audit trail

**Disadvantages:**
- Database latency (mitigated by caching)
- Additional infrastructure

### Option C: File-Based (Testing)

```yaml
yawl:
  worklet:
    enabled: true
    storage: file
    file:
      path: /var/lib/yawl/worklets/rdr-sets.json
    cache-size: 100
```

---

## Task 2: Set Up RDR Database Schema

### PostgreSQL

```sql
-- Create tables for RDR storage
CREATE TABLE rdr_sets (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rdr_nodes (
    id BIGSERIAL PRIMARY KEY,
    rdr_set_id BIGINT NOT NULL,
    node_id INT NOT NULL,
    condition TEXT,
    conclusion VARCHAR(255),
    parent_node_id INT,
    is_true_child BOOLEAN,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (rdr_set_id) REFERENCES rdr_sets(id) ON DELETE CASCADE
);

CREATE TABLE rdr_node_history (
    id BIGSERIAL PRIMARY KEY,
    node_id INT NOT NULL,
    rdr_set_id BIGINT NOT NULL,
    operation VARCHAR(20),  -- CREATE, UPDATE, DELETE
    old_condition TEXT,
    new_condition TEXT,
    changed_by VARCHAR(255),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (rdr_set_id) REFERENCES rdr_sets(id) ON DELETE CASCADE
);

CREATE INDEX idx_rdr_sets_task_id ON rdr_sets(task_id);
CREATE INDEX idx_rdr_nodes_rdr_set ON rdr_nodes(rdr_set_id);
CREATE INDEX idx_rdr_nodes_parent ON rdr_nodes(parent_node_id);
CREATE INDEX idx_history_changes ON rdr_node_history(changed_at DESC);
```

### Initialize Schema

```bash
# Using Flyway (recommended)
mvn flyway:migrate -Dflyway.configFiles=worklet-schema.properties

# Or manual SQL
psql -h db.production -U yawl_user -d yawl_worklets < /path/to/worklet-schema.sql
```

---

## Task 3: Create Complex RDR Trees

### Example: Multi-Level Approval Workflow

```bash
curl -X POST http://localhost:8080/yawl/api/v1/worklets/rules \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "taskId": "ApprovalTask",
    "rootRule": {
      "id": 1,
      "condition": "amount > 100000 AND riskScore > 0.7",
      "conclusion": "ExecutiveApprovalWorklet",
      "trueChild": {
        "id": 2,
        "condition": "customerSegment == \"VIP\"",
        "conclusion": "VIPExecutiveApprovalWorklet",
        "falseChild": {
          "id": 3,
          "condition": "riskScore > 0.9",
          "conclusion": "ComplianceReviewWorklet"
        }
      },
      "falseChild": {
        "id": 4,
        "condition": "amount > 50000",
        "conclusion": "ManagerApprovalWorklet",
        "trueChild": {
          "id": 5,
          "condition": "country NOT IN (\"US\", \"EU\")",
          "conclusion": "InternationalApprovalWorklet"
        },
        "falseChild": {
          "id": 6,
          "condition": "amount > 10000",
          "conclusion": "TeamLeadApprovalWorklet",
          "falseChild": {
            "id": 7,
            "condition": null,
            "conclusion": "AutoApprovalWorklet"
          }
        }
      }
    }
  }'
```

---

## Task 4: Implement Custom Condition Evaluators

For complex conditions beyond simple comparisons:

### Create Custom Evaluator

```java
package org.yawlfoundation.yawl.worklet.custom;

import org.yawlfoundation.yawl.worklet.ConditionEvaluator;
import java.util.Map;

public class BusinessRuleEvaluator implements ConditionEvaluator {

    @Override
    public boolean evaluate(String condition, Map<String, Object> context) {
        // Examples of custom conditions
        switch (condition) {
            case "isQuarterEnd()":
                return isQuarterEnd();

            case "hasActiveComplaints(customerId)":
                String customerId = (String) context.get("customerId");
                return hasActiveComplaints(customerId);

            case "getDayOfWeek() == FRIDAY":
                return getDayOfWeek().equals("FRIDAY");

            case "invoiceMatchesPoRate(invoiceAmount, expectedAmount)":
                double invoiceAmount = ((Number) context.get("invoiceAmount")).doubleValue();
                double expectedAmount = ((Number) context.get("expectedAmount")).doubleValue();
                return invoiceMatchesPoRate(invoiceAmount, expectedAmount);

            default:
                return evaluateExpression(condition, context);
        }
    }

    private boolean isQuarterEnd() {
        java.time.LocalDate today = java.time.LocalDate.now();
        int month = today.getMonthValue();
        int day = today.getDayOfMonth();
        int maxDay = today.withDayOfMonth(1).plusMonths(1).minusDays(1).getDayOfMonth();
        return (month % 3 == 0) && (day == maxDay);
    }

    private boolean hasActiveComplaints(String customerId) {
        // Query complaint service
        return getComplaintService().hasActive(customerId);
    }

    private String getDayOfWeek() {
        return java.time.LocalDate.now().getDayOfWeek().toString();
    }

    private boolean invoiceMatchesPoRate(double invoiceAmount, double expectedAmount) {
        double tolerance = 0.02; // 2% tolerance
        return Math.abs(invoiceAmount - expectedAmount) <= expectedAmount * tolerance;
    }

    private boolean evaluateExpression(String condition, Map<String, Object> context) {
        // Fallback: use SpEL or Groovy for dynamic evaluation
        throw new UnsupportedOperationException("Custom evaluator for: " + condition);
    }

    private ComplaintService getComplaintService() {
        // Inject or look up complaint service
        throw new UnsupportedOperationException();
    }
}
```

### Register Custom Evaluator

```yaml
yawl:
  worklet:
    enabled: true
    custom-evaluators:
      - class: org.yawlfoundation.yawl.worklet.custom.BusinessRuleEvaluator
        priority: 10  # Higher priority than default
```

---

## Task 5: Version and Track RDR Changes

### Audit RDR Modifications

All rule changes are automatically logged:

```bash
# Get audit trail for a rule tree
curl -X GET 'http://localhost:8080/yawl/api/v1/worklets/rules/ApprovalTask/history' \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.history[] | {changedAt, operation, changedBy, oldCondition, newCondition}'

# Output:
# {
#   "changedAt": "2026-02-28T14:00:00Z",
#   "operation": "CREATE",
#   "changedBy": "admin",
#   "oldCondition": null,
#   "newCondition": "amount > 100000"
# }
# {
#   "changedAt": "2026-02-28T15:30:00Z",
#   "operation": "UPDATE",
#   "changedBy": "business_analyst",
#   "oldCondition": "amount > 100000 && riskScore > 0.7",
#   "newCondition": "amount > 100000 && riskScore > 0.8"
# }
```

### Enable Rule Versioning

```yaml
yawl:
  worklet:
    versioning:
      enabled: true
      strategy: timestamp  # or 'semantic'
      retention-days: 90
```

### Rollback to Previous Rule Version

```bash
# Get available versions
curl -X GET 'http://localhost:8080/yawl/api/v1/worklets/rules/ApprovalTask/versions' \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Rollback to specific version
curl -X POST 'http://localhost:8080/yawl/api/v1/worklets/rules/ApprovalTask/rollback' \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"versionId": "v_20260228_140000"}'
```

---

## Task 6: Monitor RDR Evaluation Performance

### Enable Performance Monitoring

```yaml
yawl:
  worklet:
    monitoring:
      enabled: true
      track-evaluation-time: true
      slow-evaluation-threshold-ms: 100
```

### Query Performance Metrics

```bash
# Get evaluation statistics for a rule tree
curl -X GET 'http://localhost:8080/yawl/api/v1/worklets/rules/ApprovalTask/metrics' \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq .

# Output:
# {
#   "taskId": "ApprovalTask",
#   "totalEvaluations": 1523,
#   "averageEvaluationTimeMs": 15.7,
#   "p95EvaluationTimeMs": 45.2,
#   "p99EvaluationTimeMs": 120.5,
#   "slowEvaluations": 3,
#   "selectedWorklets": {
#     "ExecutiveApprovalWorklet": 450,
#     "ManagerApprovalWorklet": 820,
#     "AutoApprovalWorklet": 253
#   }
# }
```

---

## Task 7: Handle Exception Worklets

### Define Exception Rules

```bash
curl -X POST 'http://localhost:8080/yawl/api/v1/worklets/rules/ApprovalTask/exceptions' \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
    "exceptionRules": {
      "TIMEOUT": {
        "condition": "age > 86400",  // > 24 hours old
        "conclusion": "ApprovalTimeoutEscalation",
        "description": "Escalate approval if exceeded 24 hours"
      },
      "CONSTRAINT_VIOLATION": {
        "condition": null,
        "conclusion": "ConstraintRecovery",
        "description": "Handle constraint violations"
      },
      "MANUAL_SUSPENSION": {
        "condition": "suspensionReason == \"WAITING_FOR_INFO\"",
        "conclusion": "FollowUpWorklet",
        "description": "Follow up on suspended approvals"
      },
      "MAX_REASSIGNMENTS_EXCEEDED": {
        "condition": null,
        "conclusion": "ExecutiveEscalation",
        "description": "Escalate to executive if reassigned >3 times"
      }
    }
  }'
```

---

## Task 8: Load RDR Rules from External Sources

### Load from CSV

```bash
#!/bin/bash

# rules.csv format:
# taskId,nodeId,parentNodeId,isTrue,condition,conclusion
# ApprovalTask,1,,false,amount > 100000,ExecutiveApprovalWorklet
# ApprovalTask,2,1,true,customerType == VIP,VIPApprovalWorklet

TASK_ID="ApprovalTask"
ADMIN_TOKEN="your-admin-token"

awk -F',' 'NR>1 {
  curl -X POST http://localhost:8080/yawl/api/v1/worklets/rules/$TASK_ID/import \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{
      \"nodeId\": $3,
      \"condition\": \"$5\",
      \"conclusion\": \"$6\",
      \"parentNodeId\": $4,
      \"isTrueChild\": $([ \"$3\" = \"true\" ] && echo true || echo false)
    }"
}' rules.csv
```

### Load from Database

```yaml
yawl:
  worklet:
    import:
      enabled: true
      sources:
        - type: database
          url: jdbc:postgresql://legacy-db:5432/rules
          username: ${LEGACY_DB_USER}
          password: ${LEGACY_DB_PASSWORD}
          query: "SELECT task_id, node_id, parent_node_id, condition, conclusion FROM rdr_rules"
```

---

## Task 9: Integration with Event System

### Subscribe to Worklet Selection Events

```java
import org.yawlfoundation.yawl.engine.*;
import org.springframework.context.event.EventListener;

@Component
public class WorkletSelectionListener {

    @EventListener
    public void onWorkletSelected(WorkletSelectedEvent event) {
        String taskId = event.getTaskId();
        String selectedWorklet = event.getSelectedWorklet();
        String caseId = event.getCaseId();

        logger.info("Worklet selected for case {}: {} -> {}",
            caseId, taskId, selectedWorklet);

        // Update metrics
        metrics.increment("worklets.selected." + selectedWorklet);

        // Audit log
        auditLog.record("WORKLET_SELECTED",
            Map.of(
                "caseId", caseId,
                "taskId", taskId,
                "worklet", selectedWorklet
            ));
    }

    @EventListener
    public void onWorkletCompletion(WorkletCompletedEvent event) {
        String caseId = event.getCaseId();
        String worklet = event.getWorkletSpecId();
        long durationMs = event.getDurationMs();

        logger.info("Worklet completed: {} in {}ms", worklet, durationMs);

        // Record performance
        metrics.timer("worklets.duration." + worklet)
            .record(Duration.ofMillis(durationMs));
    }
}
```

---

## Task 10: Production Deployment Checklist

- [ ] RDR rules stored in database (not memory)
- [ ] Database backed up regularly
- [ ] Rule change audit trail enabled
- [ ] Performance monitoring enabled
- [ ] Exception handling rules defined
- [ ] Custom condition evaluators tested
- [ ] Integration tests for all rule paths
- [ ] Monitoring dashboards created
- [ ] Alert rules for slow evaluations
- [ ] Documentation of rule set per task
- [ ] Approval process for rule changes
- [ ] Rollback strategy tested

### Example Production Config

```yaml
yawl:
  worklet:
    enabled: true

    storage: database
    database:
      url: jdbc:postgresql://rds.aws.amazon.com:5432/yawl_worklets
      username: ${WORKLET_DB_USER}
      password: ${WORKLET_DB_PASSWORD}
      dialect: postgresql
      max-pool-size: 20

    cache:
      size: 1000
      ttl-minutes: 30

    monitoring:
      enabled: true
      track-evaluation-time: true
      slow-evaluation-threshold-ms: 100

    versioning:
      enabled: true
      retention-days: 90

    evaluation:
      timeout-ms: 5000
      max-tree-depth: 20

    custom-evaluators:
      - class: org.yawlfoundation.yawl.worklet.custom.BusinessRuleEvaluator
        priority: 10
```

---

## Troubleshooting

### Rule Tree Not Found

**Error:** `WorkletRuleNotFoundException`

**Solution:**
```bash
# Verify rule tree exists
curl -X GET http://localhost:8080/yawl/api/v1/worklets/rules/{taskId} \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# If not found, create it
curl -X POST http://localhost:8080/yawl/api/v1/worklets/rules \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{...}'
```

### Slow Rule Evaluation

**Issue:** Worklet selection takes >100ms

**Solution:**
1. Reduce tree depth (max 20 levels recommended)
2. Move complex conditions to custom evaluators
3. Enable caching with appropriate TTL
4. Profile slow queries with monitoring enabled

### Rule Changes Not Applied

**Issue:** New rules don't affect running cases

**Solution:**
- Rule changes only apply to new cases/work items
- Pending work items use previously-selected worklet
- This is by design (prevents mid-execution changes)

---

## What's Next?

- **[Worklet Configuration Reference](../reference/yawl-worklet-config.md)** — All options
- **[How-To: Advanced RDR Patterns](../how-to/yawl-worklet-patterns.md)** — Pattern library
- **[Architecture: RDR Design](../explanation/yawl-worklet-architecture.md)** — Theory

---

**Return to:** [Tutorial: Getting Started](../tutorials/yawl-worklet-getting-started.md)
