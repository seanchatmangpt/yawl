# How to Migrate Case IDs from Integer to UUID

## Problem

Legacy YAWL deployments use integer case IDs (1, 2, 3, ...) generated from a shared counter that requires cross-replica coordination in Kubernetes. This becomes a bottleneck in multi-node clusters where all pods contend for the next case number. UUID case IDs (e.g., `550e8400-e29b-41d4-a716-446655440000`) eliminate coordination overhead and scale horizontally without collisions. You need to migrate existing integer IDs to UUIDs while maintaining backward compatibility with running cases.

## Prerequisites

- YAWL v6.0+ (UUID support added in v6.0)
- PostgreSQL 13+ or MySQL 8.0+ (case ID column must support 36-character strings)
- Downtime window of 5-10 minutes (for schema migration)
- Database backup before migration
- Understanding of `YCaseNbrStore` and case lifecycle

## Steps

### 1. Review the YCaseNbrStore Changes

The case ID generation logic was modified in v6.0:

```java
package org.yawlfoundation.yawl.engine;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides for the persistence of the last allocated case id, and the generation
 * of new case ids.
 */
public class YCaseNbrStore {

    private int pkey = 1001;
    private final AtomicInteger caseNbr;
    private boolean persisted = false;
    private boolean persisting = false;

    // ... initialization ...

    /**
     * Returns the next case identifier as a UUID string.
     *
     * UUID generation requires no cross-replica coordination, eliminating
     * the AtomicInteger collision hazard when multiple engine pods
     * share a case namespace. The legacy integer counter is kept for
     * getCaseNbr()/setCaseNbr() callers (e.g. restore from
     * persisted state) but is no longer used for new case IDs.
     *
     * @param pmgr persistence manager (ignored — UUIDs are not persisted)
     * @return a fresh UUID string suitable for use as a YIdentifier value
     */
    public String getNextCaseNbr(YPersistenceManager pmgr) {
        return UUID.randomUUID().toString();
    }

    // Backward-compatibility getters/setters for integer counter
    public int getCaseNbr() { return caseNbr.get(); }
    public void setCaseNbr(int nbr) { caseNbr.set(nbr); }
}
```

Key points:
- `getNextCaseNbr()` now returns UUID strings instead of integers
- Legacy `getCaseNbr()`/`setCaseNbr()` methods still exist for deserializing old case data from the database
- No database schema changes required (column type is already VARCHAR/CHAR)
- New cases automatically use UUIDs; old integer IDs are never overwritten

### 2. Prepare the Database Schema

Before upgrading the engine, ensure the case ID column can store UUID strings (36 characters + hyphens):

#### PostgreSQL

```sql
-- Check current column definition
\d yawl_case
-- Column: case_id | Type: character varying(50)

-- If column is smaller, expand it
ALTER TABLE yawl_case
  ALTER COLUMN case_id TYPE character varying(50);

-- Ensure uniqueness constraint still holds
CREATE UNIQUE INDEX idx_case_id_unique ON yawl_case(case_id);
```

#### MySQL

```sql
-- Check current column definition
DESCRIBE yawl_case;
-- Look for: case_id | varchar(50)

-- If needed, expand the column
ALTER TABLE yawl_case
  MODIFY COLUMN case_id VARCHAR(50) NOT NULL UNIQUE;
```

### 3. Create a Data Migration Script (Optional)

If you need to document existing integer case IDs for auditing, create a migration log before upgrading:

```sql
-- Backup table of legacy integer case IDs
CREATE TABLE yawl_case_id_migration_log AS
SELECT
    case_id,
    case_status,
    started_at,
    completed_at,
    'INTEGER' AS original_format,
    NOW() AS migrated_at
FROM yawl_case
WHERE case_id ~ '^[0-9]+$'  -- PostgreSQL: regex for digits only
   OR case_id REGEXP '^[0-9]+$';  -- MySQL: regex for digits only

-- Verify: all active cases should be integer IDs (UUID cases don't exist yet)
SELECT COUNT(*) as legacy_int_cases FROM yawl_case WHERE case_id ~ '^[0-9]+$';
SELECT COUNT(*) as new_uuid_cases FROM yawl_case WHERE case_id ~ '^[0-9a-f-]{36}$';
```

