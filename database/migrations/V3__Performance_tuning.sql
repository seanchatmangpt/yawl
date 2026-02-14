-- YAWL Database Schema - Performance Tuning
-- Version: 3.0.0
-- Materialized views, partitioning, and performance optimizations

-- =============================================================================
-- MATERIALIZED VIEWS
-- =============================================================================

-- Active work items summary view
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_active_work_items AS
SELECT
    wi.work_item_id,
    wi.task_id,
    wi.case_id,
    wi.spec_id,
    wi.resource_id,
    wi.resource_type,
    wi.status,
    wi.priority,
    wi.created_at,
    wi.started_at,
    t.task_name,
    t.task_type,
    ni.spec_id AS specification_id,
    p.full_name AS resource_name,
    p.email AS resource_email
FROM work_items wi
JOIN tasks t ON wi.task_id = t.task_id
JOIN net_instances ni ON wi.case_id = ni.case_id
LEFT JOIN participants p ON wi.resource_id = p.participant_id
WHERE wi.status IN ('ENABLED', 'OFFERED', 'ALLOCATED', 'STARTED')
ORDER BY wi.priority DESC, wi.created_at ASC;

-- Refresh index for materialized view
CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_active_work_items_id ON mv_active_work_items(work_item_id);

-- Case statistics view
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_case_statistics AS
SELECT
    ni.spec_id,
    ni.status,
    COUNT(*) AS case_count,
    AVG(EXTRACT(EPOCH FROM (COALESCE(ni.completed_at, CURRENT_TIMESTAMP) - ni.started_at))/3600) AS avg_duration_hours,
    MIN(ni.started_at) AS earliest_start,
    MAX(COALESCE(ni.completed_at, CURRENT_TIMESTAMP)) AS latest_activity,
    COUNT(CASE WHEN ni.status = 'RUNNING' THEN 1 END) AS running_count,
    COUNT(CASE WHEN ni.status = 'COMPLETED' THEN 1 END) AS completed_count,
    COUNT(CASE WHEN ni.status = 'FAILED' THEN 1 END) AS failed_count
FROM net_instances ni
GROUP BY ni.spec_id, ni.status;

-- Resource workload view
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_resource_workload AS
SELECT
    wi.resource_id,
    p.full_name AS resource_name,
    p.email,
    COUNT(*) AS total_work_items,
    COUNT(CASE WHEN wi.status = 'STARTED' THEN 1 END) AS active_items,
    COUNT(CASE WHEN wi.status IN ('ENABLED', 'OFFERED') THEN 1 END) AS pending_items,
    AVG(wi.priority) AS avg_priority,
    MIN(wi.created_at) AS oldest_item_created
FROM work_items wi
LEFT JOIN participants p ON wi.resource_id = p.participant_id
WHERE wi.status IN ('ENABLED', 'OFFERED', 'ALLOCATED', 'STARTED')
GROUP BY wi.resource_id, p.full_name, p.email;

-- Process performance metrics view
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_process_metrics AS
SELECT
    ni.spec_id,
    s.spec_name,
    COUNT(DISTINCT ni.case_id) AS total_cases,
    COUNT(DISTINCT t.task_id) AS total_tasks,
    AVG(EXTRACT(EPOCH FROM (t.completed_at - t.started_at))) AS avg_task_duration_seconds,
    AVG(t.retry_count) AS avg_retry_count,
    SUM(CASE WHEN t.status = 'FAILED' THEN 1 ELSE 0 END) AS failed_tasks,
    SUM(CASE WHEN wi.status = 'SUSPENDED' THEN 1 ELSE 0 END) AS suspended_items
FROM net_instances ni
JOIN specifications s ON ni.spec_id = s.spec_id
LEFT JOIN tasks t ON ni.instance_id = t.instance_id
LEFT JOIN work_items wi ON t.task_id = wi.task_id
GROUP BY ni.spec_id, s.spec_name;

-- =============================================================================
-- PARTITIONING (PostgreSQL specific)
-- =============================================================================

