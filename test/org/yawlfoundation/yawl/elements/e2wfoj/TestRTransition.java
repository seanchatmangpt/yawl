package org.yawlfoundation.yawl.elements.e2wfoj;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Chicago TDD tests for RTransition.
 * Tests remove set management and cancel transitions.
 */
@DisplayName("RTransition Tests")
@Tag("unit")
class TestRTransition {

    private RTransition transition;

    @BeforeEach
    void setUp() {
        transition = new RTransition("trans1");
    }

    @Nested
    @DisplayName("RTransition Creation Tests")
    class RTransitionCreationTests {

        @Test
        @DisplayName("RTransition should store ID correctly")
        void rTransitionStoresIdCorrectly() {
            assertEquals("trans1", transition.getID());
        }

        @Test
        @DisplayName("RTransition should extend RElement")
        void rTransitionExtendsRElement() {
            assertTrue(transition instanceof RElement);
        }

        @Test
        @DisplayName("New transition should have empty remove set")
        void newTransitionHasEmptyRemoveSet() {
            assertTrue(transition.getRemoveSet().isEmpty());
        }

        @Test
        @DisplayName("New transition should not be cancel transition")
        void newTransitionIsNotCancelTransition() {
            assertFalse(transition.isCancelTransition());
        }
    }

    @Nested
    @DisplayName("Remove Set Management Tests")
    class RemoveSetManagementTests {

        @Test
        @DisplayName("Set remove set with single place should work")
        void setRemoveSetWithSinglePlaceWorks() {
            RPlace place = new RPlace("place1");
            transition.setRemoveSet(place);

            assertEquals(1, transition.getRemoveSet().size());
            assertTrue(transition.getRemoveSet().contains(place));
        }

        @Test
        @DisplayName("Set remove set with multiple places should work")
        void setRemoveSetWithMultiplePlacesWorks() {
            Set<RPlace> places = new HashSet<>();
            places.add(new RPlace("place1"));
            places.add(new RPlace("place2"));
            places.add(new RPlace("place3"));

            transition.setRemoveSet(places);

            assertEquals(3, transition.getRemoveSet().size());
        }

        @Test
        @DisplayName("Set remove set should make it cancel transition")
        void setRemoveSetMakesItCancelTransition() {
            RPlace place = new RPlace("place1");
            transition.setRemoveSet(place);

            assertTrue(transition.isCancelTransition());
        }

        @Test
        @DisplayName("Add to remove set should accumulate places")
        void addToRemoveSetAccumulatesPlaces() {
            transition.setRemoveSet(new RPlace("place1"));
            transition.setRemoveSet(new RPlace("place2"));

            assertEquals(2, transition.getRemoveSet().size());
        }

        @Test
        @DisplayName("Get remove set should return defensive copy")
        void getRemoveSetReturnsDefensiveCopy() {
            RPlace place = new RPlace("place1");
            transition.setRemoveSet(place);

            Set<RPlace> set1 = transition.getRemoveSet();
            Set<RPlace> set2 = transition.getRemoveSet();

            assertNotSame(set1, set2);
        }

        @Test
        @DisplayName("Set remove set with set should add all")
        void setRemoveSetWithSetAddsAll() {
            Set<RPlace> places = new HashSet<>();
            places.add(new RPlace("place1"));
            places.add(new RPlace("place2"));

            transition.setRemoveSet(places);
            transition.setRemoveSet(new RPlace("place3"));

            assertEquals(3, transition.getRemoveSet().size());
        }
    }

    @Nested
    @DisplayName("Cancel Transition Tests")
    class CancelTransitionTests {

        @Test
        @DisplayName("Transition with remove set is cancel transition")
        void transitionWithRemoveSetIsCancelTransition() {
            transition.setRemoveSet(new RPlace("cancel1"));
            assertTrue(transition.isCancelTransition());
        }

        @Test
        @DisplayName("Transition without remove set is not cancel transition")
        void transitionWithoutRemoveSetIsNotCancelTransition() {
            assertFalse(transition.isCancelTransition());
        }

