package org.yawlfoundation.yawl.bridge.erlang.domain;

import org.yawlfoundation.yawl.bridge.erlang.ErlTerm;
import org.yawlfoundation.yawl.bridge.erlang.ErlangException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Process mining client for YAWL-Erlang bridge.
 *
 * <p>This interface provides high-level domain operations for process mining
 * capabilities, delegating to Erlang implementations through the typed bridge.</p>
 *
 * @since 1.0.0
 */
public interface ProcessMiningClient extends AutoCloseable {

    /**
     * Imports an OCEL (Object-Centric Event Log) into the process mining system.
     *
     * @param ocelPath Path to the OCEL file
     * @return The import result containing metadata
     * @throws ProcessMiningException if import fails
     */
    ImportResult importOcel(Path ocelPath) throws ProcessMiningException;

    /**
     * Performs slim link discovery on the imported event log.
     *
     * <p>This uses the heuristic-based discovery algorithm from the process mining domain.</p>
     *
     * @param parameters Additional parameters for slim link discovery
     * @return The discovery result with extracted patterns
     * @throws ProcessMiningException if discovery fails
     */
    DiscoveryResult slimLink(Map<String, ErlTerm> parameters) throws ProcessMiningException;

    /**
     * Discovers process models from OCEL data using DECLARE patterns.
     *
     * <p>This implements the OCEL-based DECLARE discovery algorithm.</p>
     *
     * @param discoveryType The type of discovery to perform
     * @param constraints Additional constraints for discovery
     * @return The discovery results
     * @throws ProcessMiningException if discovery fails
     */
    DeclareDiscoveryResult discoverOcDeclare(DiscoveryType discoveryType,
                                           Map<String, ErlTerm> constraints) throws ProcessMiningException;

    /**
     * Performs token replay analysis on the imported event log.
     *
     * <p>This simulates the execution of cases in the imported log against the discovered model.</p>
     *
     * @param logId The event log identifier
     * @param modelId The model identifier
     * @param replayParameters Parameters for the replay analysis
     * @return The replay results with alignment information
     * @throws ProcessMiningException if replay fails
     */
    ReplayResult tokenReplay(String logId, String modelId,
                            ReplayParameters replayParameters) throws ProcessMiningException;

    /**
     * Executes a custom mining query through the Erlang backend.
     *
     * @param moduleName The Erlang module name
     * @param functionName The Erlang function name
     * @param arguments The arguments for the query
     * @return The query result
     * @throws ProcessMiningException if query fails
     */
    ErlTerm executeMiningQuery(String moduleName, String functionName,
                              List<ErlTerm> arguments) throws ProcessMiningException;

    /**
     * Checks the connection status to the Erlang process mining node.
     *
     * @return true if connected and ready
     */
    boolean isConnected();

    /**
     * Returns the connection information.
     *
     * @return Connection information
     */
    ConnectionInfo getConnectionInfo();
}

/**
 * Exception thrown by process mining operations.
 */
class ProcessMiningException extends Exception {
    private final String operation;
    private final String erlModule;
    private final String erlFunction;

    public ProcessMiningException(String message, String operation) {
        super(message);
        this.operation = operation;
        this.erlModule = null;
        this.erlFunction = null;
    }

    public ProcessMiningException(String message, String operation,
                                String erlModule, String erlFunction) {
        super(message);
        this.operation = operation;
        this.erlModule = erlModule;
        this.erlFunction = erlFunction;
    }

    public ProcessMiningException(String message, String operation, Throwable cause) {
        super(message, cause);
        this.operation = operation;
        this.erlModule = null;
        this.erlFunction = null;
    }

    public ProcessMiningException(String message, String operation,
                                String erlModule, String erlFunction, Throwable cause) {
        super(message, cause);
        this.operation = operation;
        this.erlModule = erlModule;
        this.erlFunction = erlFunction;
    }

    public String getOperation() {
        return operation;
    }

    public String getErlangModule() {
        return erlModule;
    }

    public String getErlangFunction() {
        return erlFunction;
    }
}

/**
 * Result of OCEL import operation.
 */
class ImportResult {
    private final String logId;
    private final int eventCount;
    private final int objectCount;
    private final Map<String, ErlTerm> metadata;

    public ImportResult(String logId, int eventCount, int objectCount,
                       Map<String, ErlTerm> metadata) {
        this.logId = logId;
        this.eventCount = eventCount;
        this.objectCount = objectCount;
        this.metadata = metadata;
    }

