package org.yawlfoundation.yawl.stateless.elements.marking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for stateless marking classes.
 * Tests real YAWL marking semantics: YMarking, YIdentifier, YIdentifierBag.
 * No mocks — uses actual Petri net marking semantics.
 *
 * @author Claude Code / GODSPEED Protocol
 * @since 6.0.0
 */
@DisplayName("YStateless Marking Semantics")
class TestYStatelessMarking {

    @Nested
    @DisplayName("YIdentifier lifecycle")
    class TestYIdentifier {
        private YIdentifier id1;
        private YIdentifier id2;

        @BeforeEach
        void setUp() {
            id1 = new YIdentifier("case-001");
            id2 = new YIdentifier("case-002");
        }

        @Test
        @DisplayName("Create identifier with unique case ID")
        void testIdentifierCreation() {
            assertNotNull(id1);
            assertEquals("case-001", id1.toString());
            assertNotEquals(id1, id2);
        }

        @Test
        @DisplayName("Identifier equality by value")
        void testIdentifierEquality() {
            YIdentifier id1Copy = new YIdentifier("case-001");
            assertEquals(id1, id1Copy);
            assertEquals(id1.hashCode(), id1Copy.hashCode());
        }

        @Test
        @DisplayName("Identifier uniqueness in set")
        void testIdentifierSetBehavior() {
            Set<YIdentifier> ids = new java.util.HashSet<>();
            ids.add(id1);
            ids.add(new YIdentifier("case-001")); // Duplicate
            assertEquals(1, ids.size(), "Duplicate identifiers should merge in set");
        }
    }

    @Nested
    @DisplayName("YIdentifierBag collection semantics")
    class TestYIdentifierBag {
        private YIdentifierBag bag;
        private YIdentifier id1;
        private YIdentifier id2;

        @BeforeEach
        void setUp() {
            bag = new YIdentifierBag();
            id1 = new YIdentifier("case-001");
            id2 = new YIdentifier("case-002");
        }

        @Test
        @DisplayName("Empty bag initialization")
        void testEmptyBag() {
            assertTrue(bag.isEmpty(), "Bag should start empty");
            assertEquals(0, bag.size());
        }

        @Test
        @DisplayName("Add and retrieve identifiers")
        void testAddAndRetrieve() {
            bag.add(id1);
            bag.add(id2);
            assertEquals(2, bag.size());
            assertTrue(bag.contains(id1));
            assertTrue(bag.contains(id2));
        }

        @Test
        @DisplayName("Remove identifier from bag")
        void testRemove() {
            bag.add(id1);
            bag.add(id2);
            assertTrue(bag.remove(id1));
            assertEquals(1, bag.size());
            assertFalse(bag.contains(id1));
            assertTrue(bag.contains(id2));
        }

        @Test
        @DisplayName("Bag of multiple instances of same identifier")
        void testMultipleInstances() {
            bag.add(id1);
            bag.add(id1);
            bag.add(id1);
            assertEquals(3, bag.size(), "Bag should allow multiple instances of same ID");
        }

        @Test
        @DisplayName("Bag iterator and iteration")
        void testIteration() {
            bag.add(id1);
            bag.add(id2);

            Set<YIdentifier> iterated = new java.util.HashSet<>();
            for (YIdentifier id : bag) {
                iterated.add(id);
            }
            assertEquals(2, iterated.size());
        }
    }

    @Nested
    @DisplayName("YMarking state transitions")
    class TestYMarking {
        private YMarking marking;
        private YIdentifier id1;
        private YIdentifier id2;

        @BeforeEach
        void setUp() {
            marking = new YMarking();
            id1 = new YIdentifier("case-001");
            id2 = new YIdentifier("case-002");
        }

        @Test
        @DisplayName("Create empty marking")
        void testEmptyMarking() {
            assertTrue(marking.isEmpty(), "New marking should be empty");
            assertEquals(0, marking.getLocations().size());
        }

        @Test
        @DisplayName("Add token to marking location")
        void testAddToken() {
            marking.addToken("start", id1);
            assertFalse(marking.isEmpty());
            assertTrue(marking.hasTokensAtLocation("start"));
        }

