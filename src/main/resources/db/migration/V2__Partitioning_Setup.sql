-- ============================================================================
-- YAWL Database Partitioning Setup for High-Growth Tables
-- Version: 2.0.0
-- Target: PostgreSQL (adapt for MySQL/Oracle as needed)
-- Purpose: Partition logging tables by time for efficient archiving
-- ============================================================================

-- Note: This migration requires PostgreSQL 12+
-- For H2/MySQL, use the alternative approaches in V2__Partitioning_Alternative.sql

-- ============================================================================
-- Create partitioned logEvent table (if migrating existing table)
-- ============================================================================

-- Step 1: Create new partitioned table structure
-- This is a template for PostgreSQL partitioning

/*
-- Create parent table for time-based partitioning
CREATE TABLE IF NOT EXISTS logEvent_partitioned (
    eventID BIGSERIAL,
    descriptor VARCHAR(255),
    eventTime BIGINT NOT NULL,
    instanceID BIGINT,
    serviceID BIGINT,
    rootNetInstanceID BIGINT,
    PRIMARY KEY (eventID, eventTime)
) PARTITION BY RANGE (eventTime);

-- Create monthly partitions (example for 2026)
CREATE TABLE logEvent_2026_01 PARTITION OF logEvent_partitioned
    FOR VALUES FROM (1735689600000) TO (1738368000000); -- Jan 2026

CREATE TABLE logEvent_2026_02 PARTITION OF logEvent_partitioned
    FOR VALUES FROM (1738368000000) TO (1740787200000); -- Feb 2026

CREATE TABLE logEvent_2026_03 PARTITION OF logEvent_partitioned
    FOR VALUES FROM (1740787200000) TO (1743465600000); -- Mar 2026

-- Create default partition for out-of-range data
CREATE TABLE logEvent_default PARTITION OF logEvent_partitioned DEFAULT;

-- Create indexes on partitioned table
CREATE INDEX idx_logEvent_part_time ON logEvent_partitioned(eventTime);
CREATE INDEX idx_logEvent_part_root ON logEvent_partitioned(rootNetInstanceID);

-- Rename tables to swap
ALTER TABLE logEvent RENAME TO logEvent_legacy;
ALTER TABLE logEvent_partitioned RENAME TO logEvent;

-- Migrate data (do in batches during low-traffic period)
INSERT INTO logEvent SELECT * FROM logEvent_legacy;

-- Drop legacy table after verification
-- DROP TABLE logEvent_legacy;
*/

-- ============================================================================
-- Partition management function (PostgreSQL)
-- ============================================================================

/*
CREATE OR REPLACE FUNCTION create_log_partition(partition_date DATE)
RETURNS VOID AS $$
DECLARE
    partition_name TEXT;
    start_epoch BIGINT;
    end_epoch BIGINT;
BEGIN
    partition_name := 'logEvent_' || TO_CHAR(partition_date, 'YYYY_MM');
    start_epoch := (EXTRACT(EPOCH FROM partition_date) * 1000)::BIGINT;
    end_epoch := (EXTRACT(EPOCH FROM partition_date + INTERVAL '1 month') * 1000)::BIGINT;
    
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF logEvent 
         FOR VALUES FROM (%s) TO (%s)',
        partition_name, start_epoch, end_epoch
    );
END;
$$ LANGUAGE plpgsql;
*/

-- ============================================================================
-- Archive view for easy querying across partitions
-- ============================================================================

/*
CREATE OR REPLACE VIEW v_log_events_with_dates AS
SELECT 
    eventID,
    descriptor,
    eventTime,
    TO_TIMESTAMP(eventTime / 1000.0) AS event_timestamp,
    DATE(TO_TIMESTAMP(eventTime / 1000.0)) AS event_date,
    instanceID,
    serviceID,
    rootNetInstanceID
FROM logEvent;
*/

-- ============================================================================
-- H2-compatible alternative (using separate archive tables)
-- ============================================================================

-- For H2, we use separate archive tables with a union view

CREATE TABLE IF NOT EXISTS logEvent_archive (
    eventID BIGINT,
    descriptor VARCHAR(255),
    eventTime BIGINT,
    instanceID BIGINT,
    serviceID BIGINT,
    rootNetInstanceID BIGINT,
    archived_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (eventID)
);

CREATE INDEX IF NOT EXISTS idx_logEvent_archive_time ON logEvent_archive(eventTime);

-- Similar archive tables for other high-growth tables
CREATE TABLE IF NOT EXISTS logTaskInstance_archive (
    taskInstanceID BIGINT,
    engineInstanceID VARCHAR(255),
    taskID BIGINT,
    parentNetInstanceID BIGINT,
    parentTaskInstanceID BIGINT,
    archived_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (taskInstanceID)
);

CREATE TABLE IF NOT EXISTS auditEvent_archive (
    eventID BIGINT,
    eventTime BIGINT,
    eventType VARCHAR(255),
    eventDescriptor VARCHAR(1024),
    userID VARCHAR(255),
    archived_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (eventID)
);
