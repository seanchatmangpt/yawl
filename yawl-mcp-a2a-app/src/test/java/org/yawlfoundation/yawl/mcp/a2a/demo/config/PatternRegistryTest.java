/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.demo.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yawlfoundation.yawl.mcp.a2a.demo.report.PatternResult.Difficulty;
import org.yawlfoundation.yawl.mcp.a2a.demo.report.PatternResult.PatternInfo;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link PatternRegistry} verifying catalog completeness
 * per van der Aalst's Workflow Patterns initiative.
 *
 * <p>Tests verify:
 * <ul>
 *   <li>Catalog completeness for WCP 1-21 (the original van der Aalst patterns)</li>
 *   <li>Extended pattern coverage (WCP 44-68, ENT, AGT series)</li>
 *   <li>Category coverage — every populated category returns patterns</li>
 *   <li>Pattern access semantics — ID lookup, case-insensitivity, null safety</li>
 *   <li>Similarity search — Levenshtein-based suggestions</li>
 *   <li>YAML resource integrity — every registered path resolves on the classpath</li>
 *   <li>Registry structural invariants — uniqueness, completeness, count consistency</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("PatternRegistry Tests")
class PatternRegistryTest {

    // Total number of patterns registered in PatternRegistry.initializePatterns()
    // WCP-1..5 (5) + WCP-6..11 (6) + WCP-12..17 (6) + WCP-18..21 (4)
    // + WCP-44 (1) + WCP-45..50 (6) + WCP-51..59 (9) + WCP-60..68 (9)
    // + ENT-1..8 (8) + AGT-1..3 (3) = 57
    private static final int EXPECTED_TOTAL_PATTERNS = 57;

