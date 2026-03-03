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
 * You should have received a copy of the GNU Lesser General
 * Public License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.benchmark.sparql;

import org.openjdk.jmh.annotations.*;
import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import org.yawlfoundation.yawl.qlever.QLeverResult;
import org.yawlfoundation.yawl.qlever.QLeverFfiException;

import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark for QLever embedded SPARQL engine.
 *
 * IMPORTANT: QLever is an embedded FFI engine (NOT HTTP).
 * All benchmarks use QLeverEmbeddedSparqlEngine directly (in-process).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1)
public class QLeverBenchmark {

    @Param({"qlever-embedded", "oxigraph"})
    private String engineType;

    private QLeverEmbeddedSparqlEngine qleverEngine;
    private boolean engineAvailable = false;

    @Setup
    public void setup() throws Exception {
        switch (engineType) {
            case "qlever-embedded":
                qleverEngine = new QLeverEmbeddedSparqlEngine();
                try {
                    qleverEngine.initialize();
                    engineAvailable = qleverEngine.isInitialized();
                } catch (QLeverFfiException e) {
                    System.err.println("QLever embedded engine not available: " + e.getMessage());
                    engineAvailable = false;
                }
                break;
            case "oxigraph":
                // Oxigraph would be tested via HTTP endpoint
                // This benchmark focuses on QLever embedded
                engineAvailable = false;
                System.err.println("Oxigraph benchmark not implemented - use QLever embedded");
                break;
            default:
                throw new IllegalArgumentException("Unknown engine type: " + engineType);
        }

        if (!engineAvailable) {
            System.err.println("Engine " + engineType + " not available, skipping benchmark");
        }
    }

    @TearDown
    public void tearDown() {
        if (qleverEngine != null) {
            try {
                qleverEngine.shutdown();
            } catch (Exception e) {
                // Ignore shutdown errors
            }
        }
    }

    @Benchmark
    public void simpleConstruct(Blackhole bh) throws Exception {
        if (!engineAvailable) return;

        String query = "PREFIX yawl: <http://www.yawlfoundation.org/yawl#> " +
                       "SELECT ?case WHERE { ?case a yawl:Case . } LIMIT 100";

        QLeverResult result = qleverEngine.executeQuery(query);
        bh.consume(result);
    }

    @Benchmark
    public void complexJoin(Blackhole bh) throws Exception {
        if (!engineAvailable) return;

        String query = "PREFIX yawl: <http://www.yawlfoundation.org/yawl#> " +
                       "SELECT ?case ?task WHERE { " +
                       "  ?case a yawl:Case . " +
                       "  ?case yawl:hasTask ?task . " +
                       "  ?task yawl:hasStatus 'running' " +
                       "} LIMIT 200";

        QLeverResult result = qleverEngine.executeQuery(query);
        bh.consume(result);
    }

    @Benchmark
    public void largeResult(Blackhole bh) throws Exception {
        if (!engineAvailable) return;

        String query = "SELECT ?s ?p ?o WHERE { ?s ?p ?o . } LIMIT 5000";

        QLeverResult result = qleverEngine.executeQuery(query);
        bh.consume(result);
    }

    @Benchmark
    public void caseTaskRelationship(Blackhole bh) throws Exception {
        if (!engineAvailable) return;

        String query = "PREFIX yawl: <http://www.yawlfoundation.org/yawl#> " +
                       "SELECT ?case ?task ?status WHERE { " +
                       "  ?case a yawl:Case . " +
                       "  ?case yawl:hasTask ?task . " +
                       "  ?task yawl:hasStatus ?status . " +
                       "  ?task a yawl:Task . " +
                       "} LIMIT 1000";

        QLeverResult result = qleverEngine.executeQuery(query);
        bh.consume(result);
    }
}
