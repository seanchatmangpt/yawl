package org.yawlfoundation.yawl.elements;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdom2.Attribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.util.DynamicValue;

/**
 * Chicago TDD tests for YAttributeMap.
 * Tests map operations and XML serialization.
 */
@DisplayName("YAttributeMap Tests")
@Tag("unit")
class TestYAttributeMap {

    private YAttributeMap attrMap;

    @BeforeEach
    void setUp() {
        attrMap = new YAttributeMap();
    }

    @Nested
    @DisplayName("YAttributeMap Creation Tests")
    class YAttributeMapCreationTests {

        @Test
        @DisplayName("Default constructor creates empty map")
        void defaultConstructorCreatesEmptyMap() {
            assertTrue(attrMap.isEmpty());
        }

        @Test
        @DisplayName("Constructor with map initializes with values")
        void constructorWithMapInitializesWithValues() {
            Map<String, String> initial = new HashMap<>();
            initial.put("key1", "value1");
            initial.put("key2", "value2");

            YAttributeMap newMap = new YAttributeMap(initial);

            assertEquals(2, newMap.size());
            assertEquals("value1", newMap.get("key1"));
            assertEquals("value2", newMap.get("key2"));
        }
    }

    @Nested
    @DisplayName("Basic Map Operations Tests")
    class BasicMapOperationsTests {

        @Test
        @DisplayName("Put and get work correctly")
        void putAndGetWorkCorrectly() {
            attrMap.put("key1", "value1");
            assertEquals("value1", attrMap.get("key1"));
        }

        @Test
        @DisplayName("ContainsKey returns true for existing key")
        void containsKeyReturnsTrueForExistingKey() {
            attrMap.put("key1", "value1");
            assertTrue(attrMap.containsKey("key1"));
        }

        @Test
        @DisplayName("ContainsKey returns false for non-existing key")
        void containsKeyReturnsFalseForNonExistingKey() {
            assertFalse(attrMap.containsKey("nonexistent"));
        }

        @Test
        @DisplayName("Remove deletes key-value pair")
        void removeDeletesKeyValue() {
            attrMap.put("key1", "value1");
            attrMap.remove("key1");
            assertFalse(attrMap.containsKey("key1"));
        }

        @Test
        @DisplayName("Clear empties the map")
        void clearEmptiesTheMap() {
            attrMap.put("key1", "value1");
            attrMap.put("key2", "value2");
            attrMap.clear();
            assertTrue(attrMap.isEmpty());
        }

        @Test
        @DisplayName("Set replaces all entries")
        void setReplacesAllEntries() {
            attrMap.put("old", "value");

            Map<String, String> newEntries = new HashMap<>();
            newEntries.put("new1", "value1");
            newEntries.put("new2", "value2");

            attrMap.set(newEntries);

            assertEquals(2, attrMap.size());
            assertFalse(attrMap.containsKey("old"));
        }
    }

    @Nested
    @DisplayName("GetBoolean Tests")
    class GetBooleanTests {

        @Test
        @DisplayName("GetBoolean returns true for 'true' value")
        void getBooleanReturnsTrueForTrueValue() {
            attrMap.put("flag", "true");
            assertTrue(attrMap.getBoolean("flag"));
        }

        @Test
        @DisplayName("GetBoolean returns true for 'TRUE' value (case insensitive)")
        void getBooleanReturnsTrueForTrueValueCaseInsensitive() {
            attrMap.put("flag", "TRUE");
            assertTrue(attrMap.getBoolean("flag"));
        }

        @Test
        @DisplayName("GetBoolean returns false for non-true value")
        void getBooleanReturnsFalseForNonTrueValue() {
            attrMap.put("flag", "false");
            assertFalse(attrMap.getBoolean("flag"));
        }

        @Test
        @DisplayName("GetBoolean returns false for missing key")
        void getBooleanReturnsFalseForMissingKey() {
            assertFalse(attrMap.getBoolean("nonexistent"));
        }
    }

    @Nested
    @DisplayName("Dynamic Value Tests")
    class DynamicValueTests {

        @Test
        @DisplayName("Put with DynamicValue stores in dynamics map")
        void putWithDynamicValueStoresInDynamicsMap() {
            DynamicValue dv = new DynamicValue("testExpression", this);
            attrMap.put("dynamic", dv);

            // The value should be retrievable via get
            assertNotNull(attrMap.get("dynamic"));
        }

        @Test
        @DisplayName("Remove removes from dynamics map if key is there")
        void removeRemovesFromDynamicsMap() {
            DynamicValue dv = new DynamicValue("testExpression", this);
            attrMap.put("dynamic", dv);

            attrMap.remove("dynamic");

            assertNull(attrMap.get("dynamic"));
        }
    }

