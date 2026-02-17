package org.yawlfoundation.yawl.logging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for YLogDataItem - the immutable logging data record.
 *
 * Verifies construction, accessor methods, and the with* copy-modifier
 * methods introduced in the Java 25 record conversion. XML serialisation
 * tests are excluded because the yawl-utilities test classpath has a
 * log4j-slf4j bridge conflict that prevents JDOMUtil from initialising.
 *
 * @author YAWL Foundation
 * @since 5.2
 */
class TestYLogDataItem {

    // Canonical constructor field order: (name, value, dataTypeName, dataTypeDefinition, descriptor)

    @Test
    void testDefaultConstructorProducesNullFields() {
        YLogDataItem item = new YLogDataItem();
        assertNull(item.name());
        assertNull(item.value());
        assertNull(item.dataTypeName());
        assertNull(item.dataTypeDefinition());
        assertNull(item.descriptor());
    }

    @Test
    void testCanonicalConstructorStoresAllFields() {
        YLogDataItem item = new YLogDataItem("itemName", "itemValue", "xsd:string",
                "stringDefinition", "inputData");
        assertEquals("itemName", item.name());
        assertEquals("itemValue", item.value());
        assertEquals("xsd:string", item.dataTypeName());
        assertEquals("stringDefinition", item.dataTypeDefinition());
        assertEquals("inputData", item.descriptor());
    }

    @Test
    void testDescriptorConstructorDefaultsDataTypeDefinitionToDataTypeName() {
        // 4-arg constructor: (descriptor, name, value, dataTypeName)
        YLogDataItem item = new YLogDataItem("outputData", "amount", "42.50", "xsd:decimal");
        assertEquals("amount", item.name());
        assertEquals("42.50", item.value());
        assertEquals("xsd:decimal", item.dataTypeName());
        assertEquals("xsd:decimal", item.dataTypeDefinition());
        assertEquals("outputData", item.descriptor());
    }

    @Test
    void testWithValueCreatesNewInstanceWithUpdatedValue() {
        YLogDataItem original = new YLogDataItem("desc", "name", "oldVal", "xsd:string");
        YLogDataItem updated = original.withValue("newVal");
        assertEquals("newVal", updated.value());
        assertEquals("oldVal", original.value());
        assertEquals(original.name(), updated.name());
        assertEquals(original.descriptor(), updated.descriptor());
    }

    @Test
    void testWithValueObjectCreatesNewInstanceWithStringifiedValue() {
        YLogDataItem original = new YLogDataItem("desc", "count", "0", "xsd:integer");
        YLogDataItem updated = original.withValue(Integer.valueOf(99));
        assertEquals("99", updated.value());
        assertEquals("0", original.value());
    }

    @Test
    void testWithNameCreatesNewInstanceWithUpdatedName() {
        YLogDataItem original = new YLogDataItem("desc", "oldName", "val", "xsd:string");
        YLogDataItem updated = original.withName("newName");
        assertEquals("newName", updated.name());
        assertEquals("oldName", original.name());
    }

    @Test
    void testWithDescriptorCreatesNewInstanceWithUpdatedDescriptor() {
        YLogDataItem original = new YLogDataItem("oldDesc", "n", "v", "xsd:string");
        YLogDataItem updated = original.withDescriptor("newDesc");
        assertEquals("newDesc", updated.descriptor());
        assertEquals("oldDesc", original.descriptor());
    }

    @Test
    void testWithDataTypeNameCreatesNewInstanceWithUpdatedDataType() {
        YLogDataItem original = new YLogDataItem("d", "n", "v", "xsd:string");
        YLogDataItem updated = original.withDataTypeName("xsd:integer");
        assertEquals("xsd:integer", updated.dataTypeName());
        assertEquals("xsd:string", original.dataTypeName());
    }

    @Test
    void testWithDataTypeDefinitionCreatesNewInstanceWithUpdatedDefinition() {
        // Canonical constructor: (name, value, dataTypeName, dataTypeDefinition, descriptor)
        YLogDataItem original = new YLogDataItem("myName", "myValue", "xsd:string", "def1", "myDescriptor");
        YLogDataItem updated = original.withDataTypeDefinition("def2");
        assertEquals("def2", updated.dataTypeDefinition());
        assertEquals("def1", original.dataTypeDefinition());
    }

    @Test
    void testLegacyGettersReturnSameAsRecordAccessors() {
        YLogDataItem item = new YLogDataItem("theName", "theValue", "xsd:boolean",
                "boolDef", "theDescriptor");
        assertEquals(item.name(), item.getName());
        assertEquals(item.value(), item.getValue());
        assertEquals(item.dataTypeName(), item.getDataTypeName());
        assertEquals(item.dataTypeDefinition(), item.getDataTypeDefinition());
        assertEquals(item.descriptor(), item.getDescriptor());
    }

    @Test
    void testRecordEqualityBasedOnAllFields() {
        YLogDataItem a = new YLogDataItem("n", "v", "xsd:string", "def", "d");
        YLogDataItem b = new YLogDataItem("n", "v", "xsd:string", "def", "d");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void testRecordInequalityWhenValueDiffers() {
        YLogDataItem a = new YLogDataItem("n", "v1", "xsd:string", "def", "d");
        YLogDataItem b = new YLogDataItem("n", "v2", "xsd:string", "def", "d");
        assertNotEquals(a, b);
    }

    @Test
    void testWithValuePreservesDataTypeFields() {
        YLogDataItem original = new YLogDataItem("myName", "oldVal", "xsd:integer", "intDef", "myDesc");
        YLogDataItem updated = original.withValue("newVal");
        assertEquals("xsd:integer", updated.dataTypeName());
        assertEquals("intDef", updated.dataTypeDefinition());
        assertEquals("myDesc", updated.descriptor());
        assertEquals("myName", updated.name());
    }

    @Test
    void testWithNamePreservesAllOtherFields() {
        YLogDataItem original = new YLogDataItem("orig", "val", "xsd:string", "def", "desc");
        YLogDataItem updated = original.withName("new");
        assertEquals("new", updated.name());
        assertEquals("val", updated.value());
        assertEquals("xsd:string", updated.dataTypeName());
        assertEquals("def", updated.dataTypeDefinition());
        assertEquals("desc", updated.descriptor());
    }
}
