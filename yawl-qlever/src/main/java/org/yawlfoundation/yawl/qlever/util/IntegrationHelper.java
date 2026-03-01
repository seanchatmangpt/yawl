/*
 * Copyright 2026 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.yawlfoundation.yawl.qlever.util;

import org.yawlfoundation.yawl.qlever.QLeverEmbedded;
import org.yawlfoundation.yawl.qlever.sparql.SparqlResult;
import org.yawlfoundation.yawl.elements.YAWLOntology;

import java.io.*;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Helper class providing common utility methods for QLever integration with YAWL components.
 * This class includes methods for data loading, query optimization, and result processing.
 */
public class IntegrationHelper {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * Loads YAWL workflow data into QLever from various sources
     */
    public static void loadYawlData(QLeverEmbedded qlever, String source, String format) throws IOException {
        // Validate input
        if (qlever == null) {
            throw new IllegalArgumentException("QLever engine cannot be null");
        }
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Data source cannot be empty");
        }

        // Determine loading strategy based on source type
        if (source.startsWith("http://") || source.startsWith("https://")) {
            loadFromUrl(qlever, source, format);
        } else if (source.endsWith(".ttl") || source.endsWith(".turtle")) {
            loadFromFile(qlever, source, "Turtle");
        } else if (source.endsWith(".n3") || source.endsWith(".n-triples")) {
            loadFromFile(qlever, source, "N-Triples");
        } else if (source.endsWith(".json")) {
            loadFromJson(qlever, source);
        } else {
            // Default to RDF/XML
            loadFromFile(qlever, source, "RDF/XML");
        }
    }

    private static void loadFromUrl(QLeverEmbedded qlever, String url, String format) throws IOException {
        // Implementation for loading from URL
        // This would typically involve HTTP client to fetch the data
        // For now, we'll use a simple file-based approach for testing
        File tempFile = File.createTempFile("yawl-data-", "." + format.toLowerCase());

        // Write URL content to temp file (mock implementation)
        try (Writer writer = new FileWriter(tempFile)) {
            writer.write("# Mock data from URL: " + url + "\n");
            writer.write("<http://example.org/workflow> a <http://www.yawlfoundation.org/yawl#Workflow> .\n");
        }

        qlever.loadRdfData(tempFile.getAbsolutePath(), format);
        tempFile.delete();
    }

    private static void loadFromFile(QLeverEmbedded qlever, String filePath, String format) throws IOException {
        // Validate file exists and is readable
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + filePath);
        }
        if (!file.canRead()) {
            throw new IOException("File not readable: " + filePath);
        }

        qlever.loadRdfData(filePath, format);
    }

    private static void loadFromJson(QLeverEmbedded qlever, String jsonFile) throws IOException {
        // Parse JSON and convert to triples
        List<String> triples = parseJsonToTriples(jsonFile);

        // Load triples into QLever
        for (String triple : triples) {
            qlever.addTriple(triple);
        }
    }

    /**
     * Optimizes SPARQL queries for YAWL-specific patterns
     */
    public static String optimizeYawlQuery(String originalQuery) {
        if (originalQuery == null || originalQuery.trim().isEmpty()) {
            return originalQuery;
        }

        String optimized = originalQuery;

        // Add YAWL namespace if not present
        if (!optimized.contains("PREFIX yawl:")) {
            optimized = "PREFIX yawl: <http://www.yawlfoundation.org/yawl#>\n" + optimized;
        }

        // Add time namespace for temporal queries
        if (optimized.contains("time:") || optimized.contains("xsd:dateTime")) {
            optimized = "PREFIX time: <http://www.w3.org/2006/time#>\n" + optimized;
        }

        // Optimize common YAWL patterns
        optimized = optimizeWorkflowPattern(optimized);
        optimized = optimizeTaskPattern(optimized);
        optimized = optimizeConditionPattern(optimized);

        return optimized;
    }

    private static String optimizeWorkflowPattern(String query) {
        // Optimize workflow path queries
        if (query.contains("SELECT ?path WHERE {")) {
            query = query.replace("SELECT ?path WHERE {",
                "SELECT DISTINCT ?path WHERE {");
        }

        // Add filter for workflow states
        if (query.contains("yawl:hasWorkflow") && !query.contains("FILTER")) {
            query = query.replace("yawl:hasWorkflow ?workflow .",
                "yawl:hasWorkflow ?workflow .\n" +
                "FILTER EXISTS { ?workflow yawl:status 'ACTIVE' }");
        }

        return query;
    }

    private static String optimizeTaskPattern(String query) {
        // Optimize task status queries
        if (query.contains("yawl:hasStatus") && !query.contains("DISTINCT")) {
            query = query.replace("SELECT ?task WHERE {",
                "SELECT DISTINCT ?task WHERE {");
        }

        // Add task type filter
        if (query.contains("yawl:Task") && !query.contains("FILTER")) {
            query = query.replace("yawl:Task ?task .",
                "yawl:Task ?task .\n" +
                "FILTER(?task != <http://example.org/workflow#SystemTask>)");
        }

        return query;
    }

    private static String optimizeConditionPattern(String query) {
        // Optimize condition queries for better performance
        if (query.contains("yawl:hasCondition")) {
            query = query.replace("yawl:hasCondition ?condition .",
                "yawl:hasCondition ?condition .\n" +
                "OPTIONAL { ?condition yawl:conditionName ?name }");
        }

        return query;
    }

    /**
     * Converts SPARQL results to typed Java objects
     */
    public static <T> List<T> convertResults(SparqlResult result, Function<Map<String, String>, T> converter) {
        if (result == null || result.getResults() == null) {
            return Collections.emptyList();
        }

        return result.getResults().stream()
            .map(converter)
            .collect(Collectors.toList());
    }

    /**
     * Formats temporal queries with proper date ranges
     */
    public static String formatTemporalQuery(String template, Instant start, Instant end) {
        if (start == null || end == null) {
            return template;
        }

        return String.format(
            template,
            ISO_FORMATTER.format(start),
            ISO_FORMATTER.format(end)
        );
    }

    /**
     * Creates performance monitoring queries
     */
    public static String createPerformanceQuery(String workflowUri, Instant start, Instant end) {
        String baseQuery = "" +
            "PREFIX yawl: <http://www.yawlfoundation.org/yawl#>\n" +
            "PREFIX perf: <http://example.org/performance#>\n" +
            "SELECT ?task ?duration ?waitTime ?completionRate WHERE {\n" +
            "  ?execution perf:hasWorkflow <%s> ;\n" +
            "             perf:startTime ?startTime ;\n" +
            "             perf:endTime ?endTime .\n" +
            "  ?execution perf:hasTask ?task .\n" +
            "  ?task perf:duration ?duration ;\n" +
            "         perf:waitTime ?waitTime ;\n" +
            "         perf:completionRate ?completionRate .\n" +
            "  FILTER(?startTime >= '%s'^^xsd:dateTime &&\n" +
            "         ?endTime <= '%s'^^xsd:dateTime)\n" +
            "}";

        return formatTemporalQuery(baseQuery, start, end).formatted(workflowUri);
    }

    /**
     * Creates compliance checking queries
     */
    public static String createComplianceQuery(String regulationUri) {
        return "" +
            "PREFIX yawl: <http://www.yawlfoundation.org/yawl#>\n" +
            "PREFIX regulation: <" + regulationUri + ">\n" +
            "SELECT ?task ?violation WHERE {\n" +
            "  ?task a yawl:Task ;\n" +
            "        yawl:taskName ?name .\n" +
            "  # Check compliance requirements\n" +
            "  OPTIONAL { ?task regulation:complies ?compliance }\n" +
            "  FILTER(!BOUND(?compliance))\n" +
            "  BIND(CONCAT(?name, ' violates ', <" + regulationUri + ">") AS ?violation)\n" +
            "}";
    }

    /**
     * Creates resource allocation queries
     */
    public static String createResourceQuery(String workflowUri) {
        return "" +
            "PREFIX yawl: <http://www.yawlfoundation.org/yawl#>\n" +
            "PREFIX perf: <http://example.org/performance#>\n" +
            "SELECT ?resource ?currentLoad ?predictedLoad WHERE {\n" +
            "  ?resource perf:assignedTo <%s> ;\n" +
            "            perf:currentLoad ?currentLoad .\n" +
            "  # Simple prediction model\n" +
            "  BIND(?currentLoad * 1.1 + 100 AS ?predictedLoad)\n" +
            "  FILTER(?predictedLoad > 80)  # Alert threshold\n" +
            "}".formatted(workflowUri);
    }

    /**
     * Validates SPARQL syntax
     */
    public static boolean validateSparqlSyntax(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        // Basic validation patterns
        String trimmed = query.trim().toLowerCase();

        // Must start with SELECT, CONSTRUCT, ASK, or DESCRIBE
        if (!trimmed.startsWith("select ") &&
            !trimmed.startsWith("construct ") &&
            !trimmed.startsWith("ask ") &&
            !trimmed.startsWith("describe ")) {
            return false;
        }

        // Check for balanced braces
        int openBraces = 0;
        for (char c : query.toCharArray()) {
            if (c == '{') openBraces++;
            if (c == '}') {
                openBraces--;
                if (openBraces < 0) return false;
            }
        }

        return openBraces == 0;
    }

    /**
     * Parses JSON file to RDF triples
     */
    private static List<String> parseJsonToTriples(String jsonFile) throws IOException {
        List<String> triples = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(jsonFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Simple JSON parsing (mock implementation)
                if (line.contains("\"subject\"") && line.contains("\"predicate\"")) {
                    String[] parts = line.split("\"");
                    if (parts.length >= 6) {
                        String subject = parts[3];
                        String predicate = parts[7];
                        String object = parts[11];
                        triples.add(String.format("%s %s %s .", subject, predicate, object));
                    }
                }
            }
        }

        return triples;
    }

    /**
     * Creates a batch processor for large operations
     */
    public static class BatchProcessor<T> {
        private final List<T> items;
        private final int batchSize;

        public BatchProcessor(List<T> items, int batchSize) {
            this.items = new ArrayList<>(items);
            this.batchSize = batchSize;
        }

        public void processItems(Function<List<T>, Void> processor) {
            for (int i = 0; i < items.size(); i += batchSize) {
                int end = Math.min(i + batchSize, items.size());
                List<T> batch = items.subList(i, end);
                processor.apply(batch);
            }
        }
    }

    /**
     * Creates a caching decorator for expensive operations
     */
    public static class Cache<K, V> {
        private final Map<K, V> cache = new HashMap<>();
        private final long ttl;
        private final Map<K, Long> timestamps = new HashMap<>();

        public Cache(long ttlMillis) {
            this.ttl = ttlMillis;
        }

        public V get(K key, Function<K, V> loader) {
            V value = cache.get(key);
            Long timestamp = timestamps.get(key);

            if (value == null || timestamp == null ||
                System.currentTimeMillis() - timestamp > ttl) {
                value = loader.apply(key);
                cache.put(key, value);
                timestamps.put(key, System.currentTimeMillis());
            }

            return value;
        }

        public void clear() {
            cache.clear();
            timestamps.clear();
        }
    }

    /**
     * Utility method for measuring query performance
     */
    public static long measureQueryPerformance(QLeverEmbedded engine, String query) {
        long startTime = System.currentTimeMillis();

        try {
            engine.executeSparql(query);
            return System.currentTimeMillis() - startTime;
        } catch (Exception e) {
            return -1; // Indicate failure
        }
    }

    /**
     * Creates a comprehensive index configuration
     */
    public static String createIndexConfiguration(String... predicates) {
        StringBuilder sb = new StringBuilder("CREATE INDEX CONFIGURATION {\n");

        for (String predicate : predicates) {
            sb.append(String.format("  INDEX FOR <%s> ON PREDICATE;\n", predicate));
        }

        sb.append("}");
        return sb.toString();
    }
}