-- Note: These commands are for PostgreSQL. For other databases, use equivalent partitioning syntax

-- Partition audit_log by month (for large-scale deployments)
-- First, rename existing table and create partitioned version
-- This is commented out as it requires data migration
/*
ALTER TABLE audit_log RENAME TO audit_log_legacy;

CREATE TABLE audit_log (
    log_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(255),
    resource_type VARCHAR(100),
    resource_id VARCHAR(255),
    action VARCHAR(100) NOT NULL,
    old_value JSON,
    new_value JSON,
    ip_address VARCHAR(50),
    user_agent VARCHAR(512),
    session_id VARCHAR(255),
    PRIMARY KEY (log_id, event_timestamp)
) PARTITION BY RANGE (event_timestamp);

-- Create partitions for the next 12 months
CREATE TABLE audit_log_2024_01 PARTITION OF audit_log
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
CREATE TABLE audit_log_2024_02 PARTITION OF audit_log
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
-- Add more partitions as needed
*/

-- Partition event_log by month
/*
ALTER TABLE event_log RENAME TO event_log_legacy;

CREATE TABLE event_log (
    event_id VARCHAR(255) NOT NULL,
    case_id VARCHAR(255) NOT NULL,
    activity VARCHAR(255) NOT NULL,
    resource VARCHAR(255),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_type VARCHAR(50) NOT NULL,
    lifecycle_transition VARCHAR(50),
    data_attributes JSON,
    process_id VARCHAR(255),
    PRIMARY KEY (event_id, timestamp)
) PARTITION BY RANGE (timestamp);
*/

-- =============================================================================
-- STORED PROCEDURES
-- =============================================================================

-- Procedure to refresh materialized views
CREATE OR REPLACE PROCEDURE refresh_materialized_views()
LANGUAGE plpgsql
AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_active_work_items;
    REFRESH MATERIALIZED VIEW mv_case_statistics;
    REFRESH MATERIALIZED VIEW mv_resource_workload;
    REFRESH MATERIALIZED VIEW mv_process_metrics;
END;
$$;

-- Procedure to archive old audit logs
CREATE OR REPLACE PROCEDURE archive_audit_logs(archive_before_date TIMESTAMP)
LANGUAGE plpgsql
AS $$
DECLARE
    archived_count INTEGER;
BEGIN
    -- Move old audit logs to archive table (create if not exists)
    -- In production, this would move to cold storage or S3

    -- Create archive table if not exists
    CREATE TABLE IF NOT EXISTS audit_log_archive (LIKE audit_log INCLUDING INDEXES);

    -- Insert old records
    INSERT INTO audit_log_archive
    SELECT * FROM audit_log WHERE event_timestamp < archive_before_date;

    GET DIAGNOSTICS archived_count = ROW_COUNT;

    -- Delete archived records from main table
    DELETE FROM audit_log WHERE event_timestamp < archive_before_date;

    RAISE NOTICE 'Archived % audit log records', archived_count;

    COMMIT;
END;
$$;

-- Procedure to cleanup completed cases
CREATE OR REPLACE PROCEDURE cleanup_completed_cases(
    retention_days INTEGER DEFAULT 365,
    batch_size INTEGER DEFAULT 1000
)
LANGUAGE plpgsql
AS $$
DECLARE
    cutoff_date TIMESTAMP;
    deleted_count INTEGER := 0;
    batch_deleted INTEGER;
BEGIN
    cutoff_date := CURRENT_TIMESTAMP - (retention_days || ' days')::INTERVAL;

    LOOP
        -- Delete in batches to avoid long locks
        DELETE FROM queue_items
        WHERE work_item_id IN (
            SELECT wi.work_item_id
            FROM work_items wi
            JOIN net_instances ni ON wi.case_id = ni.case_id
            WHERE ni.status = 'COMPLETED'
            AND ni.completed_at < cutoff_date
            LIMIT batch_size
        );

        GET DIAGNOSTICS batch_deleted = ROW_COUNT;
        deleted_count := deleted_count + batch_deleted;

        EXIT WHEN batch_deleted = 0;
        COMMIT;
    END LOOP;

    RAISE NOTICE 'Deleted % queue items for completed cases', deleted_count;