### 4. Stop the Engine and Drain Running Cases

Drain in-flight work items to ensure no new cases are launched during migration:

```bash
# 1. Set engine to read-only mode (if supported by your deployment)
kubectl set env deployment/yawl-engine -n yawl \
  YAWL_MODE=readonly

# 2. Wait for in-flight cases to complete (or timeout)
sleep 120

# 3. Verify no active cases
ACTIVE_COUNT=$(kubectl exec -it deployment/yawl-engine -n yawl -- \
  psql -U yawl_user -d yawl_db \
  -c "SELECT COUNT(*) FROM yawl_case WHERE case_status != 'COMPLETED';" | grep -o "[0-9]*")

if [ "$ACTIVE_COUNT" -gt 0 ]; then
  echo "WARNING: $ACTIVE_COUNT active cases remain. Consider forcefully terminating them."
fi

# 4. Stop the engine
kubectl scale deployment yawl-engine -n yawl --replicas=0
kubectl rollout status deployment/yawl-engine -n yawl
```

### 5. Upgrade the Engine to v6.0+

Deploy the new version:

```bash
# Update the deployment image
kubectl set image deployment/yawl-engine -n yawl \
  yawl-engine=yawl/engine:6.0.0
kubectl rollout status deployment/yawl-engine -n yawl --timeout=5m
```

The engine now calls `UUID.randomUUID()` for every new case launched.

### 6. Verify UUID Format in New Cases

After the engine starts, launch a test case and verify the format:

```bash
# Get specification ID
SPEC_ID=$(kubectl exec -it deployment/yawl-engine -n yawl -- \
  curl -s "http://localhost:8080/yawl/ib?action=listSpecifications" | \
  grep -o '"specID":"[^"]*' | head -1 | cut -d'"' -f4)

# Launch a case
CASE=$(kubectl exec -it deployment/yawl-engine -n yawl -- \
  curl -s "http://localhost:8080/yawl/ib?action=launchCase&specID=$SPEC_ID&sessionHandle=..." | \
  grep -o '"case_id":"[^"]*' | cut -d'"' -f4)

echo "New case ID: $CASE"

# Verify it's a UUID format (36 hex characters and hyphens)
if [[ $CASE =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]]; then
  echo "SUCCESS: Case ID is UUID format"
else
  echo "ERROR: Case ID is not UUID format: $CASE"
fi
```

### 7. Restore Production Replicas

Scale the engine back to the production replica count:

```bash
kubectl scale deployment yawl-engine -n yawl --replicas=3
kubectl rollout status deployment/yawl-engine -n yawl --timeout=10m

# Verify all pods are running
kubectl get pods -n yawl -l app.kubernetes.io/name=yawl-engine
```

## Backward Compatibility

### Querying Legacy Integer Case IDs

The engine still supports querying and interacting with cases that have integer IDs (from before the migration):

```bash
# This still works for legacy cases
curl "http://yawl-engine:8080/yawl/ib?action=getCaseInfo&caseID=12345&sessionHandle=..."

# And for UUID cases
curl "http://yawl-engine:8080/yawl/ib?action=getCaseInfo&caseID=550e8400-e29b-41d4-a716-446655440000&sessionHandle=..."
```

### Database Persistence

The `YCaseNbrStore` still maintains an `AtomicInteger` counter for deserializing legacy persisted state:

```java
// When restoring from database backup with integer case ID = 5000
YCaseNbrStore store = YCaseNbrStore.getInstance();
store.setCaseNbr(5000);  // Legacy deserialization still works

// But new cases generated will be UUIDs
String nextId = store.getNextCaseNbr(pmgr);
// Result: "550e8400-e29b-41d4-a716-446655440000"
```

## Verification

### 1. Check Case ID Formats in the Database

