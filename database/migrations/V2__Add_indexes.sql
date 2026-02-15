-- YAWL Database Schema - Index Creation
-- Version: 2.0.0
-- Creates indexes for optimal query performance

-- =============================================================================
-- SPECIFICATION INDEXES
-- =============================================================================

-- Index for specification lookups by name
CREATE INDEX IF NOT EXISTS idx_specifications_name ON specifications(spec_name);

-- Index for specification lookups by status
CREATE INDEX IF NOT EXISTS idx_specifications_status ON specifications(status);

-- Index for specification version lookups
CREATE INDEX IF NOT EXISTS idx_specifications_version ON specifications(spec_name, spec_version);

-- Composite index for specification filtering
CREATE INDEX IF NOT EXISTS idx_specifications_composite ON specifications(status, created_at DESC);

-- =============================================================================
-- NET INSTANCE INDEXES
-- =============================================================================

-- Index for case lookups
CREATE INDEX IF NOT EXISTS idx_net_instances_case_id ON net_instances(case_id);

-- Index for specification to instances lookup
CREATE INDEX IF NOT EXISTS idx_net_instances_spec_id ON net_instances(spec_id);

-- Index for status-based queries
CREATE INDEX IF NOT EXISTS idx_net_instances_status ON net_instances(status);

-- Index for running instances
CREATE INDEX IF NOT EXISTS idx_net_instances_running ON net_instances(status, started_at) WHERE status = 'RUNNING';

-- Index for parent case lookups
CREATE INDEX IF NOT EXISTS idx_net_instances_parent ON net_instances(parent_case_id);

-- Index for time-based queries
CREATE INDEX IF NOT EXISTS idx_net_instances_time ON net_instances(started_at DESC, completed_at DESC);

-- Composite index for dashboard queries
CREATE INDEX IF NOT EXISTS idx_net_instances_dashboard ON net_instances(status, started_at DESC, spec_id);

-- =============================================================================
-- TASK INDEXES
-- =============================================================================

-- Index for instance to tasks lookup
CREATE INDEX IF NOT EXISTS idx_tasks_instance_id ON tasks(instance_id);

-- Index for task status queries
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);

-- Index for task name lookups
CREATE INDEX IF NOT EXISTS idx_tasks_name ON tasks(task_name);

-- Index for timer-based task queries
CREATE INDEX IF NOT EXISTS idx_tasks_timer ON tasks(timer_expiry) WHERE timer_expiry IS NOT NULL;

-- Index for failed tasks
CREATE INDEX IF NOT EXISTS idx_tasks_failed ON tasks(status, failed_at) WHERE status = 'FAILED';

-- Composite index for active tasks
CREATE INDEX IF NOT EXISTS idx_tasks_active ON tasks(instance_id, status, enabled_at DESC);

-- Index for retry processing
CREATE INDEX IF NOT EXISTS idx_tasks_retry ON tasks(retry_count, status) WHERE retry_count > 0 AND status = 'FAILED';

-- =============================================================================
-- WORK ITEM INDEXES
-- =============================================================================

-- Index for task to work items lookup
CREATE INDEX IF NOT EXISTS idx_work_items_task_id ON work_items(task_id);

-- Index for case to work items lookup
CREATE INDEX IF NOT EXISTS idx_work_items_case_id ON work_items(case_id);

-- Index for resource allocation
CREATE INDEX IF NOT EXISTS idx_work_items_resource ON work_items(resource_id, resource_type);

-- Index for work item status queries
CREATE INDEX IF NOT EXISTS idx_work_items_status ON work_items(status);

-- Index for priority-based queries
CREATE INDEX IF NOT EXISTS idx_work_items_priority ON work_items(priority DESC, created_at);

-- Composite index for work queue queries
CREATE INDEX IF NOT EXISTS idx_work_items_queue ON work_items(status, resource_id, priority DESC);

-- Index for spec-based work item queries
CREATE INDEX IF NOT EXISTS idx_work_items_spec ON work_items(spec_id, status);

-- Index for time-based work item analytics
CREATE INDEX IF NOT EXISTS idx_work_items_time ON work_items(created_at DESC, completed_at DESC);

