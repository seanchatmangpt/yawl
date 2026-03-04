package org.yawlfoundation.yawl.bridge.erlang.domain;

import org.yawlfoundation.yawl.bridge.erlang.ErlangException;
import org.yawlfoundation.yawl.bridge.erlang.ErlTerm;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
     * @param path Path to the OCEL file
     * @return The OCEL ID for the imported log
     * @throws ErlangException if import fails
     */
    OcelId importOcel(Path path) throws ErlangException;

    /**
     * Performs slim link discovery on the imported event log.
     *
     * <p>This uses the heuristic-based discovery algorithm from the process mining domain.</p>
     *
     * @param id The OCEL ID to perform slim link discovery on
     * @return The slim OCEL ID with discovered links
     * @throws ErlangException if discovery fails
     */
    SlimOcelId slimLink(OcelId id) throws ErlangException;

    /**
     * Discovers process models from OCEL data using DECLARE patterns.
     *
     * <p>This implements the OCEL-based DECLARE discovery algorithm.</p>
     *
     * @param id The slim OCEL ID to analyze
     * @return List of discovered DECLARE constraints
     * @throws ErlangException if discovery fails
     */
    List<Constraint> discoverOcDeclare(SlimOcelId id) throws ErlangException;

    /**
     * Performs token replay analysis on the imported event log.
     *
     * <p>This simulates the execution of cases in the imported log against the discovered model.</p>
     *
     * @param ocel The OCEL ID of the event log
     * @param pn The Petri net ID to replay against
     * @return The conformance result with alignment information
     * @throws ErlangException if replay fails
     */
    ConformanceResult tokenReplay(OcelId ocel, PetriNetId pn) throws ErlangException;

    /**
     * Discovers a Directly Follows Graph from the OCEL data.
     *
     * <p>This extracts the sequence relationships between activities.</p>
     *
     * @param id The slim OCEL ID to analyze
     * @return The directly follows graph representation
     * @throws ErlangException if discovery fails
     */
    DirectlyFollowsGraph discoverDfg(SlimOcelId id) throws ErlangException;

    /**
     * Mines a Petri net using Alpha++ algorithm.
     *
     * <p>This mines a Petri net model from the OCEL data using the enhanced Alpha algorithm.</p>
     *
     * @param id The slim OCEL ID to analyze
     * @return The discovered Petri net in PNML XML format
     * @throws ErlangException if mining fails
     */
    PetriNet mineAlphaPlusPlus(SlimOcelId id) throws ErlangException;

    /**
     * Frees the OCEL resources from the Erlang node.
     *
     * @param id The OCEL ID to free
     * @throws ErlangException if freeing fails
     */
    void freeOcel(OcelId id) throws ErlangException;

    /**
     * Creates a new ProcessMiningClient with default configuration.
     *
     * @return A new ProcessMiningClient instance
     */
    static ProcessMiningClient create() {
        return ProcessMiningClientImpl.create();
    }

    /**
     * Checks if the client is connected to the Erlang node.
     *
     * @return true if connected, false otherwise
     */
    boolean isConnected();
}