        @Test
        @DisplayName("Multiple tokens at location")
        void testMultipleTokens() {
            marking.addToken("start", id1);
            marking.addToken("start", id2);
            YIdentifierBag tokens = marking.getTokensAtLocation("start");
            assertEquals(2, tokens.size());
        }

        @Test
        @DisplayName("Remove token from marking")
        void testRemoveToken() {
            marking.addToken("start", id1);
            marking.addToken("start", id2);
            assertTrue(marking.removeToken("start", id1));
            YIdentifierBag remaining = marking.getTokensAtLocation("start");
            assertEquals(1, remaining.size());
            assertTrue(remaining.contains(id2));
        }

        @Test
        @DisplayName("Token flow through net locations")
        void testTokenFlow() {
            // Simulate token moving from input to processing to output
            marking.addToken("input", id1);
            assertTrue(marking.hasTokensAtLocation("input"));

            // Move token: input → processing
            assertTrue(marking.removeToken("input", id1));
            marking.addToken("processing", id1);
            assertFalse(marking.hasTokensAtLocation("input"));
            assertTrue(marking.hasTokensAtLocation("processing"));

            // Move token: processing → output
            assertTrue(marking.removeToken("processing", id1));
            marking.addToken("output", id1);
            assertTrue(marking.hasTokensAtLocation("output"));
        }

        @Test
        @DisplayName("Clear marking")
        void testClearMarking() {
            marking.addToken("start", id1);
            marking.addToken("middle", id2);
            marking.clear();
            assertTrue(marking.isEmpty());
            assertFalse(marking.hasTokensAtLocation("start"));
        }
    }

    @Nested
    @DisplayName("YSetOfMarkings collection semantics")
    class TestYSetOfMarkings {
        private YSetOfMarkings set;
        private YMarking marking1;
        private YMarking marking2;
        private YIdentifier id1;

        @BeforeEach
        void setUp() {
            set = new YSetOfMarkings();
            marking1 = new YMarking();
            marking2 = new YMarking();
            id1 = new YIdentifier("case-001");
        }

        @Test
        @DisplayName("Create empty set of markings")
        void testEmptySet() {
            assertTrue(set.isEmpty());
            assertEquals(0, set.getMarkings().size());
        }

        @Test
        @DisplayName("Add marking to set")
        void testAddMarking() {
            marking1.addToken("start", id1);
            set.add(marking1);
            assertEquals(1, set.size());
        }

        @Test
        @DisplayName("Markings are independent states")
        void testMarkingIndependence() {
            marking1.addToken("start", id1);
            marking2.addToken("middle", id1);

            set.add(marking1);
            set.add(marking2);

            assertEquals(2, set.size(), "Both markings should coexist");
            assertFalse(marking1.hasTokensAtLocation("middle"));
            assertFalse(marking2.hasTokensAtLocation("start"));
        }
    }

    @Nested
    @DisplayName("YInternalCondition marking semantics")
    class TestYInternalCondition {
        private YInternalCondition condition;
        private YIdentifier id1;

        @BeforeEach
        void setUp() {
            condition = new YInternalCondition("condition-001");
            id1 = new YIdentifier("case-001");
        }

        @Test
        @DisplayName("Create internal condition")
        void testConditionCreation() {
            assertNotNull(condition);
            assertEquals("condition-001", condition.getID());
        }

        @Test
        @DisplayName("Internal condition holds tokens")
        void testConditionMarking() {
            condition.add(id1);
            assertTrue(condition.contains(id1));
            assertEquals(1, condition.size());
        }

        @Test
        @DisplayName("Remove token from internal condition")
        void testRemoveFromCondition() {
            condition.add(id1);
            assertTrue(condition.remove(id1));
            assertFalse(condition.contains(id1));
            assertEquals(0, condition.size());
        }
    }

    /**
     * Test coverage summary:
     * - YIdentifier: creation, equality, hashing, set behavior
     * - YIdentifierBag: add, remove, contains, iteration, size
     * - YMarking: token addition/removal, location management, state transitions
     * - YSetOfMarkings: collection semantics, state independence
     * - YInternalCondition: creation, token holding, removal
     *
     * All tests use real YAWL marking semantics. No mocks.
     * Target: 80%+ line coverage on marking package.
     *
     * @since 6.0.0 GODSPEED Protocol
     */
}
