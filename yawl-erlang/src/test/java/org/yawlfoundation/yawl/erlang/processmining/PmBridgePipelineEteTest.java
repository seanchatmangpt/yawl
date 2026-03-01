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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.erlang.processmining;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.erlang.bridge.ErlangNode;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end pipeline validation for the pm-bridge-ggen Five-Agent ggen Native Bridge.
 *
 * <p>Validates the complete construction chain without requiring a running Erlang node:
 * <pre>
 *   process_mining crate (Rust, 0.5.2)
 *     → gen-ttl binary (cargo run)
 *     → ontology/pm-bridge.ttl  (53 NativeCall triples)
 *     → lib/java/ProcessMiningCapability.java  (53-constant enum)
 *     → lib/java/ProcessMiningBridge.java       (53 RPC methods)
 *     → lib/java/CapabilityRegistry.java        (6-group static registry)
 *     → lib/test/ProcessMiningCapabilityTest.java (53 @Test stubs)
 *     → lib/erlang/process_mining_bridge.erl    (53 handle_call clauses)
 *     → golden/ (committed expected outputs, drift-detected by validate.mjs)
 * </pre>
 *
 * <p>Five tiers:
 * <ol>
 *   <li><b>Enum invariants</b> — Java ProcessMiningCapability structural correctness</li>
 *   <li><b>Registry consistency</b> — CapabilityRegistry cross-reference integrity</li>
 *   <li><b>Cross-artifact consistency</b> — .erl, .ttl, .java aligned on 53 capabilities</li>
 *   <li><b>Golden drift detection</b> — lib/ matches committed golden/ files byte-for-byte</li>
 *   <li><b>Java API completeness</b> — ProcessMiningBridge reflection + constructor coupling</li>
 * </ol>
 */
@Tag("pm-bridge-pipeline")
@DisplayName("PM Bridge Pipeline — End-to-End Construction Validation")
class PmBridgePipelineEteTest {

    /** Canonical capability count locked after first successful gen-ttl run. */
    private static final int EXPECTED_CAPABILITY_COUNT = 53;

    /** Exactly 6 groups emitted by gen-ttl group_for(); no fallback "misc" allowed. */
    private static final Set<String> VALID_GROUPS =
            Set.of("analysis", "conformance", "discovery", "io", "locel", "utility");

    /** Only recognised feature gate in process_mining 0.5.2. */
    private static final Set<String> VALID_FEATURE_FLAGS = Set.of("graphviz-export");

    /**
     * The sole consumesInput=true capability: maps:take removes the resource from
     * gen_server state so a new UUID replaces the old one.
     */
    private static final String CONSUMES_INPUT_ERL_FUNCTION = "add_init_exit_events_to_ocel";

    /** Safe-atom conversion of "discover_alpha+++" — validates the +++ → _ppp rule. */
    private static final String ALPHA_PPP_ERL_FUNCTION = "discover_alpha_ppp";

    /** Store-only shape: no UUID input, creates new resource, returns UUID. */
    private static final String LOCEL_NEW_ERL_FUNCTION = "locel_new";

    private static Path pmBridgeGgenRoot;

    @BeforeAll
    static void locatePmBridgeGgen() {
        String basedir = System.getProperty("project.basedir", ".");
        pmBridgeGgenRoot = Path.of(basedir).resolve("pm-bridge-ggen")
                .toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(pmBridgeGgenRoot),
                "pm-bridge-ggen directory must exist at: " + pmBridgeGgenRoot
                        + "\n  Run: mvn generate-sources -pl yawl-erlang");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tier 1: Java enum structural invariants
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Capability count is locked at 53 (process_mining 0.5.2 binding surface)")
    void capabilityCountIsLockedAt53() {
        int actual = ProcessMiningCapability.values().length;
        assertEquals(EXPECTED_CAPABILITY_COUNT, actual,
                "ProcessMiningCapability must declare exactly " + EXPECTED_CAPABILITY_COUNT
                        + " constants (process_mining 0.5.2). "
                        + "Actual=" + actual + ". If the crate added functions, "
                        + "re-run gen-ttl, update EXPECTED_CAPABILITY_COUNT, and commit new golden files.");
    }

    @Test
    @DisplayName("Every capability has a non-null, non-blank erlFunction")
    void allCapabilitiesHaveNonNullErlFunction() {
        for (ProcessMiningCapability cap : ProcessMiningCapability.values()) {
            assertNotNull(cap.erlFunction(),
                    "erlFunction() must not return null for " + cap);
            assertFalse(cap.erlFunction().isBlank(),
                    "erlFunction() must not be blank for " + cap);
        }
    }

