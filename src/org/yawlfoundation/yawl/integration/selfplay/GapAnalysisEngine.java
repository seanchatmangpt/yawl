package org.yawlfoundation.yawl.integration.selfplay;

import org.yawlfoundation.yawl.integration.selfplay.model.V7Gap;
import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import org.yawlfoundation.yawl.qlever.QLeverFfiException;
import org.yawlfoundation.yawl.qlever.QLeverResult;

import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

/**
 * Gap Analysis Engine for Self-Play Simulation Loop v3.0.
 *
 * <p>Discovers and prioritizes capability gaps using WSJF (Weighted Shortest Job First)
 * methodology. Integrates with QLever for SPARQL-based gap discovery and ranking.
 *
 * <p>Core responsibilities:
 * <ul>
 *   <li>Load capability gaps from SPARQL query results</li>
 *   <li>Compute WSJF scores: (demand * frequency) / complexity</li>
 *   <li>Rank gaps by priority</li>
 *   <li>Return top N gaps for inner loop optimization</li>
 *   <li>Integrate with QLever via EmbeddedQLever for SPARQL execution</li>
 * </ul>
 *
 * @see org.yawlfoundation.yawl.integration.selfplay.model.V7Gap
 * @see queries/capability-gap-discovery.sparql
 * @see queries/wsjf-ranking.sparql
 */
@ThreadSafe
public final class GapAnalysisEngine {

    private final QLeverEmbeddedSparqlEngine qleverEngine;
    private final Map<String, CapabilityGap> discoveredGaps;
    private final Map<String, GapPriority> gapPriorities;
    private final Map<V7Gap, Double> knownGapsDemand;
    private Instant lastAnalysisTime;
    private int analysisCount;
    private String discoveryQueryTemplate;
    private String rankingQueryTemplate;

    /**
     * Represents a discovered capability gap with demand and complexity metrics.
     *
     * @param gapId Unique identifier for the gap
     * @param requiredType The capability type that is missing
     * @param demandScore How many consumers need this capability (higher = more critical)
     * @param complexity Estimated implementation complexity (higher = harder)
     * @param description Human-readable description of the gap
     */
    public record CapabilityGap(
            String gapId,
            String requiredType,
            double demandScore,
            double complexity,
            String description
    ) {}

    /**
     * Represents a prioritized gap with WSJF score and ranking.
     *
     * @param priorityId Unique priority identifier
     * @param gap The capability gap being prioritized
     * @param wsjfScore WSJF score (higher = higher priority)
     * @param rank Position in priority ranking (1 = highest)
     * @param businessValue Business value component of WSJF
     * @param timeCriticality Time criticality component
     * @param riskReduction Risk reduction component
     * @param jobSize Estimated job size (inverse of complexity)
     */
    public record GapPriority(
            String priorityId,
            CapabilityGap gap,
            double wsjfScore,
            int rank,
            double businessValue,
            double timeCriticality,
            double riskReduction,
            double jobSize
    ) {

        /**
         * Returns a copy of this GapPriority with the specified rank.
         *
         * @param newRank The new rank to set
         * @return A new GapPriority with updated rank
         */
        public GapPriority withRank(int newRank) {
            return new GapPriority(
                this.priorityId,
                this.gap,
                this.wsjfScore,
                newRank,
                this.businessValue,
                this.timeCriticality,
                this.riskReduction,
                this.jobSize
            );
        }
    }

    /**
     * Creates a new GapAnalysisEngine with default QLever integration.
     */
    public GapAnalysisEngine() {
        this.qleverEngine = new QLeverEmbeddedSparqlEngine();
        this.discoveredGaps = new ConcurrentHashMap<>();
        this.gapPriorities = new ConcurrentHashMap<>();
        this.knownGapsDemand = initKnownGapsDemand();
        this.analysisCount = 0;
    }

    /**
     * Initializes demand scores for known V7Gap types.
     */
    private Map<V7Gap, Double> initKnownGapsDemand() {
        return Map.of(
            V7Gap.ASYNC_A2A_GOSSIP, 8.0,
            V7Gap.MCP_SERVERS_SLACK_GITHUB_OBS, 9.0,
            V7Gap.DETERMINISTIC_REPLAY_BLAKE3, 7.0,
            V7Gap.THREADLOCAL_YENGINE_PARALLELIZATION, 6.0,
            V7Gap.SHACL_COMPLIANCE_SHAPES, 8.0,
            V7Gap.BYZANTINE_CONSENSUS, 5.0,
            V7Gap.BURIED_ENGINES_MCP_A2A_WIRING, 10.0
        );
    }