    @Nested
    @DisplayName("ToJDOM Tests")
    class ToJDOMTests {

        @Test
        @DisplayName("ToJDOM returns set of attributes")
        void toJdomReturnsSetOfAttributes() {
            attrMap.put("attr1", "value1");
            attrMap.put("attr2", "value2");

            Set<Attribute> attrs = attrMap.toJDOM();

            assertEquals(2, attrs.size());
        }

        @Test
        @DisplayName("ToJDOM returns empty set for empty map")
        void toJdomReturnsEmptySetForEmptyMap() {
            Set<Attribute> attrs = attrMap.toJDOM();
            assertTrue(attrs.isEmpty());
        }
    }

    @Nested
    @DisplayName("FromJDOM Tests")
    class FromJDOMTests {

        @Test
        @DisplayName("FromJDOM populates map from attributes")
        void fromJdomPopulatesMapFromAttributes() {
            List<Attribute> attrs = List.of(
                new Attribute("attr1", "value1"),
                new Attribute("attr2", "value2")
            );

            attrMap.fromJDOM(attrs);

            assertEquals(2, attrMap.size());
            assertEquals("value1", attrMap.get("attr1"));
            assertEquals("value2", attrMap.get("attr2"));
        }

        @Test
        @DisplayName("FromJDOM with null does not throw")
        void fromJdomWithNullDoesNotThrow() {
            assertDoesNotThrow(() -> attrMap.fromJDOM(null));
        }
    }

    @Nested
    @DisplayName("ToXML Tests")
    class ToXMLTests {

        @Test
        @DisplayName("ToXML with key returns attribute format")
        void toXmlWithKeyReturnsAttributeFormat() {
            attrMap.put("attr", "value");

            String xml = attrMap.toXML("attr");

            assertEquals("attr=\"value\"", xml);
        }

        @Test
        @DisplayName("ToXML with missing key returns empty string")
        void toXmlWithMissingKeyReturnsEmptyString() {
            String xml = attrMap.toXML("nonexistent");
            assertEquals("", xml);
        }

        @Test
        @DisplayName("ToXML without key returns all attributes")
        void toXmlWithoutKeyReturnsAllAttributes() {
            attrMap.put("attr1", "value1");
            attrMap.put("attr2", "value2");

            String xml = attrMap.toXML();

            assertTrue(xml.contains("attr1=\"value1\""));
            assertTrue(xml.contains("attr2=\"value2\""));
        }
    }

    @Nested
    @DisplayName("ToXMLElement Tests")
    class ToXMLElementTests {

        @Test
        @DisplayName("ToXMLElement with key returns element format")
        void toXmlElementWithKeyReturnsElementFormat() {
            attrMap.put("attr", "value");

            String xml = attrMap.toXMLElement("attr");

            assertEquals("<attr>value</attr>", xml);
        }

        @Test
        @DisplayName("ToXMLElements returns all as elements")
        void toXmlElementsReturnsAllAsElements() {
            attrMap.put("attr1", "value1");
            attrMap.put("attr2", "value2");

            String xml = attrMap.toXMLElements();

            assertTrue(xml.contains("<attr1>value1</attr1>"));
            assertTrue(xml.contains("<attr2>value2</attr2>"));
        }
    }

    @Nested
    @DisplayName("FromXMLElements Tests")
    class FromXMLElementsTests {

        @Test
        @DisplayName("FromXMLElements populates from XML elements")
        void fromXmlElementsPopulatesFromXmlElements() {
            String xml = "<root><attr1>value1</attr1><attr2>value2</attr2></root>";

            attrMap.fromXMLElements(xml);

            assertEquals("value1", attrMap.get("attr1"));
            assertEquals("value2", attrMap.get("attr2"));
        }

        @Test
        @DisplayName("FromXMLElements with invalid XML does not throw")
        void fromXmlElementsWithInvalidXmlDoesNotThrow() {
            assertDoesNotThrow(() -> attrMap.fromXMLElements("not valid xml"));
        }
    }

    @Nested
    @DisplayName("TransformDynamicValues Tests")
    class TransformDynamicValuesTests {

        @Test
        @DisplayName("TransformDynamicValues converts dynamic{...} syntax")
        void transformDynamicValuesConvertsSyntax() {
            attrMap.put("dynamic", "dynamic{testExpression}");
            attrMap.transformDynamicValues(this);

            // After transformation, the value should be a DynamicValue
            Object result = attrMap.get("dynamic");
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Inheritance Tests")
    class InheritanceTests {

        @Test
        @DisplayName("YAttributeMap extends TreeMap")
        void yAttributeMapExtendsTreeMap() {
            assertTrue(attrMap instanceof java.util.TreeMap);
        }
    }
}
