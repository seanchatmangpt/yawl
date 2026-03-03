package org.yawlfoundation.yawl.bridge.erlang.domain;

import org.yawlfoundation.yawl.bridge.erlang.*;
import org.yawlfoundation.yawl.bridge.erlang.ErlangException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of the ProcessMiningClient that delegates to Erlang nodes.
 *
 * @since 1.0.0
 */
public final class ProcessMiningClientImpl implements ProcessMiningClient {

    private final ErlangNode erlangNode;
    private final ConnectionInfo connectionInfo;
    private volatile boolean closed = false;

    /**
     * Creates a new ProcessMiningClient with the specified node configuration.
     *
     * @param nodeName The Erlang node name
     * @param cookie The Erlang cookie for authentication
     * @param socketPath Path to the Unix domain socket
     */
    public ProcessMiningClientImpl(String nodeName, String cookie, Path socketPath) {
        this.erlangNode = new ErlangNode(nodeName, cookie, socketPath);
        this.connectionInfo = new ConnectionInfo(
            nodeName,
            "local",  // Unix domain socket protocol
            socketPath,
            System.currentTimeMillis()
        );
    }

    /**
     * Creates a new ProcessMiningClient with default socket path.
     *
     * @param nodeName The Erlang node name
     * @param cookie The Erlang cookie for authentication
     */
    public ProcessMiningClientImpl(String nodeName, String cookie) {
        this(nodeName, cookie, getDefaultSocketPath(nodeName));
    }

    @Override
    public ImportResult importOcel(Path ocelPath) throws ProcessMiningException {
        checkClosed();
        validatePath(ocelPath, "OCEL file");

        try {
            // Build import request
            ErlTerm[] request = buildImportRequest(ocelPath);

            // Execute import
            ErlTerm response = erlangNode.rpc("pm_ocel", "import", request);

            // Parse response
            return parseImportResponse(response);

        } catch (ErlangException e) {
            throw new ProcessMiningException(
                "Failed to import OCEL file: " + e.getMessage(),
                "importOcel", "pm_ocel", "import", e);
        }
    }

    @Override
    public DiscoveryResult slimLink(Map<String, ErlTerm> parameters) throws ProcessMiningException {
        checkClosed();
        validateParameters(parameters, "slimLink parameters");

        try {
            // Build discovery request
            ErlTerm[] request = buildDiscoveryRequest(parameters);

            // Execute discovery
            ErlTerm response = erlangNode.rpc("pm_discovery", "slim_link", request);

            // Parse response
            return parseDiscoveryResponse(response);

        } catch (ErlangException e) {
            throw new ProcessMiningException(
                "Failed to perform slim link discovery: " + e.getMessage(),
                "slimLink", "pm_discovery", "slim_link", e);
        }
    }

    @Override
    public DeclareDiscoveryResult discoverOcDeclare(DiscoveryType discoveryType,
                                                 Map<String, ErlTerm> constraints) throws ProcessMiningException {
        checkClosed();
        validateDiscoveryType(discoveryType);
        validateParameters(constraints, "DECLARE discovery constraints");

        try {
            // Build discovery request
            ErlTerm[] request = buildDeclareDiscoveryRequest(discoveryType, constraints);

            // Execute discovery
            ErlTerm response = erlangNode.rpc("pm_declare", "discover_oc_declare", request);

            // Parse response
            return parseDeclareDiscoveryResponse(response);

        } catch (ErlangException e) {
            throw new ProcessMiningException(
                "Failed to perform DECLARE discovery: " + e.getMessage(),
                "discoverOcDeclare", "pm_declare", "discover_oc_declare", e);
        }
    }

    @Override
    public ReplayResult tokenReplay(String logId, String modelId,
                                  ReplayParameters replayParameters) throws ProcessMiningException {
        checkClosed();
        validateNotBlank(logId, "log ID");
        validateNotBlank(modelId, "model ID");
        validateParameters(replayParameters, "replay parameters");

        try {
            // Build replay request
            ErlTerm[] request = buildReplayRequest(logId, modelId, replayParameters);

            // Execute replay
            ErlTerm response = erlangNode.rpc("pm_replay", "token_replay", request);

            // Parse response
            return parseReplayResponse(response);

        } catch (ErlangException e) {
            throw new ProcessMiningException(
                "Failed to perform token replay: " + e.getMessage(),
                "tokenReplay", "pm_replay", "token_replay", e);
        }
    }