    /**
     * Initializes the engine and loads necessary SPARQL queries.
     *
     * @throws QLeverFfiException if initialization fails
     */
    public synchronized void initialize() throws QLeverFfiException {
        qleverEngine.initialize();
        loadSparqlQueries();
    }

    /**
     * Loads and registers SPARQL queries for gap discovery and ranking.
     *
     * @throws QLeverFfiException if query loading fails
     */
    private void loadSparqlQueries() throws QLeverFfiException {
        discoveryQueryTemplate = loadQueryFile("queries/capability-gap-discovery.sparql");
        rankingQueryTemplate   = loadQueryFile("queries/wsjf-ranking.sparql");
    }

    /**
     * Loads a SPARQL query file from the filesystem.
     *
     * @param queryPath Path to the query file
     * @return Query string
     * @throws QLeverFfiException if file cannot be read
     */
    private String loadQueryFile(String queryPath) throws QLeverFfiException {
        try {
            Path path = Paths.get(queryPath);
            if (!Files.exists(path)) {
                throw new QLeverFfiException("Query file not found: " + queryPath);
            }
            return Files.readString(path);
        } catch (IOException e) {
            throw new QLeverFfiException("Failed to load query file: " + queryPath, e);
        }
    }

    /**
     * Discovers capability gaps using SPARQL queries against QLever.
     *
     * @return List of discovered capability gaps
     * @throws QLeverFfiException if SPARQL execution fails
     */
    public synchronized List<CapabilityGap> discoverGaps() throws QLeverFfiException {
        analysisCount++;
        lastAnalysisTime = Instant.now();

        // Clear previous discoveries
        discoveredGaps.clear();
        gapPriorities.clear();

        // Execute gap discovery query
        String discoveryQuery = buildDiscoveryQuery();
        QLeverResult result = qleverEngine.executeQuery(discoveryQuery);

        // Parse results and create capability gaps
        List<CapabilityGap> gaps = parseDiscoveryResults(result);

        // Store discovered gaps
        for (CapabilityGap gap : gaps) {
            discoveredGaps.put(gap.gapId(), gap);
        }

        return gaps;
    }

    /**
     * Builds the SPARQL query for capability gap discovery.
     *
     * @return SPARQL query string
     */
    private String buildDiscoveryQuery() {
        if (discoveryQueryTemplate != null && !discoveryQueryTemplate.isBlank()) {
            return discoveryQueryTemplate;
        }
        // Fallback SELECT when query file was not loaded (engine not yet initialized)
        return """
            PREFIX yawl-bridge: <http://yawlfoundation.org/yawl/bridge#>

            SELECT DISTINCT ?requiredType (COUNT(?consumerCap) AS ?demand) WHERE {
              ?consumerCap a yawl-bridge:BridgeCapability ;
                           yawl-bridge:nativeTarget ?consumerTarget .
              ?consumerFn  a yawl-bridge:NativeFunction ;
                           yawl-bridge:forCapability ?consumerCap ;
                           yawl-bridge:fnDescriptor ?consumerDesc .
              BIND(
                IF(CONTAINS(?consumerDesc, "ADDRESS"),   "MemorySegment",
                IF(CONTAINS(?consumerDesc, "JAVA_LONG"), "long",
                "unknown")) AS ?requiredType
              )
              FILTER(?requiredType != "unknown")
              FILTER NOT EXISTS {
                ?producerFn a yawl-bridge:NativeFunction ;
                            yawl-bridge:fnReturnType ?returnType .
                FILTER(CONTAINS(?returnType, ?requiredType))
              }
            }
            GROUP BY ?requiredType
            ORDER BY DESC(?demand)
            """;
    }

    /**
     * Parses SPARQL query results into CapabilityGap objects.
     *
     * @param result QLever query result
     * @return List of capability gaps
     */
    private List<CapabilityGap> parseDiscoveryResults(QLeverResult result) {
        if (!result.isSuccess() || result.data() == null || result.data().isBlank()) {
            return List.of();
        }
        // Parse SPARQL SELECT JSON: {"results":{"bindings":[{"requiredType":{"value":"..."},...}]}}
        List<CapabilityGap> gaps = new ArrayList<>();
        java.util.regex.Matcher typeMatcher = java.util.regex.Pattern
            .compile("\"requiredType\"\\s*:\\s*\\{[^}]*\"value\"\\s*:\\s*\"([^\"]+)\"")
            .matcher(result.data());
        while (typeMatcher.find()) {
            String requiredType = typeMatcher.group(1);
            double demand = extractDoubleJsonValue(result.data(), "demand", 1.0);
            double complexity = estimateComplexity(requiredType);
            gaps.add(new CapabilityGap(
                "gap_" + requiredType.toLowerCase().replace(" ", "_").replace(":", "_"),
                requiredType,
                demand,
                complexity,
                "Missing capability to produce " + requiredType + " type for native bridge operations"
            ));
        }
        return gaps;
    }

