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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.qlever;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Generates realistic test data for QLeveldb benchmarks.
 */
public final class BenchmarkDataGenerator {
    
    private static final Random RANDOM = new Random(42); // Fixed seed for reproducibility
    
    private BenchmarkDataGenerator() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Simple SPARQL query patterns for basic benchmarking.
     */
    private static final String[] SIMPLE_PATTERNS = {
        "PREFIX workflow: <http://yawl.io/workflow#> SELECT ?case WHERE { ?case workflow:status ?status }",
        "SELECT ?case ?status WHERE { ?case workflow:status ?status }",
        "PREFIX yawl: <http://yawl.io/> SELECT ?case ?task WHERE { ?case yawl:hasTask ?task }",
        "SELECT ?case WHERE { ?case a workflow:Case }",
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> SELECT ?label WHERE { ?case rdfs:label ?label }"
    };
    
    /**
     * Complex SPARQL query patterns with joins and filters.
     */
    private static final String[] COMPLEX_PATTERNS = {
        "PREFIX yawl: <http://yawl.io/> " +
        "SELECT ?case ?task ?status WHERE { " +
        "  ?case yawl:hasTask ?task ; " +
        "         yawl:hasStatus ?status . " +
        "  FILTER (?status = 'active') " +
        "}",
        
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
        "SELECT ?case ?task ?label WHERE { " +
        "  ?case rdfs:label ?label ; " +
        "         yawl:hasTask ?task . " +
        "  ?task rdfs:label ?taskLabel " +
        "}",
        
        "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> " +
        "SELECT ?case ?created ?deadline WHERE { " +
        "  ?case workflow:created ?created ; " +
        "         workflow:deadline ?deadline . " +
        "  FILTER (?deadline > \"2023-01-01\"^^xsd:dateTime) " +
        "}",
        
        "PREFIX schema: <http://schema.org/> " +
        "SELECT ?case ?task ?priority WHERE { " +
        "  ?case schema:hasPart ?task . " +
        "  ?task schema:priority ?priority . " +
        "  ORDER BY DESC(?priority) " +
        "}"
    };
    
    /**
     * Large UNION queries for scaling tests.
     */
    private static final String[] LARGE_QUERY_PARTS = IntStream.range(0, 50)
        .mapToObj(i -> String.format(
            "{ SELECT ?case_%d ?status_%d WHERE { ?case_%d workflow:status ?status_%d } }",
            i, i, i, i))
        .toArray(String[]::new);
    
    /**
     * Generates a simple query based on pattern index.
     */
    public static String generateSimpleQuery(int patternIndex) {
        return SIMPLE_PATTERNS[patternIndex % SIMPLE_PATTERNS.length];
    }
    
    /**
     * Generates a complex query based on pattern index.
     */
    public static String generateComplexQuery(int patternIndex) {
        return COMPLEX_PATTERNS[patternIndex % COMPLEX_PATTERNS.length];
    }
    
    /**
     * Generates a large UNION query for scalability testing.
     */
    public static String generateLargeUnionQuery(int querySize) {
        if (querySize <= 0) {
            return generateSimpleQuery(0);
        }
        
        int partsToUse = Math.min(querySize, LARGE_QUERY_PARTS.length);
        String union = String.join(" UNION ", 
            IntStream.range(0, partsToUse)
                .mapToObj(i -> LARGE_QUERY_PARTS[i])
                .toArray(String[]::new));
        
        return "SELECT * WHERE " + union;
    }
    
    /**
     * Generates a query with result size limit.
     */
    public static String generateQueryWithLimit(int limit) {
        return String.format(
            "SELECT ?case ?status WHERE { ?case workflow:status ?status } LIMIT %d",
            limit
        );
    }
    
    /**
     * Generates a query with random parameters.
     */
    public static String generateRandomQuery() {
        int patternType = RANDOM.nextInt(3);
        switch (patternType) {
            case 0:
                return generateSimpleQuery(RANDOM.nextInt(SIMPLE_PATTERNS.length));
            case 1:
                return generateComplexQuery(RANDOM.nextInt(COMPLEX_PATTERNS.length));
            case 2:
                int size = RANDOM.nextInt(20) + 1; // 1-20 queries
                return generateLargeUnionQuery(size);
            default:
                return generateSimpleQuery(0);
        }
    }
    
    /**
     * Generates a series of queries for stress testing.
     */
    public static List<String> generateQueryBatch(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> generateRandomQuery())
            .collect(Collectors.toList());
    }
    
    /**
     * Creates benchmark configuration with realistic parameters.
     */
    public static BenchmarkConfig createBenchmarkConfig() {
        return new BenchmarkConfig(
            10,   // warmupIterations
            100,  // measurementIterations
            8,    // threadCount
            30,   // timeoutSeconds
            System.getProperty("user.dir") + "/benchmark-results"
        );
    }
    
    /**
     * Configuration class for benchmark runs.
     */
    public static class BenchmarkConfig {
        private final int warmupIterations;
        private final int measurementIterations;
        private final int threadCount;
        private final long timeoutSeconds;
        private final String resultsDirectory;
        
        public BenchmarkConfig(int warmupIterations, int measurementIterations, 
                             int threadCount, long timeoutSeconds, String resultsDirectory) {
            this.warmupIterations = warmupIterations;
            this.measurementIterations = measurementIterations;
            this.threadCount = threadCount;
            this.timeoutSeconds = timeoutSeconds;
            this.resultsDirectory = resultsDirectory;
        }
        
        // Getters
        public int getWarmupIterations() { return warmupIterations; }
        public int getMeasurementIterations() { return measurementIterations; }
        public int getThreadCount() { return threadCount; }
        public long getTimeoutSeconds() { return timeoutSeconds; }
        public String getResultsDirectory() { return resultsDirectory; }
    }
}
