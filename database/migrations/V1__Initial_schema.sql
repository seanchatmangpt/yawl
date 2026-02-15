-- YAWL Database Schema - Initial Schema
-- Version: 1.0.0
-- Compatible with: PostgreSQL 14+, MySQL 8+, Oracle 19c+, SQL Server 2019+

-- =============================================================================
-- YAWL ENGINE TABLES
-- =============================================================================

-- Specifications table - stores YAWL workflow specifications
CREATE TABLE IF NOT EXISTS specifications (
    spec_id VARCHAR(255) NOT NULL,
    spec_uri VARCHAR(1024),
    spec_version VARCHAR(50),
    spec_name VARCHAR(255),
    spec_document CLOB,  -- Use TEXT for PostgreSQL, CLOB for Oracle
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    metadata JSON,  -- Use JSONB for PostgreSQL
    PRIMARY KEY (spec_id)
);

-- Net instances table - runtime instances of workflow nets
CREATE TABLE IF NOT EXISTS net_instances (
    instance_id VARCHAR(255) NOT NULL,
    spec_id VARCHAR(255) NOT NULL,
    case_id VARCHAR(255) NOT NULL,
    parent_case_id VARCHAR(255),
    net_name VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'RUNNING',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    starter VARCHAR(255),
    root_case_id VARCHAR(255),
    data_snapshot JSON,
    PRIMARY KEY (instance_id),
    CONSTRAINT fk_net_spec FOREIGN KEY (spec_id) REFERENCES specifications(spec_id)
);

-- Tasks table - task definitions and instances
CREATE TABLE IF NOT EXISTS tasks (
    task_id VARCHAR(255) NOT NULL,
    instance_id VARCHAR(255) NOT NULL,
    task_name VARCHAR(255) NOT NULL,
    task_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLED',
    enabled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    failed_at TIMESTAMP,
    timer_expiry TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    input_data JSON,
    output_data JSON,
    PRIMARY KEY (task_id),
    CONSTRAINT fk_task_instance FOREIGN KEY (instance_id) REFERENCES net_instances(instance_id)
);

-- Work items table - units of work assigned to resources
CREATE TABLE IF NOT EXISTS work_items (
    work_item_id VARCHAR(255) NOT NULL,
    task_id VARCHAR(255) NOT NULL,
    case_id VARCHAR(255) NOT NULL,
    spec_id VARCHAR(255) NOT NULL,
    resource_id VARCHAR(255),
    resource_type VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    suspended_at TIMESTAMP,
    priority INTEGER DEFAULT 0,
    allocation_strategy VARCHAR(50),
    PRIMARY KEY (work_item_id),
    CONSTRAINT fk_workitem_task FOREIGN KEY (task_id) REFERENCES tasks(task_id),
    CONSTRAINT fk_workitem_case FOREIGN KEY (case_id) REFERENCES net_instances(case_id)
);

-- =============================================================================
-- RESOURCE SERVICE TABLES
-- =============================================================================

-- Participants table - human resources
CREATE TABLE IF NOT EXISTS participants (
    participant_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(255),
    email VARCHAR(255),
    password_hash VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP,
    preferences JSON,
    PRIMARY KEY (participant_id)
);

-- Roles table - role definitions
CREATE TABLE IF NOT EXISTS roles (
    role_id VARCHAR(255) NOT NULL,
    role_name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id)
);

-- Participant roles junction table
CREATE TABLE IF NOT EXISTS participant_roles (
    participant_id VARCHAR(255) NOT NULL,
    role_id VARCHAR(255) NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(255),
    PRIMARY KEY (participant_id, role_id),
    CONSTRAINT fk_pr_participant FOREIGN KEY (participant_id) REFERENCES participants(participant_id),
    CONSTRAINT fk_pr_role FOREIGN KEY (role_id) REFERENCES roles(role_id)
);

-- Capabilities table - non-human resources
CREATE TABLE IF NOT EXISTS capabilities (
    capability_id VARCHAR(255) NOT NULL,
    capability_name VARCHAR(255) NOT NULL UNIQUE,
    capability_type VARCHAR(50) NOT NULL,
    description VARCHAR(1024),
    config JSON,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (capability_id)
);

