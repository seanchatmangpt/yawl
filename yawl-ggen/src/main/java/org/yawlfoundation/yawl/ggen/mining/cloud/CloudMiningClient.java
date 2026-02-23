package org.yawlfoundation.yawl.ggen.mining.cloud;

import java.io.IOException;
import java.util.Map;

/**
 * Interface for cloud-based process mining platform integrations.
 * Implementations provide access to discovered process models and metrics
 * from platforms like Celonis, UiPath, Signavio, SAP Analytics Cloud.
 */
public interface CloudMiningClient {

    /**
     * Authenticate with the cloud service.
     * Implementation varies: OAuth 2.0, API keys, tokens, etc.
     */
    void authenticate() throws IOException;

    /**
     * List all discovered process models accessible via this client.
     * @return Map of process ID → process name
     */
    Map<String, Object> listProcessModels() throws IOException;

    /**
     * Get conformance metrics for a discovered process.
     * @return ConformanceMetrics with fitness, precision, generalization
     */
    CelonicsMiningClient.ConformanceMetrics getConformanceMetrics(String processId) throws IOException;

    /**
     * Export a discovered process model as BPMN XML.
     * @return BPMN XML string
     */
    String exportProcessAsBpmn(String processId) throws IOException;

    /**
     * Get the event log for a process (for reprocessing or analysis).
     * @return Event log in CSV or XES format
     */
    String getEventLog(String processId) throws IOException;
}
