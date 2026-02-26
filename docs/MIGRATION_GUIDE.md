# YAWL v5 to v6.0.0-GA Migration Guide

**Audience**: YAWL v5.x users | **Updated**: February 2026

---

## Table of Contents

1. [Pre-Migration Checklist](#1-pre-migration-checklist)
2. [API Changes Matrix](#2-api-changes-matrix)
3. [Configuration Migration](#3-configuration-migration)
4. [Data Migration](#4-data-migration)
5. [Testing Strategy](#5-testing-strategy)
6. [Rollback Procedures](#6-rollback-procedures)

---

## 1. Pre-Migration Checklist

### Environment Requirements

| Requirement | v5.x | v6.0.0-GA | Action |
|-------------|------|-----------|--------|
| Java | 11+ | **25+** | Upgrade JDK |
| Maven | 3.6+ | 3.9.11+ | Upgrade Maven |
| Database | PostgreSQL 12+ | PostgreSQL 14+ | Upgrade if needed |
| Servlet Container | Tomcat 9+ | Tomcat 10+ / Jetty 12 | Upgrade container |

### Pre-Flight Checks

```bash
# 1. Check Java version (must be 25+)
java -version
# Expected: openjdk version "25.x.x"

# 2. Check Maven version
mvn -version
# Expected: Apache Maven 3.9.11+

# 3. Backup existing database
pg_dump -h localhost -U yawl yawl_v5 > backup_v5_$(date +%Y%m%d).sql

# 4. Export workflow specifications
# (via YAWL control panel or API)

# 5. Document custom configurations
cat /path/to/yawl/config/*.xml > config_backup.xml
```

### Compatibility Assessment

| Component | Compatible? | Notes |
|-----------|-------------|-------|
| Workflow specifications (XML) | ✅ Yes | No schema changes |
| Case data | ✅ Yes | Schema compatible |
| User/role data | ✅ Yes | Schema compatible |
| Custom work item handlers | ⚠️ Review | API changes possible |
| Custom logins | ⚠️ Review | Interface changes |
| RMI clients | ❌ No | Removed in v6 |

---

## 2. API Changes Matrix

### Engine API Changes

| Class/Method | v5.x | v6.0.0-GA | Migration |
|--------------|------|-----------|-----------|
| `YEngine.getInstance()` | Singleton | Singleton | No change |
| `YWorkItem.getStatus()` | `String` | `YWorkItemStatus` enum | Update callers |
| `YWorkItem.getStatusName()` | N/A | `String` | Use for string representation |
| `YCase.getID()` | `String` | `String` | No change |
| `YNetRunner.getCaseID()` | `String` | `String` | No change |

### Status Enum Migration

```java
// v5.x
String status = workItem.getStatus();
if (status.equals("Enabled")) { ... }

// v6.0.0-GA
YWorkItemStatus status = workItem.getStatus();
if (status == YWorkItemStatus.enabled) { ... }

// For string comparison
String statusName = workItem.getStatusName();
```

### Interface Changes

| Interface | v5.x | v6.0.0-GA |
|-----------|------|-----------|
| `YWorkItemService` | Old interface | **New interface** |
| `YLogReader` | Same | Same |
| `YLogWriter` | Same | Same |
| `YAuthenticationService` | Same | Same |
| `YResourceService` | Same | Same |

### Removed APIs

| API | Status | Replacement |
|-----|--------|-------------|
| RMI remote engine | **Removed** | REST API or MCP |
| EJB deployment | **Deprecated** | Servlet deployment |
| `YWorkItemService` (old) | **Removed** | New `YWorkItemService` |

### REST API Changes

| Endpoint | v5.x | v6.0.0-GA |
|----------|------|-----------|
| `/api/cases` | GET | GET (unchanged) |
| `/api/cases/{id}` | GET | GET (unchanged) |
| `/api/cases/{id}/items` | GET | GET (unchanged) |
| `/api/items/{id}/start` | POST | POST (unchanged) |
| `/api/items/{id}/complete` | POST | POST (unchanged) |
| `/api/generate` | N/A | **NEW** GRPO generation |

---

## 3. Configuration Migration

### Engine Configuration

```xml
<!-- v5.x: yawl-engine.xml -->
<engine>
    <mode>stateful</mode>
    <threadPool size="100"/>
</engine>

<!-- v6.0.0-GA: yawl-engine.xml -->
<engine>
    <mode>stateful</mode>  <!-- or stateless -->
    <virtualThreads enabled="true"/>
    <structuredConcurrency enabled="true"/>
</engine>
```

### Database Configuration

```xml
<!-- v5.x -->
<datasource>
    <driver>org.postgresql.Driver</driver>
    <url>jdbc:postgresql://localhost:5432/yawl</url>
    <pool>
        <maxActive>50</maxActive>
    </pool>
</datasource>

<!-- v6.0.0-GA (mostly compatible) -->
<datasource>
    <driver>org.postgresql.Driver</driver>
    <url>jdbc:postgresql://localhost:5432/yawl</url>
    <pool>
        <maxActive>50</maxActive>
        <!-- New: virtual thread aware -->
        <virtualThreadCompatible>true</virtualThreadCompatible>
    </pool>
</datasource>
```

### New Configuration: rl-config.toml

```toml
# New in v6.0.0-GA: GRPO engine configuration
[grpo]
k = 4
stage = "VALIDITY_GAP"
max_validations = 3
timeout_secs = 60

[llm]
provider = "ollama"
base_url = "http://localhost:11434"
model = "qwen2.5-coder"
```

### Configuration File Mapping

| v5.x File | v6.0.0-GA File | Notes |
|-----------|----------------|-------|
| `yawl-engine.xml` | `yawl-engine.xml` | Updated format |
| `yawl-datasource.xml` | `yawl-datasource.xml` | Compatible |
| `yawl-security.xml` | `yawl-security.xml` | Compatible |
| N/A | `rl-config.toml` | **NEW** |

---

## 4. Data Migration

### Schema Compatibility

| Table | Changes | Migration Required |
|-------|---------|-------------------|
| `yawl_cases` | None | No |
| `yawl_workitems` | New column: `status_enum` | Auto-migrated |
| `yawl_specs` | None | No |
| `yawl_logs` | None | No |
| `yawl_users` | None | No |
| `yawl_roles` | None | No |

### Migration Script

```sql
-- migrate_v5_to_v6.sql
-- Run this after backing up your database

BEGIN;

-- Add status_enum column to workitems
ALTER TABLE yawl_workitems
ADD COLUMN IF NOT EXISTS status_enum VARCHAR(50);

-- Migrate existing status strings to enum values
UPDATE yawl_workitems
SET status_enum = CASE status
    WHEN 'Enabled' THEN 'enabled'
    WHEN 'Executing' THEN 'executing'
    WHEN 'Complete' THEN 'complete'
    WHEN 'Suspended' THEN 'suspended'
    WHEN 'Fired' THEN 'fired'
    WHEN 'IsParent' THEN 'isParent'
    WHEN 'Deadlocked' THEN 'deadlocked'
    WHEN 'Failed' THEN 'failed'
    ELSE 'enabled'
END;

-- Add index on new column
CREATE INDEX IF NOT EXISTS idx_workitems_status_enum
ON yawl_workitems(status_enum);

COMMIT;
```

### Custom Work Item Handler Migration

```java
// v5.x
public class MyHandler extends AbstractWorkItemHandler {
    @Override
    public void execute(YWorkItem workItem) {
        String status = workItem.getStatus();
        // ...
    }
}

// v6.0.0-GA
public class MyHandler extends AbstractWorkItemHandler {
    @Override
    public void execute(YWorkItem workItem) {
        YWorkItemStatus status = workItem.getStatus();
        // Use enum comparison
        if (status == YWorkItemStatus.executing) {
            // ...
        }
    }
}
```

---

## 5. Testing Strategy

### Test Phases

| Phase | Scope | Duration |
|-------|-------|----------|
| **1. Unit Tests** | API compatibility | 1 day |
| **2. Integration Tests** | Database, handlers | 2 days |
| **3. End-to-End Tests** | Full workflow execution | 3 days |
| **4. Performance Tests** | Load testing | 2 days |
| **5. User Acceptance** | Business validation | 5 days |

### Test Checklist

```bash
# Phase 1: Unit Tests
mvn test -pl yawl-engine,yawl-elements

# Phase 2: Integration Tests
mvn verify -pl yawl-integration

# Phase 3: End-to-End Tests
mvn verify -pl test

# Phase 4: Performance Tests
mvn verify -P performance
```

### Compatibility Test Matrix

| Test Case | v5.x Behavior | v6.0.0-GA Expected | Pass? |
|-----------|---------------|-------------------|-------|
| Start case | Creates case | Creates case | ☐ |
| Complete work item | Status → complete | Status → complete | ☐ |
| Cancel case | Case cancelled | Case cancelled | ☐ |
| Parallel split | AND gateway | AND gateway | ☐ |
| Exclusive choice | XOR gateway | XOR gateway | ☐ |
| Loop | While loop | While loop | ☐ |
| Timer | Delay | Delay | ☐ |
| Custom handler | Executes | Executes | ☐ |

### Performance Baseline

| Metric | v5.x Baseline | v6.0.0-GA Target |
|--------|---------------|------------------|
| Cases/min | 1,200 | 3,000+ |
| P99 latency | 450ms | 150ms |
| Memory/case | 2.1KB | 1.6KB |

---

## 6. Rollback Procedures

### Rollback Decision Tree

```
Migration fails?
├─ Unit test failure → Fix code, retry
├─ Integration test failure → Check configuration, retry
├─ Data migration failure → Restore backup, investigate
└─ Production issue → ROLLBACK
```

### Rollback Steps

```bash
# 1. Stop v6.0.0-GA services
kubectl scale deployment yawl-engine --replicas=0 -n yawl

# 2. Restore database from backup
psql -h localhost -U yawl yawl < backup_v5_YYYYMMDD.sql

# 3. Deploy v5.x
kubectl apply -f yawl-v5-deployment.yaml

# 4. Verify v5.x is working
curl http://yawl.example.com/health

# 5. Monitor for issues
kubectl logs -f deployment/yawl-engine -n yawl
```

### Rollback Checklist

- [ ] v6.0.0-GA services stopped
- [ ] Database restored from pre-migration backup
- [ ] v5.x deployment verified
- [ ] All workflows executing normally
- [ ] No data loss confirmed
- [ ] Stakeholders notified

### Post-Rollback Analysis

1. **Document failure**: What went wrong?
2. **Root cause**: Configuration? Data? Code?
3. **Fix plan**: What needs to change?
4. **Retry timeline**: When to attempt again?

---

## Migration Timeline Template

| Week | Activities | Deliverables |
|------|------------|--------------|
| **Week 1** | Assessment, backup | Migration plan |
| **Week 2** | Dev environment migration | Working dev |
| **Week 3** | Staging migration | Validated staging |
| **Week 4** | Production migration | Live production |
| **Week 5** | Monitoring, optimization | Stable production |

---

## Related Documentation

- [GA Release Guide](./GA_RELEASE_GUIDE.md) — What's new in v6.0.0-GA
- [Deployment Guide](./DEPLOYMENT_GUIDE.md) — Production deployment
- [Quick Start](./QUICK-START.md) — Fresh installation

---

*Last Updated: February 26, 2026*
*Version: YAWL v6.0.0-GA*