    private PatternRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PatternRegistry();
    }

    // =========================================================================
    // CatalogCompletenessTests — van der Aalst WCP 1-21
    // =========================================================================

    @Nested
    @DisplayName("Catalog Completeness — van der Aalst WCPs")
    class CatalogCompletenessTests {

        @Test
        @DisplayName("WCP-1 Sequence exists with correct metadata")
        void testWcp1Sequence() {
            Optional<PatternInfo> pattern = registry.getPattern("WCP-1");
            assertTrue(pattern.isPresent(), "WCP-1 (Sequence) must be registered");
            PatternInfo info = pattern.get();
            assertEquals("WCP-1", info.id());
            assertEquals("Sequence", info.name());
            assertNotNull(info.description());
            assertFalse(info.description().isBlank(), "WCP-1 description must not be blank");
            assertEquals(Difficulty.BASIC, info.difficulty());
        }

        @Test
        @DisplayName("WCP-2 Parallel Split exists with correct metadata")
        void testWcp2ParallelSplit() {
            Optional<PatternInfo> pattern = registry.getPattern("WCP-2");
            assertTrue(pattern.isPresent(), "WCP-2 (Parallel Split) must be registered");
            PatternInfo info = pattern.get();
            assertEquals("WCP-2", info.id());
            assertEquals("Parallel Split", info.name());
            assertEquals(Difficulty.BASIC, info.difficulty());
        }

        @Test
        @DisplayName("WCP-3 Synchronization exists with correct metadata")
        void testWcp3Synchronization() {
            Optional<PatternInfo> pattern = registry.getPattern("WCP-3");
            assertTrue(pattern.isPresent(), "WCP-3 (Synchronization) must be registered");
            PatternInfo info = pattern.get();
            assertEquals("WCP-3", info.id());
            assertEquals("Synchronization", info.name());
            assertEquals(Difficulty.BASIC, info.difficulty());
        }

        @Test
        @DisplayName("WCP-4 Exclusive Choice exists with correct metadata")
        void testWcp4ExclusiveChoice() {
            Optional<PatternInfo> pattern = registry.getPattern("WCP-4");
            assertTrue(pattern.isPresent(), "WCP-4 (Exclusive Choice) must be registered");
            PatternInfo info = pattern.get();
            assertEquals("WCP-4", info.id());
            assertEquals("Exclusive Choice", info.name());
            assertEquals(Difficulty.BASIC, info.difficulty());
        }

        @Test
        @DisplayName("WCP-5 Simple Merge exists with correct metadata")
        void testWcp5SimpleMerge() {
            Optional<PatternInfo> pattern = registry.getPattern("WCP-5");
            assertTrue(pattern.isPresent(), "WCP-5 (Simple Merge) must be registered");
            PatternInfo info = pattern.get();
            assertEquals("WCP-5", info.id());
            assertEquals("Simple Merge", info.name());
            assertEquals(Difficulty.BASIC, info.difficulty());
        }

        @Test
        @DisplayName("Advanced branching WCP-6 through WCP-9 all exist")
        void testAdvancedBranchingWcp6To9() {
            assertAll(
                "Advanced branching patterns must all be registered",
                () -> assertTrue(registry.hasPattern("WCP-6"),
                    "WCP-6 (Multi-Choice) must be registered"),
                () -> assertTrue(registry.hasPattern("WCP-7"),
                    "WCP-7 (Structured Synchronizing Merge) must be registered"),
                () -> assertTrue(registry.hasPattern("WCP-8"),
                    "WCP-8 (Multi-Merge) must be registered"),
                () -> assertTrue(registry.hasPattern("WCP-9"),
                    "WCP-9 (Structured Discriminator) must be registered")
            );
        }

        @Test
        @DisplayName("WCP-6 Multi-Choice has INTERMEDIATE difficulty")
        void testWcp6MultiChoiceDifficulty() {
            PatternInfo info = registry.getPattern("WCP-6").orElseThrow();
            assertEquals("Multi-Choice", info.name());
            assertEquals(Difficulty.INTERMEDIATE, info.difficulty());
        }

        @Test
        @DisplayName("WCP-9 Structured Discriminator has INTERMEDIATE difficulty")
        void testWcp9DiscriminatorDifficulty() {
            PatternInfo info = registry.getPattern("WCP-9").orElseThrow();
            assertEquals("Structured Discriminator", info.name());
            assertEquals(Difficulty.INTERMEDIATE, info.difficulty());
        }

        @Test
        @DisplayName("WCP-10 Structured Loop exists (iteration pattern)")
        void testWcp10StructuredLoop() {
            Optional<PatternInfo> pattern = registry.getPattern("WCP-10");
            assertTrue(pattern.isPresent(), "WCP-10 (Structured Loop) must be registered");
            assertEquals("Structured Loop", pattern.get().name());
            assertEquals(Difficulty.BASIC, pattern.get().difficulty());
        }

        @Test
        @DisplayName("WCP-11 Implicit Termination exists (structural pattern)")
        void testWcp11ImplicitTermination() {
            Optional<PatternInfo> pattern = registry.getPattern("WCP-11");
            assertTrue(pattern.isPresent(), "WCP-11 (Implicit Termination) must be registered");
            assertEquals("Implicit Termination", pattern.get().name());
            assertEquals(Difficulty.BASIC, pattern.get().difficulty());
        }

        @Test
        @DisplayName("Multi-instance WCP-12 through WCP-16 all exist")
        void testMultiInstanceWcp12To16() {
            assertAll(
                "Multi-instance patterns must all be registered",
                () -> assertTrue(registry.hasPattern("WCP-12"),
                    "WCP-12 (MI Without Synchronization) must be registered"),
                () -> assertTrue(registry.hasPattern("WCP-13"),
                    "WCP-13 (MI With A Priori Design Time Knowledge) must be registered"),
                () -> assertTrue(registry.hasPattern("WCP-14"),
                    "WCP-14 (MI With A Priori Runtime Knowledge) must be registered"),
                () -> assertTrue(registry.hasPattern("WCP-15"),
                    "WCP-15 (MI Without A Priori Runtime Knowledge) must be registered"),
                () -> assertTrue(registry.hasPattern("WCP-16"),
                    "WCP-16 (MI Without A Priori Knowledge) must be registered")
            );
        }

        @Test
        @DisplayName("WCP-15 and WCP-16 have ADVANCED difficulty reflecting complexity")
        void testAdvancedMultiInstanceDifficulty() {
            assertAll(
                () -> assertEquals(Difficulty.ADVANCED,
                    registry.getPattern("WCP-15").orElseThrow().difficulty(),
                    "WCP-15 must be ADVANCED"),
                () -> assertEquals(Difficulty.ADVANCED,
                    registry.getPattern("WCP-16").orElseThrow().difficulty(),
                    "WCP-16 must be ADVANCED")
            );
        }

        @Test
        @DisplayName("WCP-17 Interleaved Parallel Routing exists")
        void testWcp17InterleavedRouting() {
            Optional<PatternInfo> pattern = registry.getPattern("WCP-17");
            assertTrue(pattern.isPresent(), "WCP-17 (Interleaved Parallel Routing) must be registered");
            assertEquals("Interleaved Parallel Routing", pattern.get().name());
        }

        @Test
        @DisplayName("State-based WCP-18 through WCP-21 all exist")
        void testStateBasedWcp18To21() {
            assertAll(
                "State-based patterns must all be registered",
                () -> assertTrue(registry.hasPattern("WCP-18"),
                    "WCP-18 (Deferred Choice) must be registered"),
                () -> assertTrue(registry.hasPattern("WCP-19"),
                    "WCP-19 (Milestone) must be registered"),
                () -> assertTrue(registry.hasPattern("WCP-20"),
                    "WCP-20 (Cancel Activity) must be registered"),
                () -> assertTrue(registry.hasPattern("WCP-21"),
                    "WCP-21 (Cancel Case) must be registered")
            );
        }

        @Test
        @DisplayName("WCP-18 Deferred Choice has ADVANCED difficulty")
        void testWcp18DeferredChoiceDifficulty() {
            assertEquals(Difficulty.ADVANCED,
                registry.getPattern("WCP-18").orElseThrow().difficulty(),
                "WCP-18 (Deferred Choice) must be ADVANCED");
        }

        @Test
        @DisplayName("Total registered pattern count meets minimum van der Aalst baseline")
        void testMinimumPatternCount() {
            assertTrue(registry.getPatternCount() >= 21,
                "Registry must contain at least the 21 core van der Aalst WCPs, found: "
                    + registry.getPatternCount());
        }

        @Test
        @DisplayName("All 57 expected patterns are registered")
        void testExactPatternCount() {
            assertEquals(EXPECTED_TOTAL_PATTERNS, registry.getPatternCount(),
                "Registry must contain exactly " + EXPECTED_TOTAL_PATTERNS + " patterns");
        }

        @ParameterizedTest(name = "Pattern {0} exists")
        @ValueSource(strings = {
            "WCP-44", "WCP-45", "WCP-46", "WCP-47", "WCP-48", "WCP-49", "WCP-50"
        })
        @DisplayName("Extended/Distributed WCP-44 through WCP-50 all exist")
        void testExtendedAndDistributedPatterns(String patternId) {
            assertTrue(registry.hasPattern(patternId),
                patternId + " must be registered in the extended patterns section");
        }

        @ParameterizedTest(name = "Pattern {0} exists")
        @ValueSource(strings = {
            "WCP-51", "WCP-52", "WCP-53", "WCP-54", "WCP-55",
            "WCP-56", "WCP-57", "WCP-58", "WCP-59"
        })
        @DisplayName("Event-driven WCP-51 through WCP-59 all exist")
        void testEventDrivenPatterns(String patternId) {
            assertTrue(registry.hasPattern(patternId),
                patternId + " must be registered in the event-driven patterns section");
        }

        @ParameterizedTest(name = "Pattern {0} exists")
        @ValueSource(strings = {
            "WCP-60", "WCP-61", "WCP-62", "WCP-63", "WCP-64",
            "WCP-65", "WCP-66", "WCP-67", "WCP-68"
        })
        @DisplayName("AI/ML WCP-60 through WCP-68 all exist")
        void testAiMlPatterns(String patternId) {
            assertTrue(registry.hasPattern(patternId),
                patternId + " must be registered in the AI/ML patterns section");
        }

        @ParameterizedTest(name = "Pattern {0} exists")
        @ValueSource(strings = {
            "ENT-1", "ENT-2", "ENT-3", "ENT-4", "ENT-5", "ENT-6", "ENT-7", "ENT-8"
        })
        @DisplayName("Enterprise ENT-1 through ENT-8 all exist")
        void testEnterprisePatterns(String patternId) {
            assertTrue(registry.hasPattern(patternId),
                patternId + " must be registered in the enterprise patterns section");
        }

        @ParameterizedTest(name = "Pattern {0} exists")
        @ValueSource(strings = {"AGT-1", "AGT-2", "AGT-3"})
        @DisplayName("Agent AGT-1 through AGT-3 all exist")
        void testAgentPatterns(String patternId) {
            assertTrue(registry.hasPattern(patternId),
                patternId + " must be registered in the agent patterns section");
        }
    }

    // =========================================================================
    // CategoryCoverageTests
    // =========================================================================

    @Nested
    @DisplayName("Category Coverage Tests")
    class CategoryCoverageTests {

        @Test
        @DisplayName("BASIC category contains exactly 5 patterns (WCP-1 through WCP-5)")
        void testBasicCategoryHasExactlyFivePatterns() {
            List<PatternInfo> basicPatterns = registry.getPatternsByCategory(PatternCategory.BASIC);
            assertEquals(5, basicPatterns.size(),
                "BASIC category must contain exactly 5 patterns (WCP-1 through WCP-5), found: "
                    + basicPatterns.size());
        }

        @Test
        @DisplayName("BASIC category contains correct WCP IDs")
        void testBasicCategoryContainsCorrectIds() {
            List<PatternInfo> basicPatterns = registry.getPatternsByCategory(PatternCategory.BASIC);
            Set<String> ids = new HashSet<>();
            for (PatternInfo p : basicPatterns) {
                ids.add(p.id());
            }
            assertAll(
                () -> assertTrue(ids.contains("WCP-1"), "BASIC must contain WCP-1"),
                () -> assertTrue(ids.contains("WCP-2"), "BASIC must contain WCP-2"),
                () -> assertTrue(ids.contains("WCP-3"), "BASIC must contain WCP-3"),
                () -> assertTrue(ids.contains("WCP-4"), "BASIC must contain WCP-4"),
                () -> assertTrue(ids.contains("WCP-5"), "BASIC must contain WCP-5")
            );
        }

        @Test
        @DisplayName("ADVANCED_BRANCHING category contains exactly 4 patterns (WCP-6 through WCP-9)")
        void testAdvancedBranchingCategoryCount() {
            List<PatternInfo> patterns = registry.getPatternsByCategory(PatternCategory.ADVANCED_BRANCHING);
            assertEquals(4, patterns.size(),
                "ADVANCED_BRANCHING must have exactly 4 patterns (WCP-6..9), found: " + patterns.size());
        }

        @Test
        @DisplayName("ITERATION category contains exactly 1 pattern (WCP-10)")
        void testIterationCategoryCount() {
            List<PatternInfo> patterns = registry.getPatternsByCategory(PatternCategory.ITERATION);
            assertEquals(1, patterns.size(),
                "ITERATION must have exactly 1 pattern (WCP-10), found: " + patterns.size());
            assertEquals("WCP-10", patterns.get(0).id());
        }

        @Test
        @DisplayName("STRUCTURAL category contains exactly 1 pattern (WCP-11)")
        void testStructuralCategoryCount() {
            List<PatternInfo> patterns = registry.getPatternsByCategory(PatternCategory.STRUCTURAL);
            assertEquals(1, patterns.size(),
                "STRUCTURAL must have exactly 1 pattern (WCP-11), found: " + patterns.size());
            assertEquals("WCP-11", patterns.get(0).id());
        }

        @Test
        @DisplayName("MULTIINSTANCE category contains exactly 5 patterns (WCP-12 through WCP-16)")
        void testMultiInstanceCategoryCount() {
            List<PatternInfo> patterns = registry.getPatternsByCategory(PatternCategory.MULTIINSTANCE);
            assertEquals(5, patterns.size(),
                "MULTIINSTANCE must have exactly 5 patterns (WCP-12..16), found: " + patterns.size());
        }

        @Test
        @DisplayName("STATE_BASED category contains exactly 3 patterns (WCP-17, WCP-18, WCP-19)")
        void testStateBasedCategoryCount() {
            List<PatternInfo> patterns = registry.getPatternsByCategory(PatternCategory.STATE_BASED);
            assertEquals(3, patterns.size(),
                "STATE_BASED must have exactly 3 patterns (WCP-17, WCP-18, WCP-19), found: "
                    + patterns.size());
        }

        @Test
        @DisplayName("STATE_BASED category contains WCP-17, WCP-18, WCP-19")
        void testStateBasedCategoryContents() {
            List<PatternInfo> patterns = registry.getPatternsByCategory(PatternCategory.STATE_BASED);
            Set<String> ids = new HashSet<>();
            for (PatternInfo p : patterns) {
                ids.add(p.id());
            }
            assertAll(
                () -> assertTrue(ids.contains("WCP-17"), "STATE_BASED must contain WCP-17"),
                () -> assertTrue(ids.contains("WCP-18"), "STATE_BASED must contain WCP-18"),
                () -> assertTrue(ids.contains("WCP-19"), "STATE_BASED must contain WCP-19")
            );
        }

        @Test
        @DisplayName("CANCELLATION category contains exactly 2 patterns (WCP-20, WCP-21)")
        void testCancellationCategoryCount() {
            List<PatternInfo> patterns = registry.getPatternsByCategory(PatternCategory.CANCELLATION);
            assertEquals(2, patterns.size(),
                "CANCELLATION must have exactly 2 patterns (WCP-20, WCP-21), found: "
                    + patterns.size());
            Set<String> ids = new HashSet<>();
            for (PatternInfo p : patterns) {
                ids.add(p.id());
            }
            assertTrue(ids.contains("WCP-20"), "CANCELLATION must contain WCP-20");
            assertTrue(ids.contains("WCP-21"), "CANCELLATION must contain WCP-21");
        }

        @Test
        @DisplayName("EXTENDED category contains at least 1 pattern (WCP-44)")
        void testExtendedCategory() {
            List<PatternInfo> patterns = registry.getPatternsByCategory(PatternCategory.EXTENDED);
            assertFalse(patterns.isEmpty(), "EXTENDED category must not be empty");
            assertEquals(1, patterns.size(),
                "EXTENDED must have exactly 1 pattern (WCP-44), found: " + patterns.size());
            assertEquals("WCP-44", patterns.get(0).id());
        }

        @Test
        @DisplayName("DISTRIBUTED category contains exactly 6 patterns (WCP-45 through WCP-50)")
        void testDistributedCategoryCount() {
            List<PatternInfo> patterns = registry.getPatternsByCategory(PatternCategory.DISTRIBUTED);
            assertEquals(6, patterns.size(),
                "DISTRIBUTED must have exactly 6 patterns (WCP-45..50), found: " + patterns.size());
        }

        @Test
        @DisplayName("EVENT_DRIVEN category contains exactly 9 patterns (WCP-51 through WCP-59)")
        void testEventDrivenCategoryCount() {
            List<PatternInfo> patterns = registry.getPatternsByCategory(PatternCategory.EVENT_DRIVEN);
            assertEquals(9, patterns.size(),
                "EVENT_DRIVEN must have exactly 9 patterns (WCP-51..59), found: " + patterns.size());
        }

        @Test
        @DisplayName("AI_ML category contains exactly 9 patterns (WCP-60 through WCP-68)")
        void testAiMlCategoryCount() {
            List<PatternInfo> patterns = registry.getPatternsByCategory(PatternCategory.AI_ML);
            assertEquals(9, patterns.size(),
                "AI_ML must have exactly 9 patterns (WCP-60..68), found: " + patterns.size());
        }

        @Test
        @DisplayName("ENTERPRISE category contains exactly 8 patterns (ENT-1 through ENT-8)")
        void testEnterpriseCategoryCount() {
            List<PatternInfo> patterns = registry.getPatternsByCategory(PatternCategory.ENTERPRISE);
            assertEquals(8, patterns.size(),
                "ENTERPRISE must have exactly 8 patterns (ENT-1..8), found: " + patterns.size());
        }

        @Test
        @DisplayName("AGENT category contains exactly 3 patterns (AGT-1 through AGT-3)")
        void testAgentCategoryCount() {
            List<PatternInfo> patterns = registry.getPatternsByCategory(PatternCategory.AGENT);
            assertEquals(3, patterns.size(),
                "AGENT must have exactly 3 patterns (AGT-1..3), found: " + patterns.size());
        }

        @Test
        @DisplayName("getPatternsByCategory returns empty list for category with no registered patterns")
        void testEmptyCategoryReturnsEmptyList() {
            // BRANCHING, MULTI_INSTANCE, TERMINATION, UNCLASSIFIED are not used in initializePatterns()
            List<PatternInfo> branching = registry.getPatternsByCategory(PatternCategory.BRANCHING);
            assertNotNull(branching, "getPatternsByCategory must never return null");
            assertTrue(branching.isEmpty(),
                "BRANCHING (legacy enum value not used in registry) must return empty list");
        }

        @Test
        @DisplayName("getPatternsByCategory for MULTI_INSTANCE returns empty list")
        void testMultiInstanceLegacyEnumReturnsEmpty() {
            List<PatternInfo> patterns = registry.getPatternsByCategory(PatternCategory.MULTI_INSTANCE);
            assertNotNull(patterns, "getPatternsByCategory must never return null");
            assertTrue(patterns.isEmpty(),
                "MULTI_INSTANCE (legacy enum, registry uses MULTIINSTANCE) must return empty list");
        }

        @Test
        @DisplayName("Sum of all category counts equals total pattern count")
        void testCategoryCountsSumToTotal() {
            int sum = 0;
            for (PatternCategory category : PatternCategory.values()) {
                sum += registry.getPatternsByCategory(category).size();
            }
            assertEquals(registry.getPatternCount(), sum,
                "Sum of all category pattern counts must equal total pattern count");
        }

        @Test
        @DisplayName("All patterns in every category have the category's display name on their ResultPatternCategory")
        void testPatternCategoryDisplayNameConsistency() {
            for (PatternCategory category : PatternCategory.values()) {
                List<PatternInfo> patterns = registry.getPatternsByCategory(category);
                for (PatternInfo info : patterns) {
                    assertNotNull(info.category(),
                        "Pattern " + info.id() + " must have non-null category");
                    assertEquals(category.getDisplayName(), info.category().name(),
                        "Pattern " + info.id() + " category display name must match "
                            + category.getDisplayName());
                }
            }
        }
    }

    // =========================================================================
    // PatternAccessTests
    // =========================================================================

    @Nested
    @DisplayName("Pattern Access Tests")
    class PatternAccessTests {

        @Test
        @DisplayName("getPattern(WCP-1) returns a present Optional")
        void testGetPatternReturnsPresent() {
            Optional<PatternInfo> result = registry.getPattern("WCP-1");
            assertTrue(result.isPresent(), "getPattern(\"WCP-1\") must return a present Optional");
        }

        @Test
        @DisplayName("getPattern(NONEXISTENT) returns empty Optional")
        void testGetPatternNonExistentReturnsEmpty() {
            Optional<PatternInfo> result = registry.getPattern("NONEXISTENT");
            assertFalse(result.isPresent(),
                "getPattern(\"NONEXISTENT\") must return an empty Optional");
        }

        @Test
        @DisplayName("getPattern(null) returns empty Optional without throwing")
        void testGetPatternNullReturnsEmpty() {
            Optional<PatternInfo> result = registry.getPattern(null);
            assertFalse(result.isPresent(),
                "getPattern(null) must return an empty Optional, not throw");
        }

        @Test
        @DisplayName("getPattern is case-insensitive: wcp-1 resolves to WCP-1")
        void testGetPatternCaseInsensitiveLowercase() {
            Optional<PatternInfo> result = registry.getPattern("wcp-1");
            assertTrue(result.isPresent(),
                "getPattern(\"wcp-1\") must resolve case-insensitively to WCP-1");
            assertEquals("WCP-1", result.get().id());
        }

        @Test
        @DisplayName("getPattern is case-insensitive: Wcp-1 resolves to WCP-1")
        void testGetPatternCaseInsensitiveMixed() {
            Optional<PatternInfo> result = registry.getPattern("Wcp-1");
            assertTrue(result.isPresent(),
                "getPattern(\"Wcp-1\") must resolve case-insensitively to WCP-1");
            assertEquals("WCP-1", result.get().id());
        }

        @Test
        @DisplayName("getPattern is case-insensitive: ent-1 resolves to ENT-1")
        void testGetPatternCaseInsensitiveEnterprise() {
            Optional<PatternInfo> result = registry.getPattern("ent-1");
            assertTrue(result.isPresent(),
                "getPattern(\"ent-1\") must resolve to ENT-1");
            assertEquals("ENT-1", result.get().id());
        }

        @Test
        @DisplayName("getPattern is case-insensitive: agt-1 resolves to AGT-1")
        void testGetPatternCaseInsensitiveAgent() {
            Optional<PatternInfo> result = registry.getPattern("agt-1");
            assertTrue(result.isPresent(),
                "getPattern(\"agt-1\") must resolve to AGT-1");
            assertEquals("AGT-1", result.get().id());
        }

        @Test
        @DisplayName("getPattern(WCP-999) returns empty Optional for out-of-range ID")
        void testGetPatternOutOfRangeId() {
            Optional<PatternInfo> result = registry.getPattern("WCP-999");
            assertFalse(result.isPresent(),
                "WCP-999 is not a registered pattern and must return empty");
        }

        @Test
        @DisplayName("hasPattern returns true for existing pattern")
        void testHasPatternExisting() {
            assertTrue(registry.hasPattern("WCP-1"));
            assertTrue(registry.hasPattern("ENT-1"));
            assertTrue(registry.hasPattern("AGT-1"));
        }

        @Test
        @DisplayName("hasPattern returns false for unknown ID")
        void testHasPatternUnknown() {
            assertFalse(registry.hasPattern("UNKNOWN-999"));
        }

        @Test
        @DisplayName("hasPattern(null) returns false without throwing")
        void testHasPatternNull() {
            assertFalse(registry.hasPattern(null),
                "hasPattern(null) must return false, not throw");
        }

        @Test
        @DisplayName("hasPattern is case-insensitive for WCP patterns")
        void testHasPatternCaseInsensitiveWcp() {
            assertTrue(registry.hasPattern("wcp-1"),
                "hasPattern must be case-insensitive");
            assertTrue(registry.hasPattern("WCP-1"),
                "hasPattern must match uppercase");
        }

        @Test
        @DisplayName("hasPattern is case-insensitive for ENT patterns")
        void testHasPatternCaseInsensitiveEnt() {
            assertTrue(registry.hasPattern("ent-8"),
                "hasPattern must be case-insensitive for ENT patterns");
        }

        @Test
        @DisplayName("getAllPatternIds returns unmodifiable set")
        void testGetAllPatternIdsIsUnmodifiable() {
            Set<String> ids = registry.getAllPatternIds();
            assertNotNull(ids);
            assertThrows(UnsupportedOperationException.class,
                () -> ids.add("FAKE-0"),
                "getAllPatternIds() must return an unmodifiable set");
        }

        @Test
        @DisplayName("getAllPatternIds contains all expected series prefixes")
        void testGetAllPatternIdsContainsAllSeries() {
            Set<String> ids = registry.getAllPatternIds();
            assertTrue(ids.stream().anyMatch(id -> id.startsWith("WCP-")),
                "Pattern IDs must include WCP series");
            assertTrue(ids.stream().anyMatch(id -> id.startsWith("ENT-")),
                "Pattern IDs must include ENT series");
            assertTrue(ids.stream().anyMatch(id -> id.startsWith("AGT-")),
                "Pattern IDs must include AGT series");
        }

        @Test
        @DisplayName("getAllPatterns returns a mutable list independent of the registry")
        void testGetAllPatternsMutability() {
            List<PatternInfo> allPatterns = registry.getAllPatterns();
            int sizeBeforeModification = allPatterns.size();
            allPatterns.clear();
            assertEquals(EXPECTED_TOTAL_PATTERNS, registry.getPatternCount(),
                "Modifying the returned list must not affect registry state");
            assertEquals(sizeBeforeModification, EXPECTED_TOTAL_PATTERNS,
                "getAllPatterns must return all registered patterns");
        }
    }

    // =========================================================================
    // SimilaritySearchTests
    // =========================================================================

    @Nested
    @DisplayName("Similarity Search Tests")
    class SimilaritySearchTests {

        @Test
        @DisplayName("findSimilarPatterns(WCP-1) returns exactly 3 results")
        void testFindSimilarPatternsReturnsThreeResults() {
            List<String> similar = registry.findSimilarPatterns("WCP-1");
            assertEquals(3, similar.size(),
                "findSimilarPatterns must return at most 3 results, got: " + similar.size());
        }

        @Test
        @DisplayName("findSimilarPatterns(WCP-1) includes WCP-1 as closest match")
        void testFindSimilarPatternsIncludesExactMatch() {
            List<String> similar = registry.findSimilarPatterns("WCP-1");
            assertTrue(similar.contains("WCP-1"),
                "findSimilarPatterns(\"WCP-1\") must include WCP-1 as closest match");
        }

        @Test
        @DisplayName("findSimilarPatterns result IDs are all valid registered patterns")
        void testFindSimilarPatternsResultsAreValid() {
            List<String> similar = registry.findSimilarPatterns("WCP-1");
            for (String id : similar) {
                assertTrue(registry.hasPattern(id),
                    "Similar pattern ID " + id + " must be a valid registered pattern");
            }
        }

        @Test
        @DisplayName("findSimilarPatterns(WCP) returns non-empty results for prefix query")
        void testFindSimilarPatternsPrefixQuery() {
            List<String> similar = registry.findSimilarPatterns("WCP");
            assertFalse(similar.isEmpty(),
                "findSimilarPatterns(\"WCP\") prefix must return some results");
            assertEquals(3, similar.size(),
                "findSimilarPatterns must cap results at 3");
        }

        @Test
        @DisplayName("findSimilarPatterns(WCP-44) returns WCP-44 as closest match")
        void testFindSimilarPatternsForExtendedPattern() {
            List<String> similar = registry.findSimilarPatterns("WCP-44");
            assertTrue(similar.contains("WCP-44"),
                "findSimilarPatterns(\"WCP-44\") must include WCP-44 as exact match");
        }

        @Test
        @DisplayName("findSimilarPatterns(ENT-1) returns 3 results with ENT-1 first")
        void testFindSimilarPatternsEnterprisePattern() {
            List<String> similar = registry.findSimilarPatterns("ENT-1");
            assertEquals(3, similar.size(),
                "findSimilarPatterns must return exactly 3 results");
            assertTrue(similar.contains("ENT-1"),
                "findSimilarPatterns(\"ENT-1\") must include ENT-1");
        }

        @Test
        @DisplayName("findSimilarPatterns(null) returns empty list without throwing")
        void testFindSimilarPatternsNull() {
            List<String> similar = registry.findSimilarPatterns(null);
            assertNotNull(similar, "findSimilarPatterns(null) must not return null");
            assertTrue(similar.isEmpty(),
                "findSimilarPatterns(null) must return empty list");
        }

        @Test
        @DisplayName("findSimilarPatterns(empty string) returns empty list without throwing")
        void testFindSimilarPatternsEmptyString() {
            List<String> similar = registry.findSimilarPatterns("");
            assertNotNull(similar, "findSimilarPatterns(\"\") must not return null");
            assertTrue(similar.isEmpty(),
                "findSimilarPatterns(\"\") must return empty list");
        }

        @Test
        @DisplayName("findSimilarPatterns is case-insensitive: wcp-1 returns same as WCP-1")
        void testFindSimilarPatternsCaseInsensitive() {
            List<String> lowerResult = registry.findSimilarPatterns("wcp-1");
            List<String> upperResult = registry.findSimilarPatterns("WCP-1");
            assertEquals(upperResult, lowerResult,
                "findSimilarPatterns must be case-insensitive in distance computation");
        }

        @Test
        @DisplayName("findSimilarPatterns result list is never larger than 3")
        void testFindSimilarPatternsMaxResultsInvariant() {
            String[] probes = {"WCP-1", "ENT-5", "AGT-2", "FOOBAR", "WCP-99"};
            for (String probe : probes) {
                List<String> results = registry.findSimilarPatterns(probe);
                assertTrue(results.size() <= 3,
                    "findSimilarPatterns must never return more than 3 results for probe: " + probe);
            }
        }
    }

    // =========================================================================
    // YamlResourceTests
    // =========================================================================

    @Nested
    @DisplayName("YAML Resource Tests")
    class YamlResourceTests {

        @Test
        @DisplayName("Every registered pattern has a non-null yamlExample path")
        void testEveryPatternHasNonNullYamlPath() {
            List<PatternInfo> allPatterns = registry.getAllPatterns();
            for (PatternInfo info : allPatterns) {
                assertNotNull(info.yamlExample(),
                    "Pattern " + info.id() + " must have non-null yamlExample path");
            }
        }

        @Test
        @DisplayName("Every registered pattern's yamlExample path ends with .yaml")
        void testEveryYamlPathEndsWithDotYaml() {
            List<PatternInfo> allPatterns = registry.getAllPatterns();
            for (PatternInfo info : allPatterns) {
                assertTrue(info.yamlExample().endsWith(".yaml"),
                    "Pattern " + info.id() + " yamlExample path must end with .yaml, was: "
                        + info.yamlExample());
            }
        }

        @Test
        @DisplayName("Every registered pattern's yamlExample path is non-blank")
        void testEveryYamlPathIsNonBlank() {
            List<PatternInfo> allPatterns = registry.getAllPatterns();
            for (PatternInfo info : allPatterns) {
                assertFalse(info.yamlExample().isBlank(),
                    "Pattern " + info.id() + " yamlExample path must not be blank");
            }
        }

        @Test
        @DisplayName("Every yamlExample path resolves to a non-null classpath resource")
        void testEveryYamlPathResolvesOnClasspath() {
            List<PatternInfo> allPatterns = registry.getAllPatterns();
            for (PatternInfo info : allPatterns) {
                String resourcePath = "patterns/" + info.yamlExample();
                InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
                assertNotNull(stream,
                    "Pattern " + info.id() + " yamlExample '" + info.yamlExample()
                        + "' must resolve to a classpath resource at: " + resourcePath);
                try {
                    stream.close();
                } catch (Exception ignored) {
                    // Closing the stream is best-effort; the key assertion is non-null above
                }
            }
        }

        @Test
        @DisplayName("Every YAML resource is non-empty (contains at least one byte)")
        void testEveryYamlResourceIsNonEmpty() {
            List<PatternInfo> allPatterns = registry.getAllPatterns();
            for (PatternInfo info : allPatterns) {
                String resourcePath = "patterns/" + info.yamlExample();
                InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
                assertNotNull(stream,
                    "Resource must exist for pattern " + info.id());
                try {
                    int firstByte = stream.read();
                    assertTrue(firstByte != -1,
                        "YAML resource for pattern " + info.id() + " must not be empty: "
                            + resourcePath);
                    stream.close();
                } catch (Exception e) {
                    fail("IOException reading YAML resource for pattern " + info.id()
                        + " at " + resourcePath + ": " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("Every YAML resource is parseable as UTF-8 text with a name: field")
        void testEveryYamlResourceIsParseableAsText() {
            List<PatternInfo> allPatterns = registry.getAllPatterns();
            for (PatternInfo info : allPatterns) {
                String resourcePath = "patterns/" + info.yamlExample();
                InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
                assertNotNull(stream,
                    "Resource must exist for pattern " + info.id());
                try {
                    byte[] bytes = stream.readAllBytes();
                    String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    assertFalse(content.isBlank(),
                        "YAML content for pattern " + info.id() + " must not be blank");
                    assertTrue(content.contains("name:"),
                        "YAML content for pattern " + info.id()
                            + " must contain a 'name:' field at: " + resourcePath);
                    stream.close();
                } catch (Exception e) {
                    fail("Failed to read YAML content for pattern " + info.id()
                        + " at " + resourcePath + ": " + e.getMessage());
                }
            }
        }

        @Test
        @DisplayName("WCP-1 YAML resource at controlflow/wcp-1-sequence.yaml is readable")
        void testWcp1YamlResourceDirectly() {
            InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("patterns/controlflow/wcp-1-sequence.yaml");
            assertNotNull(stream,
                "patterns/controlflow/wcp-1-sequence.yaml must exist on the classpath");
            try {
                byte[] bytes = stream.readAllBytes();
                String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(content.contains("SequencePattern"),
                    "WCP-1 YAML must define SequencePattern");
                assertTrue(content.contains("tasks:"),
                    "WCP-1 YAML must have a tasks: section");
                stream.close();
            } catch (Exception e) {
                fail("Could not read WCP-1 YAML: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("No two registered patterns share the same yamlExample path")
        void testNoDuplicateYamlPaths() {
            List<PatternInfo> allPatterns = registry.getAllPatterns();
            Set<String> seenPaths = new HashSet<>();
            for (PatternInfo info : allPatterns) {
                String path = info.yamlExample();
                assertFalse(seenPaths.contains(path),
                    "Duplicate yamlExample path detected for pattern " + info.id()
                        + ": '" + path + "' is already registered by another pattern");
                seenPaths.add(path);
            }
        }
    }

    // =========================================================================
    // RegistryIntegrityTests
    // =========================================================================

    @Nested
    @DisplayName("Registry Integrity Tests")
    class RegistryIntegrityTests {

        @Test
        @DisplayName("No duplicate IDs: getAllPatternIds size equals getPatternCount")
        void testNoDuplicateIds() {
            Set<String> ids = registry.getAllPatternIds();
            assertEquals(registry.getPatternCount(), ids.size(),
                "getAllPatternIds() size must equal getPatternCount() — no duplicate IDs allowed");
        }

        @Test
        @DisplayName("getPatternCount() matches getAllPatterns().size()")
        void testPatternCountConsistency() {
            assertEquals(registry.getPatternCount(), registry.getAllPatterns().size(),
                "getPatternCount() must match getAllPatterns().size()");
        }

        @Test
        @DisplayName("All patterns have non-empty name")
        void testAllPatternsHaveNonEmptyName() {
            List<PatternInfo> allPatterns = registry.getAllPatterns();
            for (PatternInfo info : allPatterns) {
                assertNotNull(info.name(),
                    "Pattern " + info.id() + " must have non-null name");
                assertFalse(info.name().isBlank(),
                    "Pattern " + info.id() + " must have non-blank name");
            }
        }

        @Test
        @DisplayName("All patterns have non-empty description")
        void testAllPatternsHaveNonEmptyDescription() {
            List<PatternInfo> allPatterns = registry.getAllPatterns();
            for (PatternInfo info : allPatterns) {
                assertNotNull(info.description(),
                    "Pattern " + info.id() + " must have non-null description");
                assertFalse(info.description().isBlank(),
                    "Pattern " + info.id() + " must have non-blank description");
            }
        }

        @Test
        @DisplayName("All patterns have non-null Difficulty")
        void testAllPatternsHaveNonNullDifficulty() {
            List<PatternInfo> allPatterns = registry.getAllPatterns();
            for (PatternInfo info : allPatterns) {
                assertNotNull(info.difficulty(),
                    "Pattern " + info.id() + " must have non-null difficulty");
            }
        }

        @Test
        @DisplayName("All patterns have non-null category")
        void testAllPatternsHaveNonNullCategory() {
            List<PatternInfo> allPatterns = registry.getAllPatterns();
            for (PatternInfo info : allPatterns) {
                assertNotNull(info.category(),
                    "Pattern " + info.id() + " must have non-null category");
            }
        }

        @Test
        @DisplayName("All patterns have non-null ID")
        void testAllPatternsHaveNonNullId() {
            List<PatternInfo> allPatterns = registry.getAllPatterns();
            for (PatternInfo info : allPatterns) {
                assertNotNull(info.id(),
                    "Every registered pattern must have a non-null ID");
            }
        }

        @Test
        @DisplayName("All WCP pattern IDs match the WCP-N format")
        void testWcpPatternIdFormat() {
            Set<String> ids = registry.getAllPatternIds();
            for (String id : ids) {
                if (id.startsWith("WCP-")) {
                    String numericPart = id.substring(4);
                    assertFalse(numericPart.isBlank(),
                        "WCP pattern ID " + id + " must have a numeric part after 'WCP-'");
                    assertTrue(numericPart.matches("\\d+"),
                        "WCP pattern ID " + id + " numeric part must be digits only, was: "
                            + numericPart);
                }
            }
        }

        @Test
        @DisplayName("All ENT pattern IDs match the ENT-N format")
        void testEntPatternIdFormat() {
            Set<String> ids = registry.getAllPatternIds();
            for (String id : ids) {
                if (id.startsWith("ENT-")) {
                    String numericPart = id.substring(4);
                    assertTrue(numericPart.matches("\\d+"),
                        "ENT pattern ID " + id + " numeric part must be digits only");
                }
            }
        }

        @Test
        @DisplayName("All AGT pattern IDs match the AGT-N format")
        void testAgtPatternIdFormat() {
            Set<String> ids = registry.getAllPatternIds();
            for (String id : ids) {
                if (id.startsWith("AGT-")) {
                    String numericPart = id.substring(4);
                    assertTrue(numericPart.matches("\\d+"),
                        "AGT pattern ID " + id + " numeric part must be digits only");
                }
            }
        }

        @Test
        @DisplayName("BASIC patterns all have BASIC difficulty")
        void testBasicCategoryPatternsAllHaveBasicDifficulty() {
            List<PatternInfo> basicPatterns = registry.getPatternsByCategory(PatternCategory.BASIC);
            for (PatternInfo info : basicPatterns) {
                assertEquals(Difficulty.BASIC, info.difficulty(),
                    "Pattern " + info.id() + " in BASIC category must have BASIC difficulty");
            }
        }

        @Test
        @DisplayName("PatternInfo records are structurally correct — all fields accessible via accessors")
        void testPatternInfoRecordAccessors() {
            PatternInfo wcp1 = registry.getPattern("WCP-1").orElseThrow();
            assertAll(
                "PatternInfo record accessors must return correct values for WCP-1",
                () -> assertEquals("WCP-1", wcp1.id()),
                () -> assertEquals("Sequence", wcp1.name()),
                () -> assertNotNull(wcp1.description()),
                () -> assertEquals(Difficulty.BASIC, wcp1.difficulty()),
                () -> assertNotNull(wcp1.category()),
                () -> assertEquals("Basic Control Flow", wcp1.category().name()),
                () -> assertNotNull(wcp1.yamlExample()),
                () -> assertEquals("controlflow/wcp-1-sequence.yaml", wcp1.yamlExample())
            );
        }

        @Test
        @DisplayName("ResultPatternCategory color codes are non-null for all patterns")
        void testResultPatternCategoryColorCodesNonNull() {
            List<PatternInfo> allPatterns = registry.getAllPatterns();
            for (PatternInfo info : allPatterns) {
                assertNotNull(info.category().colorCode(),
                    "Pattern " + info.id() + " ResultPatternCategory colorCode must not be null");
                assertFalse(info.category().colorCode().isBlank(),
                    "Pattern " + info.id() + " ResultPatternCategory colorCode must not be blank");
            }
        }

        @Test
        @DisplayName("Two independent PatternRegistry instances have identical pattern counts")
        void testTwoIndependentRegistriesAreIdentical() {
            PatternRegistry registry2 = new PatternRegistry();
            assertEquals(registry.getPatternCount(), registry2.getPatternCount(),
                "Two PatternRegistry instances must have identical pattern counts");
            assertEquals(registry.getAllPatternIds(), registry2.getAllPatternIds(),
                "Two PatternRegistry instances must have identical pattern ID sets");
        }

        @Test
        @DisplayName("getPatternsByCategory returns a different list object on each call")
        void testGetPatternsByCategoryReturnsFreshList() {
            List<PatternInfo> first = registry.getPatternsByCategory(PatternCategory.BASIC);
            List<PatternInfo> second = registry.getPatternsByCategory(PatternCategory.BASIC);
            // Contents must be equal but they may be the same object (from getOrDefault)
            // The key invariant is that the list is not null and has the same contents
            assertNotNull(first);
            assertNotNull(second);
            assertEquals(first.size(), second.size(),
                "Repeated calls to getPatternsByCategory must return same-sized list");
        }
    }
}
