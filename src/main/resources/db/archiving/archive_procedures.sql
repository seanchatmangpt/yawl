-- ============================================================================
-- YAWL Data Archiving Procedures
-- Version: 1.0.0
-- Purpose: Archive old log data while maintaining queryability
-- Target: Keep production tables under 10M rows for optimal performance
-- ============================================================================

-- ============================================================================
-- ARCHIVING CONFIGURATION
-- ============================================================================

-- Default retention periods (in days)
-- logEvent: 90 days active, then archive
-- logTaskInstance: 90 days active, then archive  
-- auditEvent: 365 days active, then archive
-- Work items (completed): 30 days active, then archive

-- ============================================================================
-- LOG EVENT ARCHIVING
-- ============================================================================

-- Archive log events older than retention period
CREATE PROCEDURE IF NOT EXISTS archive_log_events(
    IN retention_days INT DEFAULT 90,
    IN batch_size INT DEFAULT 10000,
    IN dry_run BOOLEAN DEFAULT FALSE
)
LANGUAGE SQL
AS $$
DECLARE
    cutoff_epoch BIGINT;
    archived_count INT;
    total_archived INT := 0;
BEGIN
    -- Calculate cutoff timestamp (milliseconds since epoch)
    cutoff_epoch := (EXTRACT(EPOCH FROM CURRENT_TIMESTAMP - (retention_days || ' days')::INTERVAL) * 1000)::BIGINT;
    
    -- Process in batches to avoid long locks
    WHILE TRUE DO
        -- Get batch to archive
        archived_count := 0;
        
        IF NOT dry_run THEN
            -- Insert into archive table
            INSERT INTO logEvent_archive (
                eventID, descriptor, eventTime, instanceID, 
                serviceID, rootNetInstanceID, archived_at
            )
            SELECT eventID, descriptor, eventTime, instanceID,
                   serviceID, rootNetInstanceID, CURRENT_TIMESTAMP
            FROM logEvent
            WHERE eventTime < cutoff_epoch
              AND eventID IN (
                  SELECT eventID FROM logEvent 
                  WHERE eventTime < cutoff_epoch 
                  LIMIT batch_size
              )
            ON CONFLICT (eventID) DO NOTHING;
            
            GET DIAGNOSTICS archived_count = ROW_COUNT;
            
            -- Delete archived records from main table
            DELETE FROM logEvent
            WHERE eventTime < cutoff_epoch
              AND eventID IN (
                  SELECT eventID FROM logEvent_archive
                  WHERE archived_at >= CURRENT_TIMESTAMP - INTERVAL '1 minute'
              );
              
            total_archived := total_archived + archived_count;
        ELSE
            -- Dry run: just count
            SELECT COUNT(*) INTO archived_count
            FROM logEvent
            WHERE eventTime < cutoff_epoch
            LIMIT batch_size;
            
            total_archived := total_archived + archived_count;
        END IF;
        
        -- Exit if no more records to process
        IF archived_count = 0 OR archived_count < batch_size THEN
            EXIT;
        END IF;
        
        -- Brief pause between batches to reduce load
        -- CALL pg_sleep(0.1);
    END LOOP;
    
    RAISE NOTICE 'Archived % log events (dry_run=%)', total_archived, dry_run;
END;
$$;

-- ============================================================================
-- TASK INSTANCE ARCHIVING
-- ============================================================================

CREATE PROCEDURE IF NOT EXISTS archive_task_instances(
    IN retention_days INT DEFAULT 90,
    IN batch_size INT DEFAULT 10000,
    IN dry_run BOOLEAN DEFAULT FALSE
)
LANGUAGE SQL
AS $$
DECLARE
    archived_count INT;
    total_archived INT := 0;
    cutoff_time BIGINT;
BEGIN
    cutoff_time := (EXTRACT(EPOCH FROM CURRENT_TIMESTAMP - (retention_days || ' days')::INTERVAL) * 1000)::BIGINT;
    
    -- Archive task instances whose parent events have been archived
    IF NOT dry_run THEN
        INSERT INTO logTaskInstance_archive (
            taskInstanceID, engineInstanceID, taskID,
            parentNetInstanceID, parentTaskInstanceID, archived_at
        )
        SELECT ti.taskInstanceID, ti.engineInstanceID, ti.taskID,
               ti.parentNetInstanceID, ti.parentTaskInstanceID, CURRENT_TIMESTAMP
        FROM logTaskInstance ti
        WHERE NOT EXISTS (
            SELECT 1 FROM logEvent le 
            WHERE le.instanceID = ti.taskInstanceID 
              AND le.eventTime >= cutoff_time
        )
        LIMIT batch_size
        ON CONFLICT (taskInstanceID) DO NOTHING;
        
        GET DIAGNOSTICS archived_count = ROW_COUNT;
        
        DELETE FROM logTaskInstance
        WHERE taskInstanceID IN (
            SELECT taskInstanceID FROM logTaskInstance_archive
            WHERE archived_at >= CURRENT_TIMESTAMP - INTERVAL '1 minute'
        );
        
        total_archived := total_archived + archived_count;
    END IF;
    
    RAISE NOTICE 'Archived % task instances', total_archived;
END;
$$;

-- ============================================================================
-- AUDIT EVENT ARCHIVING
-- ============================================================================

