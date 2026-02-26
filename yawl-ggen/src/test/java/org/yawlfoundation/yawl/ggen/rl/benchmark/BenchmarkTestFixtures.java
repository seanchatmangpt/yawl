/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl.benchmark;

import org.yawlfoundation.yawl.ggen.powl.PowlActivity;
import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.powl.PowlNode;
import org.yawlfoundation.yawl.ggen.powl.PowlOperatorNode;
import org.yawlfoundation.yawl.ggen.powl.PowlOperatorType;
import org.yawlfoundation.yawl.ggen.rl.CandidateSet;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pre-built test fixtures for deterministic benchmarking.
 *
 * <p>Provides POWL models of varying complexity and process descriptions
 * for repeatable benchmark runs. All fixtures are deterministic and
 * do not depend on external resources.
 */
public final class BenchmarkTestFixtures {

    private BenchmarkTestFixtures() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    // ─── Process Descriptions (5 Complexity Levels) ───────────────────────────

    /** Simple process: 3-5 activities */
    public static final String PROCESS_SIMPLE = "Submit form, review, approve or reject.";

    /** Medium process: 6-10 activities */
    public static final String PROCESS_MEDIUM =
            "Customer submits loan application. Bank checks credit score " +
            "and verifies documents in parallel. Then bank either approves " +
            "or rejects the application. If approved, loan is disbursed.";

    /** Complex process: 11-20 activities */
    public static final String PROCESS_COMPLEX =
            "Order is received. Inventory is checked. If items available, " +
            "pick and pack in parallel. If not available, backorder. " +
            "After packing, ship and notify customer. Customer can return " +
            "within 30 days. If returned, process refund and restock.";

    /** Very complex process: 21+ activities */
    public static final String PROCESS_VERY_COMPLEX =
            "Customer places order through web portal. System validates payment. " +
            "Inventory service checks stock across warehouses. If available, " +
            "reservation is made and picking slip generated. Picking team collects items. " +
            "Quality control inspects. If passed, packing commences. " +
            "Shipping carrier is selected based on destination. " +
            "Customs documentation is generated for international orders. " +
            "Shipment is tracked. Customer receives notifications. " +
            "Upon delivery, feedback is requested. If feedback is negative, " +
            "support ticket is created. Returns are processed. Refunds issued if needed.";

    /** Edge case: empty description */
    public static final String PROCESS_EMPTY = "";

    /** Edge case: single activity */
    public static final String PROCESS_SINGLE = "Submit form.";

    // ─── POWL Model Fixtures ──────────────────────────────────────────────────

    /**
     * Creates a simple sequence model: A → B → C
     */
    public static PowlModel createSimpleSequence() {
        PowlActivity a = activity("A");
        PowlActivity b = activity("B");
        PowlActivity c = activity("C");

        PowlOperatorNode root = new PowlOperatorNode(
                "seq_1", PowlOperatorType.SEQUENCE, List.of(a, b, c));

        return new PowlModel("model_simple_seq", root, Instant.now());
    }

    /**
     * Creates an XOR choice model: A ⊕ B
     */
    public static PowlModel createSimpleXor() {
        PowlActivity a = activity("A");
        PowlActivity b = activity("B");

        PowlOperatorNode root = new PowlOperatorNode(
                "xor_1", PowlOperatorType.XOR, List.of(a, b));

        return new PowlModel("model_simple_xor", root, Instant.now());
    }

    /**
     * Creates a parallel model: A || B
     */
    public static PowlModel createSimpleParallel() {
        PowlActivity a = activity("A");
        PowlActivity b = activity("B");

        PowlOperatorNode root = new PowlOperatorNode(
                "par_1", PowlOperatorType.PARALLEL, List.of(a, b));

        return new PowlModel("model_simple_par", root, Instant.now());
    }

    /**
     * Creates a loop model: do A while B
     */
    public static PowlModel createSimpleLoop() {
        PowlActivity doActivity = activity("DoWork");
        PowlActivity redoActivity = activity("CheckCondition");

        PowlOperatorNode root = new PowlOperatorNode(
                "loop_1", PowlOperatorType.LOOP, List.of(doActivity, redoActivity));

        return new PowlModel("model_simple_loop", root, Instant.now());
    }

