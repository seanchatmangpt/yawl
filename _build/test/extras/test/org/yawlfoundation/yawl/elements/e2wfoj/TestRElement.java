package org.yawlfoundation.yawl.elements.e2wfoj;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Chicago TDD tests for RElement.
 * Tests base element functionality and flow management.
 */
@DisplayName("RElement Tests")
@Tag("unit")
class TestRElement {

    private RElement element;

    @BeforeEach
    void setUp() {
        element = new RPlace("testElement");
    }

    @Nested
    @DisplayName("RElement Creation Tests")
    class RElementCreationTests {

        @Test
        @DisplayName("RElement should store ID correctly")
        void rElementStoresIdCorrectly() {
            assertEquals("testElement", element.getID());
        }

        @Test
        @DisplayName("RElement should have empty preset initially")
        void rElementHasEmptyPresetInitially() {
            assertTrue(element.getPresetFlows().isEmpty());
        }

        @Test
        @DisplayName("RElement should have empty postset initially")
        void rElementHasEmptyPostsetInitially() {
            assertTrue(element.getPostsetFlows().isEmpty());
        }

        @Test
        @DisplayName("RElement name should be null initially")
        void rElementNameIsNullInitially() {
            assertNull(element.getName());
        }
    }

    @Nested
    @DisplayName("Name Management Tests")
    class NameManagementTests {

        @Test
        @DisplayName("Set name should store correctly")
        void setNameStoresCorrectly() {
            element.setName("MyElement");
            assertEquals("MyElement", element.getName());
        }

        @Test
        @DisplayName("Set name to null should work")
        void setNameToNullWorks() {
            element.setName("MyElement");
            element.setName(null);
            assertNull(element.getName());
        }

        @Test
        @DisplayName("Set name to empty string should work")
        void setNameToEmptyStringWorks() {
            element.setName("");
            assertEquals("", element.getName());
        }
    }

    @Nested
    @DisplayName("Preset Flow Tests")
    class PresetFlowTests {

        @Test
        @DisplayName("Set preset should add flow to map")
        void setPresetAddsFlowToMap() {
            RElement other = new RPlace("other");
            RFlow flow = new RFlow(other, element);

            element.setPreset(flow);

            assertTrue(element.getPresetFlows().containsKey("other"));
        }

        @Test
        @DisplayName("Set preset should update other element's postset")
        void setPresetUpdatesOtherPostset() {
            RElement other = new RPlace("other");
            RFlow flow = new RFlow(other, element);

            element.setPreset(flow);

            assertTrue(other.getPostsetFlows().containsKey("testElement"));
        }

        @Test
        @DisplayName("Set preset flows should replace all flows")
        void setPresetFlowsReplacesAll() {
            RElement other1 = new RPlace("other1");
            element.setPreset(new RFlow(other1, element));

            Map<String, RFlow> newFlows = new HashMap<>();
            RElement other2 = new RPlace("other2");
            newFlows.put("other2", new RFlow(other2, element));

            element.setPresetFlows(newFlows);

            assertEquals(1, element.getPresetFlows().size());
            assertTrue(element.getPresetFlows().containsKey("other2"));
        }

        @Test
        @DisplayName("Set preset with null should not throw")
        void setPresetWithNullDoesNotThrow() {
            assertDoesNotThrow(() -> element.setPreset(null));
        }
    }

    @Nested
    @DisplayName("Postset Flow Tests")
    class PostsetFlowTests {

        @Test
        @DisplayName("Set postset should add flow to map")
        void setPostsetAddsFlowToMap() {
            RElement other = new RPlace("other");
            RFlow flow = new RFlow(element, other);

            element.setPostset(flow);

            assertTrue(element.getPostsetFlows().containsKey("other"));
        }

        @Test
        @DisplayName("Set postset should update other element's preset")
        void setPostsetUpdatesOtherPreset() {
            RElement other = new RPlace("other");
            RFlow flow = new RFlow(element, other);

            element.setPostset(flow);

            assertTrue(other.getPresetFlows().containsKey("testElement"));
        }

