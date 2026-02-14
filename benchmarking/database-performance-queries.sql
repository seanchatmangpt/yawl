-- ============================================================================
-- YAWL Database Performance Testing Queries
-- ============================================================================
-- This file contains SQL queries for benchmarking database performance
-- including execution time analysis, index effectiveness, and query optimization
--
-- Usage:
--   psql -U username -d database_name -f database-performance-queries.sql
--   mysql -u username -p database_name < database-performance-queries.sql
-- ============================================================================

-- ============================================================================
-- SECTION 1: Setup and Initial Configuration
-- ============================================================================

-- Enable query timing (PostgreSQL)
-- \timing on

-- Set timing on (MySQL)
-- SET SESSION profiling = 1;

-- ============================================================================
-- SECTION 2: Basic Query Performance Tests
-- ============================================================================

-- Test 2.1: Simple SELECT query
-- Purpose: Baseline single table query performance
-- Expected: < 100ms for typical datasets
EXPLAIN ANALYZE
SELECT * FROM resources LIMIT 100;

-- Test 2.2: SELECT with WHERE clause (indexed column)
-- Purpose: Test index performance on common filters
-- Expected: < 50ms with proper index
EXPLAIN ANALYZE
SELECT * FROM resources WHERE status = 'active' LIMIT 100;

-- Test 2.3: SELECT with WHERE clause (non-indexed column)
-- Purpose: Identify full table scans
-- Expected: May be slow without index
EXPLAIN ANALYZE
SELECT * FROM resources WHERE custom_field = 'value' LIMIT 100;

-- Test 2.4: SELECT with ORDER BY
-- Purpose: Test sorting performance
-- Expected: < 200ms with index
EXPLAIN ANALYZE
SELECT * FROM resources
ORDER BY created_at DESC
LIMIT 100;

-- Test 2.5: SELECT with OFFSET/LIMIT (pagination)
-- Purpose: Test pagination performance at various offsets
-- Expected: Degrades with large offsets
EXPLAIN ANALYZE
SELECT * FROM resources
ORDER BY id
LIMIT 100 OFFSET 1000;

-- Test 2.6: COUNT query
-- Purpose: Test aggregation performance
-- Expected: < 100ms
EXPLAIN ANALYZE
SELECT COUNT(*) FROM resources;

-- Test 2.7: COUNT with WHERE
-- Purpose: Test filtered aggregation
-- Expected: < 100ms
EXPLAIN ANALYZE
SELECT COUNT(*) FROM resources WHERE status = 'active';

-- ============================================================================
-- SECTION 3: Join Performance Tests
-- ============================================================================

-- Test 3.1: Simple INNER JOIN
-- Purpose: Test join performance
-- Expected: < 200ms
EXPLAIN ANALYZE
SELECT r.id, r.name, u.username
FROM resources r
INNER JOIN users u ON r.user_id = u.id
LIMIT 100;

-- Test 3.2: LEFT JOIN
-- Purpose: Test outer join performance
-- Expected: < 250ms
EXPLAIN ANALYZE
SELECT r.id, r.name, c.count
FROM resources r
LEFT JOIN (
  SELECT resource_id, COUNT(*) as count
  FROM comments
  GROUP BY resource_id
) c ON r.id = c.resource_id
LIMIT 100;

-- Test 3.3: Multiple JOINs
-- Purpose: Test complex join performance
-- Expected: < 500ms
EXPLAIN ANALYZE
SELECT r.id, r.name, u.username, a.action
FROM resources r
INNER JOIN users u ON r.user_id = u.id
LEFT JOIN audit_logs a ON r.id = a.resource_id
WHERE r.status = 'active'
LIMIT 100;

-- Test 3.4: Self JOIN
-- Purpose: Test self-referencing tables
-- Expected: < 300ms
EXPLAIN ANALYZE
SELECT r1.id, r1.name, r2.id as parent_id, r2.name as parent_name
FROM resources r1
LEFT JOIN resources r2 ON r1.parent_id = r2.id
LIMIT 100;

-- ============================================================================
-- SECTION 4: Aggregation and Grouping Tests
-- ============================================================================