    /**
     * Creates a medium complexity model with nested structures:
     * Sequence(XOR(A, B), Parallel(C, D), E)
     */
    public static PowlModel createMediumModel() {
        PowlActivity a = activity("Approve");
        PowlActivity b = activity("Reject");
        PowlActivity c = activity("CheckCredit");
        PowlActivity d = activity("VerifyDocs");
        PowlActivity e = activity("Disburse");

        PowlOperatorNode xor = new PowlOperatorNode(
                "xor_medium", PowlOperatorType.XOR, List.of(a, b));
        PowlOperatorNode parallel = new PowlOperatorNode(
                "par_medium", PowlOperatorType.PARALLEL, List.of(c, d));

        PowlOperatorNode root = new PowlOperatorNode(
                "seq_medium", PowlOperatorType.SEQUENCE, List.of(xor, parallel, e));

        return new PowlModel("model_medium", root, Instant.now());
    }

    /**
     * Creates a complex model with deep nesting:
     * Multiple levels of XOR, PARALLEL, and SEQUENCE operators
     */
    public static PowlModel createComplexModel() {
        // Level 3: Leaf activities
        PowlActivity a1 = activity("ReceiveOrder");
        PowlActivity a2 = activity("CheckInventory");
        PowlActivity a3 = activity("PickItems");
        PowlActivity a4 = activity("PackItems");
        PowlActivity a5 = activity("Ship");
        PowlActivity a6 = activity("NotifyCustomer");
        PowlActivity a7 = activity("Backorder");
        PowlActivity a8 = activity("Restock");
        PowlActivity a9 = activity("ProcessRefund");
        PowlActivity a10 = activity("ReceiveReturn");

        // Level 2: Operators
        PowlOperatorNode pickPack = new PowlOperatorNode(
                "par_pickpack", PowlOperatorType.PARALLEL, List.of(a3, a4));
        PowlOperatorNode shipNotify = new PowlOperatorNode(
                "par_shipnotify", PowlOperatorType.PARALLEL, List.of(a5, a6));
        PowlOperatorNode returnFlow = new PowlOperatorNode(
                "seq_return", PowlOperatorType.SEQUENCE, List.of(a9, a8));

        // Level 1: Main branches
        PowlOperatorNode availableBranch = new PowlOperatorNode(
                "seq_available", PowlOperatorType.SEQUENCE, List.of(pickPack, shipNotify));
        PowlOperatorNode unavailableBranch = new PowlOperatorNode(
                "seq_unavailable", PowlOperatorType.SEQUENCE, List.of(a7));

        PowlOperatorNode inventoryChoice = new PowlOperatorNode(
                "xor_inventory", PowlOperatorType.XOR, List.of(availableBranch, unavailableBranch));

        // Root: Receive → Check → Choice
        PowlOperatorNode root = new PowlOperatorNode(
                "seq_main", PowlOperatorType.SEQUENCE, List.of(a1, a2, inventoryChoice));

        return new PowlModel("model_complex", root, Instant.now());
    }

