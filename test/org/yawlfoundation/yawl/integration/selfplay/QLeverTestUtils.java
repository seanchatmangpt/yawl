/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
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

package org.yawlfoundation.yawl.integration.selfplay;

import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import org.yawlfoundation.yawl.qlever.QLeverFfiException;
import org.yawlfoundation.yawl.qlever.QLeverSparqlResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Test utilities for interacting with QLever in test scenarios.
 * Provides methods to query composition counts, native call counts, and conformance scores.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class QLeverTestUtils {

    private final QLeverEmbeddedSparqlEngine qlever;

    public QLeverTestUtils() throws QLeverFfiException {
        this.qlever = new QLeverEmbeddedSparqlEngine();
        this.qlever.initialize();
    }

    public void shutdown() throws QLeverFfiException {
        if (qlever != null) {
            qlever.shutdown();
        }
    }

    /**
     * Get the current composition count from QLever.
     *
     * @return Current number of compositions in QLever
     * @throws QLeverFfiException if query fails
     */
    public int getCompositionCount() throws QLeverFfiException {
        String query = """
            SELECT COUNT(?comp) as ?count
            WHERE {
                ?comp a composition .
            }
            """;

        QLeverSparqlResult result = qlever.query(query);
        return result.getInt("count");
    }

    /**
     * Get the current native call count from QLever.
     *
     * @return Current number of native calls in QLever
     * @throws QLeverFfiException if query fails
     */
    public int getNativeCallCount() throws QLeverFfiException {
        String query = """
            SELECT COUNT(?call) as ?count
            WHERE {
                ?call a nativeCall .
            }
            """;

        QLeverSparqlResult result = qlever.query(query);
        return result.getInt("count");
    }

    /**
     * Get the latest conformance score from QLever.
     *
     * @return Latest conformance score (0.0 to 1.0)
     * @throws QLeverFfiException if query fails
     */
    public double getConformanceScore() throws QLeverFfiException {
        String query = """
            SELECT ?score
            WHERE {
                ?analysis a conformanceAnalysis ;
                         hasScore ?score .
            }
            ORDER BY DESC(?analysis.timestamp)
            LIMIT 1
            """;

        QLeverSparqlResult result = qlever.query(query);
        return result.getDouble("score");
    }

    /**
     * Count the number of capability gaps in QLever.
     *
     * @return Number of persisted capability gaps
     * @throws QLeverFfiException if query fails
     */
    public int countCapabilityGaps() throws QLeverFfiException {
        String query = """
            SELECT COUNT(?gap) as ?count
            WHERE {
                ?gap a capabilityGap .
            }
            """;

        QLeverSparqlResult result = qlever.query(query);
        return result.getInt("count");
    }

    /**
     * Get all persisted gap IDs from QLever.
     *
     * @return List of persisted gap IDs
     * @throws QLeverFfiException if query fails
     */
    public List<String> getPersistedGapIds() throws QLeverFfiException {
        String query = """
            SELECT ?gapId
            WHERE {
                ?gap a capabilityGap ;
                     hasId ?gapId .
            }
            """;

        QLeverSparqlResult result = qlever.query(query);
        return result.getResults().stream()
            .map(row -> row.get("gapId"))
            .collect(Collectors.toList());
    }

    /**
     * Verify that a specific gap exists in QLever.
     *
     * @param gapId Gap ID to check for
     * @return true if gap exists, false otherwise
     * @throws QLeverFfiException if query fails
     */
    public boolean gapExists(String gapId) throws QLeverFfiException {
        String query = """
            ASK WHERE {
                ?gap a capabilityGap ;
                     hasId ?id .
                FILTER (?id = \"""" + gapId + """\")
            }
            """;

        QLeverSparqlResult result = qlever.query(query);
        return result.getBoolean("result");
    }

    /**
     * Get the latest composition file path from QLever.
     *
     * @return Path to the latest composition file
     * @throws QLeverFfiException if query fails
     */
    public String getLatestCompositionPath() throws QLeverFfiException {
        String query = """
            SELECT ?path
            WHERE {
                ?comp a composition ;
                      hasPath ?path .
            }
            ORDER BY DESC(?comp.timestamp)
            LIMIT 1
            """;

        QLeverSparqlResult result = qlever.query(query);
        return result.getString("path");
    }

    /**
     * Get statistics from QLever about test data.
     *
     * @return Map of statistics keys to values
     * @throws QLeverFfiException if query fails
     */
    public Map<String, Object> getTestStatistics() throws QLeverFfiException {
        String query = """
            SELECT (COUNT(?comp) as ?compositions)
                   (COUNT(?gap) as ?gaps)
                   (COUNT(?call) as ?nativeCalls)
            WHERE {
                ?comp a composition .
                ?gap a capabilityGap .
                ?call a nativeCall .
            }
            """;

        QLeverSparqlResult result = qlever.query(query);
        var row = result.getResults().get(0);
        return Map.of(
            "compositions", row.get("compositions"),
            "gaps", row.get("gaps"),
            "nativeCalls", row.get("nativeCalls")
        );
    }
}