CREATE PROCEDURE IF NOT EXISTS archive_audit_events(
    IN retention_days INT DEFAULT 365,
    IN batch_size INT DEFAULT 10000,
    IN dry_run BOOLEAN DEFAULT FALSE
)
LANGUAGE SQL
AS $$
DECLARE
    cutoff_time BIGINT;
    archived_count INT;
BEGIN
    cutoff_time := (EXTRACT(EPOCH FROM CURRENT_TIMESTAMP - (retention_days || ' days')::INTERVAL) * 1000)::BIGINT;
    
    IF NOT dry_run THEN
        INSERT INTO auditEvent_archive (
            eventID, eventTime, eventType, eventDescriptor, 
            userID, archived_at
        )
        SELECT eventID, eventTime, eventType, eventDescriptor,
               userID, CURRENT_TIMESTAMP
        FROM auditEvent
        WHERE eventTime < cutoff_time
        LIMIT batch_size
        ON CONFLICT (eventID) DO NOTHING;
        
        DELETE FROM auditEvent
        WHERE eventTime < cutoff_time
          AND eventID IN (
              SELECT eventID FROM auditEvent_archive
              WHERE archived_at >= CURRENT_TIMESTAMP - INTERVAL '1 minute'
          );
    END IF;
    
    RAISE NOTICE 'Audit archive completed';
END;
$$;

-- ============================================================================
-- COMPLETED WORK ITEM CLEANUP
-- ============================================================================

CREATE PROCEDURE IF NOT EXISTS cleanup_completed_work_items(
    IN retention_days INT DEFAULT 30,
    IN batch_size INT DEFAULT 1000,
    IN dry_run BOOLEAN DEFAULT FALSE
)
LANGUAGE SQL
AS $$
DECLARE
    cutoff_time TIMESTAMP;
    deleted_count INT;
BEGIN
    cutoff_time := CURRENT_TIMESTAMP - (retention_days || ' days')::INTERVAL;
    
    IF NOT dry_run THEN
        -- Delete completed work items older than retention
        DELETE FROM Work_Items
        WHERE status IN ('Completed', 'Failed', 'Cancelled')
          AND enablement_time < cutoff_time
          AND thisid IN (
              SELECT thisid FROM Work_Items
              WHERE status IN ('Completed', 'Failed', 'Cancelled')
                AND enablement_time < cutoff_time
              LIMIT batch_size
          );
        
        GET DIAGNOSTICS deleted_count = ROW_COUNT;
    END IF;
    
    RAISE NOTICE 'Cleaned up completed work items';
END;
$$;

-- ============================================================================
-- MASTER ARCHIVING PROCEDURE
-- ============================================================================

CREATE PROCEDURE IF NOT EXISTS run_archiving(
    IN dry_run BOOLEAN DEFAULT FALSE,
    IN log_retention_days INT DEFAULT 90,
    IN audit_retention_days INT DEFAULT 365,
    IN workitem_retention_days INT DEFAULT 30
)
LANGUAGE SQL
AS $$
DECLARE
    start_time TIMESTAMP;
BEGIN
    start_time := CURRENT_TIMESTAMP;
    
    RAISE NOTICE 'Starting archiving process at %', start_time;
    
    -- Run each archiving procedure
    CALL archive_log_events(log_retention_days, 10000, dry_run);
    CALL archive_task_instances(log_retention_days, 10000, dry_run);
    CALL archive_audit_events(audit_retention_days, 10000, dry_run);
    CALL cleanup_completed_work_items(workitem_retention_days, 1000, dry_run);
    
    RAISE NOTICE 'Archiving completed in % seconds', 
        EXTRACT(EPOCH FROM CURRENT_TIMESTAMP - start_time);
END;
$$;

-- ============================================================================
-- ARCHIVE MAINTENANCE
-- ============================================================================

-- Vacuum analyze archive tables (run weekly)
CREATE PROCEDURE IF NOT EXISTS maintain_archive_tables()
LANGUAGE SQL
AS $$
BEGIN
    -- Rebuild indexes on archive tables
    REINDEX TABLE logEvent_archive;
    REINDEX TABLE logTaskInstance_archive;
    REINDEX TABLE auditEvent_archive;
    
    -- Update statistics
    ANALYZE logEvent_archive;
    ANALYZE logTaskInstance_archive;
    ANALYZE auditEvent_archive;
    
    RAISE NOTICE 'Archive maintenance completed';
END;
$$;

-- Purge old archive data (older than 7 years for compliance)
CREATE PROCEDURE IF NOT EXISTS purge_old_archives(
    IN archive_retention_years INT DEFAULT 7
)
LANGUAGE SQL
AS $$
DECLARE
    cutoff_time BIGINT;
BEGIN
    cutoff_time := (EXTRACT(EPOCH FROM CURRENT_TIMESTAMP - (archive_retention_years || ' years')::INTERVAL) * 1000)::BIGINT;
    
    DELETE FROM logEvent_archive WHERE eventTime < cutoff_time;
    DELETE FROM logTaskInstance_archive WHERE archived_at < CURRENT_TIMESTAMP - (archive_retention_years || ' years')::INTERVAL;
    DELETE FROM auditEvent_archive WHERE eventTime < cutoff_time;
    
    RAISE NOTICE 'Old archives purged';
END;
$$;
