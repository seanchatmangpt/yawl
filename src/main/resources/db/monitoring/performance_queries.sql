-- ============================================================================
-- YAWL Database Performance Monitoring Queries
-- Version: 1.0.0
-- Purpose: Real-time and historical performance analysis
-- ============================================================================

-- ============================================================================
-- CONNECTION POOL MONITORING (PostgreSQL)
-- ============================================================================

-- Current connection count by state
SELECT 
    state,
    COUNT(*) as connection_count,
    MAX(query_start) as oldest_query
FROM pg_stat_activity
WHERE datname = current_database()
GROUP BY state
ORDER BY connection_count DESC;

-- Long-running queries (over 5 seconds)
SELECT 
    pid,
    now() - pg_stat_activity.query_start AS duration,
    query,
    state,
    usename
FROM pg_stat_activity
WHERE (now() - pg_stat_activity.query_start) > interval '5 seconds'
  AND state != 'idle'
ORDER BY duration DESC;

-- Blocked queries
SELECT 
    blocked_locks.pid AS blocked_pid,
    blocked_activity.usename AS blocked_user,
    blocking_locks.pid AS blocking_pid,
    blocking_activity.usename AS blocking_user,
    blocked_activity.query AS blocked_statement,
    blocking_activity.query AS current_statement_in_blocking_process
FROM pg_catalog.pg_locks blocked_locks
    JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
    JOIN pg_catalog.pg_locks blocking_locks ON blocking_locks.locktype = blocked_locks.locktype
        AND blocking_locks.database IS NOT DISTINCT FROM blocked_locks.database
        AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation
        AND blocking_locks.page IS NOT DISTINCT FROM blocked_locks.page
        AND blocking_locks.tuple IS NOT DISTINCT FROM blocked_locks.tuple
        AND blocking_locks.virtualxid IS NOT DISTINCT FROM blocked_locks.virtualxid
        AND blocking_locks.transactionid IS NOT DISTINCT FROM blocked_locks.transactionid
        AND blocking_locks.classid IS NOT DISTINCT FROM blocked_locks.classid
        AND blocking_locks.objid IS NOT DISTINCT FROM blocked_locks.objid
        AND blocking_locks.objsubid IS NOT DISTINCT FROM blocked_locks.objsubid
        AND blocking_locks.pid != blocked_locks.pid
    JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted;

-- ============================================================================
-- TABLE STATISTICS
-- ============================================================================

-- Table size and row counts
SELECT 
    schemaname,
    relname AS table_name,
    n_live_tup AS row_count,
    n_dead_tup AS dead_rows,
    ROUND(100.0 * n_dead_tup / NULLIF(n_live_tup + n_dead_tup, 0), 2) AS dead_ratio_pct,
    last_vacuum,
    last_autovacuum,
    last_analyze,
    last_autoanalyze
FROM pg_stat_user_tables
ORDER BY n_live_tup DESC
LIMIT 20;

-- Index usage statistics
SELECT 
    schemaname,
    relname AS table_name,
    indexrelname AS index_name,
    idx_scan AS index_scans,
    idx_tup_read AS tuples_read,
    idx_tup_fetch AS tuples_fetched,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC
LIMIT 20;

-- Unused indexes (candidates for removal)
SELECT 
    schemaname || '.' || relname AS table,
    indexrelname AS index,
    pg_size_pretty(pg_relation_size(i.indexrelid)) AS index_size,
    idx_scan AS index_scans
FROM pg_stat_user_indexes ui
    JOIN pg_index i ON ui.indexrelid = i.indexrelid
WHERE NOT i.indisunique 
  AND idx_scan < 50 
  AND pg_relation_size(i.indexrelid) > 1024 * 1024
ORDER BY pg_relation_size(i.indexrelid) DESC;

-- ============================================================================
-- QUERY PERFORMANCE
-- ============================================================================

-- Slowest queries (requires pg_stat_statements extension)
/*
SELECT 
    query,
    calls,
    ROUND(total_exec_time::numeric, 2) AS total_time_ms,
    ROUND(mean_exec_time::numeric, 2) AS mean_time_ms,
    ROUND((100 * total_exec_time / SUM(total_exec_time) OVER ())::numeric, 2) AS percentage
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 20;
*/

-- Current query performance (H2 compatible)
SELECT 
    SESSION_ID,
    STATEMENT,
    START_TIME,
    DATEDIFF('MILLISECOND', START_TIME, CURRENT_TIMESTAMP) AS duration_ms
FROM INFORMATION_SCHEMA.SESSIONS
WHERE STATE IS NOT NULL
ORDER BY duration_ms DESC;

-- ============================================================================
-- YAWL-SPECIFIC MONITORING
-- ============================================================================

-- Work item status distribution
SELECT 
    status,
    COUNT(*) AS item_count,
    MIN(enablement_time) AS oldest_enabled,
    MAX(enablement_time) AS newest_enabled
FROM Work_Items
GROUP BY status
ORDER BY item_count DESC;

-- Active cases by specification
SELECT 
    specID,
    specVersion,
    COUNT(DISTINCT case_id) AS active_cases,
    COUNT(*) AS total_runners,
    MIN(startTime) AS oldest_case,
    MAX(startTime) AS newest_case
FROM RUNNER_STATES
WHERE executionStatus = 'Running'
GROUP BY specID, specVersion
ORDER BY active_cases DESC;

-- Log event growth rate (last 24 hours)
SELECT 
    DATE(TO_TIMESTAMP(eventTime / 1000.0)) AS event_date,
    HOUR(TO_TIMESTAMP(eventTime / 1000.0)) AS event_hour,
    COUNT(*) AS event_count
FROM logEvent
WHERE eventTime > EXTRACT(EPOCH FROM CURRENT_TIMESTAMP - INTERVAL '24 hours') * 1000
GROUP BY event_date, event_hour
ORDER BY event_date DESC, event_hour DESC;

-- Table growth estimates
SELECT 
    table_name,
    pg_size_pretty(pg_total_relation_size(quote_ident(table_name))) AS total_size,
    pg_size_pretty(pg_relation_size(quote_ident(table_name))) AS table_size,
    pg_size_pretty(pg_indexes_size(quote_ident(table_name))) AS indexes_size
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE'
ORDER BY pg_total_relation_size(quote_ident(table_name)) DESC
LIMIT 20;

-- ============================================================================
-- HEALTH CHECK QUERIES
-- ============================================================================

-- Simple connectivity check (should return in <50ms)
SELECT 1 AS health_check;

-- Transaction check
SELECT 
    COUNT(*) AS active_transactions,
    MAX(age(now(), xact_start)) AS oldest_transaction
FROM pg_stat_activity
WHERE state IN ('idle in transaction', 'active')
  AND xact_start IS NOT NULL;

-- Database bloat check
SELECT 
    schemaname,
    relname,
    n_dead_tup,
    n_live_tup,
    CASE WHEN n_live_tup > 0 
        THEN ROUND(100.0 * n_dead_tup / n_live_tup, 2)
        ELSE 0 
    END AS bloat_ratio
FROM pg_stat_user_tables
WHERE n_dead_tup > 1000
ORDER BY n_dead_tup DESC;
