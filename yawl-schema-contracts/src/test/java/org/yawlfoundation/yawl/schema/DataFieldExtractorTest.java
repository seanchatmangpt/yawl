/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.schema;

import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DataFieldExtractor}.
 *
 * @since 6.0.0
 */
class DataFieldExtractorTest {

    @Test
    void fromElement_nullElement_returnsEmptyMap() {
        Map<String, String> result = DataFieldExtractor.fromElement(null);

        assertTrue(result.isEmpty(), "Null element should produce empty map");
        assertNotNull(result, "Result map should not be null");
    }

    @Test
    void fromElement_elementWithThreeChildren_extractsAllFields() {
        Element data = new Element("taskData");
        data.addContent(new Element("order_id").setText("12345"));
        data.addContent(new Element("customer_id").setText("CUST-001"));
        data.addContent(new Element("status").setText("CONFIRMED"));

        Map<String, String> result = DataFieldExtractor.fromElement(data);

        assertEquals(3, result.size());
        assertEquals("12345", result.get("order_id"));
        assertEquals("CUST-001", result.get("customer_id"));
        assertEquals("CONFIRMED", result.get("status"));
    }

    @Test
    void fromElement_singleField_extractsCorrectly() {
        Element data = new Element("input");
        data.addContent(new Element("transaction_id").setText("TXN-999"));

        Map<String, String> result = DataFieldExtractor.fromElement(data);

        assertEquals(1, result.size());
        assertEquals("TXN-999", result.get("transaction_id"));
    }

    @Test
    void fromElement_emptyElement_returnsEmptyMap() {
        Element data = new Element("taskData");

        Map<String, String> result = DataFieldExtractor.fromElement(data);

        assertTrue(result.isEmpty(), "Element with no children should produce empty map");
    }

    @Test
    void fromElement_elementWithEmptyChildElements_extractsEmptyStrings() {
        Element data = new Element("taskData");
        data.addContent(new Element("field_1").setText(""));
        data.addContent(new Element("field_2").setText(""));

        Map<String, String> result = DataFieldExtractor.fromElement(data);

        assertEquals(2, result.size());
        assertEquals("", result.get("field_1"));
        assertEquals("", result.get("field_2"));
    }

    @Test
    void fromElement_preservesFieldOrder_linkedHashMap() {
        Element data = new Element("taskData");
        data.addContent(new Element("field_1").setText("value_1"));
        data.addContent(new Element("field_2").setText("value_2"));
        data.addContent(new Element("field_3").setText("value_3"));

        Map<String, String> result = DataFieldExtractor.fromElement(data);

        // LinkedHashMap preserves insertion order
        var keys = result.keySet().stream().toList();
        assertEquals("field_1", keys.get(0));
        assertEquals("field_2", keys.get(1));
        assertEquals("field_3", keys.get(2));
    }

    @Test
    void fromElement_fieldWithSpecialCharacters_extractedAsIs() {
        Element data = new Element("taskData");
        data.addContent(new Element("amount").setText("$1,234.56"));
        data.addContent(new Element("description").setText("Order with <special> & 'chars'"));

        Map<String, String> result = DataFieldExtractor.fromElement(data);

        assertEquals("$1,234.56", result.get("amount"));
        assertEquals("Order with <special> & 'chars'", result.get("description"));
    }

    @Test
    void fromElement_nestedElements_onlyDirectChildrenExtracted() {
        Element data = new Element("taskData");
        Element child = new Element("address");
        child.addContent(new Element("street").setText("123 Main St"));
        data.addContent(child);

        Map<String, String> result = DataFieldExtractor.fromElement(data);

        // Only direct child is extracted, nested content combined as text
        assertEquals(1, result.size());
        assertTrue(result.containsKey("address"));
    }

    @Test
    void fromDocument_nullDocument_returnsEmptyMap() {
        Map<String, String> result = DataFieldExtractor.fromDocument(null);

        assertTrue(result.isEmpty(), "Null document should produce empty map");
    }