END;
$$;

-- =============================================================================
-- FUNCTIONS
-- =============================================================================

-- Function to calculate case duration
CREATE OR REPLACE FUNCTION calculate_case_duration(p_case_id VARCHAR)
RETURNS INTERVAL
LANGUAGE plpgsql
AS $$
DECLARE
    v_started_at TIMESTAMP;
    v_completed_at TIMESTAMP;
BEGIN
    SELECT started_at, completed_at INTO v_started_at, v_completed_at
    FROM net_instances WHERE case_id = p_case_id;

    IF v_completed_at IS NULL THEN
        RETURN CURRENT_TIMESTAMP - v_started_at;
    ELSE
        RETURN v_completed_at - v_started_at;
    END IF;
END;
$$;

-- Function to get resource availability score
CREATE OR REPLACE FUNCTION get_resource_availability(p_resource_id VARCHAR)
RETURNS DECIMAL(5,2)
LANGUAGE plpgsql
AS $$
DECLARE
    v_active_items INTEGER;
    v_avg_completion_time DECIMAL(10,2);
    v_availability_score DECIMAL(5,2);
BEGIN
    -- Count active work items
    SELECT COUNT(*) INTO v_active_items
    FROM work_items
    WHERE resource_id = p_resource_id
    AND status IN ('STARTED', 'ALLOCATED');

    -- Get average completion time in hours
    SELECT AVG(EXTRACT(EPOCH FROM (completed_at - started_at))/3600)
    INTO v_avg_completion_time
    FROM work_items
    WHERE resource_id = p_resource_id
    AND completed_at IS NOT NULL
    AND completed_at > CURRENT_TIMESTAMP - INTERVAL '30 days';

    -- Calculate availability score (higher is better)
    -- Formula: 100 - (active_items * 10) + efficiency_bonus
    v_availability_score := 100.0 - (v_active_items * 10.0);

    -- Bonus for fast completion times
    IF v_avg_completion_time IS NOT NULL AND v_avg_completion_time < 1 THEN
        v_availability_score := v_availability_score + 10;
    ELSIF v_avg_completion_time IS NOT NULL AND v_avg_completion_time < 4 THEN
        v_availability_score := v_availability_score + 5;
    END IF;

    RETURN GREATEST(0, LEAST(100, v_availability_score));
END;
$$;

-- Function to auto-assign work items
CREATE OR REPLACE FUNCTION auto_assign_work_item(
    p_work_item_id VARCHAR,
    p_strategy VARCHAR DEFAULT 'ROUND_ROBIN'
)
RETURNS VARCHAR
LANGUAGE plpgsql
AS $$
DECLARE
    v_resource_id VARCHAR;
    v_available_resources CURSOR(p_spec_id VARCHAR) FOR
        SELECT r.resource_id, get_resource_availability(r.resource_id) AS availability
        FROM (
            SELECT participant_id AS resource_id
            FROM participant_roles pr
            JOIN roles r ON pr.role_id = r.role_id
            WHERE r.role_name IN ('ROLE_USER', 'ROLE_MANAGER')
            UNION
            SELECT capability_id AS resource_id
            FROM capabilities
            WHERE status = 'ACTIVE'
        ) r
        ORDER BY
            CASE p_strategy
                WHEN 'LEAST_LOADED' THEN get_resource_availability(r.resource_id)
                ELSE RANDOM()
            END DESC
        LIMIT 1;
    v_spec_id VARCHAR;
BEGIN
    -- Get specification for the work item
    SELECT spec_id INTO v_spec_id
    FROM work_items WHERE work_item_id = p_work_item_id;

    -- Find best resource based on strategy
    OPEN v_available_resources(v_spec_id);
    FETCH v_available_resources INTO v_resource_id;
    CLOSE v_available_resources;

    -- Assign the work item
    IF v_resource_id IS NOT NULL THEN
        UPDATE work_items
        SET resource_id = v_resource_id,
            status = 'ALLOCATED',
            started_at = CURRENT_TIMESTAMP
        WHERE work_item_id = p_work_item_id;

        RETURN v_resource_id;
    END IF;

    RETURN NULL;
