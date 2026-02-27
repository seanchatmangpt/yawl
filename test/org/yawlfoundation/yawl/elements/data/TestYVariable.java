package org.yawlfoundation.yawl.elements.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YAWLServiceGateway;
import org.yawlfoundation.yawl.elements.YAttributeMap;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.logging.YLogPredicate;
import org.yawlfoundation.yawl.util.DynamicValue;
import org.yawlfoundation.yawl.util.YVerificationHandler;

/**
 * Chicago TDD tests for YVariable.
 * Tests data types, verification, attributes, and cloning.
 */
@DisplayName("YVariable Tests")
@Tag("unit")
class TestYVariable {

    private YSpecification spec;
    private YAWLServiceGateway gateway;

    @BeforeEach
    void setUp() {
        spec = new YSpecification("http://test.com/test-spec");
        gateway = new YAWLServiceGateway("testGateway", spec);
    }

    @Nested
    @DisplayName("YVariable Creation Tests")
    class YVariableCreationTests {

        @Test
        @DisplayName("YVariable with decomposition should be created")
        void yVariableWithDecompositionCreated() {
            YVariable var = new YVariable(gateway);
            assertEquals(gateway, var.getParentDecomposition());
        }

        @Test
        @DisplayName("Default constructor should work")
        void defaultConstructorWorks() {
            YVariable var = new YVariable();
            assertNull(var.getParentDecomposition());
        }

        @Test
        @DisplayName("Full constructor should set all fields")
        void fullConstructorSetsAllFields() {
            YVariable var = new YVariable(gateway, "xs:string", "myVar",
                "<default>value</default>", "http://www.w3.org/2001/XMLSchema");

            assertEquals(gateway, var.getParentDecomposition());
            assertEquals("xs:string", var.getDataTypeName());
            assertEquals("myVar", var.getName());
            assertEquals("<default>value</default>", var.getInitialValue());
            assertEquals("http://www.w3.org/2001/XMLSchema", var.getDataTypeNameSpace());
        }
    }

    @Nested
    @DisplayName("Name and Type Tests")
    class NameAndTypeTests {

        @Test
        @DisplayName("SetName should store correctly")
        void setNameStoresCorrectly() {
            YVariable var = new YVariable(gateway);
            var.setName("testVar");
            assertEquals("testVar", var.getName());
        }

        @Test
        @DisplayName("SetDataTypeAndName should set all three")
        void setDataTypeAndNameSetsAllThree() {
            YVariable var = new YVariable(gateway);
            var.setDataTypeAndName("xs:int", "count", "http://www.w3.org/2001/XMLSchema");

            assertEquals("xs:int", var.getDataTypeName());
            assertEquals("count", var.getName());
            assertEquals("http://www.w3.org/2001/XMLSchema", var.getDataTypeNameSpace());
        }

        @Test
        @DisplayName("GetPreferredName returns name when set")
        void getPreferredNameReturnsNameWhenSet() {
            YVariable var = new YVariable(gateway);
            var.setName("myVar");
            assertEquals("myVar", var.getPreferredName());
        }

        @Test
        @DisplayName("GetPreferredName returns elementName when name not set")
        void getPreferredNameReturnsElementNameWhenNameNotSet() {
            YVariable var = new YVariable(gateway);
            var.setElementName("elemName");
            assertEquals("elemName", var.getPreferredName());
        }

        @Test
        @DisplayName("GetDataTypeNameUnprefixed removes prefix")
        void getDataTypeNameUnprefixedRemovesPrefix() {
            YVariable var = new YVariable(gateway);
            var.setDataTypeAndName("xs:string", "myVar", null);

            assertEquals("string", var.getDataTypeNameUnprefixed());
            assertEquals("xs:", var.getDataTypePrefix());
        }

        @Test
        @DisplayName("IsUserDefinedType returns true for custom types")
        void isUserDefinedTypeReturnsTrueForCustomTypes() {
            YVariable var = new YVariable(gateway);
            var.setDataTypeAndName("customType", "myVar", null);

            assertTrue(var.isUserDefinedType());
        }

        @Test
        @DisplayName("IsUserDefinedType returns false for XSD types")
        void isUserDefinedTypeReturnsFalseForXsdTypes() {
            YVariable var = new YVariable(gateway);
            var.setDataTypeAndName("xs:string", "myVar", "http://www.w3.org/2001/XMLSchema");

            assertFalse(var.isUserDefinedType());
        }
    }

    @Nested
    @DisplayName("Untyped and Element Tests")
    class UntypedAndElementTests {

        @Test
        @DisplayName("SetUntyped should store correctly")
        void setUntypedStoresCorrectly() {
            YVariable var = new YVariable(gateway);
            var.setUntyped(true);
            assertTrue(var.isUntyped());
        }

        @Test
        @DisplayName("SetElementName should store correctly")
        void setElementNameStoresCorrectly() {
            YVariable var = new YVariable(gateway);
            var.setElementName("myElement");
            assertEquals("myElement", var.getElementName());
        }

