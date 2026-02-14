-- YAWL Workflow Engine Base Schema for Teradata
-- This script creates the core YAWL database structure on Teradata
-- Adapted for Teradata SQL Engine compatibility

-- Create YAWL database (if not using existing one)
-- Note: Adjust database properties as needed for your environment
CREATE DATABASE IF NOT EXISTS yawl_workflow
AS PERM = 104857600, -- 100MB
    SPOOL = 52428800;  -- 50MB

USING yawl_workflow;

-- Enable logging and transaction support
BEGIN TRANSACTION;

-- ============================================================================
-- Process Definition Tables
-- ============================================================================

CREATE MULTISET TABLE process_definitions (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    specification CLOB,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    description VARCHAR(1000),
    PRIMARY KEY (id),
    UNIQUE (name, version)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_process_def_status ON process_definitions (status);
CREATE INDEX idx_process_def_created ON process_definitions (created_at);

-- ============================================================================
-- Process Execution Tables
-- ============================================================================

CREATE MULTISET TABLE process_instances (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    process_id INT NOT NULL,
    case_id VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP,
    parent_case_id VARCHAR(255),
    created_by VARCHAR(255),
    data CLOB,
    PRIMARY KEY (id),
    FOREIGN KEY (process_id) REFERENCES process_definitions(id)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_process_instances_case ON process_instances (case_id);
CREATE INDEX idx_process_instances_status ON process_instances (status);
CREATE INDEX idx_process_instances_start ON process_instances (start_time);
CREATE INDEX idx_process_instances_parent ON process_instances (parent_case_id);

-- ============================================================================
-- Task Tables
-- ============================================================================

CREATE MULTISET TABLE tasks (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    process_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    task_type VARCHAR(50) NOT NULL,
    configuration CLOB,
    is_automated INT DEFAULT 0,
    PRIMARY KEY (id),
    FOREIGN KEY (process_id) REFERENCES process_definitions(id)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_tasks_process ON tasks (process_id);
CREATE INDEX idx_tasks_type ON tasks (task_type);

CREATE MULTISET TABLE task_instances (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    task_id INT NOT NULL,
    case_id INT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    assigned_to VARCHAR(255),
    data CLOB,
    PRIMARY KEY (id),
    FOREIGN KEY (task_id) REFERENCES tasks(id),
    FOREIGN KEY (case_id) REFERENCES process_instances(id)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_task_instances_status ON task_instances (status);
CREATE INDEX idx_task_instances_assigned ON task_instances (assigned_to);
CREATE INDEX idx_task_instances_created ON task_instances (created_at);
CREATE INDEX idx_task_instances_case ON task_instances (case_id);

-- ============================================================================
-- Resource and Worklist Tables
-- ============================================================================

CREATE MULTISET TABLE participants (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    user_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    role VARCHAR(100),
    department VARCHAR(100),
    is_active INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_participants_user ON participants (user_id);
CREATE INDEX idx_participants_active ON participants (is_active);

CREATE MULTISET TABLE worklist_items (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    task_instance_id INT NOT NULL,
    participant_id INT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OFFERED',
    accepted_at TIMESTAMP,
    completed_at TIMESTAMP,
    allocation_type VARCHAR(50),
    PRIMARY KEY (id),
    FOREIGN KEY (task_instance_id) REFERENCES task_instances(id),
    FOREIGN KEY (participant_id) REFERENCES participants(id)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_worklist_participant ON worklist_items (participant_id);
CREATE INDEX idx_worklist_status ON worklist_items (status);
CREATE INDEX idx_worklist_accepted ON worklist_items (accepted_at);

-- ============================================================================
-- Audit and Logging Tables
-- ============================================================================

CREATE MULTISET TABLE audit_log (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    case_id INT,
    task_instance_id INT,
    event_type VARCHAR(100) NOT NULL,
    actor VARCHAR(255),
    event_data CLOB,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (case_id) REFERENCES process_instances(id),
    FOREIGN KEY (task_instance_id) REFERENCES task_instances(id)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_audit_case ON audit_log (case_id);
CREATE INDEX idx_audit_timestamp ON audit_log (timestamp);
CREATE INDEX idx_audit_type ON audit_log (event_type);

-- ============================================================================
-- Exception and Error Handling Tables
-- ============================================================================

CREATE MULTISET TABLE exceptions (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    case_id INT NOT NULL,
    task_instance_id INT,
    exception_type VARCHAR(100) NOT NULL,
    message VARCHAR(2000),
    stack_trace CLOB,
    status VARCHAR(50) DEFAULT 'OPEN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (case_id) REFERENCES process_instances(id),
    FOREIGN KEY (task_instance_id) REFERENCES task_instances(id)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_exceptions_case ON exceptions (case_id);
CREATE INDEX idx_exceptions_status ON exceptions (status);
CREATE INDEX idx_exceptions_created ON exceptions (created_at);

-- ============================================================================
-- Configuration and System Tables
-- ============================================================================

CREATE MULTISET TABLE system_properties (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    property_key VARCHAR(255) NOT NULL UNIQUE,
    property_value VARCHAR(4000),
    description VARCHAR(1000),
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ON COMMIT PRESERVE ROWS;

CREATE MULTISET TABLE connector_config (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    connector_type VARCHAR(100) NOT NULL,
    connector_name VARCHAR(255) NOT NULL,
    connection_string VARCHAR(500),
    credentials_key VARCHAR(255),
    is_active INT DEFAULT 1,
    last_tested TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (connector_type, connector_name)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_connector_active ON connector_config (is_active);

-- ============================================================================
-- Data Transformation and XPath Tables
-- ============================================================================

CREATE MULTISET TABLE data_transformations (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    process_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    transformation_type VARCHAR(50),
    xquery_definition CLOB,
    xpath_expression VARCHAR(2000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (process_id) REFERENCES process_definitions(id)
) ON COMMIT PRESERVE ROWS;

-- ============================================================================
-- Service Integration Tables
-- ============================================================================

CREATE MULTISET TABLE service_integrations (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    process_id INT,
    service_name VARCHAR(255) NOT NULL,
    service_url VARCHAR(500) NOT NULL,
    service_type VARCHAR(100),
    authentication_type VARCHAR(50),
    timeout_seconds INT DEFAULT 30,
    retry_count INT DEFAULT 3,
    is_active INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (process_id) REFERENCES process_definitions(id)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_service_active ON service_integrations (is_active);

COMMIT;

-- ============================================================================
-- Create Stored Procedures and Functions
-- ============================================================================

CREATE PROCEDURE sp_get_active_cases()
RETURNS (case_id VARCHAR(255), process_name VARCHAR(255), status VARCHAR(50), start_time TIMESTAMP)
LANGUAGE SQL
READS SQL DATA
BEGIN
    SELECT
        pi.case_id,
        pd.name,
        pi.status,
        pi.start_time
    FROM process_instances pi
    JOIN process_definitions pd ON pi.process_id = pd.id
    WHERE pi.status IN ('ACTIVE', 'SUSPENDED');
END;

CREATE PROCEDURE sp_get_user_worklist(IN p_user_id VARCHAR(255))
RETURNS (task_name VARCHAR(255), case_id VARCHAR(255), created_at TIMESTAMP, priority INT)
LANGUAGE SQL
READS SQL DATA
BEGIN
    SELECT
        t.name,
        pi.case_id,
        ti.created_at,
        0 AS priority
    FROM worklist_items wi
    JOIN task_instances ti ON wi.task_instance_id = ti.id
    JOIN tasks t ON ti.task_id = t.id
    JOIN process_instances pi ON ti.case_id = pi.id
    JOIN participants p ON wi.participant_id = p.id
    WHERE p.user_id = p_user_id
    AND wi.status IN ('OFFERED', 'ALLOCATED')
    ORDER BY ti.created_at DESC;
END;

-- ============================================================================
-- Initialize System Properties
-- ============================================================================

INSERT INTO system_properties (property_key, property_value, description) VALUES
('yawl.version', '4.3', 'YAWL Engine Version'),
('yawl.database', 'teradata', 'Database Type'),
('teradata.charset', 'UTF8', 'Teradata Character Set'),
('teradata.connection.pool.size', '20', 'Connection Pool Size'),
('teradata.logon.timeout', '30', 'Teradata Logon Timeout (seconds)'),
('workflow.execution.timeout', '3600', 'Workflow Execution Timeout (seconds)'),
('audit.log.enabled', '1', 'Enable Audit Logging'),
('exception.handling.strategy', 'SUSPEND', 'Exception Handling Strategy');

COMMIT;

-- ============================================================================
-- Create Views for Easier Reporting
-- ============================================================================

CREATE VIEW vw_active_cases AS
SELECT
    pi.case_id,
    pd.name AS process_name,
    pi.status,
    pi.start_time,
    COUNT(DISTINCT ti.id) AS task_count
FROM process_instances pi
JOIN process_definitions pd ON pi.process_id = pd.id
LEFT JOIN task_instances ti ON pi.id = ti.case_id
WHERE pi.status = 'ACTIVE'
GROUP BY pi.case_id, pd.name, pi.status, pi.start_time;

CREATE VIEW vw_pending_worklist AS
SELECT
    wi.id AS worklist_id,
    p.user_id,
    p.name AS participant_name,
    t.name AS task_name,
    pi.case_id,
    ti.created_at,
    wi.status
FROM worklist_items wi
JOIN task_instances ti ON wi.task_instance_id = ti.id
JOIN tasks t ON ti.task_id = t.id
JOIN process_instances pi ON ti.case_id = pi.id
JOIN participants p ON wi.participant_id = p.id
WHERE wi.status IN ('OFFERED', 'ALLOCATED');

COMMIT;
