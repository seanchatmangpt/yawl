-- ============================================================================
-- YAWL Database Performance Optimization - Critical Indexes
-- Version: 1.0.0
-- Target: Sub-second query response for work item and case operations
-- ============================================================================

-- Work Items table - most frequently queried by status, case, spec
CREATE INDEX IF NOT EXISTS idx_work_items_status ON Work_Items(status);
CREATE INDEX IF NOT EXISTS idx_work_items_spec_id ON Work_Items(specID);
CREATE INDEX IF NOT EXISTS idx_work_items_spec_version ON Work_Items(specVersion);
CREATE INDEX IF NOT EXISTS idx_work_items_enablement_time ON Work_Items(enablement_time);
CREATE INDEX IF NOT EXISTS idx_work_items_parent ON Work_Items(parent);
CREATE INDEX IF NOT EXISTS idx_work_items_owner ON Work_Items(ownerService);

-- Composite index for common query pattern: status + spec lookup
CREATE INDEX IF NOT EXISTS idx_work_items_status_spec ON Work_Items(status, specID);

-- Net Runner states - queried by case ID and spec
CREATE INDEX IF NOT EXISTS idx_runner_case_id ON RUNNER_STATES(case_id);
CREATE INDEX IF NOT EXISTS idx_runner_spec_id ON RUNNER_STATES(specID);
CREATE INDEX IF NOT EXISTS idx_runner_status ON RUNNER_STATES(executionStatus);
CREATE INDEX IF NOT EXISTS idx_runner_start_time ON RUNNER_STATES(startTime);

-- Identifiers table
CREATE INDEX IF NOT EXISTS idx_identifier_case_id ON identifiers(caseID);

-- Case number store
CREATE INDEX IF NOT EXISTS idx_case_nbr_spec ON CaseNbrStore(specID);

-- Specifications
CREATE INDEX IF NOT EXISTS idx_spec_identifier ON specifications(identifier);
CREATE INDEX IF NOT EXISTS idx_spec_uri ON specifications(uri);

-- ============================================================================
-- Logging tables - high-growth tables need time-based indexes for archiving
-- ============================================================================

-- Log events - most critical for audit and archiving
CREATE INDEX IF NOT EXISTS idx_log_event_time ON logEvent(eventTime);
CREATE INDEX IF NOT EXISTS idx_log_event_instance ON logEvent(instanceID);
CREATE INDEX IF NOT EXISTS idx_log_event_root_net ON logEvent(rootNetInstanceID);
CREATE INDEX IF NOT EXISTS idx_log_event_service ON logEvent(serviceID);

-- Composite index for time-based archiving queries
CREATE INDEX IF NOT EXISTS idx_log_event_archive ON logEvent(eventTime, rootNetInstanceID);

-- Log task instances
CREATE INDEX IF NOT EXISTS idx_log_task_inst_task ON logTaskInstance(taskID);
CREATE INDEX IF NOT EXISTS idx_log_task_inst_net ON logTaskInstance(parentNetInstanceID);
CREATE INDEX IF NOT EXISTS idx_log_task_inst_engine ON logTaskInstance(engineInstanceID);

-- Log net instances
CREATE INDEX IF NOT EXISTS idx_log_net_inst_net ON logNetInstance(netID);
CREATE INDEX IF NOT EXISTS idx_log_net_inst_root ON logNetInstance(rootNetInstanceID);

-- Audit events
CREATE INDEX IF NOT EXISTS idx_audit_event_time ON auditEvent(eventTime);
CREATE INDEX IF NOT EXISTS idx_audit_event_type ON auditEvent(eventType);

-- ============================================================================
-- Foreign key indexes (if not automatically created)
-- ============================================================================

-- YNetData foreign keys
CREATE INDEX IF NOT EXISTS idx_net_data_case ON netdata(case_id);

-- Work item timers
CREATE INDEX IF NOT EXISTS idx_work_item_timer_item ON workitemtimer(workItemID);
CREATE INDEX IF NOT EXISTS idx_work_item_timer_expiry ON workitemtimer(expiry);

-- Problems/exceptions
CREATE INDEX IF NOT EXISTS idx_problem_time ON problems(createdTime);
CREATE INDEX IF NOT EXISTS idx_problem_severity ON problems(severity);

-- Documents
CREATE INDEX IF NOT EXISTS idx_document_case ON documents(caseID);