        @Test
        @DisplayName("UsesElementDeclaration returns true when element name set")
        void usesElementDeclarationReturnsTrueWhenElementNameSet() {
            YVariable var = new YVariable(gateway);
            var.setElementName("myElement");
            assertTrue(var.usesElementDeclaration());
        }

        @Test
        @DisplayName("UsesTypeDeclaration returns true when type name set")
        void usesTypeDeclarationReturnsTrueWhenTypeNameSet() {
            YVariable var = new YVariable(gateway);
            var.setDataTypeAndName("xs:string", "myVar", null);
            assertTrue(var.usesTypeDeclaration());
        }

        @Test
        @DisplayName("Empty typed can be set")
        void emptyTypedCanBeSet() {
            YVariable var = new YVariable(gateway);
            var.setEmptyTyped(true);
            assertTrue(var.isEmptyTyped());
        }
    }

    @Nested
    @DisplayName("Initial and Default Value Tests")
    class InitialDefaultValueTests {

        @Test
        @DisplayName("SetInitialValue should store correctly")
        void setInitialValueStoresCorrectly() {
            YVariable var = new YVariable(gateway);
            var.setInitialValue("<value>test</value>");
            assertEquals("<value>test</value>", var.getInitialValue());
        }

        @Test
        @DisplayName("SetDefaultValue should store correctly")
        void setDefaultValueStoresCorrectly() {
            YVariable var = new YVariable(gateway);
            var.setDefaultValue("<default>value</default>");
            assertEquals("<default>value</default>", var.getDefaultValue());
        }

        @Test
        @DisplayName("Initial value can be null")
        void initialValueCanBeNullOrEmpty() {
            YVariable var = new YVariable(gateway);
            var.setInitialValue(null);
            assertNull(var.getInitialValue());
        }
    }

    @Nested
    @DisplayName("Mandatory and Optional Tests")
    class MandatoryOptionalTests {

        @Test
        @DisplayName("SetMandatory should store correctly")
        void setMandatoryStoresCorrectly() {
            YVariable var = new YVariable(gateway);
            var.setMandatory(true);
            assertTrue(var.isMandatory());
        }

        @Test
        @DisplayName("Default mandatory is false")
        void defaultMandatoryIsFalse() {
            YVariable var = new YVariable(gateway);
            assertFalse(var.isMandatory());
        }

        @Test
        @DisplayName("SetOptional should add attribute")
        void setOptionalAddsAttribute() {
            YVariable var = new YVariable(gateway);
            var.setOptional(true);
            assertTrue(var.isOptional());
        }

        @Test
        @DisplayName("SetOptional false removes attribute")
        void setOptionalFalseRemovesAttribute() {
            YVariable var = new YVariable(gateway);
            var.setOptional(true);
            var.setOptional(false);
            assertFalse(var.isOptional());
        }

        @Test
        @DisplayName("IsRequired returns true for mandatory")
        void isRequiredReturnsTrueForMandatory() {
            YVariable var = new YVariable(gateway);
            var.setMandatory(true);
            assertTrue(var.isRequired());
        }
    }

    @Nested
    @DisplayName("Attribute Tests")
    class AttributeTests {

        @Test
        @DisplayName("AddAttribute should store correctly")
        void addAttributeStoresCorrectly() {
            YVariable var = new YVariable(gateway);
            var.addAttribute("key1", "value1");
            assertEquals("value1", var.getAttributes().get("key1"));
        }

        @Test
        @DisplayName("HasAttributes returns true when attributes exist")
        void hasAttributesReturnsTrueWhenExist() {
            YVariable var = new YVariable(gateway);
            var.addAttribute("key1", "value1");
            assertTrue(var.hasAttributes());
        }

        @Test
        @DisplayName("HasAttributes returns false when no attributes")
        void hasAttributesReturnsFalseWhenNone() {
            YVariable var = new YVariable(gateway);
            assertFalse(var.hasAttributes());
        }

        @Test
        @DisplayName("GetAttributes returns YAttributeMap")
        void getAttributesReturnsYAttributeMap() {
            YVariable var = new YVariable(gateway);
            assertNotNull(var.getAttributes());
            assertTrue(var.getAttributes() instanceof YAttributeMap);
        }

        @Test
        @DisplayName("AddAttribute with DynamicValue works")
        void addAttributeWithDynamicValueWorks() {
            YVariable var = new YVariable(gateway);
            DynamicValue dv = new DynamicValue("test", var);
            var.addAttribute("dynamic", dv);
            assertNotNull(var.getAttributes().get("dynamic"));
        }

        @Test
        @DisplayName("SetAttributes replaces all attributes")
        void setAttributesReplacesAll() {
            YVariable var = new YVariable(gateway);
            var.addAttribute("old", "value");

            java.util.Map<String, String> newAttrs = new java.util.HashMap<>();
            newAttrs.put("new", "value");
            var.setAttributes(newAttrs);

            assertFalse(var.getAttributes().containsKey("old"));
            assertEquals("value", var.getAttributes().get("new"));
        }
    }

