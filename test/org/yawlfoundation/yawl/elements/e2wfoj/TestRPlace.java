package org.yawlfoundation.yawl.elements.e2wfoj;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Chicago TDD tests for RPlace.
 * Tests place representation in Reset nets.
 */
@DisplayName("RPlace Tests")
@Tag("unit")
class TestRPlace {

    @Nested
    @DisplayName("RPlace Creation Tests")
    class RPlaceCreationTests {

        @Test
        @DisplayName("RPlace should store ID correctly")
        void rPlaceStoresIdCorrectly() {
            RPlace place = new RPlace("place1");
            assertEquals("place1", place.getID());
        }

        @Test
        @DisplayName("RPlace should accept complex IDs")
        void rPlaceAcceptsComplexIds() {
            RPlace place = new RPlace("p_complex-condition_123");
            assertEquals("p_complex-condition_123", place.getID());
        }

        @Test
        @DisplayName("RPlace should accept empty ID")
        void rPlaceAcceptsEmptyId() {
            RPlace place = new RPlace("");
            assertEquals("", place.getID());
        }
    }

    @Nested
    @DisplayName("RPlace Inheritance Tests")
    class RPlaceInheritanceTests {

        @Test
        @DisplayName("RPlace should extend RElement")
        void rPlaceExtendsRElement() {
            RPlace place = new RPlace("place1");
            assertTrue(place instanceof RElement);
        }

        @Test
        @DisplayName("RPlace should have RElement methods")
        void rPlaceHasRElementMethods() {
            RPlace place = new RPlace("place1");

            // Test inherited methods
            assertNotNull(place.getID());
            assertNotNull(place.getPresetFlows());
            assertNotNull(place.getPostsetFlows());
        }
    }

    @Nested
    @DisplayName("RPlace Flow Tests")
    class RPlaceFlowTests {

        @Test
        @DisplayName("RPlace should accept preset flows")
        void rPlaceAcceptsPresetFlows() {
            RPlace place1 = new RPlace("place1");
            RTransition transition = new RTransition("trans1");
            RFlow flow = new RFlow(transition, place1);

            place1.setPreset(flow);

            assertTrue(place1.getPresetFlows().containsKey("trans1"));
        }

        @Test
        @DisplayName("RPlace should accept postset flows")
        void rPlaceAcceptsPostsetFlows() {
            RPlace place1 = new RPlace("place1");
            RTransition transition = new RTransition("trans1");
            RFlow flow = new RFlow(place1, transition);

            place1.setPostset(flow);

            assertTrue(place1.getPostsetFlows().containsKey("trans1"));
        }

        @Test
        @DisplayName("RPlace can have multiple preset flows")
        void rPlaceCanHaveMultiplePresetFlows() {
            RPlace place = new RPlace("place1");
            RTransition trans1 = new RTransition("trans1");
            RTransition trans2 = new RTransition("trans2");

            place.setPreset(new RFlow(trans1, place));
            place.setPreset(new RFlow(trans2, place));

            assertEquals(2, place.getPresetFlows().size());
        }

        @Test
        @DisplayName("RPlace can have multiple postset flows")
        void rPlaceCanHaveMultiplePostsetFlows() {
            RPlace place = new RPlace("place1");
            RTransition trans1 = new RTransition("trans1");
            RTransition trans2 = new RTransition("trans2");

            place.setPostset(new RFlow(place, trans1));
            place.setPostset(new RFlow(place, trans2));

            assertEquals(2, place.getPostsetFlows().size());
        }
    }

    @Nested
    @DisplayName("RPlace Element Access Tests")
    class RPlaceElementAccessTests {

        @Test
        @DisplayName("Get preset elements should return connected transitions")
        void getPresetElementsReturnsConnectedTransitions() {
            RPlace place = new RPlace("place1");
            RTransition trans = new RTransition("trans1");
            RFlow flow = new RFlow(trans, place);

            place.setPreset(flow);

            assertEquals(1, place.getPresetElements().size());
            assertTrue(place.getPresetElements().contains(trans));
        }

        @Test
        @DisplayName("Get postset elements should return connected transitions")
        void getPostsetElementsReturnsConnectedTransitions() {
            RPlace place = new RPlace("place1");
            RTransition trans = new RTransition("trans1");
            RFlow flow = new RFlow(place, trans);

            place.setPostset(flow);

            assertEquals(1, place.getPostsetElements().size());
            assertTrue(place.getPostsetElements().contains(trans));
        }

        @Test
        @DisplayName("Get preset element should return specific element")
        void getPresetElementReturnsSpecificElement() {
            RPlace place = new RPlace("place1");
            RTransition trans = new RTransition("trans1");
            RFlow flow = new RFlow(trans, place);

            place.setPreset(flow);

            RElement result = place.getPresetElement("trans1");
            assertNotNull(result);
            assertEquals(trans, result);
        }

        @Test
        @DisplayName("Get postset element should return specific element")
        void getPostsetElementReturnsSpecificElement() {
            RPlace place = new RPlace("place1");
            RTransition trans = new RTransition("trans1");
            RFlow flow = new RFlow(place, trans);

            place.setPostset(flow);

            RElement result = place.getPostsetElement("trans1");
            assertNotNull(result);
            assertEquals(trans, result);
        }

        @Test
        @DisplayName("Get preset element should return null for non-existent")
        void getPresetElementReturnsNullForNonExistent() {
            RPlace place = new RPlace("place1");
            assertNull(place.getPresetElement("nonexistent"));
        }

        @Test
        @DisplayName("Get postset element should return null for non-existent")
        void getPostsetElementReturnsNullForNonExistent() {
            RPlace place = new RPlace("place1");
            assertNull(place.getPostsetElement("nonexistent"));
        }
    }
}