    private static double extractDoubleJsonValue(String json, String field, double defaultVal) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\"" + field + "\"\\s*:\\s*\\{[^}]*\"value\"\\s*:\\s*\"([0-9.E+\\-]+)\"")
            .matcher(json);
        if (m.find()) {
            try { return Double.parseDouble(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    private static double estimateComplexity(String type) {
        return switch (type.toLowerCase()) {
            case "memorysegment" -> 3.0;
            case "long"          -> 1.0;
            case "ocel"          -> 4.0;
            case "dfg"           -> 5.0;
            default              -> 2.0;
        };
    }

    /**
     * Computes WSJF scores and prioritizes gaps.
     *
     * @param gaps List of capability gaps to prioritize
     * @return List of prioritized gaps sorted by WSJF score (highest first)
     */
    public synchronized List<GapPriority> prioritizeGaps(List<CapabilityGap> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return List.of();
        }

        Map<String, Double> wsjfScores = new HashMap<>();
        List<GapPriority> priorities = new ArrayList<>();

        // Calculate WSJF scores for each gap
        for (CapabilityGap gap : gaps) {
            double wsjf = calculateWSJF(gap);
            wsjfScores.put(gap.gapId(), wsjf);

            GapPriority priority = createGapPriority(gap, wsjf);
            priorities.add(priority);
        }

        // Sort by WSJF score descending
        priorities.sort(Comparator.comparingDouble(GapPriority::wsjfScore).reversed());

        // Assign ranks
        for (int i = 0; i < priorities.size(); i++) {
            GapPriority priority = priorities.get(i);
            priorities.set(i, priority.withRank(i + 1));
        }

        // Store priorities
        for (GapPriority priority : priorities) {
            gapPriorities.put(priority.priorityId(), priority);
        }

        return priorities;
    }

    /**
     * Calculates WSJF (Weighted Shortest Job First) score.
     * WSJF = (Business Value + Time Criticality + Risk Reduction) / Job Size
     *
     * @param gap The capability gap to score
     * @return WSJF score (higher = higher priority)
     */
    private double calculateWSJF(CapabilityGap gap) {
        // Business Value: based on demand score
        double businessValue = gap.demandScore() * 2;

        // Time Criticality: based on gap type
        double timeCriticality = switch (gap.requiredType().toLowerCase()) {
            case "memorysegment" -> 3.0;
            case "long" -> 2.0;
            case "ocel" -> 2.0;
            case "dfg" -> 1.5;
            default -> 1.0;
        };

        // Risk Reduction: gaps in core infrastructure reduce more risk
        double riskReduction = switch (gap.requiredType().toLowerCase()) {
            case "memorysegment", "ocel" -> 3.0;
            case "long", "dfg" -> 2.0;
            default -> 1.0;
        };

        // Job Size: inverse of complexity (higher complexity = larger job)
        double jobSize = gap.complexity() * 2;

        // WSJF Formula: (BV + TC + RR) / Size
        // Avoid division by zero
        if (jobSize <= 0) {
            return 0.0;
        }

        return (businessValue + timeCriticality + riskReduction) / jobSize;
    }

    /**
     * Creates a GapPriority from a CapabilityGap and WSJF score.
     */
    private GapPriority createGapPriority(CapabilityGap gap, double wsjfScore) {
        // Calculate components
        double businessValue = gap.demandScore() * 2;
        double timeCriticality = switch (gap.requiredType().toLowerCase()) {
            case "memorysegment" -> 3.0;
            case "long" -> 2.0;
            case "ocel" -> 2.0;
            case "dfg" -> 1.5;
            default -> 1.0;
        };
        double riskReduction = switch (gap.requiredType().toLowerCase()) {
            case "memorysegment", "ocel" -> 3.0;
            case "long", "dfg" -> 2.0;
            default -> 1.0;
        };
        double jobSize = gap.complexity() * 2;

        return new GapPriority(
            "priority_" + gap.gapId(),
            gap,
            wsjfScore,
            0, // Will be set after sorting
            businessValue,
            timeCriticality,
            riskReduction,
            jobSize
        );
    }

    /**
     * Returns top N gaps for inner loop optimization.
     *
     * @param n Number of top gaps to return
     * @return List of top N prioritized gaps
     * @throws IllegalArgumentException if n <= 0
     */
    public synchronized List<GapPriority> getTopGaps(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("Number of gaps must be positive");
        }

        if (gapPriorities.isEmpty()) {
            throw new UnsupportedOperationException(
                "No gaps have been prioritized yet. Call discoverGaps() and prioritizeGaps() first."
            );
        }

        return gapPriorities.values().stream()
            .sorted(Comparator.comparingDouble(GapPriority::wsjfScore).reversed())
            .limit(n)
            .collect(Collectors.toList());
    }

    /**
     * Returns all discovered gaps.
     *
     * @return Unmodifiable list of all discovered gaps
     */
    public synchronized List<CapabilityGap> getAllDiscoveredGaps() {
        return List.copyOf(discoveredGaps.values());
    }

    /**
     * Returns all prioritized gaps.
     *
     * @return Unmodifiable list of all prioritized gaps
     */
    public synchronized List<GapPriority> getAllPrioritizedGaps() {
        return List.copyOf(gapPriorities.values());
    }

    /**
     * Gets known V7 gaps with demand scores.
     *
     * @return Map of V7 gaps to demand scores
     */
    public Map<V7Gap, Double> getKnownGapsDemand() {
        return Map.copyOf(knownGapsDemand);
    }

    /**
     * Updates demand score for a known gap.
     *
     * @param gap The V7 gap to update
     * @param newDemandScore New demand score
     * @throws IllegalArgumentException if gap is null or score is negative
     */
    public synchronized void updateGapDemand(V7Gap gap, double newDemandScore) {
        if (gap == null) {
            throw new IllegalArgumentException("Gap cannot be null");
        }
        if (newDemandScore < 0) {
            throw new IllegalArgumentException("Demand score cannot be negative");
        }

        knownGapsDemand.put(gap, newDemandScore);
    }

    /**
     * Gets analysis statistics.
     *
     * @return Analysis statistics map
     */
    public synchronized Map<String, Object> getAnalysisStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("analysisCount", analysisCount);
        stats.put("lastAnalysisTime", lastAnalysisTime);
        stats.put("discoveredGapsCount", discoveredGaps.size());
        stats.put("prioritizedGapsCount", gapPriorities.size());
        return Map.copyOf(stats);
    }

    /**
     * Shuts down the engine and cleans up resources.
     *
     * @throws QLeverFfiException if shutdown fails
     */
    public synchronized void shutdown() throws QLeverFfiException {
        qleverEngine.shutdown();
        discoveredGaps.clear();
        gapPriorities.clear();
    }

    /**
     * Executes the full gap analysis workflow:
     * 1. Discover gaps
     * 2. Prioritize gaps
     * 3. Return top N gaps
     *
     * @param n Number of top gaps to return
     * @return List of top N prioritized gaps
     * @throws QLeverFfiException if analysis fails
     * @throws UnsupportedOperationException if n <= 0 or engine not initialized
     */
    public synchronized List<GapPriority> analyzeAndPrioritize(int n) throws QLeverFfiException {
        if (n <= 0) {
            throw new UnsupportedOperationException(
                "Number of gaps must be positive. Use getTopGaps(n) with n > 0."
            );
        }

        // Step 1: Discover gaps
        List<CapabilityGap> gaps = discoverGaps();
        if (gaps.isEmpty()) {
            throw new UnsupportedOperationException(
                "No gaps discovered. Check data sources and SPARQL queries."
            );
        }

        // Step 2: Prioritize gaps
        List<GapPriority> priorities = prioritizeGaps(gaps);
        if (priorities.isEmpty()) {
            throw new UnsupportedOperationException(
                "No gaps could be prioritized. Check WSJF calculation logic."
            );
        }

        // Step 3: Return top N gaps
        return getTopGaps(n);
    }

    /**
     * Persists capability gaps to QLever as sim:CapabilityGap triples.
     *
     * <p>After discovering gaps, this method writes them to the QLever knowledge graph
     * for audit trail and cross-session persistence. Gaps are stored with:
     * <ul>
     *   <li>sim:gapId - unique identifier</li>
     *   <li>sim:requiresCapability - the missing capability type</li>
     *   <li>sim:wsjfScore - WSJF priority score</li>
     *   <li>sim:demandScore - demand metric</li>
     *   <li>sim:complexity - implementation complexity</li>
     *   <li>sim:discoveredAt - timestamp</li>
     * </ul>
     *
     * @param gaps List of capability gaps to persist
     * @return number of gaps successfully persisted
     * @throws QLeverFfiException if SPARQL INSERT fails
     */
    public synchronized int persistGaps(List<CapabilityGap> gaps) throws QLeverFfiException {
        if (gaps == null || gaps.isEmpty()) {
            return 0;
        }

        int persisted = 0;
        Instant now = Instant.now();

        for (CapabilityGap gap : gaps) {
            // Calculate WSJF score for this gap
            double wsjfScore = calculateWSJF(gap);

            // Build SPARQL INSERT query
            String gapUri = "http://yawlfoundation.org/yawl/simulation/gap/" + gap.gapId();
            String insertQuery = String.format("""
                PREFIX sim:   <http://yawlfoundation.org/yawl/simulation#>
                PREFIX rdfs:  <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX xsd:   <http://www.w3.org/2001/XMLSchema#>

                INSERT DATA {
                  <%s> a sim:CapabilityGap ;
                    sim:gapId "%s" ;
                    sim:requiresCapability "%s" ;
                    sim:wsjfScore "%.4f"^^xsd:decimal ;
                    sim:demandScore "%.4f"^^xsd:decimal ;
                    sim:complexity "%.4f"^^xsd:decimal ;
                    rdfs:comment "%s" ;
                    sim:discoveredAt "%s"^^xsd:dateTime .
                }
                """,
                gapUri,
                escapeSparql(gap.gapId()),
                escapeSparql(gap.requiredType()),
                wsjfScore,
                gap.demandScore(),
                gap.complexity(),
                escapeSparql(gap.description()),
                now.toString()
            );

            try {
                qleverEngine.executeUpdate(insertQuery);
                persisted++;
            } catch (QLeverFfiException e) {
                // Log error but continue with other gaps
                System.err.println("Failed to persist gap " + gap.gapId() + ": " + e.getMessage());
            }
        }

        return persisted;
    }

    /**
     * Persists prioritized gaps with full WSJF breakdown.
     *
     * <p>Stores additional WSJF components for audit trail:
     * business value, time criticality, risk reduction, job size.
     *
     * @param priorities List of prioritized gaps to persist
     * @return number of gaps successfully persisted
     * @throws QLeverFfiException if SPARQL INSERT fails
     */
    public synchronized int persistPrioritizedGaps(List<GapPriority> priorities) throws QLeverFfiException {
        if (priorities == null || priorities.isEmpty()) {
            return 0;
        }

        int persisted = 0;
        Instant now = Instant.now();

        for (GapPriority priority : priorities) {
            CapabilityGap gap = priority.gap();
            String gapUri = "http://yawlfoundation.org/yawl/simulation/gap/" + gap.gapId();
            String priorityUri = "http://yawlfoundation.org/yawl/simulation/priority/" + priority.priorityId();

            String insertQuery = String.format("""
                PREFIX sim:   <http://yawlfoundation.org/yawl/simulation#>
                PREFIX rdfs:  <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX xsd:   <http://www.w3.org/2001/XMLSchema#>

                INSERT DATA {
                  <%s> a sim:CapabilityGap ;
                    sim:gapId "%s" ;
                    sim:requiresCapability "%s" ;
                    sim:wsjfScore "%.4f"^^xsd:decimal ;
                    sim:demandScore "%.4f"^^xsd:decimal ;
                    sim:complexity "%.4f"^^xsd:decimal ;
                    rdfs:comment "%s" ;
                    sim:discoveredAt "%s"^^xsd:dateTime .

                  <%s> a sim:GapPriority ;
                    sim:forGap <%s> ;
                    sim:rank %d ;
                    sim:businessValue "%.4f"^^xsd:decimal ;
                    sim:timeCriticality "%.4f"^^xsd:decimal ;
                    sim:riskReduction "%.4f"^^xsd:decimal ;
                    sim:jobSize "%.4f"^^xsd:decimal ;
                    sim:priorityScore "%.4f"^^xsd:decimal .
                }
                """,
                gapUri,
                escapeSparql(gap.gapId()),
                escapeSparql(gap.requiredType()),
                priority.wsjfScore(),
                gap.demandScore(),
                gap.complexity(),
                escapeSparql(gap.description()),
                now.toString(),
                priorityUri,
                gapUri,
                priority.rank(),
                priority.businessValue(),
                priority.timeCriticality(),
                priority.riskReduction(),
                priority.jobSize(),
                priority.wsjfScore()
            );

            try {
                qleverEngine.executeUpdate(insertQuery);
                persisted++;
            } catch (QLeverFfiException e) {
                System.err.println("Failed to persist priority " + priority.priorityId() + ": " + e.getMessage());
            }
        }

        return persisted;
    }

    /**
     * Queries persisted gaps from QLever.
     *
     * @return List of gap IDs currently stored in QLever
     * @throws QLeverFfiException if SPARQL SELECT fails
     */
    public synchronized List<String> queryPersistedGaps() throws QLeverFfiException {
        String selectQuery = """
            PREFIX sim: <http://yawlfoundation.org/yawl/simulation#>

            SELECT ?gapId ?wsjfScore WHERE {
              ?gap a sim:CapabilityGap ;
                  sim:gapId ?gapId ;
                  sim:wsjfScore ?wsjfScore .
            }
            ORDER BY DESC(?wsjfScore)
            """;

        QLeverResult result = qleverEngine.executeQuery(selectQuery);
        if (!result.isSuccess() || result.data() == null || result.data().isBlank()) {
            return List.of();
        }
        List<String> gapIds = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\"gapId\"\\s*:\\s*\\{[^}]*\"value\"\\s*:\\s*\"([^\"]+)\"")
            .matcher(result.data());
        while (m.find()) {
            gapIds.add(m.group(1));
        }
        return Collections.unmodifiableList(gapIds);
    }

    /**
     * Counts persisted capability gaps in QLever.
     *
     * @return count of CapabilityGap triples
     * @throws QLeverFfiException if SPARQL query fails
     */
    public synchronized int countPersistedGaps() throws QLeverFfiException {
        String countQuery = """
            PREFIX sim: <http://yawlfoundation.org/yawl/simulation#>
            SELECT (COUNT(?gap) AS ?count) WHERE {
              ?gap a sim:CapabilityGap .
            }
            """;

        QLeverResult result = qleverEngine.executeQuery(countQuery);
        if (!result.isSuccess() || result.data() == null || result.data().isBlank()) {
            return 0;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\"count\"\\s*:\\s*\\{[^}]*\"value\"\\s*:\\s*\"([0-9]+)\"")
            .matcher(result.data());
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    /**
     * Escapes special characters for SPARQL string literals.
     *
     * @param value the string to escape (must not be null)
     * @return the escaped string
     * @throws IllegalArgumentException if value is null (null values indicate missing data)
     */
    private String escapeSparql(String value) {
        if (value == null) {
            throw new IllegalArgumentException(
                "SPARQL string value cannot be null. " +
                "All gap properties (gapId, requiredType, description) must have values. " +
                "Check that CapabilityGap records are fully populated before persistence."
            );
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    /**
     * Convenience method: discover, prioritize, persist, and return top N gaps.
     *
     * <p>This is the full workflow method that:
     * <ol>
     *   <li>Discovers gaps from QLever</li>
     *   <li>Prioritizes by WSJF</li>
     *   <li>Persists results to QLever</li>
     *   <li>Returns top N for implementation</li>
     * </ol>
     *
     * @param n Number of top gaps to return
     * @return List of top N prioritized gaps
     * @throws QLeverFfiException if any step fails
     */
    public synchronized List<GapPriority> analyzePrioritizeAndPersist(int n) throws QLeverFfiException {
        // Step 1: Discover gaps
        List<CapabilityGap> gaps = discoverGaps();

        if (gaps.isEmpty()) {
            return List.of();
        }

        // Step 2: Prioritize gaps
        List<GapPriority> priorities = prioritizeGaps(gaps);

        // Step 3: Persist to QLever
        persistPrioritizedGaps(priorities);

        // Step 4: Return top N
        return getTopGaps(n);
    }

    /**
     * Validates that required resources are available.
     *
     * @return true if engine is ready for analysis
     */
    public synchronized boolean isReady() {
        return qleverEngine.isInitialized() && !discoveredGaps.isEmpty();
    }

    /**
     * Gets the underlying QLever engine for advanced operations.
     *
     * @return QLeverEmbeddedSparqlEngine instance
     */
    public QLeverEmbeddedSparqlEngine getQleverEngine() {
        return qleverEngine;
    }
}