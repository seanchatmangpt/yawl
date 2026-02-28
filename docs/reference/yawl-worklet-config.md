# YAWL Worklet Configuration Reference

Complete configuration options for Ripple Down Rules engine and worklet service.

---

## Worklet Service Configuration

### Core Settings

```yaml
yawl:
  worklet:
    # Enable worklet service
    # Default: true
    enabled: true

    # RDR storage backend
    # Options: memory, database, file
    # Default: database
    storage: database

    # Database configuration (if storage: database)
    database:
      # JDBC URL
      url: jdbc:postgresql://localhost:5432/yawl_worklets

      # Database username
      username: yawl_user

      # Database password
      password: ${DB_PASSWORD}

      # Hibernate dialect
      dialect: org.hibernate.dialect.PostgreSQL95Dialect

      # Connection pool
      pool:
        # Maximum pool size
        # Default: 20
        max-size: 20

        # Minimum idle connections
        # Default: 5
        min-idle: 5

        # Connection timeout (milliseconds)
        # Default: 30000
        connection-timeout: 30000

    # File storage configuration (if storage: file)
    file:
      # File path for RDR sets
      path: /var/lib/yawl/worklets/rdr-sets.json

      # Auto-save interval (milliseconds)
      # Default: 5000
      auto-save-interval: 5000

      # Pretty print JSON
      # Default: true
      pretty-print: true

    # Memory cache configuration
    cache:
      # Cache size (number of RDR trees)
      # Default: 100
      size: 100

      # Cache TTL (minutes)
      # Default: 30
      ttl-minutes: 30

      # Enable cache statistics
      # Default: false
      statistics-enabled: false
```

---

## RDR Engine Configuration

### Evaluation Settings

```yaml
yawl:
  worklet:
    evaluation:
      # RDR evaluation timeout (milliseconds)
      # Default: 5000
      timeout-ms: 5000

      # Maximum tree depth
      # Default: 20
      max-tree-depth: 20

      # Maximum number of nodes per tree
      # Default: 1000
      max-nodes-per-tree: 1000

      # Fail on condition error
      # Default: true (throw exception)
      fail-on-error: true

      # Fallback conclusion if evaluation fails
      # Default: null
      fallback-conclusion: null

      # Condition syntax
      # Options: simple, spel, groovy
      # Default: simple
      condition-syntax: simple

    # Condition evaluator configuration
    evaluator:
      # Custom evaluators
      custom-evaluators:
        - class: org.yawlfoundation.yawl.worklet.custom.BusinessRuleEvaluator
          priority: 10

      # SpEL configuration (if condition-syntax: spel)
      spel:
        # Enable SpEL
        enabled: false

        # Cache compiled expressions
        cache-expressions: true

      # Groovy configuration (if condition-syntax: groovy)
      groovy:
        # Enable Groovy
        enabled: false

        # Compile to bytecode
        compile-static: true

        # Type checking mode
        type-checking: STATICCOMPILE
```

---

## Worklet Selection

### Conclusion Mapping

```yaml
yawl:
  worklet:
    selection:
      # How to resolve worklet conclusion to spec ID
      # Options: direct, lookup, builder
      # Default: direct
      resolution-strategy: direct

      # Worklet specification mapping
      # If resolution: lookup
      worklet-map:
        ExecutiveApprovalWorklet: org.mycompany.workflows:ExecutiveApproval:1.0
        ManagerApprovalWorklet: org.mycompany.workflows:ManagerApproval:1.0
        AutoApprovalWorklet: org.mycompany.workflows:AutoApproval:1.0

      # Allow dynamic worklet creation
      # Default: false
      allow-dynamic-creation: false

      # Worklet version resolution
      # Options: latest, specific, range
      # Default: latest
      version-resolution: latest

      # Multiple conclusions per node
      # Default: false
      allow-multiple-conclusions: false

    # Exception worklet configuration
    exceptions:
      # Enable exception worklet handling
      # Default: true
      enabled: true

      # Exception types supported
      exception-types:
        - TIMEOUT
        - CONSTRAINT_VIOLATION
        - MANUAL_SUSPENSION
        - MAX_REASSIGNMENTS_EXCEEDED
        - EXTERNAL_FAILURE

      # Exception to worklet mapping
      exception-map:
        TIMEOUT: org.mycompany:TimeoutHandler:1.0
        CONSTRAINT_VIOLATION: org.mycompany:ConstraintHandler:1.0
        MANUAL_SUSPENSION: org.mycompany:SuspensionHandler:1.0
```