    @Override
    public ErlTerm executeMiningQuery(String moduleName, String functionName,
                                    List<ErlTerm> arguments) throws ProcessMiningException {
        checkClosed();
        validateNotBlank(moduleName, "module name");
        validateNotBlank(functionName, "function name");
        validateNotNull(arguments, "arguments");

        try {
            // Convert arguments to array
            ErlTerm[] argsArray = arguments.toArray(new ErlTerm[0]);

            // Execute query
            return erlangNode.rpc(moduleName, functionName, argsArray);

        } catch (ErlangException e) {
            throw new ProcessMiningException(
                "Failed to execute mining query: " + e.getMessage(),
                "executeMiningQuery", moduleName, functionName, e);
        }
    }

    @Override
    public boolean isConnected() {
        return !closed && erlangNode.isConnected();
    }

    @Override
    public ConnectionInfo getConnectionInfo() {
        return connectionInfo;
    }

    @Override
    public void close() {
        if (!closed) {
            erlangNode.close();
            closed = true;
        }
    }

    /**
     * Builds an import request for an OCEL file.
     */
    private ErlTerm[] buildImportRequest(Path ocelPath) {
        ErlTerm pathTerm = ErlAtom.of(ocelPath.toString());

        return new ErlTerm[]{
            ErlAtom.of("import_ocel"),
            ErlList.of(pathTerm)
        };
    }

    /**
     * Builds a discovery request for slim link discovery.
     */
    private ErlTerm[] buildDiscoveryRequest(Map<String, ErlTerm> parameters) {
        ErlAtom opTerm = ErlAtom.of("slim_link");

        // Convert parameters to Erlang list of tuples
        List<ErlTerm> paramList = parameters.entrySet().stream()
            .map(entry -> ErlTuple.of(
                ErlAtom.of(entry.getKey()),
                entry.getValue()
            ))
            .collect(Collectors.toList());

        ErlTerm paramsTerm = ErlList.of(paramList);

        return new ErlTerm[]{opTerm, paramsTerm};
    }

    /**
     * Builds a DECLARE discovery request.
     */
    private ErlTerm[] buildDeclareDiscoveryRequest(DiscoveryType discoveryType,
                                                 Map<String, ErlTerm> constraints) {
        ErlAtom opTerm = ErlAtom.of("discover_oc_declare");
        ErlAtom typeTerm = ErlAtom.of(discoveryType.name().toLowerCase());

        // Convert constraints to Erlang list of tuples
        List<ErlTerm> constraintList = constraints.entrySet().stream()
            .map(entry -> ErlTuple.of(
                ErlAtom.of(entry.getKey()),
                entry.getValue()
            ))
            .collect(Collectors.toList());

        ErlTerm constraintsTerm = ErlList.of(constraintList);

        return new ErlTerm[]{opTerm, typeTerm, constraintsTerm};
    }

    /**
     * Builds a token replay request.
     */
    private ErlTerm[] buildReplayRequest(String logId, String modelId,
                                       ReplayParameters replayParameters) {
        ErlAtom opTerm = ErlAtom.of("token_replay");
        ErlAtom logIdTerm = ErlAtom.of(logId);
        ErlAtom modelIdTerm = ErlAtom.of(modelId);

        // Build replay parameters
        ErlTerm[] paramPairs = {
            ErlTuple.of(ErlAtom.of("alignment_method"), ErlAtom.of(replayParameters.getAlignmentMethod())),
            ErlTuple.of(ErlAtom.of("fitness_threshold"), ErlLong.of((long) (replayParameters.getFitnessThreshold() * 1000)))
        };

        ErlTerm paramsTerm = ErlList.of(paramPairs);

        return new ErlTerm[]{opTerm, logIdTerm, modelIdTerm, paramsTerm};
    }