    /**
     * Creates a very complex model with 25+ activities and deep nesting.
     */
    public static PowlModel createVeryComplexModel() {
        List<PowlNode> nodes = new ArrayList<>();

        // Create 25 activities
        for (int i = 1; i <= 25; i++) {
            nodes.add(activity("Activity_" + i));
        }

        // Build a deep tree structure
        // Level 4: Groups of 2-3 activities
        PowlOperatorNode g1 = new PowlOperatorNode("g1", PowlOperatorType.SEQUENCE,
                List.of(nodes.get(0), nodes.get(1)));
        PowlOperatorNode g2 = new PowlOperatorNode("g2", PowlOperatorType.PARALLEL,
                List.of(nodes.get(2), nodes.get(3), nodes.get(4)));
        PowlOperatorNode g3 = new PowlOperatorNode("g3", PowlOperatorType.XOR,
                List.of(nodes.get(5), nodes.get(6)));
        PowlOperatorNode g4 = new PowlOperatorNode("g4", PowlOperatorType.SEQUENCE,
                List.of(nodes.get(7), nodes.get(8), nodes.get(9)));
        PowlOperatorNode g5 = new PowlOperatorNode("g5", PowlOperatorType.LOOP,
                List.of(nodes.get(10), nodes.get(11)));
        PowlOperatorNode g6 = new PowlOperatorNode("g6", PowlOperatorType.PARALLEL,
                List.of(nodes.get(12), nodes.get(13)));
        PowlOperatorNode g7 = new PowlOperatorNode("g7", PowlOperatorType.SEQUENCE,
                List.of(nodes.get(14), nodes.get(15), nodes.get(16)));
        PowlOperatorNode g8 = new PowlOperatorNode("g8", PowlOperatorType.XOR,
                List.of(nodes.get(17), nodes.get(18), nodes.get(19)));
        PowlOperatorNode g9 = new PowlOperatorNode("g9", PowlOperatorType.SEQUENCE,
                List.of(nodes.get(20), nodes.get(21)));
        PowlOperatorNode g10 = new PowlOperatorNode("g10", PowlOperatorType.PARALLEL,
                List.of(nodes.get(22), nodes.get(23), nodes.get(24)));

        // Level 3: Combine groups
        PowlOperatorNode l3a = new PowlOperatorNode("l3a", PowlOperatorType.SEQUENCE, List.of(g1, g2));
        PowlOperatorNode l3b = new PowlOperatorNode("l3b", PowlOperatorType.SEQUENCE, List.of(g3, g4));
        PowlOperatorNode l3c = new PowlOperatorNode("l3c", PowlOperatorType.SEQUENCE, List.of(g5, g6));
        PowlOperatorNode l3d = new PowlOperatorNode("l3d", PowlOperatorType.SEQUENCE, List.of(g7, g8));
        PowlOperatorNode l3e = new PowlOperatorNode("l3e", PowlOperatorType.SEQUENCE, List.of(g9, g10));

        // Level 2: Combine level 3 nodes
        PowlOperatorNode l2a = new PowlOperatorNode("l2a", PowlOperatorType.PARALLEL, List.of(l3a, l3b));
        PowlOperatorNode l2b = new PowlOperatorNode("l2b", PowlOperatorType.PARALLEL, List.of(l3c, l3d));

        // Level 1: Top-level XOR choice
        PowlOperatorNode l1 = new PowlOperatorNode("l1", PowlOperatorType.XOR, List.of(l2a, l2b, l3e));

        // Root: Sequence of main sections
        PowlOperatorNode root = new PowlOperatorNode("root", PowlOperatorType.SEQUENCE, List.of(l1));

        return new PowlModel("model_very_complex", root, Instant.now());
    }

    // ─── Reward Lists for GroupAdvantage Testing ──────────────────────────────

    /**
     * Creates reward lists for testing different K values.
     */
    public static List<Double> createRewards(int k) {
        List<Double> rewards = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            // Create varied but deterministic rewards
            rewards.add(0.3 + (i * 0.1) % 0.7);
        }
        return rewards;
    }

    /**
     * Creates reward lists with specific patterns.
     */
    public static List<Double> createUniformRewards(int k, double value) {
        List<Double> rewards = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            rewards.add(value);
        }
        return rewards;
    }

    /**
     * Creates reward lists with a clear winner.
     */
    public static List<Double> createRewardsWithWinner(int k, int winnerIndex) {
        List<Double> rewards = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            rewards.add(i == winnerIndex ? 0.95 : 0.3 + (i * 0.05));
        }
        return rewards;
    }

    // ─── CandidateSet Fixtures ────────────────────────────────────────────────

    /**
     * Creates a CandidateSet with K candidates for testing.
     */
    public static CandidateSet createCandidateSet(int k) {
        List<PowlModel> candidates = new ArrayList<>(k);
        List<Double> rewards = new ArrayList<>(k);

        for (int i = 0; i < k; i++) {
            // Create varied models
            candidates.add(switch (i % 4) {
                case 0 -> createSimpleSequence();
                case 1 -> createSimpleXor();
                case 2 -> createSimpleParallel();
                default -> createSimpleLoop();
            });
            rewards.add(0.3 + (i * 0.15));
        }

        return new CandidateSet(candidates, rewards);
    }

    /**
     * Creates a large CandidateSet for memory benchmarks.
     */
    public static CandidateSet createLargeCandidateSet(int size) {
        List<PowlModel> candidates = new ArrayList<>(size);
        List<Double> rewards = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            candidates.add(createComplexModel());
            rewards.add(0.5 + (i % 10) * 0.05);
        }

        return new CandidateSet(candidates, rewards);
    }

    // ─── Helper Methods ───────────────────────────────────────────────────────

    private static PowlActivity activity(String label) {
        return new PowlActivity("act_" + label.toLowerCase().replace(" ", "_") + "_" +
                UUID.randomUUID().toString().substring(0, 8), label);
    }
}