-- Test 4.1: GROUP BY with COUNT
-- Purpose: Test grouping performance
-- Expected: < 200ms
EXPLAIN ANALYZE
SELECT status, COUNT(*) as count
FROM resources
GROUP BY status;

-- Test 4.2: GROUP BY with multiple aggregates
-- Purpose: Test multiple aggregate functions
-- Expected: < 300ms
EXPLAIN ANALYZE
SELECT
  status,
  COUNT(*) as total_count,
  AVG(views) as avg_views,
  MAX(created_at) as latest_created,
  MIN(created_at) as oldest_created
FROM resources
GROUP BY status;

-- Test 4.3: GROUP BY with HAVING
-- Purpose: Test filtering aggregations
-- Expected: < 250ms
EXPLAIN ANALYZE
SELECT status, COUNT(*) as count
FROM resources
GROUP BY status
HAVING COUNT(*) > 10;

-- Test 4.4: GROUP BY with JOIN
-- Purpose: Test aggregation with joins
-- Expected: < 400ms
EXPLAIN ANALYZE
SELECT u.username, COUNT(r.id) as resource_count
FROM users u
LEFT JOIN resources r ON u.id = r.user_id
GROUP BY u.id, u.username
ORDER BY resource_count DESC
LIMIT 100;

-- ============================================================================
-- SECTION 5: Subquery and Complex Query Tests
-- ============================================================================

-- Test 5.1: Subquery in WHERE clause
-- Purpose: Test subquery performance
-- Expected: < 300ms (depends on optimization)
EXPLAIN ANALYZE
SELECT * FROM resources
WHERE user_id IN (SELECT id FROM users WHERE role = 'admin')
LIMIT 100;

-- Test 5.2: Correlated subquery
-- Purpose: Test correlated subquery performance
-- Expected: May be slow, < 1000ms
EXPLAIN ANALYZE
SELECT r.id, r.name,
  (SELECT COUNT(*) FROM comments WHERE resource_id = r.id) as comment_count
FROM resources r
LIMIT 100;

-- Test 5.3: Subquery in FROM clause
-- Purpose: Test derived table performance
-- Expected: < 400ms
EXPLAIN ANALYZE
SELECT avg_data.status, avg_data.avg_views
FROM (
  SELECT status, AVG(views) as avg_views
  FROM resources
  GROUP BY status
) avg_data
WHERE avg_data.avg_views > 100;

-- Test 5.4: UNION queries
-- Purpose: Test set operations
-- Expected: < 300ms
EXPLAIN ANALYZE
SELECT id, name, 'resource' as type FROM resources
UNION
SELECT id, title, 'post' as type FROM posts
LIMIT 100;

-- Test 5.5: CTE (Common Table Expression)
-- Purpose: Test CTE performance
-- Expected: < 300ms
EXPLAIN ANALYZE
WITH active_resources AS (
  SELECT * FROM resources WHERE status = 'active'
)
SELECT ar.id, ar.name, COUNT(c.id) as comment_count
FROM active_resources ar
LEFT JOIN comments c ON ar.id = c.resource_id
GROUP BY ar.id, ar.name;

-- ============================================================================
-- SECTION 6: Index Effectiveness Tests
-- ============================================================================

-- Test 6.1: Query using primary key (should be very fast)
-- Expected: < 10ms
EXPLAIN ANALYZE
SELECT * FROM resources WHERE id = 12345;

-- Test 6.2: Query using single-column index
-- Expected: < 50ms
EXPLAIN ANALYZE
SELECT * FROM resources WHERE user_id = 100;

-- Test 6.3: Query using composite index
-- Expected: < 50ms
EXPLAIN ANALYZE
SELECT * FROM resources
WHERE user_id = 100 AND status = 'active';

-- Test 6.4: Full table scan (no index)
-- Purpose: Identify missing indexes
-- Expected: Much slower than indexed queries
EXPLAIN ANALYZE
SELECT * FROM resources
WHERE description LIKE '%test%' LIMIT 100;

-- Test 6.5: Index with range query
-- Expected: < 100ms
EXPLAIN ANALYZE
SELECT * FROM resources
WHERE created_at BETWEEN '2024-01-01' AND '2024-01-31'
LIMIT 100;