    /**
     * Parses an import response.
     */
    private ImportResult parseImportResponse(ErlTerm response) throws ProcessMiningException {
        // Expected format: {ok, LogId, EventCount, ObjectCount, Metadata}
        if (response instanceof ErlTuple && ((ErlTuple) response).hasArity(5)) {
            ErlTuple tuple = (ErlTuple) response;
            ErlTerm okTag = tuple.get(0);

            if (okTag instanceof ErlAtom && "ok".equals(((ErlAtom) okTag).getValue())) {
                ErlTerm logIdTerm = tuple.get(1);
                ErlTerm eventCountTerm = tuple.get(2);
                ErlTerm objectCountTerm = tuple.get(3);
                ErlTerm metadataTerm = tuple.get(4);

                String logId = extractString(logIdTerm);
                int eventCount = extractInt(eventCountTerm);
                int objectCount = extractInt(objectCountTerm);
                Map<String, ErlTerm> metadata = extractMap(metadataTerm);

                return new ImportResult(logId, eventCount, objectCount, metadata);
            }
        }

        throw new ProcessMiningException("Invalid import response format", "parseImportResponse");
    }

    /**
     * Parses a discovery response.
     */
    private DiscoveryResult parseDiscoveryResponse(ErlTerm response) throws ProcessMiningException {
        // Expected format: {ok, ModelId, Patterns, Metrics, Transitions}
        if (response instanceof ErlTuple && ((ErlTuple) response).hasArity(5)) {
            ErlTuple tuple = (ErlTuple) response;
            ErlTerm okTag = tuple.get(0);

            if (okTag instanceof ErlAtom && "ok".equals(((ErlAtom) okTag).getValue())) {
                ErlTerm modelIdTerm = tuple.get(1);
                ErlTerm patternsTerm = tuple.get(2);
                ErlTerm metricsTerm = tuple.get(3);
                ErlTerm transitionsTerm = tuple.get(4);

                String modelId = extractString(modelIdTerm);
                List<ErlTerm> patterns = extractList(patternsTerm);
                Map<String, ErlTerm> metrics = extractMap(metricsTerm);
                List<ErlTerm> transitions = extractList(transitionsTerm);

                return new DiscoveryResult(modelId, patterns, metrics, transitions);
            }
        }

        throw new ProcessMiningException("Invalid discovery response format", "parseDiscoveryResponse");
    }

    /**
     * Parses a DECLARE discovery response.
     */
    private DeclareDiscoveryResult parseDeclareDiscoveryResponse(ErlTerm response) throws ProcessMiningException {
        // Expected format: {ok, DiscoveryId, Patterns, Violations, Coverage}
        if (response instanceof ErlTuple && ((ErlTuple) response).hasArity(5)) {
            ErlTuple tuple = (ErlTuple) response;
            ErlTerm okTag = tuple.get(0);

            if (okTag instanceof ErlAtom && "ok".equals(((ErlAtom) okTag).getValue())) {
                ErlTerm discoveryIdTerm = tuple.get(1);
                ErlTerm patternsTerm = tuple.get(2);
                ErlTerm violationsTerm = tuple.get(3);
                ErlTerm coverageTerm = tuple.get(4);

                String discoveryId = extractString(discoveryIdTerm);
                List<ErlTerm> patterns = extractList(patternsTerm);
                Map<String, ErlTerm> violations = extractMap(violationsTerm);
                double coverage = extractDouble(coverageTerm);

                return new DeclareDiscoveryResult(discoveryId, patterns, violations, coverage);
            }
        }

        throw new ProcessMiningException("Invalid DECLARE discovery response format", "parseDeclareDiscoveryResponse");
    }

