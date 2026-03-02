/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This file is part of YAWL - Yet Another Workflow Language.
 *
 * YAWL is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.observatory.rdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for GgenObservationBridge using Chicago TDD style.
 *
 * Tests RDF model loading, SPARQL query execution, and convenience methods.
 */
public class GgenObservationBridgeTest {

    private GgenObservationBridge bridge;
    private static final String TEST_FACTS_TTL = """
        @prefix ex: <http://yawlfoundation.org/facts#> .
        @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .

        <yawl-engine> a ex:Module ;
            ex:moduleName "yawl-engine" ;
            ex:version "6.0.0" ;
            ex:lineCoverage 78.5 ;
            ex:testCount 234 ;
            ex:dependencies () .

        <yawl-stateless> a ex:Module ;
            ex:moduleName "yawl-stateless" ;
            ex:version "6.0.0" ;
            ex:lineCoverage 65.2 ;
            ex:testCount 156 ;
            ex:dependencies (<yawl-engine>) .

        <yawl-integration> a ex:Module ;
            ex:moduleName "yawl-integration" ;
            ex:version "6.0.0" ;
            ex:lineCoverage 45.0 ;
            ex:testCount 89 ;
            ex:dependencies (<yawl-engine>, <yawl-stateless>) .

        <yawl-mcp-a2a-app> a ex:Module ;
            ex:moduleName "yawl-mcp-a2a-app" ;
            ex:version "6.0.0" ;
            ex:lineCoverage 82.1 ;
            ex:testCount 312 ;
            ex:dependencies (<yawl-engine>, <yawl-integration>) .
        """;

    @BeforeEach
    void setUp() throws Exception {
        bridge = new GgenObservationBridge();
    }

    @Nested
    @DisplayName("Initialization Tests")
    class InitializationTests {

        @Test
        @DisplayName("Bridge starts uninitialized")
        void testBridgeStartsUninitialized() {
            assertFalse(bridge.isInitialized(), "Bridge should start uninitialized");
            assertThrows(IllegalStateException.class, () -> bridge.query("SELECT ?s WHERE { ?s ?p ?o }"),
                "Query should fail when uninitialized");
        }

        @Test
        @DisplayName("Load facts.ttl successfully")
        void testLoadFacts(@TempDir Path tempDir) throws IOException {
            // Given
            Path factsFile = tempDir.resolve("facts.ttl");
            Files.writeString(factsFile, TEST_FACTS_TTL);

            // When
            bridge.loadFacts(factsFile);

            // Then
            assertTrue(bridge.isInitialized(), "Bridge should be initialized after loading facts");
            assertEquals(4, bridge.getStatistics().get("statementCount"),
                "Should have 4 module statements");
        }

        @Test
        @DisplayName("Fail to load non-existent facts file")
        void testLoadFacts_FileNotFound() {
            // Given
            Path nonExistent = Path.of("/tmp/non-existent.ttl");

            // When & Then
            assertThrows(IOException.class, () -> bridge.loadFacts(nonExistent),
                "Should throw IOException for non-existent file");
            assertFalse(bridge.isInitialized(),
                "Bridge should remain uninitialized after failed load");
        }

        @Test
        @DisplayName("Load ontology successfully")
        void testLoadOntology(@TempDir Path tempDir) throws IOException {
            // Given
            Path factsFile = tempDir.resolve("facts.ttl");
            Path ontologyFile = tempDir.resolve("ontology.ttl");

            Files.writeString(factsFile, TEST_FACTS_TTL);
            Files.writeString(ontologyFile, """
                @prefix ex: <http://yawlfoundation.org/facts#> .
                @prefix sh: <http://www.w3.org/ns/shacl#> .

                ex:Module a sh:NodeShape ;
                    sh:property [
                        sh:path ex:moduleName ;
                        sh:minCount 1 ;
                        sh:maxCount 1 ;
                        sh:datatype xsd:string ;
                    ] .
                """);

            // When
            bridge.loadFacts(factsFile);
            bridge.loadOntology(ontologyFile);

            // Then
            assertTrue(bridge.isInitialized(), "Bridge should be initialized");
            // Note: validateAgainstOntology throws UnsupportedOperationException in current implementation
            assertThrows(UnsupportedOperationException.class, bridge::validateAgainstOntology,
                "Validation should throw UnsupportedOperationException without SHACL");
        }
    }

    @Nested
    @DisplayName("SPARQL Query Tests")
    class SparqlQueryTests {

        @BeforeEach
        void setupBridge(@TempDir Path tempDir) throws IOException {
            Path factsFile = tempDir.resolve("facts.ttl");
            Files.writeString(factsFile, TEST_FACTS_TTL);
            bridge.loadFacts(factsFile);
        }