---

## Monitoring and Audit

### Performance Monitoring

```yaml
yawl:
  worklet:
    monitoring:
      # Enable monitoring
      # Default: true
      enabled: true

      # Track evaluation time
      # Default: true
      track-evaluation-time: true

      # Slow evaluation threshold (milliseconds)
      # Default: 100
      slow-evaluation-threshold-ms: 100

      # Track worklet selection distribution
      # Default: true
      track-selections: true

      # Track evaluation errors
      # Default: true
      track-errors: true

      # Metrics retention (hours)
      # Default: 24
      retention-hours: 24

    # Audit configuration
    audit:
      # Enable audit logging
      # Default: true
      enabled: true

      # Events to audit
      events:
        # Rule creation/update/delete
        rule-changes: true

        # Tree version changes
        tree-versions: true

        # Worklet selections
        selections: true

        # Evaluation errors
        evaluation-errors: true

        # Threshold breaches
        slow-evaluations: true

      # Audit log retention
      # Default: 90 (days)
      retention-days: 90

      # Archive old logs
      # Default: true
      archive: true
```

### Versioning

```yaml
yawl:
  worklet:
    versioning:
      # Enable version control
      # Default: true
      enabled: true

      # Versioning strategy
      # Options: timestamp, semantic, manual
      # Default: timestamp
      strategy: timestamp

      # Retain N versions
      # Default: unlimited
      retention-count: null

      # Retain for N days
      # Default: 90
      retention-days: 90

      # Auto-create backup on changes
      # Default: true
      auto-backup: true

      # Timestamp format (for timestamp strategy)
      # Default: yyyyMMdd_HHmmss
      timestamp-format: yyyyMMdd_HHmmss
```

---

## Import/Export

### External Sources

```yaml
yawl:
  worklet:
    import:
      # Enable imports
      # Default: true
      enabled: true

      # Import sources
      sources:
        - type: database
          name: legacy-rules
          url: jdbc:postgresql://legacy-db:5432/rules
          username: ${LEGACY_DB_USER}
          password: ${LEGACY_DB_PASSWORD}
          query: "SELECT * FROM rdr_rules WHERE active = true"

        - type: file
          name: csv-rules
          path: /etc/yawl/worklets/rules.csv
          format: csv
          delimiter: ","
          header: true

        - type: api
          name: external-rules
          url: http://rules-service:8080/api/rdr
          auth: bearer
          token: ${RULES_API_TOKEN}

    export:
      # Enable exports
      # Default: true
      enabled: true

      # Export destinations
      destinations:
        - type: file
          path: /var/lib/yawl/worklets/export/
          format: json

        - type: database
          url: jdbc:postgresql://archive-db:5432/worklets_archive
          username: ${ARCHIVE_DB_USER}
          password: ${ARCHIVE_DB_PASSWORD}
```

---

## Integration

### Event Handlers

```yaml
yawl:
  worklet:
    events:
      # Enable event publishing
      # Default: true
      enabled: true

      # Async event processing
      # Default: true
      async: true

      # Event thread pool
      executor:
        # Core threads
        # Default: 2
        core-threads: 2

        # Max threads
        # Default: 4
        max-threads: 4

        # Queue capacity
        # Default: 100
        queue-capacity: 100

      # Publish events to topics
      publishers:
        - type: event-bus
          name: workflow-events

        - type: webhook
          url: http://external-system:8080/worklets
          auth: api-key
          api-key: ${WEBHOOK_API_KEY}

    # Listener configuration
    listeners:
      # Auto-register listeners
      # Default: true
      auto-register: true

      # Listener packages to scan
      scan-packages:
        - org.yawlfoundation.yawl.worklet
        - org.mycompany.worklets
```

---

## API Endpoints

