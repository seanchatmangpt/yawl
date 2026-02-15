# YAWL + Teradata Vantage Integration Guide

## Overview

This guide provides comprehensive instructions for integrating the YAWL (Yet Another Workflow Language) engine with Teradata Vantage for enterprise workflow analytics, audit logging, and data processing pipelines.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Prerequisites](#prerequisites)
3. [JDBC Integration](#jdbc-integration)
4. [ETL/ELT Pipeline Integration](#etlelt-pipeline-integration)
5. [Query Optimization](#query-optimization)
6. [Real-time Analytics](#real-time-analytics)
7. [Error Handling](#error-handling)
8. [Monitoring and Observability](#monitoring-and-observability)

---

## Architecture Overview

### Integration Patterns

```
+------------------+     +-------------------+     +------------------+
|   YAWL Engine    |     |  Integration Layer |     | Teradata Vantage |
|                  |     |                   |     |                  |
| +-------------+  |     | +---------------+ |     | +--------------+ |
| | Workflow    |  |     | | JDBC Pool    | |     | | YAWL Schema  | |
| | Execution   |----->| | Connection   |----->| | Tables       | |
| +-------------+  |     | +---------------+ |     | +--------------+ |
|                  |     |                   |     |                  |
| +-------------+  |     | +---------------+ |     | +--------------+ |
| | Task Queue  |  |     | | ETL Pipeline | |     | | Audit Log    | |
| | Management  |----->| | (Airbyte/    |----->| | Tables       | |
| +-------------+  |     | | Fivetran)    | |     | +--------------+ |
|                  |     | +---------------+ |     |                  |
| +-------------+  |     |                   |     | +--------------+ |
| | Case Data   |  |     | +---------------+ |     | | Analytics    | |
| | Store       |----->| | QueryGrid    |----->| | Views        | |
| +-------------+  |     | +---------------+ |     | +--------------+ |
+------------------+     +-------------------+     +------------------+
```

### Data Flow Patterns

| Pattern | Use Case | Latency | Throughput |
|---------|----------|---------|------------|
| JDBC Direct | Transactional operations | Low (ms) | Medium |
| Bulk Load | Historical data import | Medium | High (M rows/sec) |
| Streaming | Real-time events | Very Low | Medium |
| QueryGrid | Cross-system queries | Low | High |

---

## Prerequisites

### Software Requirements

| Component | Version | Purpose |
|-----------|---------|---------|
| Java JDK | 11+ | YAWL Engine runtime |
| Teradata JDBC Driver | 20.00+ | Database connectivity |
| Maven | 3.8+ | Dependency management |
| YAWL Engine | 5.2+ | Workflow execution |

### YAWL Configuration

Add the following to your YAWL engine configuration:

```xml
<!-- In yawl-engine-config.xml -->
<datasources>
    <datasource name="teradata">
        <driver>com.teradata.jdbc.TeraDriver</driver>
        <url>jdbc:teradata://${TERADATA_HOST}/DATABASE=yawl</url>
        <username>${TERADATA_USER}</username>
        <password>${TERADATA_PASSWORD}</password>
        <pool>
            <maxActive>20</maxActive>
            <maxIdle>10</maxIdle>
            <minIdle>5</minIdle>
            <maxWait>30000</maxWait>
        </pool>
    </datasource>
</datasources>
```

### Maven Dependencies

```xml
<dependencies>
    <!-- Teradata JDBC Driver -->
    <dependency>
        <groupId>com.teradata.jdbc</groupId>
        <artifactId>terajdbc</artifactId>
        <version>20.00.00.10</version>
    </dependency>

    <!-- Teradata TypeScript for structured queries -->
    <dependency>
        <groupId>com.teradata.jdbc</groupId>
        <artifactId>terajdbc4</artifactId>
        <version>20.00.00.10</version>
    </dependency>

    <!-- Connection pooling (HikariCP recommended) -->
    <dependency>
        <groupId>com.zaxxer</groupId>
        <artifactId>HikariCP</artifactId>
        <version>5.1.0</version>
    </dependency>
</dependencies>
```

---

## JDBC Integration

### Connection Pool Configuration

```java
package org.yawlfoundation.yawl.database.teradata;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Teradata connection pool manager for YAWL engine.
 * Provides thread-safe connection pooling with configurable parameters.
 */
public class TeradataConnectionPool {

    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();

        // Connection settings
        config.setJdbcUrl(String.format(
            "jdbc:teradata://%s/DATABASE=%s,ENCRYPTDATA=%s",
            System.getenv("TERADATA_HOST"),
            System.getenv("TERADATA_DATABASE"),
            "true"
        ));
        config.setUsername(System.getenv("TERADATA_USER"));
        config.setPassword(System.getenv("TERADATA_PASSWORD"));

        // Driver configuration
        config.setDriverClassName("com.teradata.jdbc.TeraDriver");

        // Pool sizing
        config.setMaximumPoolSize(Integer.parseInt(
            System.getenv().getOrDefault("TERADATA_POOL_SIZE", "20")
        ));
        config.setMinimumIdle(Integer.parseInt(
            System.getenv().getOrDefault("TERADATA_POOL_MIN_IDLE", "5")
        ));

        // Timeouts
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        // Performance tuning
        config.addDataSourceProperty("LOGIN_TIMEOUT", "60");
        config.addDataSourceProperty("RESPONSE_BUFFER_SIZE", "65536");
        config.addDataSourceProperty("TYPE", "FASTEXPORT");
        config.addDataSourceProperty("CHARSET", "UTF8");

        // Connection validation
        config.setConnectionTestQuery("SELECT 1");

        dataSource = new HikariDataSource(config);
    }

    /**
     * Gets a connection from the pool.
     *
     * @return A database connection
     * @throws SQLException if connection cannot be obtained
     */
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Closes the connection pool.
     */
    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
```

### YAWL Data Access Layer

```java
package org.yawlfoundation.yawl.database.teradata;

import java.sql.*;
import java.util.*;
import org.yawlfoundation.yawl.elements.YAWLService;

/**
 * Data access object for YAWL workflow persistence in Teradata.
 */
public class YAWLTeradataDAO {

    /**
     * Persists workflow execution metadata.
     *
     * @param workflowId Unique workflow identifier
     * @param specificationId Workflow specification ID
     * @param caseId Associated case ID
     * @param startTime Execution start timestamp
     * @return true if successful
     */
    public boolean persistWorkflowExecution(
            String workflowId,
            String specificationId,
            String caseId,
            Timestamp startTime) {

        String sql = """
            INSERT INTO yawl.workflow_executions
                (workflow_id, specification_id, case_id, start_time, status, processing_date)
            VALUES
                (?, ?, ?, ?, 'RUNNING', CURRENT_DATE)
            """;

        try (Connection conn = TeradataConnectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, workflowId);
            stmt.setString(2, specificationId);
            stmt.setString(3, caseId);
            stmt.setTimestamp(4, startTime);

            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            YAWLService.getLogger().error("Failed to persist workflow execution", e);
            return false;
        }
    }

    /**
     * Updates workflow execution status upon completion.
     *
     * @param workflowId Workflow identifier
     * @param endTime Execution end timestamp
     * @param status Final status (COMPLETED, FAILED, CANCELLED)
     * @param errorMessage Optional error message
     * @return true if successful
     */
    public boolean updateWorkflowCompletion(
            String workflowId,
            Timestamp endTime,
            String status,
            String errorMessage) {

        String sql = """
            UPDATE yawl.workflow_executions
            SET end_time = ?,
                status = ?,
                error_message = ?
            WHERE workflow_id = ?
            """;

        try (Connection conn = TeradataConnectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, endTime);
            stmt.setString(2, status);
            stmt.setString(3, errorMessage);
            stmt.setString(4, workflowId);

            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            YAWLService.getLogger().error("Failed to update workflow completion", e);
            return false;
        }
    }

    /**
     * Retrieves workflow metrics for a time period.
     *
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return List of metric records
     */
    public List<Map<String, Object>> getWorkflowMetrics(
            Date startDate,
            Date endDate) {

        String sql = """
            SELECT
                specification_id,
                status,
                COUNT(*) as execution_count,
                AVG(EXTRACT(SECOND FROM (end_time - start_time))) as avg_duration_seconds,
                MIN(EXTRACT(SECOND FROM (end_time - start_time))) as min_duration_seconds,
                MAX(EXTRACT(SECOND FROM (end_time - start_time))) as max_duration_seconds
            FROM yawl.workflow_executions
            WHERE processing_date BETWEEN ? AND ?
            GROUP BY specification_id, status
            ORDER BY specification_id, status
            """;

        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection conn = TeradataConnectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, new java.sql.Date(startDate.getTime()));
            stmt.setDate(2, new java.sql.Date(endDate.getTime()));

            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    results.add(row);
                }
            }

        } catch (SQLException e) {
            YAWLService.getLogger().error("Failed to retrieve workflow metrics", e);
        }

        return results;
    }
}
```

---

## ETL/ELT Pipeline Integration

### Airbyte Integration

Airbyte provides open-source data integration for loading YAWL data into Teradata.

#### Configuration

```yaml
# airbyte-connection.yaml
connection:
  source:
    type: "yawl-engine"
    configuration:
      engine_url: "http://yawl-engine:8080"
      auth_token: "${YAWL_AUTH_TOKEN}"

  destination:
    type: "teradata"
    configuration:
      host: "${TERADATA_HOST}"
      port: 1025
      database: "yawl_staging"
      username: "${TERADATA_USER}"
      password: "${TERADATA_PASSWORD}"
      ssl: true

  sync:
    mode: "incremental"
    cursor_field: "updated_at"
    primary_key: ["workflow_id", "task_id"]
    schedule: "0 */15 * * * *"  # Every 15 minutes
```

#### Terraform Provider for Airbyte + Teradata

```hcl
# main.tf - Airbyte ELT pipeline to Teradata
terraform {
  required_providers {
    airbyte = {
      source  = "airbytehq/airbyte"
      version = "~> 0.3"
    }
  }
}

# Source: YAWL Engine
resource "airbyte_source_yawl" "yawl_workflows" {
  configuration = {
    engine_url  = var.yawl_engine_url
    auth_token  = var.yawl_auth_token
    start_date  = "2024-01-01T00:00:00Z"
  }
}

# Destination: Teradata Vantage
resource "airbyte_destination_teradata" "vantage" {
  configuration = {
    host       = var.teradata_host
    port       = 1025
    database   = "yawl_staging"
    username   = var.teradata_username
    password   = var.teradata_password
    ssl_mode   = "require"
  }
}

# Connection: YAWL to Teradata
resource "airbyte_connection" "yawl_to_teradata" {
  name           = "YAWL Workflows to Teradata"
  source_id      = airbyte_source_yawl.yawl_workflows.id
  destination_id = airbyte_destination_teradata.vantage.id

  configurations = {
    streams = [
      {
        name = "workflow_executions"
        sync_mode = "incremental_dedup"
        cursor_field = ["updated_at"]
        primary_key = [["workflow_id"], ["task_id"]]
      },
      {
        name = "case_data"
        sync_mode = "incremental_dedup"
        cursor_field = ["updated_at"]
        primary_key = [["case_id"]]
      },
      {
        name = "audit_log"
        sync_mode = "append"
        cursor_field = ["timestamp"]
      }
    ]
  }

  schedule = {
    cron_expression = "0 */15 * * * *"
  }
}
```

### Fivetran Integration

```yaml
# fivetran-connector.yaml
connector:
  service: "yawl_engine"
  group_id: "yawl_analytics"

destination:
  service: "teradata"
  configuration:
    host: "${TERADATA_HOST}"
    port: 1025
    database: "yawl_fivetran"
    user: "${TERADATA_USER}"
    password: "${TERADATA_PASSWORD}"

sync:
  frequency: 15  # minutes
  tables:
    - name: "workflow_executions"
      columns:
        - name: "workflow_id"
          primary_key: true
        - name: "updated_at"
          cursor: true
```

### dbt Transformations

After loading raw data via Airbyte or Fivetran, use dbt for transformations:

```sql
-- models/stg_workflow_executions.sql
{{ config(
    materialized='table',
    database='yawl',
    schema='analytics'
) }}

SELECT
    workflow_id,
    task_id,
    case_id,
    specification_id,
    start_time,
    end_time,
    status,
    error_message,
    processing_date,
    -- Calculated fields
    EXTRACT(SECOND FROM (end_time - start_time)) AS duration_seconds,
    CASE
        WHEN status = 'COMPLETED' AND
             EXTRACT(SECOND FROM (end_time - start_time)) <= 300
        THEN 'FAST'
        WHEN status = 'COMPLETED' AND
             EXTRACT(SECOND FROM (end_time - start_time)) <= 1800
        THEN 'NORMAL'
        WHEN status = 'COMPLETED' THEN 'SLOW'
        ELSE status
    END AS performance_category
FROM {{ source('yawl_staging', 'workflow_executions') }}
WHERE processing_date >= DATEADD('day', -30, CURRENT_DATE)
```

```sql
-- models/workflow_summary_daily.sql
{{ config(
    materialized='table',
    database='yawl',
    schema='analytics'
) }}

SELECT
    processing_date,
    specification_id,
    COUNT(DISTINCT workflow_id) AS total_workflows,
    COUNT(DISTINCT case_id) AS total_cases,
    SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_count,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed_count,
    SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_count,
    AVG(duration_seconds) AS avg_duration_seconds,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY duration_seconds) AS median_duration_seconds,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_seconds) AS p95_duration_seconds
FROM {{ ref('stg_workflow_executions') }}
GROUP BY processing_date, specification_id
```

---

## Query Optimization

### Index Strategy

```sql
-- Primary index for workflow lookups
CREATE MULTISET TABLE yawl.workflow_executions (
    -- columns defined above
)
PRIMARY INDEX (workflow_id, task_id)  -- Distribution key
PARTITION BY RANGE_N(processing_date BETWEEN
    DATE '2024-01-01' AND DATE '2025-12-31' EACH INTERVAL '1' DAY)
INDEX (case_id) WITH LOAD IDENTITY      -- Secondary access path
INDEX (start_time) WITH LOAD IDENTITY;  -- Time-based queries
```

### Query Best Practices

#### 1. Use Partition Pruning

```sql
-- GOOD: Partition pruning enabled
SELECT * FROM yawl.workflow_executions
WHERE processing_date = CURRENT_DATE
  AND status = 'RUNNING';

-- BAD: Full table scan
SELECT * FROM yawl.workflow_executions
WHERE status = 'RUNNING';
```

#### 2. Leverage Collect Statistics

```sql
-- Collect statistics after significant data changes
COLLECT STATISTICS ON yawl.workflow_executions
    INDEX (workflow_id, task_id),
    COLUMN (processing_date),
    COLUMN (status),
    COLUMN (case_id)
    USING SAMPLE 100 PERCENT;
```

#### 3. Use Teradata-Specific Functions

```sql
-- Use Teradata's analytical functions for efficient aggregation
SELECT
    specification_id,
    status,
    COUNT(*) OVER (PARTITION BY specification_id) as spec_total,
    ROW_NUMBER() OVER (
        PARTITION BY specification_id
        ORDER BY start_time DESC
    ) as recency_rank
FROM yawl.workflow_executions
WHERE processing_date >= CURRENT_DATE - 30;
```

### Materialized Views for Analytics

```sql
-- Create a join index for common analytical queries
CREATE JOIN INDEX yawl.ji_workflow_analytics AS
SELECT
    we.workflow_id,
    we.specification_id,
    we.status,
    we.processing_date,
    ws.specification_name,
    ws.department,
    ws.priority
FROM yawl.workflow_executions we
LEFT JOIN yawl.workflow_specifications ws
    ON we.specification_id = ws.specification_id
PRIMARY INDEX (workflow_id);
```

---

## Real-time Analytics

### Stream Processing Architecture

```
YAWL Engine -> Kafka -> Kafka Connect -> Teradata Vantage
                          (JDBC Sink)
```

#### Kafka Connect Configuration

```json
{
  "name": "yawl-teradata-sink",
  "config": {
    "connector.class": "io.confluent.connect.jdbc.JdbcSinkConnector",
    "tasks.max": "3",
    "topics": "yawl.workflow.events",
    "connection.url": "jdbc:teradata://${TERADATA_HOST}/DATABASE=yawl_streaming",
    "connection.user": "${TERADATA_USER}",
    "connection.password": "${TERADATA_PASSWORD}",
    "auto.create": "false",
    "auto.evolve": "false",
    "insert.mode": "insert",
    "batch.size": "1000",
    "linger.ms": "1000",
    "table.name.format": "workflow_events",
    "pk.mode": "record_key",
    "pk.fields": "event_id",
    "fields.whitelist": "workflow_id,task_id,event_type,event_data,timestamp",
    "transforms": "flatten",
    "transforms.flatten.type": "org.apache.kafka.connect.transforms.Flatten$Value",
    "transforms.flatten.delimiter": "_"
  }
}
```

### Event Table Schema

```sql
CREATE MULTISET TABLE yawl_streaming.workflow_events (
    event_id VARCHAR(36) NOT NULL,
    workflow_id VARCHAR(36) NOT NULL,
    task_id VARCHAR(36),
    event_type VARCHAR(50) NOT NULL,
    event_data JSON,
    timestamp TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    processed_flag CHAR(1) DEFAULT 'N',
    PRIMARY KEY (event_id)
)
PRIMARY INDEX (workflow_id)
PARTITION BY RANGE_N(CAST(timestamp AS DATE) BETWEEN
    DATE '2024-01-01' AND DATE '2025-12-31' EACH INTERVAL '1' DAY);
```

---

## Error Handling

### Exception Handling Pattern

```java
package org.yawlfoundation.yawl.database.teradata;

import java.sql.SQLException;
import java.util.concurrent.*;

/**
 * Retry handler for transient database errors.
 */
public class TeradataRetryHandler {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final Set<String> RETRYABLE_ERRORS = Set.of(
        "08S01",  // Communication link failure
        "08001",  // Unable to establish connection
        "08007",  // Connection failure during transaction
        "40506",  // System throttled
        "40507"   // Resource limit exceeded
    );

    /**
     * Executes operation with automatic retry for transient failures.
     *
     * @param operation The database operation to execute
     * @param <T> Return type
     * @return Operation result
     * @throws SQLException if operation fails after all retries
     */
    public static <T> T executeWithRetry(TeradataOperation<T> operation)
            throws SQLException {

        SQLException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return operation.execute();
            } catch (SQLException e) {
                lastException = e;

                if (!isRetryable(e)) {
                    throw e;
                }

                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }

        throw lastException;
    }

    private static boolean isRetryable(SQLException e) {
        return RETRYABLE_ERRORS.contains(e.getSQLState());
    }

    @FunctionalInterface
    public interface TeradataOperation<T> {
        T execute() throws SQLException;
    }
}
```

### Dead Letter Queue for Failed Events

```sql
-- Dead letter queue for events that cannot be processed
CREATE MULTISET TABLE yawl.dead_letter_queue (
    dlq_id BIGINT GENERATED ALWAYS AS IDENTITY,
    original_event_id VARCHAR(36),
    original_table VARCHAR(100),
    original_operation VARCHAR(20),
    original_data JSON,
    error_code VARCHAR(10),
    error_message VARCHAR(1000),
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_retry_at TIMESTAMP(6) WITH TIME ZONE,
    PRIMARY KEY (dlq_id)
)
PRIMARY INDEX (original_event_id);
```

---

## Monitoring and Observability

### Health Check Query

```sql
-- Use as a health check endpoint
SELECT
    'connection' AS check_type,
    'healthy' AS status,
    CURRENT_TIMESTAMP AS check_time

UNION ALL

SELECT
    'recent_data' AS check_type,
    CASE
        WHEN COUNT(*) > 0 THEN 'healthy'
        ELSE 'warning'
    END AS status,
    CURRENT_TIMESTAMP AS check_time
FROM yawl.workflow_executions
WHERE processing_date = CURRENT_DATE;
```

### Performance Monitoring Views

```sql
-- Create monitoring view for DBA use
REPLACE VIEW yawl.v_monitor_performance AS
SELECT
    DATABASENAME,
    TABLENAME,
    SUM(CURRENTPERM) / 1024 / 1024 / 1024 AS size_gb,
    SUM(CURRENTPERM) / 1024 / 1024 / 1024 / NULLIF(COUNT(DISTINCT VPROC), 0)
        AS avg_skew_factor
FROM Dbc.TableSizeV
WHERE DATABASENAME = 'yawl'
GROUP BY DATABASENAME, TABLENAME;

-- Create view for query performance
REPLACE VIEW yawl.v_query_performance AS
SELECT
    QueryID,
    QueryText,
    StartTime,
    ElapseTime,
    TotalIOCount,
    TotalCPUTime,
    AMPCPUTime,
    ParserCPUTime,
    SpoolUsage
FROM Dbc.DBQLogTbl
WHERE DataBaseName = 'yawl'
  AND StartTime >= CURRENT_TIMESTAMP - INTERVAL '7' DAY;
```

### Alerting Configuration

```yaml
# prometheus-alerts.yaml
groups:
  - name: yawl-teradata
    rules:
      - alert: TeradataConnectionFailure
        expr: yawl_teradata_connection_errors_total > 5
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Teradata connection failures detected"
          description: "{{ $value }} connection errors in the last 5 minutes"

      - alert: TeradataSlowQueries
        expr: yawl_teradata_query_duration_seconds{quantile="0.95"} > 30
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Slow Teradata queries detected"
          description: "P95 query duration is {{ $value }}s"

      - alert: TeradataStorageHigh
        expr: yawl_teradata_storage_used_bytes / yawl_teradata_storage_total_bytes > 0.85
        for: 30m
        labels:
          severity: warning
        annotations:
          summary: "Teradata storage usage high"
          description: "Storage usage at {{ $value | humanizePercentage }}"
```

---

## Appendix: Complete Schema Reference

### Full Database Schema

```sql
-- Create YAWL database with proper sizing
CREATE DATABASE yawl
    AS PERMANENT = 100e9,   -- 100GB permanent space
       SPOOL = 200e9,       -- 200GB spool space
       DEFAULT MAP = TD_MAP1;

-- Create staging database
CREATE DATABASE yawl_staging
    AS PERMANENT = 50e9,
       SPOOL = 100e9,
       DEFAULT MAP = TD_MAP1;

-- Create analytics database
CREATE DATABASE yawl_analytics
    AS PERMANENT = 75e9,
       SPOOL = 150e9,
       DEFAULT MAP = TD_MAP1;

-- Grant appropriate permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON yawl TO yawl_app_user;
GRANT SELECT ON yawl_analytics TO yawl_readonly_user;
```

See `/marketplace/teradata/requirements.md` for complete schema definitions.
