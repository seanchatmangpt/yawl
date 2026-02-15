-- YAWL Database Schema - Multi-Tenancy Support
-- Version: 4.0.0
-- Adds tenant isolation, data segregation, and tenant management

-- =============================================================================
-- TENANT MANAGEMENT TABLES
-- =============================================================================

-- Tenants table - root level tenant definition
CREATE TABLE IF NOT EXISTS tenants (
    tenant_id VARCHAR(255) NOT NULL,
    tenant_code VARCHAR(50) NOT NULL UNIQUE,
    tenant_name VARCHAR(255) NOT NULL,
    description VARCHAR(1024),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    tier VARCHAR(50) NOT NULL DEFAULT 'STANDARD', -- FREE, STANDARD, PREMIUM, ENTERPRISE
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    settings JSON,
    limits JSON,
    metadata JSON,
    PRIMARY KEY (tenant_id)
);

-- Tenant limits configuration
COMMENT ON COLUMN tenants.limits IS 'JSON object containing: maxUsers, maxCases, maxSpecs, storageLimitMB, apiRateLimit';

-- Tenant settings table - per-tenant configuration
CREATE TABLE IF NOT EXISTS tenant_settings (
    setting_id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    setting_key VARCHAR(255) NOT NULL,
    setting_value VARCHAR(4096),
    setting_type VARCHAR(50) DEFAULT 'STRING',
    is_encrypted BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255),
    PRIMARY KEY (setting_id),
    CONSTRAINT fk_tenant_settings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    CONSTRAINT uk_tenant_setting UNIQUE (tenant_id, setting_key)
);

-- Tenant usage tracking
CREATE TABLE IF NOT EXISTS tenant_usage (
    usage_id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    usage_date DATE NOT NULL,
    active_users INTEGER DEFAULT 0,
    active_cases INTEGER DEFAULT 0,
    completed_cases INTEGER DEFAULT 0,
    storage_used_mb DECIMAL(10,2) DEFAULT 0,
    api_calls INTEGER DEFAULT 0,
    compute_seconds BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (usage_id),
    CONSTRAINT fk_tenant_usage_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    CONSTRAINT uk_tenant_usage_date UNIQUE (tenant_id, usage_date)
);

-- Tenant billing records
CREATE TABLE IF NOT EXISTS tenant_billing (
    billing_id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    billing_period VARCHAR(7) NOT NULL, -- YYYY-MM format
    amount DECIMAL(15,4) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    invoice_url VARCHAR(1024),
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_date TIMESTAMP,
    line_items JSON,
    PRIMARY KEY (billing_id),
    CONSTRAINT fk_tenant_billing_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id)
);

-- =============================================================================
-- ADD TENANT_ID TO ALL EXISTING TABLES
-- =============================================================================

-- Specifications
ALTER TABLE specifications ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE specifications ADD CONSTRAINT fk_spec_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Net instances
ALTER TABLE net_instances ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE net_instances ADD CONSTRAINT fk_net_instance_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Tasks
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE tasks ADD CONSTRAINT fk_task_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Work items
ALTER TABLE work_items ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE work_items ADD CONSTRAINT fk_work_item_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Participants
ALTER TABLE participants ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE participants ADD CONSTRAINT fk_participant_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Roles (tenant-specific roles)
ALTER TABLE roles ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE roles ADD COLUMN IF NOT EXISTS is_global BOOLEAN DEFAULT FALSE;
ALTER TABLE roles ADD CONSTRAINT fk_role_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Capabilities
ALTER TABLE capabilities ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE capabilities ADD CONSTRAINT fk_capability_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Work queues
ALTER TABLE work_queues ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE work_queues ADD CONSTRAINT fk_work_queue_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Worklet rules
ALTER TABLE worklet_rules ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE worklet_rules ADD CONSTRAINT fk_worklet_rule_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Worklet exceptions
ALTER TABLE worklet_exceptions ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE worklet_exceptions ADD CONSTRAINT fk_worklet_exception_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Audit log
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE audit_log ADD CONSTRAINT fk_audit_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Event log
ALTER TABLE event_log ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE event_log ADD CONSTRAINT fk_event_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Scheduled tasks
ALTER TABLE scheduled_tasks ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE scheduled_tasks ADD CONSTRAINT fk_scheduled_task_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Cost records
ALTER TABLE cost_records ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE cost_records ADD CONSTRAINT fk_cost_record_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- Budget allocations
ALTER TABLE budget_allocations ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE budget_allocations ADD CONSTRAINT fk_budget_allocation_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- =============================================================================
-- TENANT-SPECIFIC INDEXES
-- =============================================================================

