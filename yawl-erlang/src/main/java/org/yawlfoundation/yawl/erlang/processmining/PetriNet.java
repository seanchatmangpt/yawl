/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * Java API that mirrors the Rust process_mining library API exactly.
 * See: https://docs.rs/process_mining/latest/process_mining/
 */
package org.yawlfoundation.yawl.erlang.processmining;

import java.util.List;
import java.util.Set;

/**
 * Petri net representation.
 *
 * <p>This class mirrors the Rust PetriNet concept from the process_mining crate.
 * A Petri net is a mathematical modeling language for distributed systems,
 * commonly used for process modeling and conformance checking.</p>
 *
 * <h2>Rust → Java API Mapping</h2>
 * <pre>{@code
 * // ═══════════════════════════════════════════════════════════════
 * // RUST (from process_mining crate docs)
 * // ═══════════════════════════════════════════════════════════════
 * use process_mining::discovery::alphappp::*;
 *
 * let net = alphappp_discover_petri_net(&projection, AlphaPPPConfig::default());
 * println!("Places: {}", net.places.len());
 * println!("Transitions: {}", net.transitions.len());
 *
 * // ═══════════════════════════════════════════════════════════════
 * // JAVA (equivalent - same method names, same behavior)
 * // ═══════════════════════════════════════════════════════════════
 * import org.yawlfoundation.yawl.erlang.processmining.PetriNet;
 *
 * PetriNet net = log.discoverAlphaPPP();
 * System.out.println("Places: " + net.places().size());
 * System.out.println("Transitions: " + net.transitions().size());
 * }</pre>
 *
 * @see <a href="https://docs.rs/process_mining/latest/process_mining/discovery/alphappp/">Rust Alpha+++ docs</a>
 */
public final class PetriNet {

    private final List<Place> places;
    private final List<Transition> transitions;
    private final List<Arc> arcs;
    private final Set<String> initialMarking;
    private final Set<String> finalMarking;

    /**
     * Creates a Petri net from its components.
     */
    PetriNet(List<Place> places, List<Transition> transitions, List<Arc> arcs,
             Set<String> initialMarking, Set<String> finalMarking) {
        this.places = places;
        this.transitions = transitions;
        this.arcs = arcs;
        this.initialMarking = initialMarking;
        this.finalMarking = finalMarking;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FIELD ACCESSORS (mirror Rust net.places, net.transitions, etc.)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the list of places in this Petri net.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code net.places}</pre>
     * <p><b>Rust type:</b> {@code Vec<Place>}
     */
    public List<Place> places() {
        return places;
    }

    /**
     * Returns the list of transitions in this Petri net.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code net.transitions}</pre>
     * <p><b>Rust type:</b> {@code Vec<Transition>}
     */
    public List<Transition> transitions() {
        return transitions;
    }

    /**
     * Returns the list of arcs in this Petri net.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code net.arcs}</pre>
     * <p><b>Rust type:</b> {@code Vec<Arc>}
     */
    public List<Arc> arcs() {
        return arcs;
    }

    /**
     * Returns the initial marking (tokens in places at start).
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code net.initial_marking}</pre>
     */
    public Set<String> initialMarking() {
        return initialMarking;
    }

    /**
     * Returns the final marking (tokens in places at end).
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code net.final_marking}</pre>
     */
    public Set<String> finalMarking() {
        return finalMarking;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the number of places.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code net.places.len()}</pre>
     */
    public int placeCount() {
        return places.size();
    }

    /**
     * Returns the number of transitions.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code net.transitions.len()}</pre>
     */
    public int transitionCount() {
        return transitions.size();
    }

    /**
     * Exports this Petri net to PNML format.
     *
     * @return PNML XML string
     */
    public String toPnml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<pnml>\n");
        sb.append("  <net id=\"net\">\n");

        // Places
        for (Place p : places) {
            sb.append("    <place id=\"").append(escapeXml(p.id)).append("\">\n");
            if (p.label != null && !p.label.isEmpty()) {
                sb.append("      <name><text>").append(escapeXml(p.label))
                  .append("</text></name>\n");
            }
            if (initialMarking.contains(p.id)) {
                sb.append("      <initialMarking><text>1</text></initialMarking>\n");
            }
            sb.append("    </place>\n");
        }

        // Transitions
        for (Transition t : transitions) {
            sb.append("    <transition id=\"").append(escapeXml(t.id)).append("\">\n");
            if (t.label != null && !t.label.isEmpty()) {
                sb.append("      <name><text>").append(escapeXml(t.label))
                  .append("</text></name>\n");
            }
            sb.append("    </transition>\n");
        }

        // Arcs
        for (Arc a : arcs) {
            sb.append("    <arc source=\"").append(escapeXml(a.source))
              .append("\" target=\"").append(escapeXml(a.target)).append("\"/>\n");
        }

        sb.append("  </net>\n");
        sb.append("</pnml>\n");
        return sb.toString();
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NESTED TYPES (mirror Rust Place, Transition, Arc)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * A place in a Petri net (holds tokens).
     *
     * <p><b>Rust equivalent:</b> {@code Place}
     */
    public record Place(
        String id,
        String label
    ) {}

    /**
     * A transition in a Petri net (consumes/produces tokens).
     *
     * <p><b>Rust equivalent:</b> {@code Transition}
     */
    public record Transition(
        String id,
        String label,
        boolean silent
    ) {}

    /**
     * An arc connecting a place to a transition or vice versa.
     *
     * <p><b>Rust equivalent:</b> {@code Arc}
     */
    public record Arc(
        String source,
        String target,
        int weight
    ) {}
}
