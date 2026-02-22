package org.yawlfoundation.yawl.elements.e2wfoj;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Chicago TDD tests for RMarking.
 * Tests marking comparison (biggerThanOrEqual, lessThanOrEqual).
 * Uses real RMarking instances with real RElement objects.
 */
@DisplayName("RMarking Tests")
@Tag("unit")
class TestRMarking {

    private RPlace placeA;
    private RPlace placeB;
    private RPlace placeC;
    private RPlace placeD;

    @BeforeEach
    void setUp() {
        placeA = new RPlace("placeA");
        placeB = new RPlace("placeB");
        placeC = new RPlace("placeC");
        placeD = new RPlace("placeD");
    }

    @Nested
    @DisplayName("RMarking Creation Tests")
    class RMarkingCreationTests {

        @Test
        @DisplayName("RMarking from list should count tokens correctly")
        void rMarkingFromListCountsTokens() {
            List<RElement> locations = new ArrayList<>();
            locations.add(placeA);
            locations.add(placeA);
            locations.add(placeB);

            RMarking marking = new RMarking(locations);
            Map markedPlaces = marking.getMarkedPlaces();

            assertEquals(2, markedPlaces.get("placeA"));
            assertEquals(1, markedPlaces.get("placeB"));
        }

        @Test
        @DisplayName("RMarking from map should copy correctly")
        void rMarkingFromMapCopiesCorrectly() {
            Map<String, Integer> map = new HashMap<>();
            map.put("placeA", 3);
            map.put("placeB", 1);

            RMarking marking = new RMarking(map);
            assertEquals(3, marking.getMarkedPlaces().get("placeA"));
            assertEquals(1, marking.getMarkedPlaces().get("placeB"));
        }

        @Test
        @DisplayName("Get locations should return place IDs")
        void getLocationsReturnsPlaceIds() {
            List<RElement> locations = new ArrayList<>();
            locations.add(placeA);
            locations.add(placeB);

            RMarking marking = new RMarking(locations);
            List<String> locs = marking.getLocations();

            assertTrue(locs.contains("placeA"));
            assertTrue(locs.contains("placeB"));
        }

        @Test
        @DisplayName("RMarking with empty list should have no locations")
        void rMarkingWithEmptyListHasNoLocations() {
            RMarking marking = new RMarking(new ArrayList<>());
            assertTrue(marking.getLocations().isEmpty());
        }
    }

    @Nested
    @DisplayName("Equals Tests")
    class EqualsTests {

        @Test
        @DisplayName("Equal markings should have same places and counts")
        void equalMarkingsHaveSamePlacesAndCounts() {
            List<RElement> locs1 = new ArrayList<>();
            locs1.add(placeA);
            locs1.add(placeB);

            List<RElement> locs2 = new ArrayList<>();
            locs2.add(placeA);
            locs2.add(placeB);

            RMarking marking1 = new RMarking(locs1);
            RMarking marking2 = new RMarking(locs2);

            assertEquals(marking1, marking2);
        }

        @Test
        @DisplayName("Different place counts should not be equal")
        void differentPlaceCountsNotEqual() {
            List<RElement> locs1 = new ArrayList<>();
            locs1.add(placeA);
            locs1.add(placeA);
            locs1.add(placeB);

            List<RElement> locs2 = new ArrayList<>();
            locs2.add(placeA);
            locs2.add(placeB);

            RMarking marking1 = new RMarking(locs1);
            RMarking marking2 = new RMarking(locs2);

            assertNotEquals(marking1, marking2);
        }

        @Test
        @DisplayName("Different places should not be equal")
        void differentPlacesNotEqual() {
            List<RElement> locs1 = new ArrayList<>();
            locs1.add(placeA);
            locs1.add(placeB);

            List<RElement> locs2 = new ArrayList<>();
            locs2.add(placeC);
            locs2.add(placeD);

            RMarking marking1 = new RMarking(locs1);
            RMarking marking2 = new RMarking(locs2);

            assertNotEquals(marking1, marking2);
        }

        @Test
        @DisplayName("Marking should not equal null")
        void markingDoesNotEqualNull() {
            List<RElement> locs = new ArrayList<>();
            locs.add(placeA);
            RMarking marking = new RMarking(locs);

            assertNotEquals(null, marking);
        }

        @Test
        @DisplayName("Marking should not equal non-RMarking object")
        void markingDoesNotEqualOtherObject() {
            List<RElement> locs = new ArrayList<>();
            locs.add(placeA);
            RMarking marking = new RMarking(locs);

            assertNotEquals("not a marking", marking);
        }
    }

    @Nested
    @DisplayName("Is Bigger Than Or Equal Tests")
    class IsBiggerThanOrEqualTests {

        @Test
        @DisplayName("Same marking should be bigger or equal")
        void sameMarkingIsBiggerOrEqual() {
            List<RElement> locs = new ArrayList<>();
            locs.add(placeA);
            locs.add(placeB);

            RMarking marking1 = new RMarking(locs);
            RMarking marking2 = new RMarking(new ArrayList<>(locs));

            assertTrue(marking1.isBiggerThanOrEqual(marking2));
        }

        @Test
        @DisplayName("Marking with more tokens should be bigger or equal")
        void markingWithMoreTokensIsBiggerOrEqual() {
            List<RElement> locs1 = new ArrayList<>();
            locs1.add(placeA);
            locs1.add(placeA);
            locs1.add(placeB);

            List<RElement> locs2 = new ArrayList<>();
            locs2.add(placeA);
            locs2.add(placeB);

            RMarking bigger = new RMarking(locs1);
            RMarking smaller = new RMarking(locs2);

            assertTrue(bigger.isBiggerThanOrEqual(smaller));
        }