    public String getLogId() {
        return logId;
    }

    public int getEventCount() {
        return eventCount;
    }

    public int getObjectCount() {
        return objectCount;
    }

    public Map<String, ErlTerm> getMetadata() {
        return metadata;
    }
}

/**
 * Result of process model discovery.
 */
class DiscoveryResult {
    private final String modelId;
    private final List<ErlTerm> patterns;
    private final Map<String, ErlTerm> metrics;
    private final List<ErlTerm> transitions;

    public DiscoveryResult(String modelId, List<ErlTerm> patterns,
                          Map<String, ErlTerm> metrics, List<ErlTerm> transitions) {
        this.modelId = modelId;
        this.patterns = patterns;
        this.metrics = metrics;
        this.transitions = transitions;
    }

    public String getModelId() {
        return modelId;
    }

    public List<ErlTerm> getPatterns() {
        return patterns;
    }

    public Map<String, ErlTerm> getMetrics() {
        return metrics;
    }

    public List<ErlTerm> getTransitions() {
        return transitions;
    }
}

/**
 * Result of DECLARE discovery.
 */
class DeclareDiscoveryResult {
    private final String discoveryId;
    private final List<ErlTerm> declarePatterns;
    private final Map<String, ErlTerm> constraintViolations;
    private final double coverage;

    public DeclareDiscoveryResult(String discoveryId, List<ErlTerm> declarePatterns,
                                 Map<String, ErlTerm> constraintViolations, double coverage) {
        this.discoveryId = discoveryId;
        this.declarePatterns = declarePatterns;
        this.constraintViolations = constraintViolations;
        this.coverage = coverage;
    }

    public String getDiscoveryId() {
        return discoveryId;
    }

    public List<ErlTerm> getDeclarePatterns() {
        return declarePatterns;
    }

    public Map<String, ErlTerm> getConstraintViolations() {
        return constraintViolations;
    }

    public double getCoverage() {
        return coverage;
    }
}

/**
 * Result of token replay analysis.
 */
class ReplayResult {
    private final String replayId;
    private final int alignedEvents;
    private final int misalignedEvents;
    private final double fitness;
    private final Map<String, ErlTerm> alignmentDetails;

    public ReplayResult(String replayId, int alignedEvents, int misalignedEvents,
                       double fitness, Map<String, ErlTerm> alignmentDetails) {
        this.replayId = replayId;
        this.alignedEvents = alignedEvents;
        this.misalignedEvents = misalignedEvents;
        this.fitness = fitness;
        this.alignmentDetails = alignmentDetails;
    }

    public String getReplayId() {
        return replayId;
    }

    public int getAlignedEvents() {
        return alignedEvents;
    }

    public int getMisalignedEvents() {
        return misalignedEvents;
    }

    public double getFitness() {
        return fitness;
    }

    public Map<String, ErlTerm> getAlignmentDetails() {
        return alignmentDetails;
    }
}

/**
 * Parameters for token replay analysis.
 */
class ReplayParameters {
    private final String alignmentMethod;
    private final double fitnessThreshold;
    private final Map<String, ErlTerm> additionalParams;

    public ReplayParameters(String alignmentMethod, double fitnessThreshold,
                           Map<String, ErlTerm> additionalParams) {
        this.alignmentMethod = alignmentMethod;
        this.fitnessThreshold = fitnessThreshold;
        this.additionalParams = additionalParams;
    }

    public String getAlignmentMethod() {
        return alignmentMethod;
    }

    public double getFitnessThreshold() {
        return fitnessThreshold;
    }

    public Map<String, ErlTerm> getAdditionalParams() {
        return additionalParams;
    }
}

/**
 * Types of discovery operations.
 */
enum DiscoveryType {
    HEURISTIC,
    DECLARE,
    INDUCTIVE,
    ALPHA,
    PETRI_NET
}

/**
 * Connection information for the process mining client.
 */
class ConnectionInfo {
    private final String nodeName;
    private final String protocol;
    private final Path socketPath;
    private final long connectedSince;

    public ConnectionInfo(String nodeName, String protocol, Path socketPath, long connectedSince) {
        this.nodeName = nodeName;
        this.protocol = protocol;
        this.socketPath = socketPath;
        this.connectedSince = connectedSince;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getProtocol() {
        return protocol;
    }

    public Path getSocketPath() {
        return socketPath;
    }

    public long getConnectedSince() {
        return connectedSince;
    }
}