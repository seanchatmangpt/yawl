-- Flyway Migration: Enforce immutable audit log at DB level
-- Creates write-once enforcement for YAWL security audit table

CREATE TABLE IF NOT EXISTS yawl_audit_log (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    username VARCHAR(255),
    source_ip VARCHAR(45),
    result VARCHAR(50) NOT NULL,
    detail TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON yawl_audit_log(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_username ON yawl_audit_log(username);
CREATE INDEX IF NOT EXISTS idx_audit_event_type ON yawl_audit_log(event_type);

-- Prevent UPDATE operations (immutability enforcement)
CREATE OR REPLACE FUNCTION audit_prevent_update()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit log is immutable: UPDATE not allowed on yawl_audit_log';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_immutable_update
BEFORE UPDATE ON yawl_audit_log
FOR EACH ROW
EXECUTE FUNCTION audit_prevent_update();

-- Prevent DELETE operations (immutability enforcement)
CREATE OR REPLACE FUNCTION audit_prevent_delete()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit log is immutable: DELETE not allowed on yawl_audit_log';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_immutable_delete
BEFORE DELETE ON yawl_audit_log
FOR EACH ROW
EXECUTE FUNCTION audit_prevent_delete();

-- Grant permissions: only INSERT allowed for application user
REVOKE ALL ON yawl_audit_log FROM PUBLIC;
GRANT INSERT, SELECT ON yawl_audit_log TO yawl_user;
GRANT USAGE ON SEQUENCE yawl_audit_log_id_seq TO yawl_user;