-- =============================================================================
-- PARTICIPANT INDEXES
-- =============================================================================

-- Index for user authentication
CREATE INDEX IF NOT EXISTS idx_participants_user_id ON participants(user_id);

-- Index for email lookups
CREATE INDEX IF NOT EXISTS idx_participants_email ON participants(email);

-- Index for participant status
CREATE INDEX IF NOT EXISTS idx_participants_status ON participants(status);

-- Index for last login tracking
CREATE INDEX IF NOT EXISTS idx_participants_login ON participants(last_login DESC);

-- =============================================================================
-- ROLE INDEXES
-- =============================================================================

-- Index for role assignments
CREATE INDEX IF NOT EXISTS idx_participant_roles_role ON participant_roles(role_id);

-- Index for participant role lookups
CREATE INDEX IF NOT EXISTS idx_participant_roles_participant ON participant_roles(participant_id);

-- =============================================================================
-- CAPABILITY INDEXES
-- =============================================================================

-- Index for capability type lookups
CREATE INDEX IF NOT EXISTS idx_capabilities_type ON capabilities(capability_type);

-- Index for capability status
CREATE INDEX IF NOT EXISTS idx_capabilities_status ON capabilities(status);

-- =============================================================================
-- WORK QUEUE INDEXES
-- =============================================================================

-- Index for queue type lookups
CREATE INDEX IF NOT EXISTS idx_work_queues_type ON work_queues(queue_type);

-- Index for owner-based queries
CREATE INDEX IF NOT EXISTS idx_work_queues_owner ON work_queues(owner_id);

-- Index for queue items status
CREATE INDEX IF NOT EXISTS idx_queue_items_status ON queue_items(status);

-- Index for queue-based item lookups
CREATE INDEX IF NOT EXISTS idx_queue_items_queue ON queue_items(queue_id, added_at DESC);

-- =============================================================================
-- WORKLET INDEXES
-- =============================================================================

-- Index for rule type lookups
CREATE INDEX IF NOT EXISTS idx_worklet_rules_type ON worklet_rules(rule_type);

-- Index for enabled rules
CREATE INDEX IF NOT EXISTS idx_worklet_rules_enabled ON worklet_rules(enabled, priority DESC);

-- Index for exception case lookups
CREATE INDEX IF NOT EXISTS idx_worklet_exceptions_case ON worklet_exceptions(case_id);

-- Index for exception type lookups
CREATE INDEX IF NOT EXISTS idx_worklet_exceptions_type ON worklet_exceptions(exception_type);

-- Index for exception status
CREATE INDEX IF NOT EXISTS idx_worklet_exceptions_status ON worklet_exceptions(status, raised_at DESC);

-- Index for task exceptions
CREATE INDEX IF NOT EXISTS idx_worklet_exceptions_task ON worklet_exceptions(task_id);

-- =============================================================================
-- AUDIT LOG INDEXES
-- =============================================================================

-- Index for event type lookups
CREATE INDEX IF NOT EXISTS idx_audit_log_event_type ON audit_log(event_type);

-- Index for user-based audit queries
CREATE INDEX IF NOT EXISTS idx_audit_log_user ON audit_log(user_id);

-- Index for resource-based audit queries
CREATE INDEX IF NOT EXISTS idx_audit_log_resource ON audit_log(resource_type, resource_id);

-- Index for time-based audit queries
CREATE INDEX IF NOT EXISTS idx_audit_log_time ON audit_log(event_timestamp DESC);

-- Composite index for audit searches
CREATE INDEX IF NOT EXISTS idx_audit_log_search ON audit_log(event_type, event_timestamp DESC, user_id);

-- Index for session tracking
CREATE INDEX IF NOT EXISTS idx_audit_log_session ON audit_log(session_id);

-- =============================================================================
-- EVENT LOG INDEXES (Process Mining)
-- =============================================================================

-- Index for case-based event queries
CREATE INDEX IF NOT EXISTS idx_event_log_case ON event_log(case_id, timestamp);

