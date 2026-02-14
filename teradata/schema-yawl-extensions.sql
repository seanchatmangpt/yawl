-- YAWL Workflow Engine Extensions Schema for Teradata
-- This script extends the core YAWL schema with additional capabilities
-- Includes dynamic workflow, versioning, and analytics features

USING yawl_workflow;

-- ============================================================================
-- Dynamic Workflow (Worklets) Tables
-- ============================================================================

BEGIN TRANSACTION;

CREATE MULTISET TABLE worklet_definitions (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(255) NOT NULL UNIQUE,
    version VARCHAR(50),
    specification CLOB NOT NULL,
    is_active INT DEFAULT 1,
    description VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    PRIMARY KEY (id)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_worklet_active ON worklet_definitions (is_active);

CREATE MULTISET TABLE worklet_applications (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    worklet_id INT NOT NULL,
    case_id INT NOT NULL,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    removed_at TIMESTAMP,
    reason VARCHAR(500),
    PRIMARY KEY (id),
    FOREIGN KEY (worklet_id) REFERENCES worklet_definitions(id),
    FOREIGN KEY (case_id) REFERENCES process_instances(id)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_worklet_app_case ON worklet_applications (case_id);

-- ============================================================================
-- Process Versioning Tables
-- ============================================================================

CREATE MULTISET TABLE process_versions (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    process_id INT NOT NULL,
    version_number INT NOT NULL,
    specification CLOB NOT NULL,
    change_summary VARCHAR(1000),
    released_at TIMESTAMP,
    released_by VARCHAR(255),
    is_active INT DEFAULT 1,
    PRIMARY KEY (id),
    FOREIGN KEY (process_id) REFERENCES process_definitions(id),
    UNIQUE (process_id, version_number)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_versions_active ON process_versions (is_active);

-- ============================================================================
-- Analytics and Metrics Tables
-- ============================================================================

CREATE MULTISET TABLE process_metrics (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    case_id INT NOT NULL,
    process_id INT NOT NULL,
    total_duration DECIMAL(10,2),
    task_count INT,
    resource_count INT,
    exception_count INT,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (case_id) REFERENCES process_instances(id),
    FOREIGN KEY (process_id) REFERENCES process_definitions(id)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_metrics_process ON process_metrics (process_id);
CREATE INDEX idx_metrics_recorded ON process_metrics (recorded_at);

CREATE MULTISET TABLE task_performance (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    task_id INT NOT NULL,
    avg_duration DECIMAL(10,2),
    min_duration DECIMAL(10,2),
    max_duration DECIMAL(10,2),
    completion_rate DECIMAL(5,2),
    abandonment_count INT,
    last_calculated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (task_id) REFERENCES tasks(id)
) ON COMMIT PRESERVE ROWS;

-- ============================================================================
-- Document Management Tables
-- ============================================================================

CREATE MULTISET TABLE documents (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    case_id INT NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    document_type VARCHAR(100),
    file_path VARCHAR(500),
    file_size BIGINT,
    checksum VARCHAR(128),
    uploaded_by VARCHAR(255),
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_confidential INT DEFAULT 0,
    PRIMARY KEY (id),
    FOREIGN KEY (case_id) REFERENCES process_instances(id)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_documents_case ON documents (case_id);
CREATE INDEX idx_documents_uploaded ON documents (uploaded_at);

-- ============================================================================
-- Custom Form Templates
-- ============================================================================

CREATE MULTISET TABLE form_templates (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    task_id INT NOT NULL,
    form_name VARCHAR(255) NOT NULL,
    html_content CLOB,
    css_content CLOB,
    javascript_content CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (task_id) REFERENCES tasks(id)
) ON COMMIT PRESERVE ROWS;

CREATE MULTISET TABLE form_submissions (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    form_template_id INT NOT NULL,
    task_instance_id INT NOT NULL,
    submitted_data CLOB,
    submitted_by VARCHAR(255),
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (form_template_id) REFERENCES form_templates(id),
    FOREIGN KEY (task_instance_id) REFERENCES task_instances(id)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_form_submissions_timestamp ON form_submissions (submitted_at);

-- ============================================================================
-- Calendar and Scheduling Tables
-- ============================================================================

CREATE MULTISET TABLE calendars (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    calendar_name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(1000),
    time_zone VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ON COMMIT PRESERVE ROWS;

CREATE MULTISET TABLE calendar_periods (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    calendar_id INT NOT NULL,
    period_name VARCHAR(255),
    start_time TIME,
    end_time TIME,
    day_of_week INT,
    is_working_time INT DEFAULT 1,
    PRIMARY KEY (id),
    FOREIGN KEY (calendar_id) REFERENCES calendars(id)
) ON COMMIT PRESERVE ROWS;

CREATE MULTISET TABLE scheduled_tasks (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    task_instance_id INT NOT NULL,
    scheduled_start_time TIMESTAMP,
    scheduled_end_time TIMESTAMP,
    actual_start_time TIMESTAMP,
    actual_end_time TIMESTAMP,
    status VARCHAR(50),
    priority INT DEFAULT 0,
    PRIMARY KEY (id),
    FOREIGN KEY (task_instance_id) REFERENCES task_instances(id)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_scheduled_tasks_time ON scheduled_tasks (scheduled_start_time);

-- ============================================================================
-- Resource Pool and Allocation Tables
-- ============================================================================

CREATE MULTISET TABLE resource_pools (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    pool_name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(1000),
    pool_type VARCHAR(100),
    is_active INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ON COMMIT PRESERVE ROWS;

CREATE MULTISET TABLE pool_members (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    pool_id INT NOT NULL,
    participant_id INT NOT NULL,
    role VARCHAR(100),
    assignment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    removal_date TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (pool_id) REFERENCES resource_pools(id),
    FOREIGN KEY (participant_id) REFERENCES participants(id),
    UNIQUE (pool_id, participant_id)
) ON COMMIT PRESERVE ROWS;

CREATE MULTISET TABLE resource_allocations (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    task_instance_id INT NOT NULL,
    pool_id INT,
    participant_id INT,
    allocation_type VARCHAR(50),
    allocation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deallocation_time TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (task_instance_id) REFERENCES task_instances(id),
    FOREIGN KEY (pool_id) REFERENCES resource_pools(id),
    FOREIGN KEY (participant_id) REFERENCES participants(id)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_allocations_task ON resource_allocations (task_instance_id);

-- ============================================================================
-- Process Configuration Tables
-- ============================================================================

CREATE MULTISET TABLE process_configurations (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    process_id INT NOT NULL,
    configuration_set VARCHAR(255),
    configuration_data CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (process_id) REFERENCES process_definitions(id)
) ON COMMIT PRESERVE ROWS;

-- ============================================================================
-- Notification System Tables
-- ============================================================================

CREATE MULTISET TABLE notifications (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    case_id INT NOT NULL,
    recipient_id INT NOT NULL,
    notification_type VARCHAR(100),
    subject VARCHAR(255),
    message CLOB,
    delivery_status VARCHAR(50) DEFAULT 'PENDING',
    delivery_method VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (case_id) REFERENCES process_instances(id),
    FOREIGN KEY (recipient_id) REFERENCES participants(id)
) ON COMMIT PRESERVE ROWS;

CREATE INDEX idx_notifications_status ON notifications (delivery_status);
CREATE INDEX idx_notifications_created ON notifications (created_at);

-- ============================================================================
-- User Preferences and Settings Tables
-- ============================================================================

CREATE MULTISET TABLE user_preferences (
    id INT NOT NULL GENERATED ALWAYS AS IDENTITY,
    participant_id INT NOT NULL,
    preference_key VARCHAR(255),
    preference_value VARCHAR(1000),
    PRIMARY KEY (id),
    FOREIGN KEY (participant_id) REFERENCES participants(id),
    UNIQUE (participant_id, preference_key)
) ON COMMIT PRESERVE ROWS;

COMMIT;

-- ============================================================================
-- Create Additional Views for Analytics
-- ============================================================================

CREATE VIEW vw_process_statistics AS
SELECT
    pd.name AS process_name,
    COUNT(DISTINCT pi.id) AS total_instances,
    SUM(CASE WHEN pi.status = 'ACTIVE' THEN 1 ELSE 0 END) AS active_instances,
    SUM(CASE WHEN pi.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_instances,
    AVG(CAST(DATEDIFF(minute, pi.start_time, COALESCE(pi.end_time, CURRENT_TIMESTAMP)) AS DECIMAL(10,2))) AS avg_duration_minutes
FROM process_definitions pd
LEFT JOIN process_instances pi ON pd.id = pi.process_id
GROUP BY pd.name;

CREATE VIEW vw_resource_utilization AS
SELECT
    p.user_id,
    p.name,
    COUNT(wi.id) AS assigned_tasks,
    SUM(CASE WHEN wi.status = 'OFFERED' THEN 1 ELSE 0 END) AS offered_tasks,
    SUM(CASE WHEN wi.status = 'ALLOCATED' THEN 1 ELSE 0 END) AS allocated_tasks,
    SUM(CASE WHEN wi.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_tasks
FROM participants p
LEFT JOIN worklist_items wi ON p.id = wi.participant_id
GROUP BY p.user_id, p.name;

CREATE VIEW vw_process_bottlenecks AS
SELECT
    pd.name AS process_name,
    t.name AS task_name,
    tp.avg_duration,
    tp.max_duration,
    COUNT(DISTINCT ti.id) AS task_instances,
    RANK() OVER (PARTITION BY pd.id ORDER BY tp.avg_duration DESC) AS duration_rank
FROM task_performance tp
JOIN tasks t ON tp.task_id = t.id
JOIN process_definitions pd ON t.process_id = pd.id
WHERE tp.avg_duration IS NOT NULL;

COMMIT;