    @Nested
    @DisplayName("Documentation and Ordering Tests")
    class DocumentationOrderingTests {

        @Test
        @DisplayName("SetDocumentation should store correctly")
        void setDocumentationStoresCorrectly() {
            YVariable var = new YVariable(gateway);
            var.setDocumentation("This is a test variable");
            assertEquals("This is a test variable", var.getDocumentation());
        }

        @Test
        @DisplayName("SetOrdering should store correctly")
        void setOrderingStoresCorrectly() {
            YVariable var = new YVariable(gateway);
            var.setOrdering(10);
            assertEquals(10, var.getOrdering());
        }

        @Test
        @DisplayName("Default ordering is zero")
        void defaultOrderingIsZero() {
            YVariable var = new YVariable(gateway);
            assertEquals(0, var.getOrdering());
        }
    }

    @Nested
    @DisplayName("Log Predicate Tests")
    class LogPredicateTests {

        @Test
        @DisplayName("SetLogPredicate should store correctly")
        void setLogPredicateStoresCorrectly() {
            YVariable var = new YVariable(gateway);
            YLogPredicate predicate = new YLogPredicate();
            var.setLogPredicate(predicate);
            assertEquals(predicate, var.getLogPredicate());
        }

        @Test
        @DisplayName("Default log predicate is null")
        void defaultLogPredicateIsNull() {
            YVariable var = new YVariable(gateway);
            assertNull(var.getLogPredicate());
        }
    }

    @Nested
    @DisplayName("Clone Tests")
    class CloneTests {

        @Test
        @DisplayName("Clone should create copy")
        void cloneCreatesCopy() throws CloneNotSupportedException {
            YVariable var = new YVariable(gateway);
            var.setName("original");
            var.setOrdering(5);

            YVariable cloned = (YVariable) var.clone();

            assertEquals(var.getName(), cloned.getName());
            assertEquals(var.getOrdering(), cloned.getOrdering());
        }

        @Test
        @DisplayName("Clone is different object")
        void cloneIsDifferentObject() throws CloneNotSupportedException {
            YVariable var = new YVariable(gateway);
            YVariable cloned = (YVariable) var.clone();
            assertNotSame(var, cloned);
        }
    }

    @Nested
    @DisplayName("ToXML Tests")
    class ToXMLTests {

        @Test
        @DisplayName("ToXML should contain localVariable element")
        void toXmlContainsLocalVariableElement() {
            YVariable var = new YVariable(gateway);
            var.setName("testVar");
            var.setDataTypeAndName("xs:string", "testVar", "http://www.w3.org/2001/XMLSchema");

            String xml = var.toXML();
            assertTrue(xml.contains("<localVariable>"));
            assertTrue(xml.contains("</localVariable>"));
        }

        @Test
        @DisplayName("ToXML should contain name")
        void toXmlContainsName() {
            YVariable var = new YVariable(gateway);
            var.setName("myVar");
            var.setDataTypeAndName("xs:string", "myVar", null);

            String xml = var.toXML();
            assertTrue(xml.contains("<name>myVar</name>"));
        }

        @Test
        @DisplayName("ToXML should contain type")
        void toXmlContainsType() {
            YVariable var = new YVariable(gateway);
            var.setName("myVar");
            var.setDataTypeAndName("xs:int", "myVar", "http://www.w3.org/2001/XMLSchema");

            String xml = var.toXML();
            assertTrue(xml.contains("<type>xs:int</type>"));
        }

        @Test
        @DisplayName("ToXML should contain untyped flag when set")
        void toXmlContainsUntypedFlag() {
            YVariable var = new YVariable(gateway);
            var.setName("myVar");
            var.setUntyped(true);

            String xml = var.toXML();
            assertTrue(xml.contains("<isUntyped/>"));
        }
    }

    @Nested
    @DisplayName("CompareTo Tests")
    class CompareToTests {

        @Test
        @DisplayName("CompareTo orders by ordering value")
        void compareToOrdersByOrderingValue() {
            YVariable var1 = new YVariable(gateway);
            YVariable var2 = new YVariable(gateway);

            var1.setOrdering(1);
            var2.setOrdering(2);

            assertTrue(var1.compareTo(var2) < 0);
            assertTrue(var2.compareTo(var1) > 0);
            assertEquals(0, var1.compareTo(var1));
        }
    }

    @Nested
    @DisplayName("Parent Decomposition Tests")
    class ParentDecompositionTests {

        @Test
        @DisplayName("SetParentDecomposition should update parent")
        void setParentDecompositionUpdatesParent() {
            YVariable var = new YVariable();
            var.setParentDecomposition(gateway);
            assertEquals(gateway, var.getParentDecomposition());
        }
    }
}
