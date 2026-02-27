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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.benchmark;

/**
 * Provides valid YAWL specification XML strings for benchmark use.
 *
 * <p>All specs use the standard YAWL Schema 4.0 format accepted by
 * {@code YStatelessEngine.unmarshalSpecification(String xml)}. They are
 * deliberately minimal — no data variables, no resourcing — so benchmark
 * timings reflect pure engine overhead rather than query evaluation cost.</p>
 *
 * <p>Patterns represented:</p>
 * <ul>
 *   <li>{@link #SEQUENTIAL_2_TASK} — baseline 2-task sequential workflow</li>
 *   <li>{@link #SEQUENTIAL_4_TASK} — 4-task sequential for deeper chains</li>
 *   <li>{@link #PARALLEL_SPLIT_SYNC} — AND-split followed by AND-join</li>
 *   <li>{@link #EXCLUSIVE_CHOICE} — XOR-split with single path per run</li>
 *   <li>{@link #MULTI_CHOICE} — OR-split enabling multiple branches</li>
 * </ul>
 */
public final class BenchmarkSpecFactory {

    private BenchmarkSpecFactory() { }

    /** Header shared by all specs. */
    private static final String HDR =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<specificationSet version=\"4.0\"\n" +
        "    xmlns=\"http://www.yawlfoundation.org/yawlschema\"\n" +
        "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
        "    xsi:schemaLocation=\"http://www.yawlfoundation.org/yawlschema " +
        "http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd\">\n";

    private static final String FTR = "</specificationSet>\n";

    // ── Spec: 2-task sequential ───────────────────────────────────────────

    /**
     * Minimal 2-task sequential workflow.
     * Start → task1 → task2 → End
     */
    public static final String SEQUENTIAL_2_TASK =
        HDR +
        "  <specification uri=\"benchSeq2\">\n" +
        "    <metaData><title>Benchmark Seq-2</title><version>1.0</version></metaData>\n" +
        "    <decomposition id=\"net\" xsi:type=\"NetFactsType\" isRootNet=\"true\">\n" +
        "      <processControlElements>\n" +
        "        <inputCondition id=\"start\">\n" +
        "          <flowsInto><nextElementRef id=\"t1\"/></flowsInto>\n" +
        "        </inputCondition>\n" +
        "        <task id=\"t1\">\n" +
        "          <name>Task 1</name>\n" +
        "          <flowsInto><nextElementRef id=\"t2\"/></flowsInto>\n" +
        "          <join code=\"xor\"/><split code=\"and\"/>\n" +
        "        </task>\n" +
        "        <task id=\"t2\">\n" +
        "          <name>Task 2</name>\n" +
        "          <flowsInto><nextElementRef id=\"end\"/></flowsInto>\n" +
        "          <join code=\"xor\"/><split code=\"and\"/>\n" +
        "        </task>\n" +
        "        <outputCondition id=\"end\"/>\n" +
        "      </processControlElements>\n" +
        "    </decomposition>\n" +
        "  </specification>\n" +
        FTR;

    // ── Spec: 4-task sequential ───────────────────────────────────────────

