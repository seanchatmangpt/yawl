/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.nativebridge.erlang;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * High-level process mining client crossing Boundary A.
 * Erlang distribution protocol over Unix domain socket (~5-20µs).
 *
 * <p>This interface provides a pure Java 25 abstraction over the
 * process mining capabilities running in the BEAM domain. All methods
 * are thread-safe and handle automatic conversion between Java objects
 * and Erlang terms.</p>
 */
public sealed interface ProcessMiningClient extends AutoCloseable {

    /**
     * Imports OCEL JSON file into the BEAM domain.
     * Creates a persistent handle in Mnesia registry.
     *
     * @param path Path to OCEL JSON file
     * @return OcelId UUID for the imported log
     * @throws ErlangException on import errors or BEAM domain failures
     */
    OcelId importOcel(Path path) throws ErlangException;

    /**
     * Creates slim OCEL representation for mining operations.
     * The slim log retains only essential information for mining algorithms.
     *
     * @param id OcelId of the log to slim
     * @return SlimOcelId UUID for the slim representation
     * @throws ErlangException if OcelId not found or slimming fails
     */
    SlimOcelId slimLink(OcelId id) throws ErlangException;

    /**
     * Discovers declarative constraints via OC-DECLARE algorithm.
     * Returns a list of constraint candidates with their support and confidence.
     *
     * @param id SlimOcelId to mine for constraints
     * @return list of constraint candidates
     * @throws ErlangException on mining errors or BEAM domain failures
     */
    List<Constraint> discoverOcDeclare(SlimOcelId id) throws ErlangException;

    /**
     * Performs token replay with conformance checking.
     * Compares the OCEL log against a Petri net model.
     *
     * @param ocel OcelId of the event log
     * @param pn PetriNetId of the net model
     * @return conformance result with fitness, precision, etc.
     * @throws ErlangException if replay fails or models are incompatible
     */
    ConformanceResult tokenReplay(OcelId ocel, PetriNetId pn) throws ErlangException;

    /**
     * Discovers directly-follows graph from the OCEL log.
     *
     * @param id SlimOcelId of the log
     * @return directly-follows graph
     * @throws ErlangException on mining errors
     */
    DirectlyFollowsGraph discoverDfg(SlimOcelId id) throws ErlangException;

    /**
     * Mines Petri net using alpha++ algorithm.
     *
     * @param id SlimOcelId of the log
     * @return Petri net model
     * @throws ErlangException on mining errors
     */
    PetriNet mineAlphaPlusPlus(SlimOcelId id) throws ErlangException;
}

/**
 * Represents an OCEL identifier in the BEAM domain.
 */
public final record OcelId(UUID uuid) {
    public OcelId {
        if (uuid == null) {
            throw new IllegalArgumentException("OcelId UUID cannot be null");
        }
    }

    public String toHexString() {
        return uuid.toString();
    }
}

/**
 * Represents a slim OCEL identifier in the BEAM domain.
 */
public final record SlimOcelId(UUID uuid) {
    public SlimOcelId {
        if (uuid == null) {
            throw new IllegalArgumentException("SlimOcelId UUID cannot be null");
        }
    }

    public String toHexString() {
        return uuid.toString();
    }
}

/**
 * Represents a Petri net identifier in the BEAM domain.
 */
public final record PetriNetId(UUID uuid) {
    public PetriNetId {
        if (uuid == null) {
            throw new IllegalArgumentException("PetriNetId UUID cannot be null");
        }
    }

    public String toHexString() {
        return uuid.toString();
    }
}

/**
 * Represents a declarative constraint from OC-DECLARE mining.
 */
public record Constraint(
    String type,
    List<String> activities,
    double support,
    double confidence,
    String description
) {
    public Constraint {
        if (type == null || activities == null) {
            throw new IllegalArgumentException("Constraint type and activities cannot be null");
        }
    }
}

/**
 * Represents conformance checking results.
 */
public record ConformanceResult(
    double fitness,
    double precision,
    double fitnessPercentage,
    int missingTokens,
    int consumedTokens,
    int producedTokens,
    int remainingTokens,
    List<String> traces
) {
    public ConformanceResult {
        if (traces == null) {
            throw new IllegalArgumentException("Traces cannot be null");
        }
    }
}

/**
 * Represents a directly-follows graph edge.
 */
public record DfgEdge(
    String source,
    String target,
    int frequency
) {
    public DfgEdge {
        if (source == null || target == null) {
            throw new IllegalArgumentException("Source and target cannot be null");
        }
    }
}

/**
 * Represents a directly-follows graph.
 */
public record DirectlyFollowsGraph(
    List<String> activities,
    List<DfgEdge> edges,
    List<DfgEdge> reverseEdges
) {
    public DirectlyFollowsGraph {
        if (activities == null || edges == null || reverseEdges == null) {
            throw new IllegalArgumentException("Activities, edges, and reverse edges cannot be null");
        }
    }
}

/**
 * Represents a Petri net place.
 */
public record Place(
    String id,
    String label,
    int marking
) {
    public Place {
        if (id == null || label == null) {
            throw new IllegalArgumentException("Place id and label cannot be null");
        }
    }
}

/**
 * Represents a Petri net transition.
 */
public record Transition(
    String id,
    String label,
    boolean isSilent
) {
    public Transition {
        if (id == null || label == null) {
            throw new IllegalArgumentException("Transition id and label cannot be null");
        }
    }
}

/**
 * Represents a Petri net arc.
 */
public record Arc(
    String id,
    String source,
    String target,
    int weight
) {
    public Arc {
        if (id == null || source == null || target == null) {
            throw new IllegalArgumentException("Arc id, source, and target cannot be null");
        }
    }
}

/**
 * Represents a Petri net model.
 */
public record PetriNet(
    List<Place> places,
    List<Transition> transitions,
    List<Arc> arcs
) {
    public PetriNet {
        if (places == null || transitions == null || arcs == null) {
            throw new IllegalArgumentException("Places, transitions, and arcs cannot be null");
        }
    }
}