```bash
# Connect to the database
kubectl exec -it postgres-0 -- \
  psql -U yawl_user -d yawl_db

-- Query case ID formats
SELECT
    COUNT(CASE WHEN case_id ~ '^[0-9]+$' THEN 1 END) as legacy_int_count,
    COUNT(CASE WHEN case_id ~ '^[0-9a-f-]{36}$' THEN 1 END) as new_uuid_count
FROM yawl_case;

-- Expected output (before migration):
-- legacy_int_count | new_uuid_count
-- 42               | 0

-- Expected output (after migration and some new cases):
-- legacy_int_count | new_uuid_count
-- 42               | 5
```

### 2. Verify No Collisions

Run a load test to confirm no duplicate case IDs are generated:

```bash
# Launch 100 cases in parallel
for i in {1..100}; do
  (
    SPEC_ID=$(kubectl exec -it deployment/yawl-engine -n yawl -- \
      curl -s "http://localhost:8080/yawl/ib?action=listSpecifications" | \
      grep -o '"specID":"[^"]*' | head -1 | cut -d'"' -f4)

    kubectl exec -it deployment/yawl-engine -n yawl -- \
      curl -s "http://localhost:8080/yawl/ib?action=launchCase&specID=$SPEC_ID&sessionHandle=..."
  ) &
done
wait

# Check for duplicates
kubectl exec -it postgres-0 -- \
  psql -U yawl_user -d yawl_db \
  -c "SELECT case_id, COUNT(*) as cnt FROM yawl_case GROUP BY case_id HAVING COUNT(*) > 1;"

# Expected output: (no rows — no duplicates)
```

### 3. Monitor Case Throughput

Compare throughput before and after migration:

```bash
# Pre-migration: integer IDs (with contention on the counter)
# Time 100 sequential case launches
time for i in {1..100}; do
  kubectl exec -it deployment/yawl-engine -n yawl -- \
    curl -s "http://localhost:8080/yawl/ib?action=launchCase&specID=$SPEC_ID&sessionHandle=..." > /dev/null
done

# Post-migration: UUID IDs (no contention)
# Time 100 sequential case launches
time for i in {1..100}; do
  kubectl exec -it deployment/yawl-engine -n yawl -- \
    curl -s "http://localhost:8080/yawl/ib?action=launchCase&specID=$SPEC_ID&sessionHandle=..." > /dev/null
done

# Expected: Post-migration time should be similar or faster (no counter contention)
```

## Troubleshooting

### `Duplicate key value violates unique constraint "pk_yawl_case"`

A UUID collision occurred (statistically impossible, but check for bugs):

```sql
-- Find duplicate case IDs
SELECT case_id, COUNT(*) FROM yawl_case GROUP BY case_id HAVING COUNT(*) > 1;

-- If found, one must be deleted (or the root cause investigated)
DELETE FROM yawl_case WHERE case_id = '<duplicate>' AND created_at = (
  SELECT MIN(created_at) FROM yawl_case WHERE case_id = '<duplicate>'
);
```

### `Column case_id cannot expand to 50 characters` (MySQL)

The column is too small. Check and expand it:

```sql
DESCRIBE yawl_case;
-- If case_id is VARCHAR(20) or smaller:

ALTER TABLE yawl_case MODIFY COLUMN case_id VARCHAR(50);
```

### `Engine still generating integer case IDs after upgrade`

The engine code wasn't updated. Verify the version:

```bash
kubectl exec -it deployment/yawl-engine -n yawl -- \
  java -cp /opt/yawl/lib/* org.yawlfoundation.yawl.engine.YEngine --version
# Should print: YAWL v6.0.0 or later
```

If still on v5.x, redeploy with the correct image:

```bash
kubectl set image deployment/yawl-engine -n yawl \
  yawl-engine=yawl/engine:6.0.0
```

### `Rollback to integer IDs needed`

If UUID migration caused unforeseen issues, you can temporarily revert case ID generation by downgrading, but existing UUID cases cannot be re-mapped to integers. Plan carefully.

To prevent new UUID cases and revert to integer ID generation, downgrade the engine:

```bash
kubectl set image deployment/yawl-engine -n yawl \
  yawl-engine=yawl/engine:5.2.0
kubectl rollout status deployment/yawl-engine -n yawl

# Warning: Any new cases will have integer IDs again; make sure counter is at the right value
```

This is not recommended for production. Instead, ensure thorough testing in staging first.