    @Test
    @DisplayName("Every capability group is within the 6 canonical groups (no 'misc' fallback)")
    void noCapabilityHasMiscOrUnknownGroup() {
        List<String> invalid = Arrays.stream(ProcessMiningCapability.values())
                .filter(cap -> !VALID_GROUPS.contains(cap.group()))
                .map(cap -> cap.name() + " → group='" + cap.group() + "'")
                .toList();
        assertEquals(Collections.emptyList(), invalid,
                "gen-ttl group_for() must resolve every capability to a known group. Invalid: " + invalid);
    }

    @Test
    @DisplayName("Every capability label is non-null (empty string allowed)")
    void allCapabilitiesHaveNonNullLabel() {
        for (ProcessMiningCapability cap : ProcessMiningCapability.values()) {
            assertNotNull(cap.label(),
                    "label() must not return null for " + cap);
        }
    }

    @Test
    @DisplayName("Feature flags are null or from the known set {graphviz-export}")
    void featureFlagsAreNullOrFromKnownSet() {
        for (ProcessMiningCapability cap : ProcessMiningCapability.values()) {
            if (cap.hasFeatureFlag()) {
                assertTrue(VALID_FEATURE_FLAGS.contains(cap.featureFlag()),
                        "Unknown featureFlag '" + cap.featureFlag() + "' on " + cap
                                + ". Expected one of: " + VALID_FEATURE_FLAGS);
            }
        }
    }

    @Test
    @DisplayName("erlFunction names satisfy safe_atom: only [a-z0-9_], no leading/trailing _")
    void erlFunctionNamesUseSafeAtomConvention() {
        for (ProcessMiningCapability cap : ProcessMiningCapability.values()) {
            String fn = cap.erlFunction();
            assertTrue(fn.matches("[a-z0-9_]+"),
                    "erlFunction '" + fn + "' on " + cap + " must only contain [a-z0-9_]. "
                            + "safe_atom converts +++ → _ppp, + → _plus, - → _, space → _.");
            assertFalse(fn.startsWith("_"),
                    "erlFunction '" + fn + "' must not start with underscore");
            assertFalse(fn.endsWith("_"),
                    "erlFunction '" + fn + "' must not end with underscore");
        }
    }

    @Test
    @DisplayName("All 6 canonical groups are represented in the enum")
    void allSixGroupsAreRepresentedInEnum() {
        Set<String> foundGroups = Arrays.stream(ProcessMiningCapability.values())
                .map(ProcessMiningCapability::group)
                .collect(Collectors.toSet());
        assertEquals(VALID_GROUPS, foundGroups,
                "ProcessMiningCapability must contain at least one entry per group. "
                        + "Missing: " + VALID_GROUPS.stream()
                        .filter(g -> !foundGroups.contains(g))
                        .collect(Collectors.toSet()));
    }

    @Test
    @DisplayName("All locel_* capabilities are exclusively in 'locel' group")
    void locelFunctionsAreInLocelGroup() {
        List<String> violations = Arrays.stream(ProcessMiningCapability.values())
                .filter(cap -> cap.erlFunction().startsWith("locel_"))
                .filter(cap -> !"locel".equals(cap.group()))
                .map(cap -> cap.name() + " group='" + cap.group() + "'")
                .toList();
        assertEquals(Collections.emptyList(), violations,
                "All locel_* capabilities must be in 'locel' group: " + violations);
    }

    @Test
    @DisplayName("locel group has exactly 31 capabilities (all locel_* functions in 0.5.2)")
    void locelGroupHasExactly31Capabilities() {
        List<ProcessMiningCapability> locelCaps = CapabilityRegistry.BY_GROUP.get("locel");
        assertNotNull(locelCaps, "locel group must exist in BY_GROUP");
        assertEquals(31, locelCaps.size(),
                "locel group must have exactly 31 capabilities. Found: "
                        + locelCaps.stream().map(ProcessMiningCapability::erlFunction).toList());
    }

