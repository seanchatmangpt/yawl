package org.yawlfoundation.yawl.integration;

import org.yawlfoundation.yawl.logging.table.YAuditEvent;

/**
 * Pluggable sink for immutable audit log shipping to external compliance systems.
 * Routes events to AWS CloudTrail, GCP Cloud Audit Logs, Azure Monitor, or Syslog.
 * @since YAWL 6.0
 */
public interface AuditSink {
    void ship(YAuditEvent event) throws AuditShippingException;
    String sinkName();
    boolean isHealthy();
}
