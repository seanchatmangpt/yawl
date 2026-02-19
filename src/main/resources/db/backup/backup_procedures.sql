-- ============================================================================
-- YAWL Database Backup Procedures
-- Version: 1.0.0
-- Purpose: Automated backup verification and recovery procedures
-- ============================================================================

-- ============================================================================
-- BACKUP VERIFICATION QUERIES
-- ============================================================================

-- Check backup consistency - row counts should match
SELECT 
    'Work_Items' AS table_name,
    (SELECT COUNT(*) FROM Work_Items) AS row_count
UNION ALL
SELECT 
    'RUNNER_STATES',
    (SELECT COUNT(*) FROM RUNNER_STATES)
UNION ALL
SELECT 
    'logEvent',
    (SELECT COUNT(*) FROM logEvent)
UNION ALL
SELECT 
    'logTaskInstance',
    (SELECT COUNT(*) FROM logTaskInstance)
UNION ALL
SELECT 
    'auditEvent',
    (SELECT COUNT(*) FROM auditEvent);

-- Check referential integrity
SELECT 
    wi.thisid AS orphan_workitem,
    'No matching runner' AS issue
FROM Work_Items wi
WHERE NOT EXISTS (
    SELECT 1 FROM RUNNER_STATES rs 
    WHERE rs.case_id = wi._specID.identifier
)
AND wi.parent IS NULL;

-- Checksum verification for critical tables
SELECT 
    MD5(STRING_AGG(
        thisid || status || COALESCE(enablement_time::TEXT, ''), 
        ',' ORDER BY thisid
    )) AS work_items_checksum
FROM Work_Items;

-- ============================================================================
-- POINT-IN-TIME RECOVERY MARKERS
-- ============================================================================

-- Create a recovery point marker table
CREATE TABLE IF NOT EXISTS recovery_markers (
    marker_id SERIAL PRIMARY KEY,
    marker_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    checkpoint_data JSONB,
    description TEXT
);

-- Create a recovery marker
CREATE OR REPLACE FUNCTION create_recovery_marker(
    p_name VARCHAR(255,
    p_description TEXT DEFAULT NULL
) RETURNS INT AS $$
DECLARE
    v_marker_id INT;
    v_checkpoint JSONB;
BEGIN
    -- Capture current state
    SELECT jsonb_build_object(
        'work_items_count', (SELECT COUNT(*) FROM Work_Items),
        'runners_count', (SELECT COUNT(*) FROM RUNNER_STATES),
        'log_events_count', (SELECT COUNT(*) FROM logEvent),
        'timestamp', EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)
    ) INTO v_checkpoint;
    
    INSERT INTO recovery_markers (marker_name, checkpoint_data, description)
    VALUES (p_name, v_checkpoint, p_description)
    RETURNING marker_id INTO v_marker_id;
    
    RETURN v_marker_id;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- BACKUP STATUS TRACKING
-- ============================================================================

CREATE TABLE IF NOT EXISTS backup_history (
    backup_id SERIAL PRIMARY KEY,
    backup_type VARCHAR(50) NOT NULL, -- 'full', 'incremental', 'archive'
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL, -- 'running', 'completed', 'failed'
    size_bytes BIGINT,
    location TEXT,
    checksum VARCHAR(64),
    error_message TEXT
);

-- Record backup start
CREATE OR REPLACE FUNCTION start_backup(
    p_type VARCHAR(50,
    p_location TEXT
) RETURNS INT AS $$
DECLARE
    v_backup_id INT;
BEGIN
    INSERT INTO backup_history (backup_type, started_at, status, location)
    VALUES (p_type, CURRENT_TIMESTAMP, 'running', p_location)
    RETURNING backup_id INTO v_backup_id;
    
    RETURN v_backup_id;
END;
$$ LANGUAGE plpgsql;

-- Record backup completion
CREATE OR REPLACE FUNCTION complete_backup(
    p_backup_id INT,
    p_size_bytes BIGINT,
    p_checksum VARCHAR(64)
) RETURNS VOID AS $$
BEGIN
    UPDATE backup_history
    SET completed_at = CURRENT_TIMESTAMP,
        status = 'completed',
        size_bytes = p_size_bytes,
        checksum = p_checksum
    WHERE backup_id = p_backup_id;
END;
$$ LANGUAGE plpgsql;

-- Record backup failure
CREATE OR REPLACE FUNCTION fail_backup(
    p_backup_id INT,
    p_error TEXT
) RETURNS VOID AS $$
BEGIN
    UPDATE backup_history
    SET completed_at = CURRENT_TIMESTAMP,
        status = 'failed',
        error_message = p_error
    WHERE backup_id = p_backup_id;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- RESTORE VERIFICATION
-- ============================================================================

CREATE OR REPLACE FUNCTION verify_restore(
    p_expected_work_items INT DEFAULT NULL,
    p_expected_runners INT DEFAULT NULL,
    p_expected_log_events INT DEFAULT NULL
) RETURNS TABLE(
    check_name TEXT,
    expected BIGINT,
    actual BIGINT,
    status TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        'work_items'::TEXT,
        p_expected_work_items::BIGINT,
        (SELECT COUNT(*) FROM Work_Items),
        CASE 
            WHEN p_expected_work_items IS NULL THEN 'skipped'
            WHEN (SELECT COUNT(*) FROM Work_Items) >= p_expected_work_items THEN 'pass'
            ELSE 'fail'
        END
    UNION ALL
    SELECT 
        'runners'::TEXT,
        p_expected_runners::BIGINT,
        (SELECT COUNT(*) FROM RUNNER_STATES),
        CASE 
            WHEN p_expected_runners IS NULL THEN 'skipped'
            WHEN (SELECT COUNT(*) FROM RUNNER_STATES) >= p_expected_runners THEN 'pass'
            ELSE 'fail'
        END
    UNION ALL
    SELECT 
        'log_events'::TEXT,
        p_expected_log_events::BIGINT,
        (SELECT COUNT(*) FROM logEvent),
        CASE 
            WHEN p_expected_log_events IS NULL THEN 'skipped'
            WHEN (SELECT COUNT(*) FROM logEvent) >= p_expected_log_events THEN 'pass'
            ELSE 'fail'
        END;
END;
$$ LANGUAGE plpgsql;