-- Work queues table
CREATE TABLE IF NOT EXISTS work_queues (
    queue_id VARCHAR(255) NOT NULL,
    queue_name VARCHAR(255) NOT NULL,
    queue_type VARCHAR(50) NOT NULL,
    owner_id VARCHAR(255),
    description VARCHAR(1024),
    max_size INTEGER,
    priority INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (queue_id)
);

-- Queue items junction table
CREATE TABLE IF NOT EXISTS queue_items (
    queue_item_id VARCHAR(255) NOT NULL,
    queue_id VARCHAR(255) NOT NULL,
    work_item_id VARCHAR(255) NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    PRIMARY KEY (queue_item_id),
    CONSTRAINT fk_qi_queue FOREIGN KEY (queue_id) REFERENCES work_queues(queue_id),
    CONSTRAINT fk_qi_workitem FOREIGN KEY (work_item_id) REFERENCES work_items(work_item_id)
);

-- =============================================================================
-- WORKLET SERVICE TABLES
-- =============================================================================

-- Worklet rules table
CREATE TABLE IF NOT EXISTS worklet_rules (
    rule_id VARCHAR(255) NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    rule_type VARCHAR(50) NOT NULL,
    condition_expr VARCHAR(4096),
    action_type VARCHAR(50) NOT NULL,
    action_params JSON,
    priority INTEGER DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (rule_id)
);

-- Worklet exceptions table
CREATE TABLE IF NOT EXISTS worklet_exceptions (
    exception_id VARCHAR(255) NOT NULL,
    case_id VARCHAR(255) NOT NULL,
    task_id VARCHAR(255),
    exception_type VARCHAR(100) NOT NULL,
    exception_data JSON,
    raised_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handled_at TIMESTAMP,
    handling_rule_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'RAISED',
    PRIMARY KEY (exception_id),
    CONSTRAINT fk_we_rule FOREIGN KEY (handling_rule_id) REFERENCES worklet_rules(rule_id)
);

-- =============================================================================
-- LOGGING AND AUDIT TABLES
-- =============================================================================

-- Audit log table
CREATE TABLE IF NOT EXISTS audit_log (
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
    PRIMARY KEY (log_id)
);

-- Event log table - for process mining
CREATE TABLE IF NOT EXISTS event_log (
    event_id VARCHAR(255) NOT NULL,
    case_id VARCHAR(255) NOT NULL,
    activity VARCHAR(255) NOT NULL,
    resource VARCHAR(255),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_type VARCHAR(50) NOT NULL,
    lifecycle_transition VARCHAR(50),
    data_attributes JSON,
    process_id VARCHAR(255),
    PRIMARY KEY (event_id)
);

-- =============================================================================
-- MONITORING AND METRICS TABLES
-- =============================================================================

-- Engine metrics table
CREATE TABLE IF NOT EXISTS engine_metrics (
    metric_id VARCHAR(255) NOT NULL,
    metric_name VARCHAR(255) NOT NULL,
    metric_value DECIMAL(20,6) NOT NULL,
    metric_unit VARCHAR(50),
    collected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tags JSON,
    PRIMARY KEY (metric_id)
);

-- Performance snapshots table
CREATE TABLE IF NOT EXISTS performance_snapshots (
    snapshot_id VARCHAR(255) NOT NULL,
    snapshot_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active_cases INTEGER,
    active_tasks INTEGER,
    pending_work_items INTEGER,
    avg_response_time_ms DECIMAL(10,2),
    throughput_per_min INTEGER,
    memory_usage_mb DECIMAL(10,2),
    cpu_usage_percent DECIMAL(5,2),
    PRIMARY KEY (snapshot_id)
);

-- =============================================================================
-- CONFIGURATION TABLES
-- =============================================================================

-- System configuration table
CREATE TABLE IF NOT EXISTS system_config (
    config_key VARCHAR(255) NOT NULL,
    config_value VARCHAR(4096),
    config_type VARCHAR(50) DEFAULT 'STRING',
    description VARCHAR(1024),
    is_encrypted BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255),
    PRIMARY KEY (config_key)
);