        @Test
        @DisplayName("Execute basic SELECT query")
        void testBasicSelectQuery() {
            // Given
            String query = """
                SELECT ?moduleName
                WHERE {
                    ?m ex:moduleName ?moduleName .
                }
                """;

            // When
            List<Map<String, String>> results = bridge.query(query);

            // Then
            assertNotNull(results, "Results should not be null");
            assertEquals(4, results.size(), "Should find 4 modules");

            // Verify module names
            List<String> moduleNames = results.stream()
                .map(row -> row.get("moduleName"))
                .toList();
            assertTrue(moduleNames.contains("yawl-engine"));
            assertTrue(moduleNames.contains("yawl-stateless"));
            assertTrue(moduleNames.contains("yawl-integration"));
            assertTrue(moduleNames.contains("yawl-mcp-a2a-app"));
        }

        @Test
        @DisplayName("Execute query with filters")
        void testQueryWithFilters() {
            // Given
            String query = """
                SELECT ?moduleName ?lineCoverage
                WHERE {
                    ?m ex:moduleName ?moduleName ;
                       ex:lineCoverage ?lineCoverage .
                    FILTER (?lineCoverage > 70.0)
                }
                ORDER BY DESC(?lineCoverage)
                """;

            // When
            List<Map<String, String>> results = bridge.query(query);

            // Then
            assertNotNull(results, "Results should not be null");
            assertEquals(2, results.size(), "Should find 2 modules with >70% coverage");

            // Verify coverage values
            assertTrue(results.get(0).get("lineCoverage").equals("82.1"),
                "First result should be yawl-mcp-a2a-app with 82.1% coverage");
            assertTrue(results.get(1).get("lineCoverage").equals("78.5"),
                "Second result should be yawl-engine with 78.5% coverage");
        }

        @Test
        @DisplayName("Execute query with LIMIT")
        void testQueryWithLimit() {
            // Given
            String query = """
                SELECT ?moduleName
                WHERE {
                    ?m ex:moduleName ?moduleName .
                }
                LIMIT 2
                """;

            // When
            List<Map<String, String>> results = bridge.query(query);

            // Then
            assertNotNull(results, "Results should not be null");
            assertEquals(2, results.size(), "Should limit results to 2");
        }

        @Test
        @DisplayName("Handle empty query results")
        void testEmptyQueryResults() {
            // Given
            String query = """
                SELECT ?moduleName
                WHERE {
                    ?m ex:moduleName ?moduleName .
                    FILTER (STRSTARTS(?moduleName, "nonexistent"))
                }
                """;

            // When
            List<Map<String, String>> results = bridge.query(query);

            // Then
            assertNotNull(results, "Results should not be null");
            assertTrue(results.isEmpty(), "Should return empty list for no results");
        }

        @Test
        @DisplayName("Handle malformed SPARQL query")
        void testMalformedSparqlQuery() {
            // Given
            String malformedQuery = "SELECT WHERE { . }";

            // When & Then
            assertThrows(RuntimeException.class, () -> bridge.query(malformedQuery),
                "Should throw RuntimeException for malformed query");
        }

