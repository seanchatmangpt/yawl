/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.elements.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

/**
 * Test class to verify the Optional migration for YVariable methods.
 *
 * @author Claude Migration Assistant
 * @date 2026-02-25
 */
public class YVariableOptionalTest {

    @Test
    public void testGetInitialValueWhenPresent() {
        // Test when initial value is set
        YVariable variable = new YVariable();
        variable.setInitialValue("testValue");

        Optional<String> result = variable.getInitialValue();

        assertTrue(result.isPresent());
        assertEquals("testValue", result.get());
    }

    @Test
    public void testGetInitialValueWhenAbsent() {
        // Test when initial value is null (not set)
        YVariable variable = new YVariable();

        Optional<String> result = variable.getInitialValue();

        assertFalse(result.isPresent());
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetInitialValueOrElse() {
        YVariable variable1 = new YVariable();
        variable1.setInitialValue("actualValue");

        YVariable variable2 = new YVariable();

        // Test orElse with present value
        String result1 = variable1.getInitialValue().orElse("default");
        assertEquals("actualValue", result1);

        // Test orElse with absent value
        String result2 = variable2.getInitialValue().orElse("default");
        assertEquals("default", result2);
    }

    @Test
    public void testGetInitialValueIfPresent() {
        YVariable variable1 = new YVariable();
        variable1.setInitialValue("test");

        YVariable variable2 = new YVariable();

        // Test ifPresent with value present
        StringBuilder captured = new StringBuilder();
        variable1.getInitialValue().ifPresent(captured::append);
        assertEquals("test", captured.toString());

        // Test ifPresent with value absent
        StringBuilder empty = new StringBuilder();
        variable2.getInitialValue().ifPresent(empty::append);
        assertEquals("", empty.toString());
    }

    @Test
    public void testGetDefaultValueWhenPresent() {
        YVariable variable = new YVariable();
        variable.setDefaultValue("defaultValue");

        Optional<String> result = variable.getDefaultValue();

        assertTrue(result.isPresent());
        assertEquals("defaultValue", result.get());
    }

    @Test
    public void testGetDefaultValueWhenAbsent() {
        YVariable variable = new YVariable();

        Optional<String> result = variable.getDefaultValue();

        assertFalse(result.isPresent());
    }

    @Test
    public void testStreamProcessingExample() {
        // Create multiple variables with different states
        YVariable var1 = new YVariable();
        var1.setInitialValue("value1");

        YVariable var2 = new YVariable();
        var2.setInitialValue("value2");

        YVariable var3 = new YVariable();
        // No initial value

        // Stream processing example
        java.util.List<String> values = java.util.List.of(var1, var2, var3).stream()
            .map(YVariable::getInitialValue)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(java.util.stream.Collectors.toList());

        assertEquals(2, values.size());
        assertTrue(values.contains("value1"));
        assertTrue(values.contains("value2"));
    }

    @Test
    public void testNullSafety() {
        // Verify no NullPointerException can occur
        YVariable variable = new YVariable();

        // These operations should never throw NPE
        assertDoesNotThrow(() -> variable.getInitialValue().isPresent());
        assertDoesNotThrow(() -> variable.getInitialValue().isEmpty());
        assertDoesNotThrow(() -> variable.getInitialValue().orElse("safe"));
        assertDoesNotThrow(() -> variable.getInitialValue().ifPresent(v -> {}));
    }
}