package org.yawlfoundation.yawl.rust4pm.fluent;

/**
 * Sealed hierarchy of process mining pipeline stages.
 *
 * <p>Each stage represents a discrete operation in the mining pipeline.
 * Exhaustive pattern matching via {@code switch} ensures all stages are handled.
 *
 * <p>Pipeline stages execute in declaration order:
 * <ol>
 *   <li>{@link ParseOcel2} — Parse OCEL2 JSON into native log handle</li>
 *   <li>{@link DiscoverDfg} — Discover directly-follows graph</li>
 *   <li>{@link CheckConformance} — Token-based replay conformance check</li>
 *   <li>{@link ComputeStats} — Compute performance statistics</li>
 * </ol>
 */
public sealed interface PipelineStage {

    /** Parse OCEL2 JSON into a native event log handle. */
    record ParseOcel2(String json) implements PipelineStage {
        public ParseOcel2 {
            if (json == null || json.isBlank()) {
                throw new IllegalArgumentException("OCEL2 JSON must not be null or blank");
            }
        }
    }

    /** Discover a directly-follows graph from the parsed log. */
    record DiscoverDfg() implements PipelineStage {}

    /** Check conformance of the log against a Petri net (PNML XML). */
    record CheckConformance(String pnmlXml) implements PipelineStage {
        public CheckConformance {
            if (pnmlXml == null || pnmlXml.isBlank()) {
                throw new IllegalArgumentException("PNML XML must not be null or blank");
            }
        }
    }

    /** Compute performance statistics from the parsed log. */
    record ComputeStats() implements PipelineStage {}
}