    @Test
    @DisplayName("graphviz-export: exactly 4 capabilities are gated (PNG+SVG for DFG and PetriNet)")
    void graphvizExportCapabilitiesAreExactlyFour() {
        List<ProcessMiningCapability> gated = Arrays.stream(ProcessMiningCapability.values())
                .filter(cap -> "graphviz-export".equals(cap.featureFlag()))
                .toList();
        assertEquals(4, gated.size(),
                "Expected exactly 4 graphviz-export capabilities "
                        + "(export_dfg_image_png/svg, export_petri_net_image_png/svg). Found: "
                        + gated.stream().map(ProcessMiningCapability::erlFunction).toList());
        Set<String> expectedFns = Set.of(
                "export_dfg_image_png", "export_dfg_image_svg",
                "export_petri_net_image_png", "export_petri_net_image_svg");
        Set<String> actualFns = gated.stream()
                .map(ProcessMiningCapability::erlFunction)
                .collect(Collectors.toSet());
        assertEquals(expectedFns, actualFns,
                "graphviz-export capability names must exactly match the expected set");
    }

    @Test
    @DisplayName("discover_alpha_ppp exists (safe_atom converts alpha+++ to alpha_ppp)")
    void discoverAlphaPppExistsAsSafeAtomConversion() {
        ProcessMiningCapability cap = CapabilityRegistry.BY_ERL_FUNCTION.get(ALPHA_PPP_ERL_FUNCTION);
        assertNotNull(cap,
                "'" + ALPHA_PPP_ERL_FUNCTION + "' must be registered. "
                        + "safe_atom() converts 'discover_alpha+++' → 'discover_alpha_ppp' "
                        + "by replacing +++ with _ppp.");
        assertEquals("discovery", cap.group(),
                "discover_alpha_ppp must be in 'discovery' group");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tier 2: Registry consistency
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("BY_ERL_FUNCTION size matches enum count")
    void registryByErlFunctionSizeMatchesEnumCount() {
        assertEquals(ProcessMiningCapability.values().length,
                CapabilityRegistry.BY_ERL_FUNCTION.size(),
                "BY_ERL_FUNCTION must have exactly one entry per enum constant");
    }

    @Test
    @DisplayName("BY_GROUP keys are exactly the 6 canonical groups")
    void registryByGroupKeysAreExactlySixGroups() {
        assertEquals(VALID_GROUPS, CapabilityRegistry.BY_GROUP.keySet(),
                "BY_GROUP must have exactly the 6 canonical groups");
    }

    @Test
    @DisplayName("Sum of BY_GROUP list sizes equals total capability count")
    void registryGroupListSizesSumToEnumCount() {
        int sumFromGroups = CapabilityRegistry.BY_GROUP.values().stream()
                .mapToInt(List::size)
                .sum();
        assertEquals(ProcessMiningCapability.values().length, sumFromGroups,
                "Sum of all BY_GROUP list sizes must equal total capability count. "
                        + "Breakdown: " + CapabilityRegistry.BY_GROUP.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue().size())
                        .collect(Collectors.joining(", ")));
    }

    @Test
    @DisplayName("Every enum constant is reachable by erlFunction lookup and round-trips correctly")
    void everyCapabilityRoundTripsByErlFunctionLookup() {
        for (ProcessMiningCapability cap : ProcessMiningCapability.values()) {
            ProcessMiningCapability found = CapabilityRegistry.BY_ERL_FUNCTION.get(cap.erlFunction());
            assertNotNull(found,
                    "No BY_ERL_FUNCTION entry for erlFunction='" + cap.erlFunction() + "'");
            assertSame(cap, found,
                    "BY_ERL_FUNCTION['" + cap.erlFunction() + "'] returned wrong constant. "
                            + "Expected " + cap.name() + " but got " + found.name());
        }
    }

    @Test
    @DisplayName("BY_GROUP lists contain no null entries")
    void registryGroupListsContainNoNullEntries() {
        CapabilityRegistry.BY_GROUP.forEach((group, caps) ->
                caps.forEach(cap -> assertNotNull(cap,
                        "null entry found in BY_GROUP['" + group + "']")));
    }