END;
$$;

-- =============================================================================
-- TRIGGERS
-- =============================================================================

-- Trigger function to update timestamps
CREATE OR REPLACE FUNCTION update_timestamp()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

-- Apply trigger to specifications
DROP TRIGGER IF EXISTS trigger_specifications_timestamp ON specifications;
CREATE TRIGGER trigger_specifications_timestamp
    BEFORE UPDATE ON specifications
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- Apply trigger to worklet_rules
DROP TRIGGER IF EXISTS trigger_worklet_rules_timestamp ON worklet_rules;
CREATE TRIGGER trigger_worklet_rules_timestamp
    BEFORE UPDATE ON worklet_rules
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- Trigger function to log audit events
CREATE OR REPLACE FUNCTION log_audit_event()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO audit_log (
        log_id,
        event_type,
        user_id,
        resource_type,
        resource_id,
        action,
        old_value,
        new_value
    ) VALUES (
        'LOG_' || REPLACE(GEN_RANDOM_UUID()::TEXT, '-', ''),
        TG_ARGV[0], -- event type passed as argument
        CURRENT_SETTING('app.current_user', TRUE),
        TG_TABLE_NAME,
        COALESCE(NEW.spec_id, NEW.case_id, NEW.task_id, NEW.work_item_id, NEW.participant_id),
        TG_OP,
        CASE WHEN TG_OP = 'UPDATE' THEN row_to_json(OLD) ELSE NULL END,
        CASE WHEN TG_OP IN ('INSERT', 'UPDATE') THEN row_to_json(NEW) ELSE NULL END
    );
    RETURN NEW;
END;
$$;

-- Apply audit trigger to critical tables
DROP TRIGGER IF EXISTS trigger_net_instances_audit ON net_instances;
CREATE TRIGGER trigger_net_instances_audit
    AFTER INSERT OR UPDATE OR DELETE ON net_instances
    FOR EACH ROW
    EXECUTE FUNCTION log_audit_event('CASE_EVENT');

DROP TRIGGER IF EXISTS trigger_work_items_audit ON work_items;
CREATE TRIGGER trigger_work_items_audit
    AFTER INSERT OR UPDATE OR DELETE ON work_items
    FOR EACH ROW
    EXECUTE FUNCTION log_audit_event('WORK_ITEM_EVENT');

-- =============================================================================
-- PERFORMANCE HINTS AND TABLE STATISTICS
-- =============================================================================

-- Analyze tables for query planner optimization
ANALYZE specifications;
ANALYZE net_instances;
ANALYZE tasks;
ANALYZE work_items;
ANALYZE participants;
ANALYZE audit_log;
ANALYZE event_log;

-- Set statistics target for important columns
ALTER TABLE net_instances ALTER COLUMN case_id SET STATISTICS 500;
ALTER TABLE work_items ALTER COLUMN resource_id SET STATISTICS 500;
ALTER TABLE audit_log ALTER COLUMN event_type SET STATISTICS 300;

-- =============================================================================
-- TABLESPACE CONFIGURATION (PostgreSQL)
-- =============================================================================

-- Note: These require appropriate tablespace setup by DBA
-- Example for separating hot and cold data:

-- CREATE TABLESPACE ts_hot LOCATION '/mnt/fast_ssd/hot_data';
-- CREATE TABLESPACE ts_cold LOCATION '/mnt/slow_hdd/cold_data';

-- ALTER TABLE audit_log SET TABLESPACE ts_cold;
-- ALTER TABLE event_log SET TABLESPACE ts_cold;
-- ALTER TABLE net_instances SET TABLESPACE ts_hot;
-- ALTER TABLE work_items SET TABLESPACE ts_hot;