-- Tenant isolation indexes
CREATE INDEX IF NOT EXISTS idx_specifications_tenant ON specifications(tenant_id);
CREATE INDEX IF NOT EXISTS idx_net_instances_tenant ON net_instances(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tasks_tenant ON tasks(tenant_id);
CREATE INDEX IF NOT EXISTS idx_work_items_tenant ON work_items(tenant_id);
CREATE INDEX IF NOT EXISTS idx_participants_tenant ON participants(tenant_id);
CREATE INDEX IF NOT EXISTS idx_roles_tenant ON roles(tenant_id);
CREATE INDEX IF NOT EXISTS idx_capabilities_tenant ON capabilities(tenant_id);
CREATE INDEX IF NOT EXISTS idx_work_queues_tenant ON work_queues(tenant_id);
CREATE INDEX IF NOT EXISTS idx_worklet_rules_tenant ON worklet_rules(tenant_id);
CREATE INDEX IF NOT EXISTS idx_worklet_exceptions_tenant ON worklet_exceptions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant ON audit_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_event_log_tenant ON event_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_tenant ON scheduled_tasks(tenant_id);
CREATE INDEX IF NOT EXISTS idx_cost_records_tenant ON cost_records(tenant_id);
CREATE INDEX IF NOT EXISTS idx_budget_allocations_tenant ON budget_allocations(tenant_id);

-- Composite indexes for common tenant queries
CREATE INDEX IF NOT EXISTS idx_net_instances_tenant_status ON net_instances(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_work_items_tenant_status ON work_items(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_participants_tenant_status ON participants(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_time ON audit_log(tenant_id, event_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_event_log_tenant_time ON event_log(tenant_id, timestamp DESC);

-- =============================================================================
-- ROW LEVEL SECURITY POLICIES (PostgreSQL)
-- =============================================================================

-- Enable RLS on all tenant-aware tables
ALTER TABLE specifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE net_instances ENABLE ROW LEVEL SECURITY;
ALTER TABLE tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE work_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE capabilities ENABLE ROW LEVEL SECURITY;
ALTER TABLE work_queues ENABLE ROW LEVEL SECURITY;
ALTER TABLE worklet_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE worklet_exceptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE event_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE scheduled_tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE cost_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE budget_allocations ENABLE ROW LEVEL SECURITY;

-- Create RLS policies for specifications
CREATE POLICY tenant_isolation_specifications ON specifications
    USING (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR);

-- Create RLS policies for net_instances
CREATE POLICY tenant_isolation_net_instances ON net_instances
    USING (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR);

-- Create RLS policies for tasks
CREATE POLICY tenant_isolation_tasks ON tasks
    USING (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR);

-- Create RLS policies for work_items
CREATE POLICY tenant_isolation_work_items ON work_items
    USING (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR);

-- Create RLS policies for participants
CREATE POLICY tenant_isolation_participants ON participants
    USING (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR);

-- Create RLS policies for roles (global roles visible to all)
CREATE POLICY tenant_isolation_roles ON roles
    USING (is_global = TRUE OR tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR);

-- Create RLS policies for capabilities
CREATE POLICY tenant_isolation_capabilities ON capabilities
    USING (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR);

-- Create RLS policies for work_queues
CREATE POLICY tenant_isolation_work_queues ON work_queues
    USING (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR);

-- Create RLS policies for worklet_rules
CREATE POLICY tenant_isolation_worklet_rules ON worklet_rules
    USING (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR);

-- Create RLS policies for worklet_exceptions
CREATE POLICY tenant_isolation_worklet_exceptions ON worklet_exceptions
    USING (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR);

-- Create RLS policies for audit_log
CREATE POLICY tenant_isolation_audit_log ON audit_log
    USING (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR);

-- Create RLS policies for event_log
CREATE POLICY tenant_isolation_event_log ON event_log
    USING (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR);

-- Create RLS policies for scheduled_tasks
CREATE POLICY tenant_isolation_scheduled_tasks ON scheduled_tasks
    USING (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR);

-- Create RLS policies for cost_records
CREATE POLICY tenant_isolation_cost_records ON cost_records
    USING (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR);

-- Create RLS policies for budget_allocations
CREATE POLICY tenant_isolation_budget_allocations ON budget_allocations
    USING (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', TRUE)::VARCHAR);

-- =============================================================================
-- TENANT-SPECIFIC MATERIALIZED VIEWS
-- =============================================================================

-- Per-tenant case statistics
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_tenant_case_statistics AS
SELECT
    tenant_id,
    spec_id,
    status,
    COUNT(*) AS case_count,
    AVG(EXTRACT(EPOCH FROM (COALESCE(completed_at, CURRENT_TIMESTAMP) - started_at))/3600) AS avg_duration_hours,
    MIN(started_at) AS earliest_start,
    MAX(COALESCE(completed_at, CURRENT_TIMESTAMP)) AS latest_activity
FROM net_instances
GROUP BY tenant_id, spec_id, status;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_tenant_case_stats_id ON mv_tenant_case_statistics(tenant_id, spec_id, status);

-- Per-tenant resource workload
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_tenant_resource_workload AS
SELECT
    wi.tenant_id,
    wi.resource_id,
    p.full_name AS resource_name,
    COUNT(*) AS total_work_items,
    COUNT(CASE WHEN wi.status = 'STARTED' THEN 1 END) AS active_items,
    COUNT(CASE WHEN wi.status IN ('ENABLED', 'OFFERED') THEN 1 END) AS pending_items,
    AVG(wi.priority) AS avg_priority
FROM work_items wi
LEFT JOIN participants p ON wi.resource_id = p.participant_id AND wi.tenant_id = p.tenant_id
WHERE wi.status IN ('ENABLED', 'OFFERED', 'ALLOCATED', 'STARTED')
GROUP BY wi.tenant_id, wi.resource_id, p.full_name;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_tenant_resource_workload_id ON mv_tenant_resource_workload(tenant_id, resource_id);

-- =============================================================================
-- TENANT MANAGEMENT FUNCTIONS
-- =============================================================================

-- Function to create a new tenant
CREATE OR REPLACE FUNCTION create_tenant(
    p_tenant_code VARCHAR,
    p_tenant_name VARCHAR,
    p_tier VARCHAR DEFAULT 'STANDARD',
    p_settings JSON DEFAULT '{}',
    p_limits JSON DEFAULT '{}'
)
RETURNS VARCHAR
LANGUAGE plpgsql
AS $$
DECLARE
    v_tenant_id VARCHAR;
BEGIN
    v_tenant_id := 'TENANT_' || REPLACE(GEN_RANDOM_UUID()::TEXT, '-', '');

    INSERT INTO tenants (
        tenant_id,
        tenant_code,
        tenant_name,
        tier,
        settings,
        limits
    ) VALUES (
        v_tenant_id,
        p_tenant_code,
        p_tenant_name,
        p_tier,
        p_settings,
        p_limits
    );

    -- Create default tenant settings
    INSERT INTO tenant_settings (setting_id, tenant_id, setting_key, setting_value, setting_type)
    SELECT
        'TS_' || REPLACE(GEN_RANDOM_UUID()::TEXT, '-', ''),
        v_tenant_id,
        key,
        value,
        'STRING'
    FROM jsonb_each_text(p_settings::jsonb);

    RETURN v_tenant_id;
END;
$$;

-- Function to set tenant context
CREATE OR REPLACE FUNCTION set_tenant_context(p_tenant_id VARCHAR)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM set_config('app.current_tenant', p_tenant_id, FALSE);
END;
$$;

-- Function to check tenant limits
CREATE OR REPLACE FUNCTION check_tenant_limit(
    p_tenant_id VARCHAR,
    p_limit_type VARCHAR -- 'users', 'cases', 'specs', 'storage'
)
RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
DECLARE
    v_limit_value INTEGER;
    v_current_value INTEGER;
BEGIN
    -- Get limit from tenant configuration
    SELECT (limits->p_limit_type)::INTEGER INTO v_limit_value
    FROM tenants WHERE tenant_id = p_tenant_id;

    -- If no limit set, allow unlimited
    IF v_limit_value IS NULL OR v_limit_value = 0 THEN
        RETURN TRUE;
    END IF;

    -- Get current usage based on limit type
    CASE p_limit_type
        WHEN 'users' THEN
            SELECT COUNT(*) INTO v_current_value
            FROM participants WHERE tenant_id = p_tenant_id AND status = 'ACTIVE';
        WHEN 'cases' THEN
            SELECT COUNT(*) INTO v_current_value
            FROM net_instances WHERE tenant_id = p_tenant_id;
        WHEN 'specs' THEN
            SELECT COUNT(*) INTO v_current_value
            FROM specifications WHERE tenant_id = p_tenant_id AND status = 'ACTIVE';
        WHEN 'storage' THEN
            SELECT COALESCE(SUM(storage_used_mb), 0)::INTEGER INTO v_current_value
            FROM tenant_usage WHERE tenant_id = p_tenant_id AND usage_date = CURRENT_DATE;
        ELSE
            RETURN TRUE;
    END CASE;

    RETURN v_current_value < v_limit_value;
END;
$$;

-- Function to update tenant usage
CREATE OR REPLACE FUNCTION update_tenant_usage(p_tenant_id VARCHAR)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_usage_id VARCHAR;
BEGIN
    -- Upsert usage record for today
    INSERT INTO tenant_usage (
        usage_id,
        tenant_id,
        usage_date,
        active_users,
        active_cases,
        completed_cases,
        api_calls
    )
    SELECT
        'USAGE_' || REPLACE(GEN_RANDOM_UUID()::TEXT, '-', ''),
        p_tenant_id,
        CURRENT_DATE,
        (SELECT COUNT(*) FROM participants WHERE tenant_id = p_tenant_id AND status = 'ACTIVE'),
        (SELECT COUNT(*) FROM net_instances WHERE tenant_id = p_tenant_id AND status = 'RUNNING'),
        (SELECT COUNT(*) FROM net_instances WHERE tenant_id = p_tenant_id AND status = 'COMPLETED' AND DATE(completed_at) = CURRENT_DATE),
        0
    ON CONFLICT (tenant_id, usage_date) DO UPDATE SET
        active_users = EXCLUDED.active_users,
        active_cases = EXCLUDED.active_cases,
        completed_cases = EXCLUDED.completed_cases;
END;
$$;

-- Function to get tenant statistics
CREATE OR REPLACE FUNCTION get_tenant_statistics(p_tenant_id VARCHAR)
RETURNS JSON
LANGUAGE plpgsql
AS $$
DECLARE
    v_stats JSON;
BEGIN
    SELECT json_build_object(
        'tenant_id', p_tenant_id,
        'users', (SELECT COUNT(*) FROM participants WHERE tenant_id = p_tenant_id),
        'active_users', (SELECT COUNT(*) FROM participants WHERE tenant_id = p_tenant_id AND status = 'ACTIVE'),
        'total_cases', (SELECT COUNT(*) FROM net_instances WHERE tenant_id = p_tenant_id),
        'active_cases', (SELECT COUNT(*) FROM net_instances WHERE tenant_id = p_tenant_id AND status = 'RUNNING'),
        'completed_cases', (SELECT COUNT(*) FROM net_instances WHERE tenant_id = p_tenant_id AND status = 'COMPLETED'),
        'failed_cases', (SELECT COUNT(*) FROM net_instances WHERE tenant_id = p_tenant_id AND status = 'FAILED'),
        'specifications', (SELECT COUNT(*) FROM specifications WHERE tenant_id = p_tenant_id),
        'active_specifications', (SELECT COUNT(*) FROM specifications WHERE tenant_id = p_tenant_id AND status = 'ACTIVE'),
        'pending_work_items', (SELECT COUNT(*) FROM work_items WHERE tenant_id = p_tenant_id AND status IN ('ENABLED', 'OFFERED'))
    ) INTO v_stats;

    RETURN v_stats;
END;
$$;

-- =============================================================================
-- INITIAL TENANT DATA
-- =============================================================================

-- Create default system tenant
INSERT INTO tenants (tenant_id, tenant_code, tenant_name, tier, limits, settings)
VALUES (
    'TENANT_SYSTEM',
    'SYSTEM',
    'System Tenant',
    'ENTERPRISE',
    '{"maxUsers": -1, "maxCases": -1, "maxSpecs": -1, "storageLimitMB": -1}',
    '{"description": "System management tenant", "features": ["all"]}'
) ON CONFLICT (tenant_code) DO NOTHING;

-- Create default demo tenant
INSERT INTO tenants (tenant_id, tenant_code, tenant_name, tier, limits, settings)
VALUES (
    'TENANT_DEMO',
    'DEMO',
    'Demo Tenant',
    'FREE',
    '{"maxUsers": 5, "maxCases": 100, "maxSpecs": 10, "storageLimitMB": 100}',
    '{"description": "Demo tenant for evaluation", "features": ["basic"], "expiresAt": "2099-12-31"}'
) ON CONFLICT (tenant_code) DO NOTHING;

-- Update existing data to belong to system tenant (migration)
UPDATE specifications SET tenant_id = 'TENANT_SYSTEM' WHERE tenant_id IS NULL;
UPDATE net_instances SET tenant_id = 'TENANT_SYSTEM' WHERE tenant_id IS NULL;
UPDATE tasks SET tenant_id = 'TENANT_SYSTEM' WHERE tenant_id IS NULL;
UPDATE work_items SET tenant_id = 'TENANT_SYSTEM' WHERE tenant_id IS NULL;
UPDATE participants SET tenant_id = 'TENANT_SYSTEM' WHERE tenant_id IS NULL;
UPDATE roles SET tenant_id = 'TENANT_SYSTEM', is_global = TRUE WHERE tenant_id IS NULL;
UPDATE capabilities SET tenant_id = 'TENANT_SYSTEM' WHERE tenant_id IS NULL;
UPDATE work_queues SET tenant_id = 'TENANT_SYSTEM' WHERE tenant_id IS NULL;
UPDATE worklet_rules SET tenant_id = 'TENANT_SYSTEM' WHERE tenant_id IS NULL;
UPDATE worklet_exceptions SET tenant_id = 'TENANT_SYSTEM' WHERE tenant_id IS NULL;
UPDATE audit_log SET tenant_id = 'TENANT_SYSTEM' WHERE tenant_id IS NULL;
UPDATE event_log SET tenant_id = 'TENANT_SYSTEM' WHERE tenant_id IS NULL;
UPDATE scheduled_tasks SET tenant_id = 'TENANT_SYSTEM' WHERE tenant_id IS NULL;
UPDATE cost_records SET tenant_id = 'TENANT_SYSTEM' WHERE tenant_id IS NULL;
UPDATE budget_allocations SET tenant_id = 'TENANT_SYSTEM' WHERE tenant_id IS NULL;

-- Add NOT NULL constraints after data migration
ALTER TABLE specifications ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE net_instances ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE tasks ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE work_items ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE participants ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE roles ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE capabilities ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE work_queues ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE worklet_rules ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE worklet_exceptions ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE audit_log ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE event_log ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE scheduled_tasks ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE cost_records ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE budget_allocations ALTER COLUMN tenant_id SET NOT NULL;