        @Test
        @DisplayName("Marking with more places should be bigger or equal")
        void markingWithMorePlacesIsBiggerOrEqual() {
            List<RElement> locs1 = new ArrayList<>();
            locs1.add(placeA);
            locs1.add(placeB);
            locs1.add(placeC);

            List<RElement> locs2 = new ArrayList<>();
            locs2.add(placeA);
            locs2.add(placeB);

            RMarking bigger = new RMarking(locs1);
            RMarking smaller = new RMarking(locs2);

            assertTrue(bigger.isBiggerThanOrEqual(smaller));
        }

        @Test
        @DisplayName("Smaller marking should not be bigger or equal")
        void smallerMarkingNotBiggerOrEqual() {
            List<RElement> locs1 = new ArrayList<>();
            locs1.add(placeA);

            List<RElement> locs2 = new ArrayList<>();
            locs2.add(placeA);
            locs2.add(placeB);

            RMarking smaller = new RMarking(locs1);
            RMarking bigger = new RMarking(locs2);

            assertFalse(smaller.isBiggerThanOrEqual(bigger));
        }

        @Test
        @DisplayName("Different places should not be bigger or equal")
        void differentPlacesNotBiggerOrEqual() {
            List<RElement> locs1 = new ArrayList<>();
            locs1.add(placeA);

            List<RElement> locs2 = new ArrayList<>();
            locs2.add(placeB);

            RMarking marking1 = new RMarking(locs1);
            RMarking marking2 = new RMarking(locs2);

            assertFalse(marking1.isBiggerThanOrEqual(marking2));
        }
    }

    @Nested
    @DisplayName("Is Bigger Than Tests")
    class IsBiggerThanTests {

        @Test
        @DisplayName("Same marking should not be strictly bigger")
        void sameMarkingNotStrictlyBigger() {
            List<RElement> locs = new ArrayList<>();
            locs.add(placeA);
            locs.add(placeB);

            RMarking marking1 = new RMarking(locs);
            RMarking marking2 = new RMarking(new ArrayList<>(locs));

            assertFalse(marking1.isBiggerThan(marking2));
        }

        @Test
        @DisplayName("Marking with more tokens should be strictly bigger")
        void markingWithMoreTokensIsStrictlyBigger() {
            List<RElement> locs1 = new ArrayList<>();
            locs1.add(placeA);
            locs1.add(placeA);
            locs1.add(placeB);

            List<RElement> locs2 = new ArrayList<>();
            locs2.add(placeA);
            locs2.add(placeB);

            RMarking bigger = new RMarking(locs1);
            RMarking smaller = new RMarking(locs2);

            assertTrue(bigger.isBiggerThan(smaller));
        }

        @Test
        @DisplayName("Marking with more places should be strictly bigger")
        void markingWithMorePlacesIsStrictlyBigger() {
            List<RElement> locs1 = new ArrayList<>();
            locs1.add(placeA);
            locs1.add(placeB);
            locs1.add(placeC);

            List<RElement> locs2 = new ArrayList<>();
            locs2.add(placeA);
            locs2.add(placeB);

            RMarking bigger = new RMarking(locs1);
            RMarking smaller = new RMarking(locs2);

            assertTrue(bigger.isBiggerThan(smaller));
        }
    }

    @Nested
    @DisplayName("Is Less Than Or Equal Tests")
    class IsLessThanOrEqualTests {

        @Test
        @DisplayName("Same marking should be less or equal")
        void sameMarkingIsLessOrEqual() {
            List<RElement> locs = new ArrayList<>();
            locs.add(placeA);
            locs.add(placeB);

            RMarking marking1 = new RMarking(locs);
            RMarking marking2 = new RMarking(new ArrayList<>(locs));

            assertTrue(marking1.isLessThanOrEqual(marking2));
        }

        @Test
        @DisplayName("Smaller marking should be less or equal")
        void smallerMarkingIsLessOrEqual() {
            List<RElement> locs1 = new ArrayList<>();
            locs1.add(placeA);

            List<RElement> locs2 = new ArrayList<>();
            locs2.add(placeA);
            locs2.add(placeB);

            RMarking smaller = new RMarking(locs1);
            RMarking bigger = new RMarking(locs2);

            assertTrue(smaller.isLessThanOrEqual(bigger));
        }

        @Test
        @DisplayName("Bigger marking should not be less or equal")
        void biggerMarkingNotLessOrEqual() {
            List<RElement> locs1 = new ArrayList<>();
            locs1.add(placeA);
            locs1.add(placeB);

            List<RElement> locs2 = new ArrayList<>();
            locs2.add(placeA);

            RMarking bigger = new RMarking(locs1);
            RMarking smaller = new RMarking(locs2);

            assertFalse(bigger.isLessThanOrEqual(smaller));
        }
    }

    @Nested
    @DisplayName("Get Marked Places Tests")
    class GetMarkedPlacesTests {

        @Test
        @DisplayName("Get marked places should return correct map")
        void getMarkedPlacesReturnsCorrectMap() {
            List<RElement> locs = new ArrayList<>();
            locs.add(placeA);
            locs.add(placeA);
            locs.add(placeA);
            locs.add(placeB);

            RMarking marking = new RMarking(locs);
            Map<String, Integer> places = marking.getMarkedPlaces();

            assertEquals(3, places.get("placeA"));
            assertEquals(1, places.get("placeB"));
            assertEquals(2, places.size());
        }

        @Test
        @DisplayName("Empty marking should have empty marked places")
        void emptyMarkingHasEmptyMarkedPlaces() {
            RMarking marking = new RMarking(new ArrayList<>());
            assertTrue(marking.getMarkedPlaces().isEmpty());
        }
    }
}