    @Test
    @DisplayName("add_init_exit_events_to_ocel is in 'io' group (sole consumesInput capability)")
    void consumeInputCapabilityIsRegisteredInIoGroup() {
        ProcessMiningCapability cap =
                CapabilityRegistry.BY_ERL_FUNCTION.get(CONSUMES_INPUT_ERL_FUNCTION);
        assertNotNull(cap,
                "'" + CONSUMES_INPUT_ERL_FUNCTION + "' must be in BY_ERL_FUNCTION. "
                        + "This is the only consumesInput=true capability in process_mining 0.5.2.");
        assertEquals("io", cap.group(),
                "'" + CONSUMES_INPUT_ERL_FUNCTION + "' must be in 'io' group");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tier 3: Cross-artifact structural consistency (reads generated files)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("process_mining_bridge.erl has exactly one handle_call clause per capability")
    void erlangGenServerHandleCallCountMatchesEnum() throws IOException {
        Path erlFile = erlangGenServerPath();
        long clauseCount = Files.lines(erlFile)
                .filter(line -> line.startsWith("handle_call({"))
                .count();
        assertEquals(ProcessMiningCapability.values().length, clauseCount,
                "process_mining_bridge.erl must have exactly " + ProcessMiningCapability.values().length
                        + " handle_call({ clauses (one per capability, excluding catch-all). "
                        + "Found: " + clauseCount);
    }

    @Test
    @DisplayName("process_mining_bridge.erl has a catch-all handle_call clause (defensive programming)")
    void erlangGenServerHasCatchAllClause() throws IOException {
        Path erlFile = erlangGenServerPath();
        boolean hasCatchAll = Files.lines(erlFile)
                .anyMatch(line -> line.startsWith("handle_call(Unknown,"));
        assertTrue(hasCatchAll,
                "process_mining_bridge.erl must have a catch-all "
                        + "'handle_call(Unknown, _From, State) ->' clause");
    }

    @Test
    @DisplayName("add_init_exit_events_to_ocel uses maps:take (consumesInput=true shape)")
    void consumeInputCapabilityUsesMapsTake() throws IOException {
        Path erlFile = erlangGenServerPath();
        List<String> clauseBody = extractHandleCallClause(erlFile, CONSUMES_INPUT_ERL_FUNCTION);
        assertFalse(clauseBody.isEmpty(),
                "No handle_call clause found for " + CONSUMES_INPUT_ERL_FUNCTION);
        assertTrue(clauseBody.stream().anyMatch(l -> l.contains("maps:take")),
                "'" + CONSUMES_INPUT_ERL_FUNCTION + "' handle_call must use maps:take/2 "
                        + "(consumesInput=true removes the resource from state). "
                        + "Clause:\n" + String.join("\n", clauseBody));
        assertFalse(clauseBody.stream().anyMatch(l -> l.contains("maps:get")),
                "'" + CONSUMES_INPUT_ERL_FUNCTION + "' must use maps:take, not maps:get "
                        + "(resource is consumed, not read). "
                        + "Clause:\n" + String.join("\n", clauseBody));
    }

    @Test
    @DisplayName("locel_new uses maps:put directly (Store-only shape, no UUID input)")
    void locelNewUsesMapsPutDirectly() throws IOException {
        Path erlFile = erlangGenServerPath();
        List<String> clauseBody = extractHandleCallClause(erlFile, LOCEL_NEW_ERL_FUNCTION);
        assertFalse(clauseBody.isEmpty(),
                "No handle_call clause found for " + LOCEL_NEW_ERL_FUNCTION);
        assertTrue(clauseBody.stream().anyMatch(l -> l.contains("maps:put")),
                "locel_new must use maps:put to store the new SlimLinkedOCEL resource. "
                        + "Clause:\n" + String.join("\n", clauseBody));
        assertFalse(clauseBody.stream().anyMatch(l -> l.contains("maps:take")),
                "locel_new is a Store shape (no existing UUID consumed). "
                        + "Must not use maps:take.");
    }

    @Test
    @DisplayName("All Resolve-shape handle_call clauses use maps:get/3 (not maps:get/2)")
    void resolveShapesUseMapsGetThreeArgForm() throws IOException {
        Path erlFile = erlangGenServerPath();
        // Every line containing maps:get( must have , not_found) on the same line
        List<String> twoArgGetLines = Files.lines(erlFile)
                .filter(line -> line.contains("maps:get("))
                .filter(line -> !line.contains(", not_found)"))
                .toList();
        assertEquals(Collections.emptyList(), twoArgGetLines,
                "All maps:get calls must use 3-arg form maps:get(Key, Map, not_found) "
                        + "to prevent gen_server crash on missing UUID. "
                        + "Lines using 2-arg form:\n" + String.join("\n", twoArgGetLines));
    }

    @Test
    @DisplayName("pm-bridge.ttl has exactly one bridge:NativeCall triple per capability")
    void generatedTtlNativeCallCountMatchesEnum() throws IOException {
        Path ttl = pmBridgeGgenRoot.resolve("ontology/pm-bridge.ttl");
        assertTrue(Files.exists(ttl),
                "pm-bridge.ttl must exist at " + ttl
                        + "\n  Run: mvn generate-sources -pl yawl-erlang "
                        + "(pm-gen-ttl execution generates it from process_mining::bindings)");
        long nativeCallCount = Files.lines(ttl)
                .filter(line -> line.contains("a bridge:NativeCall"))
                .count();
        assertEquals(ProcessMiningCapability.values().length, nativeCallCount,
                "pm-bridge.ttl must declare exactly one bridge:NativeCall per capability. "
                        + "Expected " + ProcessMiningCapability.values().length
                        + ", found " + nativeCallCount
                        + ". Regenerate with: cd pm-bridge-ggen/gen-ttl && cargo run -- --output-dir ..");
    }

    @Test
    @DisplayName("pm-bridge.ttl has no 'misc' group triples (all functions must be resolved)")
    void generatedTtlHasNoMiscGroup() throws IOException {
        Path ttl = pmBridgeGgenRoot.resolve("ontology/pm-bridge.ttl");
        assertTrue(Files.exists(ttl), "pm-bridge.ttl must exist");
        List<String> miscLines = Files.lines(ttl)
                .filter(line -> line.contains("\"misc\""))
                .toList();
        assertEquals(Collections.emptyList(), miscLines,
                "pm-bridge.ttl must not contain any 'misc' group. "
                        + "All functions must resolve to a canonical group via group_for(). "
                        + "Lines with 'misc':\n" + String.join("\n", miscLines));
    }

    @Test
    @DisplayName("Generated ProcessMiningCapabilityTest.java has exactly one @Test per capability")
    void generatedTestClassHasOneTestPerCapability() throws IOException {
        Path testFile = pmBridgeGgenRoot.resolve("lib/test/ProcessMiningCapabilityTest.java");
        assertTrue(Files.exists(testFile),
                "lib/test/ProcessMiningCapabilityTest.java must exist");
        long testMethodCount = Files.lines(testFile)
                .map(String::trim)
                .filter("@Test"::equals)
                .count();
        assertEquals(ProcessMiningCapability.values().length, testMethodCount,
                "ProcessMiningCapabilityTest.java must have exactly one @Test per capability. "
                        + "Expected " + ProcessMiningCapability.values().length
                        + ", found " + testMethodCount);
    }

    @Test
    @DisplayName("Erlang module declares exports for all 53 capabilities")
    void erlangModuleExportsAllCapabilities() throws IOException {
        Path erlFile = erlangGenServerPath();
        String content = Files.readString(erlFile);
        List<String> missing = Arrays.stream(ProcessMiningCapability.values())
                .map(ProcessMiningCapability::erlFunction)
                .filter(fn -> !content.contains(fn + "/"))
                .toList();
        assertEquals(Collections.emptyList(), missing,
                "process_mining_bridge.erl must export all capability functions. Missing: " + missing);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tier 4: Golden file drift detection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("lib/java/ProcessMiningCapability.java matches committed golden file")
    void capabilityEnumMatchesGolden() throws IOException {
        assertLibMatchesGolden("java/ProcessMiningCapability.java");
    }

    @Test
    @DisplayName("lib/java/ProcessMiningBridge.java matches committed golden file")
    void bridgeClassMatchesGolden() throws IOException {
        assertLibMatchesGolden("java/ProcessMiningBridge.java");
    }

    @Test
    @DisplayName("lib/java/CapabilityRegistry.java matches committed golden file")
    void registryClassMatchesGolden() throws IOException {
        assertLibMatchesGolden("java/CapabilityRegistry.java");
    }

    @Test
    @DisplayName("lib/erlang/process_mining_bridge.erl matches committed golden file")
    void erlangGenServerMatchesGolden() throws IOException {
        assertLibMatchesGolden("erlang/process_mining_bridge.erl");
    }

    @Test
    @DisplayName("lib/test/ProcessMiningCapabilityTest.java matches committed golden file")
    void capabilityTestClassMatchesGolden() throws IOException {
        assertLibMatchesGolden("test/ProcessMiningCapabilityTest.java");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tier 5: Java API completeness via reflection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ProcessMiningBridge has exactly one public method per capability (camelCase)")
    void javaBridgeHasExactlyOnePublicMethodPerCapability() {
        Set<String> expectedMethods = Arrays.stream(ProcessMiningCapability.values())
                .map(cap -> toCamelCase(cap.erlFunction()))
                .collect(Collectors.toSet());
        Set<String> actualMethods = Arrays.stream(ProcessMiningBridge.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(Method::getName)
                .collect(Collectors.toSet());
        // Every capability must have a method
        List<String> missing = expectedMethods.stream()
                .filter(m -> !actualMethods.contains(m))
                .sorted().toList();
        assertEquals(Collections.emptyList(), missing,
                "ProcessMiningBridge is missing methods for these capabilities: " + missing);
        // No extra undocumented methods
        List<String> extra = actualMethods.stream()
                .filter(m -> !expectedMethods.contains(m))
                .sorted().toList();
        assertEquals(Collections.emptyList(), extra,
                "ProcessMiningBridge has unexpected public methods not in ProcessMiningCapability: "
                        + extra);
    }

    @Test
    @DisplayName("ProcessMiningBridge constructor takes exactly one ErlangNode parameter")
    void javaBridgeConstructorAcceptsErlangNode() throws NoSuchMethodException {
        var ctor = ProcessMiningBridge.class.getConstructor(ErlangNode.class);
        assertTrue(Modifier.isPublic(ctor.getModifiers()),
                "ProcessMiningBridge(ErlangNode) constructor must be public");
        assertEquals(1, ctor.getParameterCount(),
                "ProcessMiningBridge constructor must take exactly one parameter (ErlangNode)");
    }

    @Test
    @DisplayName("All ProcessMiningBridge public methods declare ErlangRpcException")
    void javaBridgeMethodsDeclareErlangRpcException() {
        Class<?> rpcExceptionClass;
        try {
            rpcExceptionClass = Class.forName(
                    "org.yawlfoundation.yawl.erlang.error.ErlangRpcException");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("ErlangRpcException class not found on classpath", e);
        }
        final Class<?> expected = rpcExceptionClass;
        List<String> missing = Arrays.stream(ProcessMiningBridge.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .filter(m -> Arrays.stream(m.getExceptionTypes())
                        .noneMatch(ex -> ex.isAssignableFrom(expected)
                                || expected.isAssignableFrom(ex)))
                .map(Method::getName)
                .sorted().toList();
        assertEquals(Collections.emptyList(), missing,
                "Every ProcessMiningBridge method must declare ErlangRpcException. Missing: " + missing);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Path erlangGenServerPath() {
        Path erlFile = pmBridgeGgenRoot.resolve("lib/erlang/process_mining_bridge.erl");
        assertTrue(Files.exists(erlFile),
                "lib/erlang/process_mining_bridge.erl must exist at " + erlFile);
        return erlFile;
    }

    private void assertLibMatchesGolden(String relPath) throws IOException {
        Path lib = pmBridgeGgenRoot.resolve("lib").resolve(relPath);
        Path golden = pmBridgeGgenRoot.resolve("golden").resolve(relPath);
        assertTrue(Files.exists(lib),
                "lib/" + relPath + " must exist — run: mvn generate-sources -pl yawl-erlang");
        assertTrue(Files.exists(golden),
                "golden/" + relPath + " must exist — commit after first successful gen-ttl run");
        assertEquals(Files.readString(golden), Files.readString(lib),
                "Artifact drift detected: lib/" + relPath + " differs from golden/" + relPath
                        + ".\n  Re-run: mvn generate-sources -pl yawl-erlang"
                        + "\n  Then:   cp lib/" + relPath + " golden/" + relPath
                        + "\n  Verify: node validate.mjs");
    }

    /**
     * Extracts the handle_call clause body for the given erlFunction name.
     * Reads from the first matching "handle_call({functionName" line until
     * the first "{reply," line (inclusive).
     */
    private List<String> extractHandleCallClause(Path erlFile, String erlFunction)
            throws IOException {
        List<String> clauseBody = new ArrayList<>();
        boolean inClause = false;
        for (String line : Files.readAllLines(erlFile)) {
            if (!inClause && line.startsWith("handle_call({" + erlFunction)) {
                inClause = true;
            }
            if (inClause) {
                clauseBody.add(line);
                if (line.trim().startsWith("{reply,")) {
                    break;
                }
            }
        }
        return clauseBody;
    }

    /**
     * Converts snake_case erlFunction name to camelCase Java method name.
     * Matches the to_camel_case.js filter used by gen-ttl:
     * "get_dotted_chart" → "getDottedChart"
     * "locel_add_e2o"     → "locelAddE2o"
     * "locel_new"         → "locelNew"
     */
    private static String toCamelCase(String snakeCase) {
        String[] parts = snakeCase.split("_");
        if (parts.length == 0) return snakeCase;
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                sb.append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }
}
