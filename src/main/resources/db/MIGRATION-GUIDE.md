# YAWL Database Migration Guide

Database schema versioning and management via Flyway.

## Overview

YAWL uses [Flyway](https://flywaydb.org) to manage database schema versioning. All database changes are captured in SQL migration files stored in `src/main/resources/db/migration/` and applied automatically on application startup.

### Key Benefits

- **Version Control**: Schema changes are tracked in Git like code
- **Reproducibility**: Same migrations run consistently across environments
- **Safety**: Flyway prevents out-of-order and duplicate migrations
- **Auditing**: Migration history is maintained in `flyway_schema_history` table
- **Reversibility**: Old migrations are never modified; new migrations fix issues

## Migration Files

Migration files follow Flyway's naming convention:

```
V{version}__{description}.sql
```

Examples:
- `V1__Initial_Indexes.sql` - First schema setup
- `V3__Add_Resilience_Metrics.sql` - Add monitoring tables
- `V4__Add_Pact_Contract_Registry.sql` - Add contract tables

### Naming Rules

1. **Version**: Numeric, zero-padded (V001, V1, V10)
   - Versions are compared numerically
   - Gaps are allowed (V1, V3, V5)
   - Must be unique (no duplicates)

2. **Description**: Underscores separate words
   - CapitalCase: `Add_Resilience_Metrics`
   - No spaces or special characters
   - Descriptive but concise (3-4 words)

3. **Extension**: Always `.sql`
   - Only SQL migrations are supported (not Java)
   - One migration per file

### Example Migration Structure

```sql
-- ============================================================================
-- YAWL Database Enhancement - Circuit Breaker State Tracking
-- Version: 5.0.0
-- Purpose: Track circuit breaker state transitions for resilience monitoring
-- ============================================================================

-- Add new table
CREATE TABLE IF NOT EXISTS circuit_breaker_events (
    event_id BIGSERIAL PRIMARY KEY,
    breaker_name VARCHAR(255) NOT NULL,
    old_state VARCHAR(50),
    new_state VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes
CREATE INDEX idx_cb_events_breaker ON circuit_breaker_events(breaker_name);
CREATE INDEX idx_cb_events_time ON circuit_breaker_events(created_at);
```

## Running Migrations Locally

### Automatic (Default)

Migrations run automatically on application startup:

```bash
mvn spring-boot:run
```

Flyway will:
1. Scan `classpath:db/migration/` for migration files
2. Check `flyway_schema_history` table for applied versions
3. Execute any pending migrations in order
4. Log results to console

### Manual Trigger

To manually trigger migrations without starting the app:

```bash
# Using Spring CLI
spring run -e "classpath:db/migration" FlywayRunner.java

# Or via Maven
mvn flyway:migrate -Dflyway.configFile=src/main/resources/flyway.properties
```

### Disable Migrations

For offline builds or testing, disable Flyway:

```bash
mvn spring-boot:run -Dflyway.enabled=false
```

Or in `application.yml`:

```yaml
flyway:
  enabled: false
```

## Migration Workflow

### 1. Create New Migration

When you need to modify the schema:

```bash
# Create file with next version number
touch src/main/resources/db/migration/V5__Add_Performance_Metrics.sql
```

```sql
-- Write migration SQL
CREATE TABLE performance_metrics (
    metric_id BIGSERIAL PRIMARY KEY,
    metric_name VARCHAR(255) NOT NULL,
    metric_value DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_metrics_name ON performance_metrics(metric_name);
```

### 2. Test Migration

```bash
# Run tests to verify migrations
mvn clean test -Dmaven.test.skip=false

# Or run specific test
mvn test -Dtest=FlywayMigrationTest -Dmaven.test.skip=false
```

### 3. Commit to Git

```bash
git add src/main/resources/db/migration/V5__Add_Performance_Metrics.sql
git commit -m "chore: add performance metrics table (V5)"
```

### 4. Deploy

On deployment, migrations run automatically on application startup.

## Schema Versioning Strategy

### Current Versions

- **V1**: Initial indexes for performance
- **V2**: Partitioning setup for high-growth tables
- **V3**: Resilience pattern metrics (circuit breaker, retry, bulkhead)
- **V4**: A2A/MCP contract registry and protocol evolution tracking

### Future Versions

When adding new migrations:

1. **V5+**: New features or schema enhancements
2. **Maintenance**: Create new V{N}__Fix_... migrations for bugs
3. **Never modify**: Existing migration files (they're immutable)

### Baseline Migrations

If retrofitting Flyway to an existing database:

```yaml
flyway:
  baseline-on-migrate: true
  baseline-version: "0"
  baseline-description: "Initial baseline"
```

This creates a virtual V0 migration representing your current schema.

## Checking Migration Status

### View Applied Migrations

Query the Flyway history table:

```sql
SELECT version, description, type, success, execution_time FROM flyway_schema_history;
```

Expected output:

```
version | description           | type | success | execution_time
--------|----------------------|------|---------|---------------
1       | Initial Indexes      | SQL  | true    | 150
2       | Partitioning Setup   | SQL  | true    | 300
3       | Add Resilience Metrics| SQL  | true    | 250
4       | Add Pact Contract... | SQL  | true    | 200
```

### View Migration Files

Check what migrations exist:

```bash
ls -la src/main/resources/db/migration/
```

```
V1__Initial_Indexes.sql
V2__Partitioning_Setup.sql
V3__Add_Resilience_Metrics.sql
V4__Add_Pact_Contract_Registry.sql
```

### Pending Migrations

Migrations not yet applied to this database:

```bash
mvn flyway:info
```

Output shows:
- Applied migrations (with checksum)
- Pending migrations (to be applied)
- Invalid/failed migrations (if any)

## Rollback Procedures

### IMPORTANT: No Automatic Rollback

Flyway does **NOT** provide automatic rollback. To revert a migration:

1. **Create a new migration** that undoes the previous one
2. **Never modify** the original migration file

### Example Rollback Migration

If V5 added a column you need to remove:

```sql
-- V6__Rollback_Performance_Metrics_Removed_Column.sql

-- Remove the problematic column
ALTER TABLE performance_metrics DROP COLUMN unwanted_column;

-- Or drop the entire table if needed
-- DROP TABLE performance_metrics;
```

### Manual Rollback (Emergency Only)

If migrations are corrupted or need to be cleared:

```sql
-- WARNING: Only in development/test environments
DELETE FROM flyway_schema_history WHERE version >= 5;
```

Never delete `flyway_schema_history` entries in production.

## Database-Specific Migrations

YAWL supports multiple databases. Use conditional SQL if needed:

### H2 (In-Memory Testing)

```sql
-- H2-specific syntax
CREATE TABLE IF NOT EXISTS my_table (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### PostgreSQL

```sql
-- PostgreSQL-specific syntax
CREATE TABLE IF NOT EXISTS my_table (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### MySQL

```sql
-- MySQL-specific syntax
CREATE TABLE IF NOT EXISTS my_table (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

For truly portable migrations, stick to SQL:2003 standard syntax.

## Troubleshooting

### Migration Failed

Check Flyway logs:

```bash
# View migration error
mvn flyway:info

# Or check application logs
tail -f logs/yawl.log | grep -i flyway
```

### Out-of-Order Migration Error

Error: "Found migration with version X that is out of order"

**Cause**: A new migration with an old version number was added.

**Fix**:
1. Rename the migration file to a higher version number
2. Delete the failed entry from `flyway_schema_history`
3. Re-run migrations

### Checksum Mismatch

Error: "Migration checksums do not match. Expected: X, Received: Y"

**Cause**: Migration file was modified after being applied.

**Fix**:
1. **DO NOT modify** the original migration
2. Create a new V{N}__Fix_... migration to correct the issue
3. Or: `DELETE FROM flyway_schema_history WHERE version = X;` (dev only)

### Stale Schema

If your local schema is out of sync with migrations:

```bash
# Completely reset (dev/test only)
mvn flyway:clean
mvn flyway:migrate

# Or drop and recreate database
DROP DATABASE yawl;
CREATE DATABASE yawl;
mvn flyway:migrate
```

## Best Practices

### DO

- ✅ Write idempotent migrations (can run multiple times safely)
- ✅ Use descriptive names that explain the change
- ✅ Keep migrations small and focused
- ✅ Test migrations locally before committing
- ✅ Include comments explaining why, not just what
- ✅ Use schema version history for auditing

### DON'T

- ❌ Modify migration files after they're applied
- ❌ Skip version numbers (except for gaps)
- ❌ Mix unrelated changes in one migration
- ❌ Assume database state (use IF EXISTS/IF NOT EXISTS)
- ❌ Use procedural code in migrations (keep it pure SQL)
- ❌ Include data manipulation in schema migrations (separate concerns)

## Migration Standards

All migrations must follow YAWL standards:

1. **Naming**: `V{version}__{description}.sql`
2. **Headers**: Include comment block with purpose and version
3. **Safety**: Use `IF NOT EXISTS` and `IF EXISTS` clauses
4. **Indexing**: Create indexes for new tables and foreign keys
5. **Archiving**: Create archive tables for high-growth tables
6. **Timestamps**: Include created_at, modified_at as appropriate
7. **Comments**: Explain complex migrations

See `V1__Initial_Indexes.sql`, `V3__Add_Resilience_Metrics.sql`, `V4__Add_Pact_Contract_Registry.sql` for examples.

## References

- [Flyway Documentation](https://flywaydb.org/documentation)
- [SQL Migration Naming](https://flywaydb.org/documentation/concepts/migrations#sql-migrations)
- [Flyway Best Practices](https://flywaydb.org/documentation/concepts/migrations#best-practices)
- YAWL `.claude/rules/schema/xsd-validation.md`
