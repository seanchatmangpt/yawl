package org.yawlfoundation.yawl.benchmark.sparql;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.*;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark comparing SPARQL engine implementations.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1)
public class QLeverBenchmark {

    @Param({"qlever-http", "qlever-embedded", "oxigraph"})
    private String engineType;

    private SparqlEngine engine;

    @Setup
    public void setup() throws Exception {
        switch (engineType) {
            case "qlever-http":
                engine = new QLeverSparqlEngine("http://localhost:7001");
                break;
            case "qlever-embedded":
                engine = new QLeverEmbeddedSparqlEngine();
                break;
            case "oxigraph":
                engine = new OxigraphSparqlEngine("http://localhost:8083");
                break;
            default:
                throw new IllegalArgumentException("Unknown engine type: " + engineType);
        }

        if (!engine.isAvailable()) {
            System.err.println("Engine " + engineType + " not available, skipping benchmark");
            engine = null;
        }
    }

    @TearDown
    public void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Benchmark
    public void simpleConstruct(Blackhole bh) throws Exception {
        if (engine == null) return;
        
        String query = "PREFIX yawl: <http://www.yawlfoundation.org/yawl#> " +
                       "CONSTRUCT { ?case a yawl:Case . } " +
                       "WHERE { ?case a yawl:Case . } LIMIT 100";
        
        String result = engine.constructToTurtle(query);
        bh.consume(result);
    }

    @Benchmark
    public void complexJoin(Blackhole bh) throws Exception {
        if (engine == null) return;
        
        String query = "PREFIX yawl: <http://www.yawlfoundation.org/yawl#> " +
                       "CONSTRUCT { ?case yawl:hasTask ?task . } " +
                       "WHERE { " +
                       "  ?case a yawl:Case . " +
                       "  ?case yawl:hasTask ?task . " +
                       "  ?task yawl:hasStatus 'running' " +
                       "} LIMIT 200";
        
        String result = engine.constructToTurtle(query);
        bh.consume(result);
    }

    @Benchmark
    public void largeResult(Blackhole bh) throws Exception {
        if (engine == null) return;

        String query = "PREFIX yawl: <http://www.yawlfoundation.org/yawl#> " +
                       "CONSTRUCT { ?s ?p ?o . } " +
                       "WHERE { ?s ?p ?o . } LIMIT 5000";

        String result = engine.constructToTurtle(query);
        bh.consume(result);
    }

    @Benchmark
    public void caseTaskRelationship(Blackhole bh) throws Exception {
        if (engine == null) return;

        String query = "PREFIX yawl: <http://www.yawlfoundation.org/yawl#> " +
                       "CONSTRUCT { ?case yawl:hasTask ?task . ?task yawl:hasStatus ?status . } " +
                       "WHERE { " +
                       "  ?case a yawl:Case . " +
                       "  ?case yawl:hasTask ?task . " +
                       "  ?task yawl:hasStatus ?status . " +
                       "  ?task a yawl:Task . " +
                       "} LIMIT 1000";

        String result = engine.constructToTurtle(query);
        bh.consume(result);
    }
}