-- Index for activity-based queries
CREATE INDEX IF NOT EXISTS idx_event_log_activity ON event_log(activity);

-- Index for time-based event queries
CREATE INDEX IF NOT EXISTS idx_event_log_time ON event_log(timestamp DESC);

-- Index for resource-based queries
CREATE INDEX IF NOT EXISTS idx_event_log_resource ON event_log(resource);

-- Index for process-based queries
CREATE INDEX IF NOT EXISTS idx_event_log_process ON event_log(process_id, timestamp);

-- Composite index for process mining queries
CREATE INDEX IF NOT EXISTS idx_event_log_mining ON event_log(case_id, activity, timestamp, resource);

-- =============================================================================
-- METRICS INDEXES
-- =============================================================================

-- Index for metric name lookups
CREATE INDEX IF NOT EXISTS idx_engine_metrics_name ON engine_metrics(metric_name);

-- Index for time-based metric queries
CREATE INDEX IF NOT EXISTS idx_engine_metrics_time ON engine_metrics(collected_at DESC);

-- Composite index for metric analysis
CREATE INDEX IF NOT EXISTS idx_engine_metrics_analysis ON engine_metrics(metric_name, collected_at DESC);

-- Index for performance snapshots
CREATE INDEX IF NOT EXISTS idx_performance_snapshots_time ON performance_snapshots(snapshot_timestamp DESC);

-- =============================================================================
-- CONFIGURATION INDEXES
-- =============================================================================

-- Index for service type lookups
CREATE INDEX IF NOT EXISTS idx_service_registry_type ON service_registry(service_type);

-- Index for service status
CREATE INDEX IF NOT EXISTS idx_service_registry_status ON service_registry(status);

-- Index for heartbeat monitoring
CREATE INDEX IF NOT EXISTS idx_service_registry_heartbeat ON service_registry(last_heartbeat);

-- =============================================================================
-- SCHEDULING INDEXES
-- =============================================================================

-- Index for scheduled task time queries
CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_time ON scheduled_tasks(scheduled_start, scheduled_end);

-- Index for scheduled task status
CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_status ON scheduled_tasks(status);

-- Index for case-based scheduling queries
CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_case ON scheduled_tasks(case_id);

-- Index for resource scheduling
CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_resource ON scheduled_tasks(resource_id, scheduled_start);

-- =============================================================================
-- COST INDEXES
-- =============================================================================

-- Index for case cost queries
CREATE INDEX IF NOT EXISTS idx_cost_records_case ON cost_records(case_id);

-- Index for cost type lookups
CREATE INDEX IF NOT EXISTS idx_cost_records_type ON cost_records(cost_type);

-- Index for time-based cost queries
CREATE INDEX IF NOT EXISTS idx_cost_records_time ON cost_records(recorded_at DESC);

-- Index for budget allocations by fiscal year
CREATE INDEX IF NOT EXISTS idx_budget_allocations_year ON budget_allocations(fiscal_year);

-- Index for department budget lookups
CREATE INDEX IF NOT EXISTS idx_budget_allocations_dept ON budget_allocations(department, fiscal_year);

-- =============================================================================
-- PARTIAL INDEXES FOR COMMON QUERY PATTERNS
-- =============================================================================

-- Active cases (running instances)
CREATE INDEX IF NOT EXISTS idx_net_instances_active ON net_instances(case_id, started_at)
    WHERE status IN ('RUNNING', 'SUSPENDED');

-- Pending work items
CREATE INDEX IF NOT EXISTS idx_work_items_pending ON work_items(work_item_id, priority DESC, created_at)
    WHERE status IN ('ENABLED', 'OFFERED', 'ALLOCATED', 'STARTED');

-- Unhandled exceptions
CREATE INDEX IF NOT EXISTS idx_worklet_exceptions_unhandled ON worklet_exceptions(exception_id, raised_at)
    WHERE status = 'RAISED';

-- Failed tasks needing retry
CREATE INDEX IF NOT EXISTS idx_tasks_retry_pending ON tasks(task_id, retry_count)
    WHERE status = 'FAILED' AND retry_count < max_retries;