-- ============================================================================
-- SECTION 7: Data Type and Conversion Performance
-- ============================================================================

-- Test 7.1: String comparison (case-sensitive)
-- Expected: < 100ms
EXPLAIN ANALYZE
SELECT * FROM resources WHERE LOWER(name) = 'test resource' LIMIT 100;

-- Test 7.2: Date comparison
-- Expected: < 100ms
EXPLAIN ANALYZE
SELECT * FROM resources
WHERE DATE(created_at) = CURRENT_DATE
LIMIT 100;

-- Test 7.3: Type conversion performance
-- Expected: < 150ms
EXPLAIN ANALYZE
SELECT * FROM resources
WHERE CAST(metadata ->> 'priority' AS INTEGER) > 5
LIMIT 100;

-- ============================================================================
-- SECTION 8: JSON and Advanced Data Type Performance
-- ============================================================================

-- Test 8.1: JSON field extraction (PostgreSQL)
-- Expected: < 150ms
EXPLAIN ANALYZE
SELECT * FROM resources
WHERE metadata ->> 'category' = 'technology'
LIMIT 100;

-- Test 8.2: JSON array contains (PostgreSQL)
-- Expected: < 200ms
EXPLAIN ANALYZE
SELECT * FROM resources
WHERE metadata -> 'tags' @> '["performance"]'
LIMIT 100;

-- Test 8.3: JSON field aggregation
-- Expected: < 300ms
EXPLAIN ANALYZE
SELECT metadata ->> 'category', COUNT(*)
FROM resources
GROUP BY metadata ->> 'category';

-- ============================================================================
-- SECTION 9: Concurrent Query Performance Tests
-- ============================================================================

-- Test 9.1: Simulate 10 concurrent SELECT queries
-- Run each query in a separate connection:
SELECT * FROM resources LIMIT 100;
SELECT * FROM resources WHERE status = 'active' LIMIT 100;
SELECT COUNT(*) FROM resources;
SELECT * FROM users LIMIT 100;
SELECT * FROM comments LIMIT 100;
SELECT * FROM resources ORDER BY created_at DESC LIMIT 100;
SELECT * FROM audit_logs LIMIT 100;
SELECT COUNT(*) FROM comments GROUP BY resource_id;
SELECT * FROM resources WHERE user_id IN (1,2,3,4,5);
SELECT * FROM resources LIMIT 50 OFFSET 1000;

-- ============================================================================
-- SECTION 10: Write Performance Tests
-- ============================================================================

-- Test 10.1: Single INSERT (prepare test data)
-- Expected: < 50ms
-- NOTE: Adjust table and columns as needed
EXPLAIN ANALYZE
INSERT INTO resources (name, description, status, user_id, created_at)
VALUES ('Test Resource', 'Performance Test', 'active', 1, NOW());

-- Test 10.2: Bulk INSERT (1000 rows)
-- Expected: < 500ms
-- Note: Uncomment and adjust as needed
/*
INSERT INTO resources (name, description, status, user_id, created_at)
SELECT
  'Test Resource ' || seq,
  'Bulk insert test',
  'active',
  1,
  NOW()
FROM generate_series(1, 1000) as seq;
*/

-- Test 10.3: UPDATE with WHERE clause
-- Expected: < 100ms
EXPLAIN ANALYZE
UPDATE resources
SET status = 'inactive'
WHERE created_at < NOW() - INTERVAL '30 days'
LIMIT 100;

-- Test 10.4: DELETE operation
-- Expected: < 100ms
-- WARNING: This will delete data - comment out unless needed
/*
EXPLAIN ANALYZE
DELETE FROM resources
WHERE status = 'deleted'
LIMIT 100;
*/

-- ============================================================================
-- SECTION 11: Lock and Contention Tests
-- ============================================================================

-- Test 11.1: Check for table locks
-- Purpose: Identify locking issues
SELECT * FROM pg_locks;  -- PostgreSQL
-- SHOW OPEN TABLES;  -- MySQL

-- Test 11.2: Check for long-running queries
-- Purpose: Identify slow queries
SELECT * FROM pg_stat_activity WHERE state = 'active';  -- PostgreSQL
-- SELECT * FROM information_schema.processlist WHERE state != 'Sleep';  -- MySQL

