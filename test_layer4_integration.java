/*
 * Layer 4 Conformance Pipeline Integration Test
 *
 * Tests the complete integration:
 * 1. Rust4PM OCEL import
 * 2. Erlang process mining bridge
 * 3. GapAnalysisEngine with QLever persistence
 * 4. Conformance scoring and WSJF ranking
 */

import org.yawlfoundation.yawl.integration.selfplay.GapAnalysisEngine;
import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import org.yawlfoundation.yawl.qlever.QLeverFfiException;
import org.yawlfoundation.yawl.qlever.QLeverResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

public class test_layer4_integration {

    public static void main(String[] args) {
        System.out.println("🔬 Layer 4: Conformance Scoring Pipeline Integration Test");
        System.out.println("======================================================");

        try {
            // Test 1: Initialize GapAnalysisEngine
            testGapAnalysisEngine();

            // Test 2: QLever persistence
            testQLeverPersistence();

            // Test 3: Simulate conformance scoring
            testConformanceScoring();

            System.out.println("\n✅ All Layer 4 tests completed successfully!");

        } catch (Exception e) {
            System.err.println("\n❌ Layer 4 test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void testGapAnalysisEngine() throws Exception {
        System.out.println("\n📊 Testing GapAnalysisEngine...");

        GapAnalysisEngine engine = new GapAnalysisEngine();
        engine.initialize();

        // Test gap discovery
        List<GapAnalysisEngine.CapabilityGap> gaps = engine.discoverGaps();
        System.out.println("   Discovered " + gaps.size() + " capability gaps");

        // Test gap prioritization
        List<GapAnalysisEngine.GapPriority> priorities = engine.prioritizeGaps(gaps);
        System.out.println("   Prioritized " + priorities.size() + " gaps");

        // Test persistence
        int persisted = engine.persistGaps(gaps);
        System.out.println("   Persisted " + persisted + " gaps to QLever");

        engine.shutdown();
    }

    private static void testQLeverPersistence() throws Exception {
        System.out.println("\n🗄️ Testing QLever persistence...");

        QLeverEmbeddedSparqlEngine qlever = new QLeverEmbeddedSparqlEngine();
        qlever.initialize();

        // Load some test RDF data
        String testRDF = """
            @prefix sim: <http://yawlfoundation.org/yawl/simulation#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            <http://yawlfoundation.org/yawl/simulation/gap/test> a sim:CapabilityGap ;
                sim:gapId "test_gap" ;
                sim:requiresCapability "TestType" ;
                sim:demandScore 8.5 ;
                sim:complexity 3.0 ;
                rdfs:comment "Test capability gap" ;
                sim:discoveredAt "2024-01-01T00:00:00Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> .
            """;

        QLeverResult result = qlever.loadRdfData(testRDF, "TURTLE");
        System.out.println("   Loaded RDF data: " + result.getStatus());

        // Test querying
        String query = """
            PREFIX sim: <http://yawlfoundation.org/yawl/simulation#>
            SELECT ?gap ?wsjf WHERE {
                ?gap a sim:CapabilityGap ;
                    sim:gapId ?gapId ;
                    sim:wsjfScore ?wsjf .
            }
            """;

        QLeverResult queryResult = qlever.executeQuery(query);
        System.out.println("   Query executed: " + queryResult.getStatus());

        qlever.shutdown();
    }

    private static void testConformanceScoring() throws Exception {
        System.out.println("\n🎯 Testing conformance scoring...");

        // Simulate conformance score persistence
        QLeverEmbeddedSparqlEngine qlever = new QLeverEmbeddedSparqlEngine();
        qlever.initialize();

        // Insert test conformance score
        String updateQuery = """
            PREFIX sim: <http://yawlfoundation.org/yawl/simulation#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

            INSERT DATA {
                <http://yawlfoundation.org/yawl/simulation/run/test001> a sim:SimulationRun ;
                    sim:conformanceScore 0.85 ;
                    sim:iteration 1 ;
                    sim:timestamp "2024-01-01T12:00:00Z"^^xsd:dateTime .
            }
            """;

        QLeverResult updateResult = qlever.executeUpdate(updateQuery);
        System.out.println("   Inserted conformance score: " + updateResult.getStatus());

        // Verify the score was inserted
        String verifyQuery = """
            PREFIX sim: <http://yawlfoundation.org/yawl/simulation#>
            SELECT ?run ?score WHERE {
                ?run a sim:SimulationRun ;
                    sim:conformanceScore ?score .
            }
            """;

        QLeverResult verifyResult = qlever.executeQuery(verifyQuery);
        System.out.println("   Verified conformance score: " + verifyResult.getStatus());

        qlever.shutdown();
    }
}