        @Test
        @DisplayName("Cancel transition can have multiple places in remove set")
        void cancelTransitionCanHaveMultiplePlaces() {
            Set<RPlace> cancelPlaces = new HashSet<>();
            cancelPlaces.add(new RPlace("cancel1"));
            cancelPlaces.add(new RPlace("cancel2"));

            transition.setRemoveSet(cancelPlaces);

            assertTrue(transition.isCancelTransition());
            assertEquals(2, transition.getRemoveSet().size());
        }
    }

    @Nested
    @DisplayName("Preset and Postset Place Tests")
    class PresetPostsetPlaceTests {

        @Test
        @DisplayName("Get preset places should return only places")
        void getPresetPlacesReturnsOnlyPlaces() {
            RPlace place1 = new RPlace("place1");
            RFlow flow = new RFlow(place1, transition);
            transition.setPreset(flow);

            Set<RPlace> presetPlaces = transition.getPresetPlaces();

            assertEquals(1, presetPlaces.size());
            assertTrue(presetPlaces.contains(place1));
        }

        @Test
        @DisplayName("Get postset places should return only places")
        void getPostsetPlacesReturnsOnlyPlaces() {
            RPlace place1 = new RPlace("place1");
            RFlow flow = new RFlow(transition, place1);
            transition.setPostset(flow);

            Set<RPlace> postsetPlaces = transition.getPostsetPlaces();

            assertEquals(1, postsetPlaces.size());
            assertTrue(postsetPlaces.contains(place1));
        }

        @Test
        @DisplayName("Get preset places should handle multiple places")
        void getPresetPlacesHandlesMultiple() {
            RPlace place1 = new RPlace("place1");
            RPlace place2 = new RPlace("place2");

            transition.setPreset(new RFlow(place1, transition));
            transition.setPreset(new RFlow(place2, transition));

            Set<RPlace> presetPlaces = transition.getPresetPlaces();

            assertEquals(2, presetPlaces.size());
        }

        @Test
        @DisplayName("Get postset places should handle multiple places")
        void getPostsetPlacesHandlesMultiple() {
            RPlace place1 = new RPlace("place1");
            RPlace place2 = new RPlace("place2");

            transition.setPostset(new RFlow(transition, place1));
            transition.setPostset(new RFlow(transition, place2));

            Set<RPlace> postsetPlaces = transition.getPostsetPlaces();

            assertEquals(2, postsetPlaces.size());
        }
    }

    @Nested
    @DisplayName("Flow Management Tests")
    class FlowManagementTests {

        @Test
        @DisplayName("Set preset flow should add to both elements")
        void setPresetFlowAddsToBothElements() {
            RPlace place = new RPlace("place1");
            RFlow flow = new RFlow(place, transition);

            transition.setPreset(flow);

            assertTrue(transition.getPresetFlows().containsKey("place1"));
            assertTrue(place.getPostsetFlows().containsKey("trans1"));
        }

        @Test
        @DisplayName("Set postset flow should add to both elements")
        void setPostsetFlowAddsToBothElements() {
            RPlace place = new RPlace("place1");
            RFlow flow = new RFlow(transition, place);

            transition.setPostset(flow);

            assertTrue(transition.getPostsetFlows().containsKey("place1"));
            assertTrue(place.getPresetFlows().containsKey("trans1"));
        }

        @Test
        @DisplayName("Set preset flows should replace existing flows")
        void setPresetFlowsReplacesExisting() {
            RPlace place1 = new RPlace("place1");
            transition.setPreset(new RFlow(place1, transition));

            java.util.Map<String, RFlow> newFlows = new java.util.HashMap<>();
            RPlace place2 = new RPlace("place2");
            newFlows.put("place2", new RFlow(place2, transition));

            transition.setPresetFlows(newFlows);

            assertEquals(1, transition.getPresetFlows().size());
            assertTrue(transition.getPresetFlows().containsKey("place2"));
        }
    }
}
