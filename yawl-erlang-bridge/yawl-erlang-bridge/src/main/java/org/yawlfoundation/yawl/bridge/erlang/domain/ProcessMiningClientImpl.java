package org.yawlfoundation.yawl.bridge.erlang.domain;

import org.yawlfoundation.yawl.bridge.erlang.*;
import org.yawlfoundation.yawl.bridge.erlang.ErlangException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * Implementation of the ProcessMiningClient that delegates to Erlang nodes.
 *
 * @since 1.0.0
 */
public final class ProcessMiningClientImpl implements ProcessMiningClient {

    private final ErlangNode erlangNode;
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
    public OcelId importOcel(Path path) throws ErlangException {
        checkClosed();
        validatePath(path, "OCEL file");

        try {
            // Build import request
            ErlTerm[] request = buildImportRequest(path);

            // Execute import
            ErlTerm response = erlangNode.rpc("pm_ocel", "import_ocel", request);

            // Parse response
            return parseOcelIdResponse(response);

        } catch (ErlangException e) {
            throw new ErlangException("Failed to import OCEL file: " + e.getMessage(), e);
        }
    }

    @Override
    public SlimOcelId slimLink(OcelId id) throws ErlangException {
        checkClosed();
        if (id == null) {
            throw new IllegalArgumentException("OCEL ID cannot be null");
        }

        try {
            // Build slim link request
            ErlTerm[] request = buildSlimLinkRequest(id);

            // Execute discovery
            ErlTerm response = erlangNode.rpc("pm_discovery", "slim_link", request);

            // Parse response
            return parseSlimOcelIdResponse(response);

        } catch (ErlangException e) {
            throw new ErlangException("Failed to perform slim link discovery: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Constraint> discoverOcDeclare(SlimOcelId id) throws ErlangException {
        checkClosed();
        if (id == null) {
            throw new IllegalArgumentException("Slim OCEL ID cannot be null");
        }

        try {
            // Build discovery request
            ErlTerm[] request = buildDiscoverOcDeclareRequest(id);

            // Execute discovery
            ErlTerm response = erlangNode.rpc("pm_declare", "discover_oc_declare", request);

            // Parse response
            return parseDiscoverOcDeclareResponse(response);

        } catch (ErlangException e) {
            throw new ErlangException("Failed to perform DECLARE discovery: " + e.getMessage(), e);
        }
    }

    @Override
    public ConformanceResult tokenReplay(OcelId ocel, PetriNetId pn) throws ErlangException {
        checkClosed();
        if (ocel == null) {
            throw new IllegalArgumentException("OCEL ID cannot be null");
        }
        if (pn == null) {
            throw new IllegalArgumentException("Petri net ID cannot be null");
        }

        try {
            // Build replay request
            ErlTerm[] request = buildTokenReplayRequest(ocel, pn);

            // Execute replay
            ErlTerm response = erlangNode.rpc("pm_replay", "token_replay", request);

            // Parse response
            return parseTokenReplayResponse(response);

        } catch (ErlangException e) {
            throw new ErlangException("Failed to perform token replay: " + e.getMessage(), e);
        }
    }

    @Override
    public DirectlyFollowsGraph discoverDfg(SlimOcelId id) throws ErlangException {
        checkClosed();
        if (id == null) {
            throw new IllegalArgumentException("Slim OCEL ID cannot be null");
        }

        try {
            // Build DFG discovery request
            ErlTerm[] request = buildDiscoverDfgRequest(id);

            // Execute discovery
            ErlTerm response = erlangNode.rpc("pm_discovery", "discover_dfg", request);

            // Parse response
            return parseDiscoverDfgResponse(response);

        } catch (ErlangException e) {
            throw new ErlangException("Failed to discover directly follows graph: " + e.getMessage(), e);
        }
    }

    @Override
    public PetriNet mineAlphaPlusPlus(SlimOcelId id) throws ErlangException {
        checkClosed();
        if (id == null) {
            throw new IllegalArgumentException("Slim OCEL ID cannot be null");
        }

        try {
            // Build mining request
            ErlTerm[] request = buildMineAlphaPlusPlusRequest(id);

            // Execute mining
            ErlTerm response = erlangNode.rpc("pm_mining", "mine_alpha_plus", request);

            // Parse response
            return parseMineAlphaPlusPlusResponse(response);

        } catch (ErlangException e) {
            throw new ErlangException("Failed to mine Petri net: " + e.getMessage(), e);
        }
    }

    @Override
    public void freeOcel(OcelId id) throws ErlangException {
        checkClosed();
        if (id == null) {
            throw new IllegalArgumentException("OCEL ID cannot be null");
        }

        try {
            // Build free request
            ErlTerm[] request = buildFreeOcelRequest(id);

            // Execute free operation
            ErlTerm response = erlangNode.rpc("pm_ocel", "free_ocel", request);

            // Verify successful response
            if (!(response instanceof ErlAtom) || !("ok".equals(((ErlAtom) response).getValue()))) {
                throw new ErlangException("Failed to free OCEL resources");
            }

        } catch (ErlangException e) {
            throw new ErlangException("Failed to free OCEL resources: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new ProcessMiningClient with default configuration.
     *
     * @return A new ProcessMiningClient instance
     */
    public static ProcessMiningClient create() {
        String nodeName = "yawl_process_mining@localhost";
        String cookie = "yawl";
        return new ProcessMiningClientImpl(nodeName, cookie);
    }

    @Override
    public boolean isConnected() {
        return !closed && erlangNode.isConnected();
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
    private ErlTerm[] buildImportRequest(Path path) {
        ErlTerm pathTerm = ErlAtom.of(path.toString());

        return new ErlTerm[]{
            ErlAtom.of("import_ocel"),
            ErlList.of(pathTerm)
        };
    }

    /**
     * Builds a slim link discovery request.
     */
    private ErlTerm[] buildSlimLinkRequest(OcelId id) {
        ErlAtom opTerm = ErlAtom.of("slim_link");
        ErlTerm idTerm = ErlAtom.of(id.value().toString());

        return new ErlTerm[]{opTerm, ErlList.of(idTerm)};
    }

    /**
     * Builds a DECLARE discovery request.
     */
    private ErlTerm[] buildDiscoverOcDeclareRequest(SlimOcelId id) {
        ErlAtom opTerm = ErlAtom.of("discover_oc_declare");
        ErlTerm idTerm = ErlAtom.of(id.value().toString());

        return new ErlTerm[]{opTerm, ErlList.of(idTerm)};
    }

    /**
     * Builds a token replay request.
     */
    private ErlTerm[] buildTokenReplayRequest(OcelId ocel, PetriNetId pn) {
        ErlAtom opTerm = ErlAtom.of("token_replay");
        ErlTerm ocelTerm = ErlAtom.of(ocel.value().toString());
        ErlTerm pnTerm = ErlAtom.of(pn.value().toString());

        return new ErlTerm[]{opTerm, ErlList.of(ocelTerm, pnTerm)};
    }

    /**
     * Builds a DFG discovery request.
     */
    private ErlTerm[] buildDiscoverDfgRequest(SlimOcelId id) {
        ErlAtom opTerm = ErlAtom.of("discover_dfg");
        ErlTerm idTerm = ErlAtom.of(id.value().toString());

        return new ErlTerm[]{opTerm, ErlList.of(idTerm)};
    }

    /**
     * Builds an Alpha++ mining request.
     */
    private ErlTerm[] buildMineAlphaPlusPlusRequest(SlimOcelId id) {
        ErlAtom opTerm = ErlAtom.of("mine_alpha_plus");
        ErlTerm idTerm = ErlAtom.of(id.value().toString());

        return new ErlTerm[]{opTerm, ErlList.of(idTerm)};
    }

    /**
     * Builds a free OCEL request.
     */
    private ErlTerm[] buildFreeOcelRequest(OcelId id) {
        ErlTerm idTerm = ErlAtom.of(id.value().toString());

        return new ErlTerm[]{ErlAtom.of("free_ocel"), ErlList.of(idTerm)};
    }

    /**
     * Parses an OCEL ID response from Erlang.
     */
    private OcelId parseOcelIdResponse(ErlTerm response) throws ErlangException {
        // Expected format: {ok, UUID}
        if (response instanceof ErlTuple && ((ErlTuple) response).hasArity(2)) {
            ErlTuple tuple = (ErlTuple) response;
            ErlTerm okTag = tuple.get(0);

            if (okTag instanceof ErlAtom && "ok".equals(((ErlAtom) okTag).getValue())) {
                ErlTerm uuidTerm = tuple.get(1);
                if (uuidTerm instanceof ErlAtom) {
                    String uuidStr = ((ErlAtom) uuidTerm).getValue();
                    return OcelId.fromString(uuidStr);
                }
            }
        }

        throw new ErlangException("Invalid OCEL ID response format");
    }

    /**
     * Parses a SlimOcelId response from Erlang.
     */
    private SlimOcelId parseSlimOcelIdResponse(ErlTerm response) throws ErlangException {
        // Expected format: {ok, UUID}
        if (response instanceof ErlTuple && ((ErlTuple) response).hasArity(2)) {
            ErlTuple tuple = (ErlTuple) response;
            ErlTerm okTag = tuple.get(0);

            if (okTag instanceof ErlAtom && "ok".equals(((ErlAtom) okTag).getValue())) {
                ErlTerm uuidTerm = tuple.get(1);
                if (uuidTerm instanceof ErlAtom) {
                    String uuidStr = ((ErlAtom) uuidTerm).getValue();
                    return SlimOcelId.fromString(uuidStr);
                }
            }
        }

        throw new ErlangException("Invalid Slim OCEL ID response format");
    }

    /**
     * Parses a DECLARE discovery response from Erlang.
     */
    private List<Constraint> parseDiscoverOcDeclareResponse(ErlTerm response) throws ErlangException {
        // Expected format: {ok, Constraints}
        if (response instanceof ErlTuple && ((ErlTuple) response).hasArity(2)) {
            ErlTuple tuple = (ErlTuple) response;
            ErlTerm okTag = tuple.get(0);

            if (okTag instanceof ErlAtom && "ok".equals(((ErlAtom) okTag).getValue())) {
                ErlTerm constraintsTerm = tuple.get(1);
                if (constraintsTerm instanceof ErlList) {
                    List<ErlTerm> constraintTerms = ((ErlList) constraintsTerm).getElements();
                    List<Constraint> constraints = new ArrayList<>();

                    for (ErlTerm constraintTerm : constraintTerms) {
                        if (constraintTerm instanceof ErlTuple && ((ErlTuple) constraintTerm).hasArity(3)) {
                            ErlTuple constraintTuple = (ErlTuple) constraintTerm;
                            ErlTerm templateTerm = constraintTuple.get(0);
                            ErlTerm paramsTerm = constraintTuple.get(1);
                            ErlTerm supportTerm = constraintTuple.get(2);

                            String template = extractString(templateTerm);
                            Map<String, Object> params = parseParams(paramsTerm);
                            double support = extractDouble(supportTerm);

                            constraints.add(new Constraint(template, params, support));
                        }
                    }

                    return constraints;
                }
            }
        }

        throw new ErlangException("Invalid DECLARE discovery response format");
    }

    /**
     * Parses a token replay response from Erlang.
     */
    private ConformanceResult parseTokenReplayResponse(ErlTerm response) throws ErlangException {
        // Expected format: {ok, Fitness, Missing, Remaining, Consumed}
        if (response instanceof ErlTuple && ((ErlTuple) response).hasArity(5)) {
            ErlTuple tuple = (ErlTuple) response;
            ErlTerm okTag = tuple.get(0);

            if (okTag instanceof ErlAtom && "ok".equals(((ErlAtom) okTag).getValue())) {
                ErlTerm fitnessTerm = tuple.get(1);
                ErlTerm missingTerm = tuple.get(2);
                ErlTerm remainingTerm = tuple.get(3);
                ErlTerm consumedTerm = tuple.get(4);

                double fitness = extractDouble(fitnessTerm);
                int missing = extractInt(missingTerm);
                int remaining = extractInt(remainingTerm);
                int consumed = extractInt(consumedTerm);

                return new ConformanceResult(fitness, missing, remaining, consumed);
            }
        }

        throw new ErlangException("Invalid token replay response format");
    }

    /**
     * Parses a DFG response from Erlang.
     */
    private DirectlyFollowsGraph parseDiscoverDfgResponse(ErlTerm response) throws ErlangException {
        // Expected format: {ok, Edges}
        if (response instanceof ErlTuple && ((ErlTuple) response).hasArity(2)) {
            ErlTuple tuple = (ErlTuple) response;
            ErlTerm okTag = tuple.get(0);

            if (okTag instanceof ErlAtom && "ok".equals(((ErlAtom) okTag).getValue())) {
                ErlTerm edgesTerm = tuple.get(1);
                if (edgesTerm instanceof ErlList) {
                    Map<String, Map<String, Integer>> edges = new HashMap<>();

                    for (ErlTerm edgeTerm : ((ErlList) edgesTerm).getElements()) {
                        if (edgeTerm instanceof ErlTuple && ((ErlTuple) edgeTerm).hasArity(3)) {
                            ErlTuple edgeTuple = (ErlTuple) edgeTerm;
                            ErlTerm sourceTerm = edgeTuple.get(0);
                            ErlTerm targetTerm = edgeTuple.get(1);
                            ErlTerm countTerm = edgeTuple.get(2);

                            String source = extractString(sourceTerm);
                            String target = extractString(targetTerm);
                            int count = extractInt(countTerm);

                            edges.computeIfAbsent(source, k -> new HashMap<>())
                                 .put(target, count);
                        }
                    }

                    return new DirectlyFollowsGraph(edges);
                }
            }
        }

        throw new ErlangException("Invalid DFG response format");
    }

    /**
     * Parses a Petri net response from Erlang.
     */
    private PetriNet parseMineAlphaPlusPlusResponse(ErlTerm response) throws ErlangException {
        // Expected format: {ok, PNML_XML}
        if (response instanceof ErlTuple && ((ErlTuple) response).hasArity(2)) {
            ErlTuple tuple = (ErlTuple) response;
            ErlTerm okTag = tuple.get(0);

            if (okTag instanceof ErlAtom && "ok".equals(((ErlAtom) okTag).getValue())) {
                ErlTerm xmlTerm = tuple.get(1);
                String pnmlXml = extractString(xmlTerm);
                return new PetriNet(pnmlXml);
            }
        }

        throw new ErlangException("Invalid Petri net mining response format");
    }

    // Helper methods for extracting values from ErlTerms
    private String extractString(ErlTerm term) throws ErlangException {
        if (term instanceof ErlAtom) {
            return ((ErlAtom) term).getValue();
        }
        throw new ErlangException("Expected atom, got: " + term.type());
    }

    private int extractInt(ErlTerm term) throws ErlangException {
        if (term instanceof ErlLong) {
            return (int) ((ErlLong) term).getValue();
        }
        throw new ErlangException("Expected integer, got: " + term.type());
    }

    private double extractDouble(ErlTerm term) throws ErlangException {
        if (term instanceof ErlLong) {
            return ((ErlLong) term).getValue();
        } else if (term instanceof ErlDouble) {
            return ((ErlDouble) term).getValue();
        }
        throw new ErlangException("Expected number, got: " + term.type());
    }

    private List<ErlTerm> extractList(ErlTerm term) throws ErlangException {
        if (term instanceof ErlList) {
            return ((ErlList) term).getElements();
        }
        throw new ErlangException("Expected list, got: " + term.type());
    }

    private Map<String, ErlTerm> extractMap(ErlTerm term) throws ErlangException {
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
        throw new ErlangException("Expected map/list of tuples, got: " + term.type());
    }

    /**
     * Parses constraint parameters from Erlang to Java Map.
     */
    private Map<String, Object> parseParams(ErlTerm term) throws ErlangException {
        if (term instanceof ErlList) {
            Map<String, Object> params = new HashMap<>();
            ErlList list = (ErlList) term;

            for (ErlTerm element : list.getElements()) {
                if (element instanceof ErlTuple && ((ErlTuple) element).hasArity(2)) {
                    ErlTuple tuple = (ErlTuple) element;
                    ErlTerm key = tuple.get(0);
                    ErlTerm value = tuple.get(1);

                    if (key instanceof ErlAtom) {
                        String keyStr = ((ErlAtom) key).getValue();
                        Object valueObj = convertErlTermToJava(value);
                        params.put(keyStr, valueObj);
                    }
                }
            }

            return params;
        }
        throw new ErlangException("Expected parameter list, got: " + term.type());
    }

    /**
     * Converts an ErlTerm to a Java object.
     */
    private Object convertErlTermToJava(ErlTerm term) throws ErlangException {
        if (term instanceof ErlAtom) {
            return ((ErlAtom) term).getValue();
        } else if (term instanceof ErlLong) {
            return ((ErlLong) term).getValue();
        } else if (term instanceof ErlDouble) {
            return ((ErlDouble) term).getValue();
        } else if (term instanceof ErlString) {
            return ((ErlString) term).getValue();
        } else if (term instanceof ErlList) {
            return ((ErlList) term).getElements();
        } else if (term instanceof ErlTuple) {
            return ((ErlTuple) term).getElements();
        } else {
            return term.toString();
        }
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
        try {
            if (!Files.exists(path)) {
                throw new IllegalArgumentException(fieldName + " does not exist: " + path);
            }
            if (!Files.isReadable(path)) {
                throw new IllegalArgumentException(fieldName + " is not readable: " + path);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(fieldName + " is not accessible: " + path, e);
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