    /**
     * Parses a token replay response.
     */
    private ReplayResult parseReplayResponse(ErlTerm response) throws ProcessMiningException {
        // Expected format: {ok, ReplayId, AlignedEvents, MisalignedEvents, Fitness, Details}
        if (response instanceof ErlTuple && ((ErlTuple) response).hasArity(6)) {
            ErlTuple tuple = (ErlTuple) response;
            ErlTerm okTag = tuple.get(0);

            if (okTag instanceof ErlAtom && "ok".equals(((ErlAtom) okTag).getValue())) {
                ErlTerm replayIdTerm = tuple.get(1);
                ErlTerm alignedTerm = tuple.get(2);
                ErlTerm misalignedTerm = tuple.get(3);
                ErlTerm fitnessTerm = tuple.get(4);
                ErlTerm detailsTerm = tuple.get(5);

                String replayId = extractString(replayIdTerm);
                int alignedEvents = extractInt(alignedTerm);
                int misalignedEvents = extractInt(misalignedTerm);
                double fitness = extractDouble(fitnessTerm);
                Map<String, ErlTerm> details = extractMap(detailsTerm);

                return new ReplayResult(replayId, alignedEvents, misalignedEvents, fitness, details);
            }
        }

        throw new ProcessMiningException("Invalid token replay response format", "parseReplayResponse");
    }

    // Helper methods for extracting values from ErlTerms
    private String extractString(ErlTerm term) throws ProcessMiningException {
        if (term instanceof ErlAtom) {
            return ((ErlAtom) term).getValue();
        }
        throw new ProcessMiningException("Expected atom, got: " + term.type(), "extractString");
    }

    private int extractInt(ErlTerm term) throws ProcessMiningException {
        if (term instanceof ErlLong) {
            return (int) ((ErlLong) term).getValue();
        }
        throw new ProcessMiningException("Expected integer, got: " + term.type(), "extractInt");
    }

    private double extractDouble(ErlTerm term) throws ProcessMiningException {
        if (term instanceof ErlLong) {
            return ((ErlLong) term).getValue();
        }
        throw new ProcessMiningException("Expected number, got: " + term.type(), "extractDouble");
    }

    private List<ErlTerm> extractList(ErlTerm term) throws ProcessMiningException {
        if (term instanceof ErlList) {
            return ((ErlList) term).getElements();
        }
        throw new ProcessMiningException("Expected list, got: " + term.type(), "extractList");
    }

    private Map<String, ErlTerm> extractMap(ErlTerm term) throws ProcessMiningException {
        if (term instanceof ErlList) {
            ErlList list = (ErlList) term;
            Map<String, ErlTerm> map = new HashMap<>();

            for (ErlTerm element : list.getElements()) {
                if (element instanceof ErlTuple && ((ErlTuple) element).hasArity(2)) {
                    ErlTuple tuple = (ErlTuple) element;
                    ErlTerm key = tuple.get(0);
                    ErlTerm value = tuple.get(1);

                    if (key instanceof ErlAtom) {
                        map.put(((ErlAtom) key).getValue(), value);
                    }
                }
            }

            return map;
        }
        throw new ProcessMiningException("Expected map/list of tuples, got: " + term.type(), "extractMap");
    }

    // Validation methods
    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException("ProcessMiningClient is closed");
        }
    }

    private void validatePath(Path path, String fieldName) {
        if (path == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException(fieldName + " does not exist: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException(fieldName + " is not readable: " + path);
        }
    }

    private void validateParameters(ReplayParameters parameters, String fieldName) {
        if (parameters == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        validateNotNull(parameters.getAdditionalParams(), "additional params");
    }

    private void validateParameters(Map<String, ErlTerm> parameters, String fieldName) {
        if (parameters == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        for (Map.Entry<String, ErlTerm> entry : parameters.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException(fieldName + " cannot contain null values");
            }
        }
    }

    private void validateDiscoveryType(DiscoveryType type) {
        if (type == null) {
            throw new IllegalArgumentException("Discovery type cannot be null");
        }
    }

    private void validateNotBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
    }

    private void validateNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
    }

    /**
     * Gets the default socket path for a node.
     */
    private static Path getDefaultSocketPath(String nodeName) {
        String normalized = nodeName.replace("@", "_");
        return Paths.get("/tmp/yawl-erlang", normalized + ".sock");
    }
}