    @Test
    void fromDocument_documentWithFields_extractsFromRoot() {
        Element root = new Element("output");
        root.addContent(new Element("fulfillment_id").setText("FULFILL-001"));
        root.addContent(new Element("shipped").setText("true"));
        Document doc = new Document(root);

        Map<String, String> result = DataFieldExtractor.fromDocument(doc);

        assertEquals(2, result.size());
        assertEquals("FULFILL-001", result.get("fulfillment_id"));
        assertEquals("true", result.get("shipped"));
    }

    @Test
    void fromDocument_complexDocument_extractsRootElements() {
        Element root = new Element("response");
        root.addContent(new Element("status").setText("SUCCESS"));
        root.addContent(new Element("message").setText("Processing complete"));
        root.addContent(new Element("timestamp").setText("2026-03-01T14:30:00Z"));
        Document doc = new Document(root);

        Map<String, String> result = DataFieldExtractor.fromDocument(doc);

        assertEquals(3, result.size());
        assertEquals("SUCCESS", result.get("status"));
        assertEquals("Processing complete", result.get("message"));
        assertEquals("2026-03-01T14:30:00Z", result.get("timestamp"));
    }

    @Test
    void fromElement_resultIsImmutable() {
        Element data = new Element("taskData");
        data.addContent(new Element("order_id").setText("12345"));

        Map<String, String> result = DataFieldExtractor.fromElement(data);

        assertThrows(UnsupportedOperationException.class,
                () -> result.put("extra", "value"),
                "Result map should be immutable");
    }

    @Test
    void fromDocument_resultIsImmutable() {
        Element root = new Element("output");
        root.addContent(new Element("id").setText("123"));
        Document doc = new Document(root);

        Map<String, String> result = DataFieldExtractor.fromDocument(doc);

        assertThrows(UnsupportedOperationException.class,
                () -> result.put("new_field", "value"),
                "Result map should be immutable");
    }

    @Test
    void fromElement_multipleCallsSameElement_producesIndependentMaps() {
        Element data = new Element("taskData");
        data.addContent(new Element("field").setText("value"));

        Map<String, String> result1 = DataFieldExtractor.fromElement(data);
        Map<String, String> result2 = DataFieldExtractor.fromElement(data);

        assertEquals(result1, result2);
        assertNotSame(result1, result2, "Each call should return a new map instance");
    }

    @Test
    void fromDocument_delegatesToFromElement() {
        Element root = new Element("data");
        root.addContent(new Element("key").setText("value"));
        Document doc = new Document(root);

        // Should extract from root element
        Map<String, String> result = DataFieldExtractor.fromDocument(doc);

        assertEquals(1, result.size());
        assertEquals("value", result.get("key"));
    }

    @Test
    void fromElement_largeDataSet_extractsAllFields() {
        Element data = new Element("taskData");
        for (int i = 1; i <= 100; i++) {
            data.addContent(new Element("field_" + i).setText("value_" + i));
        }

        Map<String, String> result = DataFieldExtractor.fromElement(data);

        assertEquals(100, result.size());
        assertEquals("value_1", result.get("field_1"));
        assertEquals("value_50", result.get("field_50"));
        assertEquals("value_100", result.get("field_100"));
    }

    @Test
    void fromElement_fieldNamesWithUnderscores_extractedCorrectly() {
        Element data = new Element("taskData");
        data.addContent(new Element("first_name").setText("John"));
        data.addContent(new Element("last_name").setText("Doe"));
        data.addContent(new Element("phone_number").setText("555-1234"));

        Map<String, String> result = DataFieldExtractor.fromElement(data);

        assertEquals("John", result.get("first_name"));
        assertEquals("Doe", result.get("last_name"));
        assertEquals("555-1234", result.get("phone_number"));
    }
}
