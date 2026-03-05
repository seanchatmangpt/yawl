/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.mcp.genome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yawlfoundation.yawl.engine.interfce.SpecificationData;

/**
 * Analyzes structural DNA of YAWL workflow specifications.
 * Computes genome fingerprints (task count, split/join counts, loops, depth, etc.),
 * similarity matrices via cosine distance, and clusters structurally similar workflows.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class WorkflowGenomeAnalyzer {

    private WorkflowGenomeAnalyzer() {
        throw new UnsupportedOperationException(
            "WorkflowGenomeAnalyzer is a static utility class and cannot be instantiated.");
    }

    /**
     * Immutable record representing the structural genome of a workflow specification.
     */
    public record WorkflowGenome(
        String specIdentifier,
        String specVersion,
        int taskCount,
        int andSplitCount,
        int xorSplitCount,
        int andJoinCount,
        int xorJoinCount,
        int loopCount,
        int maxDepth,
        int cancellationRegionCount,
        int inputParamCount,
        int[] genomeVector,
        String fingerprint
    ) {}

    /**
     * Cluster representation for similar workflows.
     */
    public record GenomeCluster(
        int clusterId,
        List<WorkflowGenome> members,
        double avgSimilarity
    ) {}

    /**
     * Generate comprehensive genome analysis report for all specifications.
     *
     * @param specs list of loaded workflow specifications
     * @param similarityThreshold clustering threshold (0-100, typically 70)
     * @return formatted ASCII report with genome profiles, similarity matrix, and clusters
     */
    public static String generateReport(List<SpecificationData> specs, int similarityThreshold) {
        if (specs == null || specs.isEmpty()) {
            return "No workflow specifications loaded.";
        }

        // Step 1: Analyze all genomes
        List<WorkflowGenome> genomes = analyzeAll(specs);

        // Step 2: Compute pairwise similarities
        double[][] similarityMatrix = computeSimilarityMatrix(genomes);

        // Step 3: Cluster by similarity
        List<GenomeCluster> clusters = clusterBySimilarity(genomes, similarityMatrix,
                similarityThreshold);

        // Step 4: Render ASCII report
        return renderReport(genomes, similarityMatrix, clusters, similarityThreshold);
    }

    /**
     * Analyze genome for each specification.
     *
     * @param specs list of specifications
     * @return list of genome records (one per spec)
     */
    private static List<WorkflowGenome> analyzeAll(List<SpecificationData> specs) {
        List<WorkflowGenome> genomes = new ArrayList<>();
        for (SpecificationData spec : specs) {
            if (spec != null) {
                genomes.add(computeGenome(spec));
            }
        }
        return genomes;
    }

    /**
     * Compute genome for a single specification by analyzing its XML representation.
     *
     * @param spec the specification to analyze
     * @return the computed genome record
     */
    private static WorkflowGenome computeGenome(SpecificationData spec) {
        String xml = spec.getAsXML();
        if (xml == null) {
            xml = "";
        }

        // Extract all structural metrics from XML
        int taskCount = countOccurrences(xml, "<task ");
        int andSplitCount = countOccurrences(xml, "type=\"and_split\"") +
                countOccurrences(xml, "split type=\"and\"");
        int xorSplitCount = countOccurrences(xml, "type=\"xor_split\"") +
                countOccurrences(xml, "split type=\"xor\"");
        int andJoinCount = countOccurrences(xml, "type=\"and_join\"") +
                countOccurrences(xml, "join type=\"and\"");
        int xorJoinCount = countOccurrences(xml, "type=\"xor_join\"") +
                countOccurrences(xml, "join type=\"xor\"");
        int loopCount = countOccurrences(xml, "type=\"loop\"");
        int maxDepth = computeMaxDepth(xml);
        int cancellationRegionCount = countOccurrences(xml, "<cancellationRegion");
        int inputParamCount = spec.getInputParams() != null ? spec.getInputParams().size() : 0;

        // Build genome vector
        int[] genomeVector = new int[]{
            taskCount, andSplitCount, xorSplitCount, andJoinCount, xorJoinCount,
            loopCount, maxDepth, cancellationRegionCount, inputParamCount
        };

        // Compute fingerprint (first 5 chars of hex hash)
        String fingerprint = computeFingerprint(genomeVector);

        return new WorkflowGenome(
            spec.getSpecIdentifier(),
            spec.getSpecVersion(),
            taskCount,
            andSplitCount,
            xorSplitCount,
            andJoinCount,
            xorJoinCount,
            loopCount,
            maxDepth,
            cancellationRegionCount,
            inputParamCount,
            genomeVector,
            fingerprint
        );
    }

    /**
     * Count non-overlapping occurrences of a substring in text.
     *
     * @param text the text to search
     * @param pattern the substring to find
     * @return count of occurrences
     */
    private static int countOccurrences(String text, String pattern) {
        if (text == null || pattern == null || pattern.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    /**
     * Estimate maximum nesting depth of the workflow by counting nested XML elements.
     *
     * @param xml the workflow XML
     * @return estimated max depth
     */
    private static int computeMaxDepth(String xml) {
        if (xml == null || xml.isEmpty()) {
            return 0;
        }

        // Count composite/net elements as depth indicators
        int compositeCount = countOccurrences(xml, "<compositeSplit");
        if (compositeCount > 0) {
            return Math.min(4, 2 + (compositeCount / 2)); // Heuristic: nested composites
        }

        // Count flow depth by looking for nested flowsInto elements
        int flowCount = countOccurrences(xml, "<flowsInto>");
        return Math.min(5, Math.max(2, flowCount / 3));
    }

    /**
     * Compute 5-character hex fingerprint from genome vector.
     *
     * @param genomeVector the structural genome vector
     * @return 5-char hex string fingerprint
     */
    private static String computeFingerprint(int[] genomeVector) {
        long hash = 0;
        for (int i = 0; i < genomeVector.length; i++) {
            hash = hash * 31 + genomeVector[i];
        }
        String hex = Long.toHexString(Math.abs(hash));
        return hex.length() >= 5 ? hex.substring(0, 5).toUpperCase() :
               String.format("%05X", Math.abs(hash) % 0x100000);
    }

    /**
     * Compute pairwise cosine similarity matrix.
     *
     * @param genomes list of workflow genomes
     * @return N×N similarity matrix (0-1 scale, then scaled 0-100)
     */
    private static double[][] computeSimilarityMatrix(List<WorkflowGenome> genomes) {
        int n = genomes.size();
        double[][] matrix = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                double similarity = computeSimilarity(genomes.get(i), genomes.get(j));
                matrix[i][j] = similarity;
                matrix[j][i] = similarity;
            }
        }

        return matrix;
    }

    /**
     * Compute cosine similarity between two genome vectors.
     *
     * @param g1 first genome
     * @param g2 second genome
     * @return similarity in range [0, 1], scaled as 0-100 percentage in display
     */
    private static double computeSimilarity(WorkflowGenome g1, WorkflowGenome g2) {
        int[] v1 = g1.genomeVector();
        int[] v2 = g2.genomeVector();

        double dotProduct = 0;
        double mag1 = 0;
        double mag2 = 0;

        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            mag1 += v1[i] * v1[i];
            mag2 += v2[i] * v2[i];
        }

        if (mag1 == 0 || mag2 == 0) {
            return g1 == g2 ? 1.0 : 0.0;
        }

        return dotProduct / (Math.sqrt(mag1) * Math.sqrt(mag2));
    }

    /**
     * Cluster genomes by similarity threshold using greedy clustering.
     *
     * @param genomes list of genomes
     * @param similarityMatrix precomputed similarity matrix
     * @param thresholdPercent clustering threshold as percentage (e.g., 70 for 70%)
     * @return list of clusters
     */
    private static List<GenomeCluster> clusterBySimilarity(
            List<WorkflowGenome> genomes,
            double[][] similarityMatrix,
            int thresholdPercent) {

        double threshold = thresholdPercent / 100.0;
        List<GenomeCluster> clusters = new ArrayList<>();
        boolean[] clustered = new boolean[genomes.size()];

        for (int i = 0; i < genomes.size(); i++) {
            if (clustered[i]) {
                continue;
            }

            List<WorkflowGenome> clusterMembers = new ArrayList<>();
            clusterMembers.add(genomes.get(i));
            clustered[i] = true;

            // Find all genomes similar to this one
            for (int j = i + 1; j < genomes.size(); j++) {
                if (!clustered[j] && similarityMatrix[i][j] >= threshold) {
                    clusterMembers.add(genomes.get(j));
                    clustered[j] = true;
                }
            }

            // Compute average similarity within cluster
            double avgSim = 1.0;
            if (clusterMembers.size() > 1) {
                double sum = 0;
                int count = 0;
                for (int a = 0; a < clusterMembers.size(); a++) {
                    for (int b = a + 1; b < clusterMembers.size(); b++) {
                        sum += computeSimilarity(clusterMembers.get(a), clusterMembers.get(b));
                        count++;
                    }
                }
                avgSim = count > 0 ? sum / count : 1.0;
            }

            clusters.add(new GenomeCluster(clusters.size() + 1, clusterMembers, avgSim));
        }

        return clusters;
    }

    /**
     * Render formatted ASCII report of genome analysis.
     *
     * @param genomes list of analyzed genomes
     * @param similarityMatrix pairwise similarity matrix
     * @param clusters identified clusters
     * @param thresholdPercent the clustering threshold used
     * @return formatted ASCII report
     */
    private static String renderReport(
            List<WorkflowGenome> genomes,
            double[][] similarityMatrix,
            List<GenomeCluster> clusters,
            int thresholdPercent) {

        StringBuilder sb = new StringBuilder();
        sb.append("\n╔═══════════════════════════════════════════════════════╗\n");
        sb.append("║          WORKFLOW GENOME FINGERPRINTING               ║\n");
        sb.append(String.format("║  Structural DNA Analysis for %d Specification%s         ║\n",
                genomes.size(), genomes.size() == 1 ? "" : "s"));
        sb.append("╚═══════════════════════════════════════════════════════╝\n\n");

        // Section 1: Genome Profiles
        sb.append("GENOME PROFILES\n");
        sb.append("───────────────\n");
        for (WorkflowGenome g : genomes) {
            String specLabel = g.specIdentifier() + " v" + g.specVersion();
            sb.append(String.format("%-30s\n", specLabel));
            sb.append(String.format("  Genome [t:%d as:%d xs:%d aj:%d xj:%d l:%d d:%d cr:%d p:%d]\n",
                    g.taskCount(), g.andSplitCount(), g.xorSplitCount(),
                    g.andJoinCount(), g.xorJoinCount(), g.loopCount(),
                    g.maxDepth(), g.cancellationRegionCount(), g.inputParamCount()));
            sb.append(String.format("  Fingerprint: %s\n", g.fingerprint()));
            sb.append("\n");
        }

        // Section 2: Similarity Matrix
        if (genomes.size() > 1) {
            sb.append("SIMILARITY MATRIX (cosine similarity)\n");
            sb.append("──────────────────────────────────────\n");

            // Header row with spec names (truncated to fit)
            int colWidth = Math.max(12, 80 / genomes.size());
            sb.append(String.format("%-" + colWidth + "s", ""));
            for (WorkflowGenome g : genomes) {
                String label = truncate(g.specIdentifier(), colWidth - 2);
                sb.append(String.format("%" + colWidth + "s", label));
            }
            sb.append("\n");

            // Data rows
            for (int i = 0; i < genomes.size(); i++) {
                String rowLabel = truncate(genomes.get(i).specIdentifier(), colWidth - 2);
                sb.append(String.format("%-" + colWidth + "s", rowLabel));
                for (int j = 0; j < genomes.size(); j++) {
                    int percentage = (int) Math.round(similarityMatrix[i][j] * 100);
                    sb.append(String.format("%" + (colWidth - 1) + "d%%", percentage));
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // Section 3: Genome Clusters
        if (!clusters.isEmpty()) {
            sb.append(String.format("GENOME CLUSTERS (>%d%% threshold)\n", thresholdPercent));
            sb.append("─────────────────────────────────────\n");

            for (GenomeCluster cluster : clusters) {
                if (cluster.members().size() == 1) {
                    WorkflowGenome g = cluster.members().get(0);
                    sb.append(String.format("[Singleton] %s — no close structural matches\n",
                            g.specIdentifier()));
                    sb.append("  → Lean workflow: good candidate for reference implementation\n\n");
                } else {
                    sb.append(String.format("[Cluster %d] ", cluster.clusterId()));
                    for (int i = 0; i < cluster.members().size(); i++) {
                        sb.append(cluster.members().get(i).specIdentifier());
                        if (i < cluster.members().size() - 1) {
                            sb.append(" + ");
                        }
                    }
                    sb.append(String.format(" (%.0f%% similar)\n", cluster.avgSimilarity() * 100));

                    // Generate insights
                    List<String> insights = generateClusterInsights(cluster, genomes);
                    for (String insight : insights) {
                        sb.append("  → ").append(insight).append("\n");
                    }
                    sb.append("\n");
                }
            }
        }

        // Legend
        sb.append("Legend: t=tasks as=AND-splits xs=XOR-splits aj=AND-joins xj=XOR-joins ");
        sb.append("l=loops d=depth cr=cancel-regions p=params\n");

        return sb.toString();
    }

    /**
     * Generate human-readable insights about a cluster.
     *
     * @param cluster the genome cluster
     * @param allGenomes all genomes for reference
     * @return list of insight strings
     */
    private static List<String> generateClusterInsights(
            GenomeCluster cluster,
            List<WorkflowGenome> allGenomes) {

        List<String> insights = new ArrayList<>();

        if (cluster.members().size() < 2) {
            return insights;
        }

        WorkflowGenome first = cluster.members().get(0);

        // Check structural patterns
        if (first.andSplitCount() > 2) {
            insights.add("Structural twins: both have multiple AND-splits (complex parallelism)");
        }
        if (first.xorSplitCount() > 1) {
            insights.add("Shared decision logic: both feature XOR-split paths");
        }
        if (first.maxDepth() > 3) {
            insights.add("Deep nesting: complex hierarchical structure");
        }
        if (first.loopCount() > 0) {
            insights.add("Contains feedback loops: potential for process iteration");
        }
        if (first.cancellationRegionCount() > 0) {
            insights.add("Uses cancellation regions: advanced error handling");
        }
        if (first.taskCount() > 10) {
            insights.add("Large workflow: " + first.taskCount() + " tasks suggests complex orchestration");
        }

        // Recommendation
        insights.add("Recommendation: Consider consolidating or sharing sub-process components");

        return insights;
    }

    /**
     * Truncate string to fit column width.
     *
     * @param str the string to truncate
     * @param maxLen maximum length
     * @return truncated string
     */
    private static String truncate(String str, int maxLen) {
        if (str == null || str.length() <= maxLen) {
            return str;
        }
        return str.substring(0, Math.max(1, maxLen - 1)) + "…";
    }
}