-- ============================================================================
-- SECTION 12: Table Statistics and Health Checks
-- ============================================================================

-- Test 12.1: Get table size information
-- Purpose: Understand storage requirements
SELECT
  schemaname,
  tablename,
  pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- Test 12.2: Analyze table distribution
-- Purpose: Identify data skew
SELECT
  status,
  COUNT(*) as count,
  ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2) as percentage
FROM resources
GROUP BY status;

-- Test 12.3: Check index usage
-- Purpose: Identify unused indexes
SELECT
  schemaname,
  tablename,
  indexname,
  idx_scan,
  idx_tup_read,
  idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;

-- ============================================================================
-- SECTION 13: Query Optimization Recommendations
-- ============================================================================

-- Recommendation 1: Add missing indexes
/*
CREATE INDEX idx_resources_status ON resources(status);
CREATE INDEX idx_resources_user_id ON resources(user_id);
CREATE INDEX idx_resources_created_at ON resources(created_at);
CREATE INDEX idx_resources_user_status ON resources(user_id, status);
*/

-- Recommendation 2: Analyze tables (update statistics)
-- PostgreSQL
ANALYZE resources;
ANALYZE users;
ANALYZE comments;
ANALYZE audit_logs;

-- MySQL: ANALYZE TABLE resources;

-- Recommendation 3: Reindex tables (optimize indexes)
-- PostgreSQL: REINDEX TABLE resources;
-- MySQL: OPTIMIZE TABLE resources;

-- ============================================================================
-- SECTION 14: Performance Monitoring Queries
-- ============================================================================

-- Test 14.1: Monitor database connections
-- Purpose: Check connection pool utilization
SELECT count(*) as total_connections FROM pg_stat_activity;

-- Test 14.2: Monitor cache hit ratio
-- Purpose: Optimize buffer pool settings
SELECT
  sum(heap_blks_read) as heap_read,
  sum(heap_blks_hit) as heap_hit,
  sum(heap_blks_hit) / (sum(heap_blks_hit) + sum(heap_blks_read)) as ratio
FROM pg_statio_user_tables;

-- Test 14.3: Slow query detection
-- Purpose: Find slow queries to optimize
SELECT
  query,
  calls,
  total_time,
  mean_time,
  max_time
FROM pg_stat_statements
ORDER BY mean_time DESC
LIMIT 10;

-- ============================================================================
-- SECTION 15: Stress Test Queries
-- ============================================================================

-- Stress Test: Heavy JOIN with large result set
-- Purpose: Test system under heavy load
-- WARNING: May consume significant resources
EXPLAIN ANALYZE
SELECT r.id, r.name, u.username, c.content, COUNT(DISTINCT a.id) as action_count
FROM resources r
INNER JOIN users u ON r.user_id = u.id
LEFT JOIN comments c ON r.id = c.resource_id
LEFT JOIN audit_logs a ON r.id = a.resource_id
WHERE r.status = 'active'
GROUP BY r.id, r.name, u.id, u.username, c.id, c.content
ORDER BY action_count DESC
LIMIT 1000;

-- ============================================================================
-- SECTION 16: Cleanup and Summary
-- ============================================================================

-- Summary: List all indexes
SELECT tablename, indexname, indexdef FROM pg_indexes WHERE schemaname = 'public';

-- Summary: Check table and index sizes
SELECT
  tablename,
  pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as total_size,
  pg_size_pretty(pg_relation_size(schemaname||'.'||tablename)) as table_size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- ============================================================================
-- END OF DATABASE PERFORMANCE TESTING QUERIES
-- ============================================================================
--
-- Notes for Performance Analysis:
-- 1. Run EXPLAIN ANALYZE on queries to see actual execution plans
-- 2. Compare Seq Scan vs Index Scan usage
-- 3. Look for table joins and filter efficiency
-- 4. Monitor execution time trends
-- 5. Create indexes for frequently filtered columns
-- 6. Archive old data to maintain table size
-- 7. Regular ANALYZE and VACUUM operations
-- 8. Use connection pooling for concurrent access
-- 9. Monitor slow query logs
-- 10. Consider query caching for frequently accessed data
-- ============================================================================