### Rule Management

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/worklets/rules` | POST | Create RDR tree |
| `/api/v1/worklets/rules/{taskId}` | GET | Get RDR tree |
| `/api/v1/worklets/rules/{taskId}` | PATCH | Update RDR tree |
| `/api/v1/worklets/rules/{taskId}` | DELETE | Delete RDR tree |
| `/api/v1/worklets/rules/{taskId}/add` | POST | Add node to tree |
| `/api/v1/worklets/rules/{taskId}/{nodeId}` | PATCH | Update node |
| `/api/v1/worklets/rules/{taskId}/{nodeId}` | DELETE | Delete node |
| `/api/v1/worklets/rules/{taskId}/evaluate` | POST | Evaluate conditions |
| `/api/v1/worklets/rules/{taskId}/metrics` | GET | Get performance metrics |
| `/api/v1/worklets/rules/{taskId}/history` | GET | Get audit trail |
| `/api/v1/worklets/rules/{taskId}/versions` | GET | List versions |
| `/api/v1/worklets/rules/{taskId}/rollback` | POST | Rollback version |

### Worklet Management

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/v1/worklets/rules/{taskId}/exceptions` | POST | Set exception rules |
| `/api/v1/cases/{caseId}/worklets` | GET | Get active worklets |
| `/api/v1/cases/{caseId}/worklets/{taskId}` | GET | Get worklet for task |

---

## Complete Example

```yaml
# application-worklets-production.yaml

yawl:
  worklet:
    enabled: true

    storage: database
    database:
      url: jdbc:postgresql://rds.example.com:5432/yawl_worklets
      username: ${WORKLET_DB_USER}
      password: ${WORKLET_DB_PASSWORD}
      dialect: org.hibernate.dialect.PostgreSQL95Dialect
      pool:
        max-size: 20
        min-idle: 5
        connection-timeout: 30000

    cache:
      size: 1000
      ttl-minutes: 30
      statistics-enabled: true

    evaluation:
      timeout-ms: 5000
      max-tree-depth: 20
      max-nodes-per-tree: 1000
      condition-syntax: spel
      evaluator:
        custom-evaluators:
          - class: org.mycompany.worklets.BusinessRuleEvaluator
            priority: 10

    monitoring:
      enabled: true
      track-evaluation-time: true
      slow-evaluation-threshold-ms: 100
      track-selections: true
      retention-hours: 24

    audit:
      enabled: true
      events:
        rule-changes: true
        tree-versions: true
        selections: true
      retention-days: 90

    versioning:
      enabled: true
      strategy: semantic
      retention-days: 90
      auto-backup: true

    import:
      enabled: true
      sources:
        - type: database
          name: legacy-rules
          url: jdbc:postgresql://legacy:5432/rules
          username: ${LEGACY_USER}
          password: ${LEGACY_PASSWORD}

    events:
      enabled: true
      async: true
      publishers:
        - type: event-bus
          name: workflow-events
```

---

## Common Patterns

### Simple Binary Decision (Invoice Approval)

```
Root: amount > 10000
  ├─ True: ExecutiveApprovalWorklet
  └─ False: StandardApprovalWorklet
```

### Multi-Level Decision (Order Processing)

```
Root: amount > 50000
  ├─ True: HighValueWorklet
  │   └─ True: VIPHighValueWorklet
  └─ False: amount > 10000
      ├─ True: StandardWorklet
      └─ False: AutoApprovalWorklet
```

### Risk-Based Decision (Compliance)

```
Root: riskScore > 0.8
  ├─ True: ComplianceReviewWorklet
  │   └─ True: ExecutiveApprovalWorklet
  └─ False: AutoApprovalWorklet
```

---

## Troubleshooting

### RDR Tree Not Found

```
Solution: Verify tree exists at GET /api/v1/worklets/rules/{taskId}
Create new tree if missing
```

### Slow Evaluation

```
Solution: Check metrics at GET /api/v1/worklets/rules/{taskId}/metrics
Reduce tree depth
Simplify conditions
Use custom evaluators for complex logic
```

### Wrong Worklet Selected

```
Solution: Enable monitoring and check selections distribution
Verify condition syntax
Test conditions independently
```

---

## See Also

- [How-To: Implement Worklet Service (Advanced)](../how-to/implement-worklet-service-advanced.md)
- [Tutorial: Getting Started](../tutorials/yawl-worklet-getting-started.md)
- [Architecture: RDR Design](../explanation/yawl-worklet-architecture.md)
