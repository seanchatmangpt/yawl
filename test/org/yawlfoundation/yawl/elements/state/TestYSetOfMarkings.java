package org.yawlfoundation.yawl.elements.state;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YNetElement;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.engine.core.marking.YCoreMarking;

/**
 * Chicago TDD tests for YSetOfMarkings.
 * Tests set operations and comparison methods.
 * Uses real marking objects with real YAWL elements.
 *
 * @author Lachlan Aldred (original)
 * @author YAWL Test Suite (expanded)
 */
@DisplayName("YSetOfMarkings Tests")
@Tag("unit")
class TestYSetOfMarkings {

    private YSetOfMarkings markingSet1;
    private YSetOfMarkings markingSet2;
    private YNet net;
    private YSpecification spec;
    private YCondition a, b, c, d, e, f;

    @BeforeEach
    void setUp() {
        spec = new YSpecification("http://test.com/test-spec");
        net = new YNet("testNet", spec);

        a = new YCondition("a", net);
        b = new YCondition("b", net);
        c = new YCondition("c", net);
        d = new YCondition("d", net);
        e = new YCondition("e", net);
        f = new YCondition("f", net);

        List<YNetElement> locs1 = new ArrayList<>();
        List<YNetElement> locs2 = new ArrayList<>();
        List<YNetElement> locs3 = new ArrayList<>();
        List<YNetElement> locs4 = new ArrayList<>();

        markingSet1 = new YSetOfMarkings();
        locs1.add(a);
        locs1.add(b);
        markingSet1.addMarking(new YMarking(locs1));
        locs2.addAll(locs1);
        locs2.add(c);
        locs2.add(d);
        markingSet1.addMarking(new YMarking(locs2));

        markingSet2 = new YSetOfMarkings();
        locs3.add(a);
        locs3.add(b);
        markingSet2.addMarking(new YMarking(locs3));
        locs4.addAll(locs3);
        locs4.add(e);
        locs4.add(f);
        markingSet2.addMarking(new YMarking(locs4));
    }

    @Test
    @DisplayName("Contains equivalent marking should return true when sets share markings")
    void testContainsEquivalentTo() {
        assertTrue(markingSet1.containsEquivalentMarkingTo(markingSet2));
    }

    @Nested
    @DisplayName("Set Creation Tests")
    class SetCreationTests {

        @Test
        @DisplayName("Set of markings should be empty initially")
        void setOfMarkingsIsEmptyInitially() {
            YSetOfMarkings emptySet = new YSetOfMarkings();
            assertEquals(0, emptySet.size());
            assertTrue(emptySet.getMarkings().isEmpty());
        }

        @Test
        @DisplayName("Set of markings should have correct size after adding")
        void setOfMarkingsHasCorrectSizeAfterAdd() {
            assertEquals(2, markingSet1.size());
        }

        @Test
        @DisplayName("Get markings should return non-null set")
        void getMarkingsReturnsNonNull() {
            assertNotNull(markingSet1.getMarkings());
        }
    }

    @Nested
    @DisplayName("Add Marking Operations")
    class AddMarkingTests {

        @Test
        @DisplayName("Add marking should increase size")
        void addMarkingIncreasesSize() {
            YSetOfMarkings newSet = new YSetOfMarkings();
            List<YNetElement> locs = new ArrayList<>();
            locs.add(new YCondition("new1", net));
            newSet.addMarking(new YMarking(locs));
            assertEquals(1, newSet.size());
        }

        @Test
        @DisplayName("Add duplicate marking should not increase size")
        void addDuplicateMarkingDoesNotIncreaseSize() {
            YSetOfMarkings newSet = new YSetOfMarkings();
            List<YNetElement> locs = new ArrayList<>();
            locs.add(new YCondition("dup1", net));

            YMarking marking1 = new YMarking(locs);
            YMarking marking2 = new YMarking(new ArrayList<>(locs));

            newSet.addMarking(marking1);
            newSet.addMarking(marking2);

            assertEquals(1, newSet.size());
        }

        @Test
        @DisplayName("Add all markings from another set")
        void addAllMarkings() {
            YSetOfMarkings targetSet = new YSetOfMarkings();
            targetSet.addAll(markingSet1);
            assertEquals(2, targetSet.size());
        }
    }

    @Nested
    @DisplayName("Remove Marking Operations")
    class RemoveMarkingTests {

        @Test
        @DisplayName("Remove all should clear the set")
        void removeAllClearsSet() {
            YSetOfMarkings newSet = new YSetOfMarkings();
            List<YNetElement> locs = new ArrayList<>();
            locs.add(new YCondition("rm1", net));
            newSet.addMarking(new YMarking(locs));
            assertEquals(1, newSet.size());

            newSet.removeAll();
            assertEquals(0, newSet.size());
        }

        @Test
        @DisplayName("Remove a marking should return the removed marking")
        void removeAMarkingReturnsMarking() {
            YCoreMarking removed = markingSet1.removeAMarking();
            assertNotNull(removed);
            assertEquals(1, markingSet1.size());
        }

        @Test
        @DisplayName("Remove a marking on empty set should return null")
        void removeAMarkingOnEmptyReturnsNull() {
            YSetOfMarkings emptySet = new YSetOfMarkings();
            YCoreMarking removed = emptySet.removeAMarking();
            assertNull(removed);
        }
    }

    @Nested
    @DisplayName("Contains Operations")
    class ContainsTests {

        @Test
        @DisplayName("Contains should return true for added marking")
        void containsReturnsTrueForAdded() {
            List<YNetElement> locs = new ArrayList<>();
            locs.add(a);
            locs.add(b);
            YMarking marking = new YMarking(locs);

            assertTrue(markingSet1.contains(marking));
        }

        @Test
        @DisplayName("Contains should return false for non-added marking")
        void containsReturnsFalseForNonAdded() {
            List<YNetElement> locs = new ArrayList<>();
            locs.add(new YCondition("nonexistent", net));
            YMarking marking = new YMarking(locs);

            assertFalse(markingSet1.contains(marking));
        }

        @Test
        @DisplayName("Contains all should return true when all are present")
        void containsAllReturnsTrueWhenAllPresent() {
            assertTrue(markingSet1.containsAll(markingSet1.getMarkings()));
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("Equals should return true for identical sets")
        void equalsReturnsTrueForIdenticalSets() {
            YSetOfMarkings copy = new YSetOfMarkings();
            copy.addAll(markingSet1);
            assertTrue(markingSet1.equals(copy));
        }

        @Test
        @DisplayName("Equals should return false for different sets")
        void equalsReturnsFalseForDifferentSets() {
            assertFalse(markingSet1.equals(markingSet2));
        }
    }

    @Nested
    @DisplayName("Bigger Equal Tests")
    class BiggerEqualTests {

        @Test
        @DisplayName("Contains bigger equal should work for bigger marking")
        void containsBiggerEqualWorksWithBigger() {
            List<YNetElement> locs = new ArrayList<>();
            locs.add(a);
            YMarking smallerMarking = new YMarking(locs);
            assertTrue(markingSet1.containsBiggerEqual(smallerMarking));
        }
    }

    @Nested
    @DisplayName("Inheritance Tests")
    class InheritanceTests {

        @Test
        @DisplayName("YSetOfMarkings should extend YCoreSetOfMarkings")
        void extendsYCoreSetOfMarkings() {
            assertTrue(markingSet1 instanceof org.yawlfoundation.yawl.engine.core.marking.YCoreSetOfMarkings);
        }
    }
}