    /**
     * 4-task sequential workflow.
     * Start → t1 → t2 → t3 → t4 → End
     */
    public static final String SEQUENTIAL_4_TASK =
        HDR +
        "  <specification uri=\"benchSeq4\">\n" +
        "    <metaData><title>Benchmark Seq-4</title><version>1.0</version></metaData>\n" +
        "    <decomposition id=\"net\" xsi:type=\"NetFactsType\" isRootNet=\"true\">\n" +
        "      <processControlElements>\n" +
        "        <inputCondition id=\"start\">\n" +
        "          <flowsInto><nextElementRef id=\"t1\"/></flowsInto>\n" +
        "        </inputCondition>\n" +
        "        <task id=\"t1\">\n" +
        "          <name>Task 1</name>\n" +
        "          <flowsInto><nextElementRef id=\"t2\"/></flowsInto>\n" +
        "          <join code=\"xor\"/><split code=\"and\"/>\n" +
        "        </task>\n" +
        "        <task id=\"t2\">\n" +
        "          <name>Task 2</name>\n" +
        "          <flowsInto><nextElementRef id=\"t3\"/></flowsInto>\n" +
        "          <join code=\"xor\"/><split code=\"and\"/>\n" +
        "        </task>\n" +
        "        <task id=\"t3\">\n" +
        "          <name>Task 3</name>\n" +
        "          <flowsInto><nextElementRef id=\"t4\"/></flowsInto>\n" +
        "          <join code=\"xor\"/><split code=\"and\"/>\n" +
        "        </task>\n" +
        "        <task id=\"t4\">\n" +
        "          <name>Task 4</name>\n" +
        "          <flowsInto><nextElementRef id=\"end\"/></flowsInto>\n" +
        "          <join code=\"xor\"/><split code=\"and\"/>\n" +
        "        </task>\n" +
        "        <outputCondition id=\"end\"/>\n" +
        "      </processControlElements>\n" +
        "    </decomposition>\n" +
        "  </specification>\n" +
        FTR;

    // ── Spec: parallel split + AND-join sync ─────────────────────────────

    /**
     * AND-split / AND-join (parallel split + synchronization).
     * Start → splitTask →[AND-split]→ taskA + taskB →[AND-join]→ syncTask → End
     */
    public static final String PARALLEL_SPLIT_SYNC =
        HDR +
        "  <specification uri=\"benchParallel\">\n" +
        "    <metaData><title>Benchmark Parallel</title><version>1.0</version></metaData>\n" +
        "    <decomposition id=\"net\" xsi:type=\"NetFactsType\" isRootNet=\"true\">\n" +
        "      <processControlElements>\n" +
        "        <inputCondition id=\"start\">\n" +
        "          <flowsInto><nextElementRef id=\"split\"/></flowsInto>\n" +
        "        </inputCondition>\n" +
        "        <task id=\"split\">\n" +
        "          <name>Split Task</name>\n" +
        "          <flowsInto><nextElementRef id=\"tA\"/></flowsInto>\n" +
        "          <flowsInto><nextElementRef id=\"tB\"/></flowsInto>\n" +
        "          <join code=\"xor\"/><split code=\"and\"/>\n" +
        "        </task>\n" +
        "        <task id=\"tA\">\n" +
        "          <name>Branch A</name>\n" +
        "          <flowsInto><nextElementRef id=\"sync\"/></flowsInto>\n" +
        "          <join code=\"xor\"/><split code=\"and\"/>\n" +
        "        </task>\n" +
        "        <task id=\"tB\">\n" +
        "          <name>Branch B</name>\n" +
        "          <flowsInto><nextElementRef id=\"sync\"/></flowsInto>\n" +
        "          <join code=\"xor\"/><split code=\"and\"/>\n" +
        "        </task>\n" +
        "        <task id=\"sync\">\n" +
        "          <name>Synchronize</name>\n" +
        "          <flowsInto><nextElementRef id=\"end\"/></flowsInto>\n" +
        "          <join code=\"and\"/><split code=\"and\"/>\n" +
        "        </task>\n" +
        "        <outputCondition id=\"end\"/>\n" +
        "      </processControlElements>\n" +
        "    </decomposition>\n" +
        "  </specification>\n" +
        FTR;

    // ── Spec: exclusive choice (XOR-split) ───────────────────────────────