        @Test
        @DisplayName("Set postset flows should replace all flows")
        void setPostsetFlowsReplacesAll() {
            RElement other1 = new RPlace("other1");
            element.setPostset(new RFlow(element, other1));

            Map<String, RFlow> newFlows = new HashMap<>();
            RElement other2 = new RPlace("other2");
            newFlows.put("other2", new RFlow(element, other2));

            element.setPostsetFlows(newFlows);

            assertEquals(1, element.getPostsetFlows().size());
            assertTrue(element.getPostsetFlows().containsKey("other2"));
        }

        @Test
        @DisplayName("Set postset with null should not throw")
        void setPostsetWithNullDoesNotThrow() {
            assertDoesNotThrow(() -> element.setPostset(null));
        }
    }

    @Nested
    @DisplayName("Get Elements Tests")
    class GetElementsTests {

        @Test
        @DisplayName("Get preset elements should return connected elements")
        void getPresetElementsReturnsConnected() {
            RElement other1 = new RPlace("other1");
            RElement other2 = new RPlace("other2");

            element.setPreset(new RFlow(other1, element));
            element.setPreset(new RFlow(other2, element));

            Set<RElement> preset = element.getPresetElements();

            assertEquals(2, preset.size());
            assertTrue(preset.contains(other1));
            assertTrue(preset.contains(other2));
        }

        @Test
        @DisplayName("Get postset elements should return connected elements")
        void getPostsetElementsReturnsConnected() {
            RElement other1 = new RPlace("other1");
            RElement other2 = new RPlace("other2");

            element.setPostset(new RFlow(element, other1));
            element.setPostset(new RFlow(element, other2));

            Set<RElement> postset = element.getPostsetElements();

            assertEquals(2, postset.size());
            assertTrue(postset.contains(other1));
            assertTrue(postset.contains(other2));
        }

        @Test
        @DisplayName("Get preset element should return specific element")
        void getPresetElementReturnsSpecific() {
            RElement other = new RPlace("other");
            element.setPreset(new RFlow(other, element));

            RElement result = element.getPresetElement("other");

            assertNotNull(result);
            assertEquals(other, result);
        }

        @Test
        @DisplayName("Get postset element should return specific element")
        void getPostsetElementReturnsSpecific() {
            RElement other = new RPlace("other");
            element.setPostset(new RFlow(element, other));

            RElement result = element.getPostsetElement("other");

            assertNotNull(result);
            assertEquals(other, result);
        }

        @Test
        @DisplayName("Get preset element should return null for non-existent")
        void getPresetElementReturnsNullForNonExistent() {
            assertNull(element.getPresetElement("nonexistent"));
        }

        @Test
        @DisplayName("Get postset element should return null for non-existent")
        void getPostsetElementReturnsNullForNonExistent() {
            assertNull(element.getPostsetElement("nonexistent"));
        }
    }

    @Nested
    @DisplayName("Complex Flow Network Tests")
    class ComplexFlowNetworkTests {

        @Test
        @DisplayName("Element can be in multiple presets and postsets")
        void elementCanBeInMultiplePresetAndPostsets() {
            RElement a = new RPlace("a");
            RElement b = new RPlace("b");
            RElement c = new RPlace("c");
            RElement d = new RPlace("d");

            // Create: a -> element -> c
            //         b -> element -> d
            element.setPreset(new RFlow(a, element));
            element.setPreset(new RFlow(b, element));
            element.setPostset(new RFlow(element, c));
            element.setPostset(new RFlow(element, d));

            assertEquals(2, element.getPresetElements().size());
            assertEquals(2, element.getPostsetElements().size());
        }

        @Test
        @DisplayName("Flow network should be bidirectional")
        void flowNetworkIsBidirectional() {
            RElement source = new RPlace("source");
            RElement target = new RPlace("target");

            RFlow flow = new RFlow(source, target);
            source.setPostset(flow);

            assertTrue(source.getPostsetFlows().containsKey("target"));
            assertTrue(target.getPresetFlows().containsKey("source"));
        }
    }
}