-- Service registry table
CREATE TABLE IF NOT EXISTS service_registry (
    service_id VARCHAR(255) NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    service_type VARCHAR(50) NOT NULL,
    endpoint_url VARCHAR(1024),
    health_check_url VARCHAR(1024),
    status VARCHAR(50) NOT NULL DEFAULT 'REGISTERED',
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_heartbeat TIMESTAMP,
    metadata JSON,
    PRIMARY KEY (service_id)
);

-- =============================================================================
-- SCHEDULING SERVICE TABLES
-- =============================================================================

-- Scheduled tasks table
CREATE TABLE IF NOT EXISTS scheduled_tasks (
    schedule_id VARCHAR(255) NOT NULL,
    case_id VARCHAR(255),
    task_id VARCHAR(255),
    schedule_type VARCHAR(50) NOT NULL,
    cron_expression VARCHAR(255),
    scheduled_start TIMESTAMP NOT NULL,
    scheduled_end TIMESTAMP,
    actual_start TIMESTAMP,
    actual_end TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED',
    resource_id VARCHAR(255),
    timezone VARCHAR(50) DEFAULT 'UTC',
    recurrence_rule VARCHAR(1024),
    PRIMARY KEY (schedule_id)
);

-- =============================================================================
-- COST SERVICE TABLES
-- =============================================================================

-- Cost records table
CREATE TABLE IF NOT EXISTS cost_records (
    cost_id VARCHAR(255) NOT NULL,
    case_id VARCHAR(255),
    task_id VARCHAR(255),
    resource_id VARCHAR(255),
    cost_type VARCHAR(50) NOT NULL,
    amount DECIMAL(15,4) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    period_start TIMESTAMP,
    period_end TIMESTAMP,
    notes VARCHAR(1024),
    PRIMARY KEY (cost_id)
);

-- Budget allocations table
CREATE TABLE IF NOT EXISTS budget_allocations (
    allocation_id VARCHAR(255) NOT NULL,
    budget_name VARCHAR(255) NOT NULL,
    total_budget DECIMAL(15,4) NOT NULL,
    spent_amount DECIMAL(15,4) DEFAULT 0,
    currency VARCHAR(3) DEFAULT 'USD',
    fiscal_year INTEGER,
    department VARCHAR(255),
    cost_center VARCHAR(255),
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (allocation_id)
);

-- =============================================================================
-- INITIAL DATA
-- =============================================================================

-- Insert default system configuration
INSERT INTO system_config (config_key, config_value, config_type, description) VALUES
    ('engine.version', '5.2.0', 'STRING', 'YAWL Engine Version'),
    ('engine.persistence.enabled', 'true', 'BOOLEAN', 'Enable database persistence'),
    ('engine.logging.level', 'INFO', 'STRING', 'Default logging level'),
    ('engine.workitem.timeout', '86400000', 'LONG', 'Default work item timeout in milliseconds'),
    ('engine.case.retention.days', '365', 'INTEGER', 'Days to retain completed cases'),
    ('resource.default.allocation', 'ROUND_ROBIN', 'STRING', 'Default resource allocation strategy'),
    ('worklet.enabled', 'true', 'BOOLEAN', 'Enable worklet service');

-- Insert default roles
INSERT INTO roles (role_id, role_name, description) VALUES
    ('ROLE_ADMIN', 'Administrator', 'Full system administrator access'),
    ('ROLE_MANAGER', 'Manager', 'Process management access'),
    ('ROLE_ANALYST', 'Analyst', 'Read-only analysis access'),
    ('ROLE_USER', 'User', 'Standard user access');

-- Insert default capabilities
INSERT INTO capabilities (capability_id, capability_name, capability_type, description) VALUES
    ('CAP_EMAIL', 'Email Service', 'SERVICE', 'Email notification service'),
    ('CAP_SMS', 'SMS Service', 'SERVICE', 'SMS notification service'),
    ('CAP_WEBHOOK', 'Webhook Service', 'SERVICE', 'External webhook integration'),
    ('CAP_SCRIPT', 'Script Execution', 'EXECUTOR', 'Custom script execution capability');