    /**
     * XOR-split / XOR-join (exclusive choice pattern).
     * Start → choice →[XOR-split]→ pathA or pathB →[XOR-join]→ merge → End
     */
    public static final String EXCLUSIVE_CHOICE =
        HDR +
        "  <specification uri=\"benchXor\">\n" +
        "    <metaData><title>Benchmark XOR</title><version>1.0</version></metaData>\n" +
        "    <decomposition id=\"net\" xsi:type=\"NetFactsType\" isRootNet=\"true\">\n" +
        "      <processControlElements>\n" +
        "        <inputCondition id=\"start\">\n" +
        "          <flowsInto><nextElementRef id=\"choice\"/></flowsInto>\n" +
        "        </inputCondition>\n" +
        "        <task id=\"choice\">\n" +
        "          <name>Choice Gate</name>\n" +
        "          <flowsInto>\n" +
        "            <nextElementRef id=\"pathA\"/>\n" +
        "            <isDefaultFlow/>\n" +
        "          </flowsInto>\n" +
        "          <flowsInto><nextElementRef id=\"pathB\"/></flowsInto>\n" +
        "          <join code=\"xor\"/><split code=\"xor\"/>\n" +
        "        </task>\n" +
        "        <task id=\"pathA\">\n" +
        "          <name>Path A</name>\n" +
        "          <flowsInto><nextElementRef id=\"merge\"/></flowsInto>\n" +
        "          <join code=\"xor\"/><split code=\"and\"/>\n" +
        "        </task>\n" +
        "        <task id=\"pathB\">\n" +
        "          <name>Path B</name>\n" +
        "          <flowsInto><nextElementRef id=\"merge\"/></flowsInto>\n" +
        "          <join code=\"xor\"/><split code=\"and\"/>\n" +
        "        </task>\n" +
        "        <task id=\"merge\">\n" +
        "          <name>Merge</name>\n" +
        "          <flowsInto><nextElementRef id=\"end\"/></flowsInto>\n" +
        "          <join code=\"xor\"/><split code=\"and\"/>\n" +
        "        </task>\n" +
        "        <outputCondition id=\"end\"/>\n" +
        "      </processControlElements>\n" +
        "    </decomposition>\n" +
        "  </specification>\n" +
        FTR;

    // ── Spec: multi-choice (OR-split) ─────────────────────────────────────

    /**
     * OR-split (multi-choice pattern) — enables all branches simultaneously.
     * Start → mcTask →[OR-split]→ branchA + branchB + branchC → End
     *
     * <p>Uses OR-join which fires after any enabled branch completes.</p>
     */
    public static final String MULTI_CHOICE =
        HDR +
        "  <specification uri=\"benchOr\">\n" +
        "    <metaData><title>Benchmark OR</title><version>1.0</version></metaData>\n" +
        "    <decomposition id=\"net\" xsi:type=\"NetFactsType\" isRootNet=\"true\">\n" +
        "      <processControlElements>\n" +
        "        <inputCondition id=\"start\">\n" +
        "          <flowsInto><nextElementRef id=\"mc\"/></flowsInto>\n" +
        "        </inputCondition>\n" +
        "        <task id=\"mc\">\n" +
        "          <name>Multi-Choice Gate</name>\n" +
        "          <flowsInto><nextElementRef id=\"brA\"/><predicate ordering=\"1\">true()</predicate></flowsInto>\n" +
        "          <flowsInto><nextElementRef id=\"brB\"/><predicate ordering=\"2\">true()</predicate></flowsInto>\n" +
        "          <flowsInto><nextElementRef id=\"brC\"/><predicate ordering=\"3\">true()</predicate></flowsInto>\n" +
        "          <join code=\"xor\"/><split code=\"or\"/>\n" +
        "        </task>\n" +
        "        <task id=\"brA\">\n" +
        "          <name>Branch A</name>\n" +
        "          <flowsInto><nextElementRef id=\"end\"/></flowsInto>\n" +
        "          <join code=\"xor\"/><split code=\"and\"/>\n" +
        "        </task>\n" +
        "        <task id=\"brB\">\n" +
        "          <name>Branch B</name>\n" +
        "          <flowsInto><nextElementRef id=\"end\"/></flowsInto>\n" +
        "          <join code=\"xor\"/><split code=\"and\"/>\n" +
        "        </task>\n" +
        "        <task id=\"brC\">\n" +
        "          <name>Branch C</name>\n" +
        "          <flowsInto><nextElementRef id=\"end\"/></flowsInto>\n" +
        "          <join code=\"xor\"/><split code=\"and\"/>\n" +
        "        </task>\n" +
        "        <outputCondition id=\"end\"/>\n" +
        "      </processControlElements>\n" +
        "    </decomposition>\n" +
        "  </specification>\n" +
        FTR;
}