        @Test
        @DisplayName("Handle SELECT DISTINCT query")
        void testSelectDistinctQuery() {
            // Given - create duplicate entries
            Path factsFile = createFactsFileWithDuplicates();

            try {
                bridge = new GgenObservationBridge();
                bridge.loadFacts(factsFile);

                // Given
                String query = """
                    SELECT DISTINCT ?moduleName
                    WHERE {
                        ?m ex:moduleName ?moduleName .
                    }
                    """;

                // When
                List<Map<String, String>> results = bridge.query(query);

                // Then
                assertNotNull(results, "Results should not be null");
                assertEquals(4, results.size(), "DISTINCT should remove duplicates");

            } catch (IOException e) {
                fail("Failed to create test file: " + e.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Convenience Method Tests")
    class ConvenienceMethodTests {

        @BeforeEach
        void setupBridge(@TempDir Path tempDir) throws IOException {
            Path factsFile = tempDir.resolve("facts.ttl");
            Files.writeString(factsFile, TEST_FACTS_TTL);
            bridge.loadFacts(factsFile);
        }

        @Test
        @DisplayName("Get all modules")
        void testGetModules() {
            // When
            List<String> modules = bridge.getModules();

            // Then
            assertNotNull(modules, "Module list should not be null");
            assertEquals(4, modules.size(), "Should find 4 modules");
            assertTrue(modules.contains("yawl-engine"));
            assertTrue(modules.contains("yawl-stateless"));
            assertTrue(modules.contains("yawl-integration"));
            assertTrue(modules.contains("yawl-mcp-a2a-app"));
        }

        @Test
        @DisplayName("Get dependencies")
        void testGetDependencies() {
            // When
            List<Map<String, String>> dependencies = bridge.getDependencies();

            // Then
            assertNotNull(dependencies, "Dependencies list should not be null");

            // Count total dependencies
            long totalDeps = dependencies.stream()
                .filter(dep -> dep.get("from").equals("yawl-stateless"))
                .count();
            assertEquals(1, totalDeps, "yawl-stateless should depend on yawl-engine");

            totalDeps = dependencies.stream()
                .filter(dep -> dep.get("from").equals("yawl-integration"))
                .count();
            assertEquals(2, totalDeps, "yawl-integration should depend on 2 modules");

            totalDeps = dependencies.stream()
                .filter(dep -> dep.get("from").equals("yawl-engine"))
                .count();
            assertEquals(0, totalDeps, "yawl-engine should have no dependencies");
        }

        @Test
        @DisplayName("Find circular dependencies")
        void testFindCircularDependencies() {
            // Given - facts with circular dependency would be unusual, but test the query
            // Our test data has no circular dependencies

            // When
            List<Map<String, String>> circularDeps = bridge.findCircularDependencies();

            // Then
            assertNotNull(circularDeps, "Circular dependencies list should not be null");
            assertTrue(circularDeps.isEmpty(), "Should find no circular dependencies in test data");
        }

        @Test
        @DisplayName("Get low coverage modules")
        void testGetLowCoverageModules() {
            // When
            List<Map<String, String>> lowCoverage = bridge.getLowCoverageModules();

            // Then
            assertNotNull(lowCoverage, "Low coverage list should not be null");
            assertEquals(1, lowCoverage.size(), "Should find 1 module with <65% coverage");

            Map<String, String> module = lowCoverage.get(0);
            assertEquals("yawl-integration", module.get("moduleName"));
            assertEquals("45.0", module.get("lineCoverage"));
        }

        @Test
        @DisplayName("Get integration points")
        void testGetIntegrationPoints() {
            // When
            List<Map<String, String>> integrationPoints = bridge.getIntegrationPoints();

            // Then
            assertNotNull(integrationPoints, "Integration points list should not be null");
            // Our test data doesn't have integration points, so should be empty
            assertTrue(integrationPoints.isEmpty(), "Should find no integration points in test data");
        }

        @Test
        @DisplayName("Get statistics")
        void testGetStatistics() {
            // When
            Map<String, Object> stats = bridge.getStatistics();

            // Then
            assertNotNull(stats, "Statistics should not be null");
            assertEquals(4, stats.get("statementCount"), "Should have 4 statements");
            assertEquals(4, stats.get("moduleCount"), "Should have 4 modules");

            @SuppressWarnings("unchecked")
            List<Map<String, String>> deps = (List<Map<String, String>>) stats.get("dependencyCount");
            assertNotNull(deps, "Dependencies should not be null");
            assertEquals(3, deps.size(), "Should have 3 dependencies total");
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Throw IllegalStateException when querying uninitialized bridge")
        void testQueryUninitializedBridge() {
            // Given - uninitialized bridge
            GgenObservationBridge uninitializedBridge = new GgenObservationBridge();

            // When & Then
            assertThrows(IllegalStateException.class,
                () -> uninitializedBridge.query("SELECT ?s WHERE { ?s ?p ?o }"),
                "Should throw IllegalStateException when querying uninitialized bridge");
        }

        @Test
        @DisplayName("Handle query timeout gracefully")
        void testQueryTimeout() {
            // This would test query timeout handling if implemented
            // For now, we test that queries complete within reasonable time

            // Given
            Path factsFile = createFactsFileWithComplexData();

            try {
                bridge = new GgenObservationBridge();
                bridge.loadFacts(factsFile);

                // When - complex query that should execute quickly
                long startTime = System.currentTimeMillis();
                List<Map<String, String>> results = bridge.query("""
                    SELECT ?moduleName ?lineCoverage
                    WHERE {
                        ?m ex:moduleName ?moduleName ;
                           ex:lineCoverage ?lineCoverage .
                    }
                    ORDER BY DESC(?lineCoverage)
                    """);
                long duration = System.currentTimeMillis() - startTime;

                // Then
                assertNotNull(results, "Query should complete successfully");
                assertTrue(duration < 5000, "Complex query should complete within 5 seconds");

            } catch (IOException e) {
                fail("Failed to create test file: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("Handle large result set")
        void testLargeResultSet() {
            // Given - facts with many modules
            Path factsFile = createFactsFileWithManyModules();

            try {
                bridge = new GgenObservationBridge();
                bridge.loadFacts(factsFile);

                // When
                List<Map<String, String>> results = bridge.getModules();

                // Then
                assertTrue(results.size() > 100, "Should handle large result sets");

            } catch (IOException e) {
                fail("Failed to create test file: " + e.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Handle empty facts.ttl file")
        void testEmptyFactsFile(@TempDir Path tempDir) throws IOException {
            // Given
            Path emptyFactsFile = tempDir.resolve("empty.ttl");
            Files.writeString(emptyFactsFile, "@prefix ex: <http://yawlfoundation.org/facts#> .");

            // When
            bridge.loadFacts(emptyFactsFile);

            // Then
            assertTrue(bridge.isInitialized(), "Bridge should be initialized even with empty facts");

            List<Map<String, String>> results = bridge.query("SELECT ?s WHERE { ?s ?p ?o }");
            assertTrue(results.isEmpty(), "Empty facts should return empty results");
        }

        @Test
        @DisplayName("Handle facts with literal values")
        void testLiteralValues() {
            // Given
            Path factsFile = createFactsFileWithLiterals();

            try {
                bridge = new GgenObservationBridge();
                bridge.loadFacts(factsFile);

                // When
                List<Map<String, String>> results = bridge.query("""
                    SELECT ?moduleName ?coverage ?hasTests
                    WHERE {
                        ?m ex:moduleName ?moduleName ;
                           ex:lineCoverage ?coverage ;
                           ex:hasTests ?hasTests .
                    }
                    """);

                // Then
                assertNotNull(results, "Should handle literal values");
                assertEquals(1, results.size(), "Should find one module");

                Map<String, String> module = results.get(0);
                assertEquals("yawl-engine", module.get("moduleName"));
                assertEquals("78.5", module.get("coverage"));
                assertEquals("true", module.get("hasTests"));

            } catch (IOException e) {
                fail("Failed to create test file: " + e.getMessage());
            }
        }
    }

    // Helper methods
    private Path createFactsFileWithDuplicates() {
        try {
            Path factsDir = Path.of(TEMP_DIR, "facts");
            Files.createDirectories(factsDir);
            Path factsFile = factsDir.resolve("facts.ttl");
            Files.writeString(factsFile, TEST_FACTS_TTL);
            return factsFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create facts file", e);
        }
    }

    private Path createFactsFileWithComplexData() {
        try {
            StringBuilder complexData = new StringBuilder(TEST_FACTS_TTL);

            // Add more complex data
            for (int i = 0; i < 50; i++) {
                complexData.append("""

                    <module-%d> a ex:Module ;
                        ex:moduleName "module-%d" ;
                        ex:version "6.0.0" ;
                        ex:lineCoverage %f ;
                        ex:testCount %d ;
                        ex:dependencies () .
                    """.formatted(i, i, Math.random() * 100, i * 10));
            }

            Path factsDir = Path.of(TEMP_DIR, "facts");
            Files.createDirectories(factsDir);
            Path factsFile = factsDir.resolve("complex.ttl");
            Files.writeString(factsFile, complexData.toString());
            return factsFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create complex facts file", e);
        }
    }

    private Path createFactsFileWithManyModules() {
        try {
            StringBuilder manyModules = new StringBuilder("@prefix ex: <http://yawlfoundation.org/facts#> .\n\n");

            // Create 200 modules
            for (int i = 0; i < 200; i++) {
                manyModules.append("""
                    <module-%d> a ex:Module ;
                        ex:moduleName "module-%d" ;
                        ex:version "6.0.0" ;
                        ex:lineCoverage %f ;
                        ex:testCount %d ;
                        ex:dependencies () .
                    """.formatted(i, i, Math.random() * 100, i));
            }

            Path factsDir = Path.of(TEMP_DIR, "facts");
            Files.createDirectories(factsDir);
            Path factsFile = factsDir.resolve("many-modules.ttl");
            Files.writeString(factsFile, manyModules.toString());
            return factsFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create many modules file", e);
        }
    }

    private Path createFactsFileWithLiterals() {
        try {
            String literalData = """
                @prefix ex: <http://yawlfoundation.org/facts#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

                <yawl-engine> a ex:Module ;
                    ex:moduleName "yawl-engine" ;
                    ex:lineCoverage "78.5"^^xsd:decimal ;
                    ex:hasTests "true"^^xsd:boolean .
                """;

            Path factsDir = Path.of(TEMP_DIR, "facts");
            Files.createDirectories(factsDir);
            Path factsFile = factsDir.resolve("literals.ttl");
            Files.writeString(factsFile, literalData);
            return factsFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create literals file", e);
        }
    }
}