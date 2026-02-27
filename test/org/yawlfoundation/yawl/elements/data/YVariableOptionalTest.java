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
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for YVariable initial value and default value accessors.
 * YVariable.getInitialValue() and getDefaultValue() return String (not Optional).
 */
public class YVariableOptionalTest {

    @Test
    public void testGetInitialValueWhenPresent() {
        YVariable variable = new YVariable();
        variable.setInitialValue("testValue");

        String result = variable.getInitialValue();

        assertNotNull(result);
        assertEquals("testValue", result);
    }

    @Test
    public void testGetInitialValueWhenAbsent() {
        YVariable variable = new YVariable();

        String result = variable.getInitialValue();

        assertNull(result);
    }

    @Test
    public void testGetInitialValueOrElse() {
        YVariable variable1 = new YVariable();
        variable1.setInitialValue("actualValue");

        YVariable variable2 = new YVariable();

        String result1 = variable1.getInitialValue() != null ? variable1.getInitialValue() : "default";
        assertEquals("actualValue", result1);

        String result2 = variable2.getInitialValue() != null ? variable2.getInitialValue() : "default";
        assertEquals("default", result2);
    }

    @Test
    public void testGetInitialValueIfPresent() {
        YVariable variable1 = new YVariable();
        variable1.setInitialValue("test");

        YVariable variable2 = new YVariable();

        StringBuilder captured = new StringBuilder();
        if (variable1.getInitialValue() != null) captured.append(variable1.getInitialValue());
        assertEquals("test", captured.toString());

        StringBuilder empty = new StringBuilder();
        if (variable2.getInitialValue() != null) empty.append(variable2.getInitialValue());
        assertEquals("", empty.toString());
    }

    @Test
    public void testGetDefaultValueWhenPresent() {
        YVariable variable = new YVariable();
        variable.setDefaultValue("defaultValue");

        String result = variable.getDefaultValue();

        assertNotNull(result);
        assertEquals("defaultValue", result);
    }

    @Test
    public void testGetDefaultValueWhenAbsent() {
        YVariable variable = new YVariable();

        String result = variable.getDefaultValue();

        assertNull(result);
    }

    @Test
    public void testStreamProcessingExample() {
        YVariable var1 = new YVariable();
        var1.setInitialValue("value1");

        YVariable var2 = new YVariable();
        var2.setInitialValue("value2");

        YVariable var3 = new YVariable();

        java.util.List<String> values = java.util.List.of(var1, var2, var3).stream()
            .map(YVariable::getInitialValue)
            .filter(v -> v != null)
            .collect(java.util.stream.Collectors.toList());

        assertEquals(2, values.size());
        assertTrue(values.contains("value1"));
        assertTrue(values.contains("value2"));
    }

    @Test
    public void testNullSafety() {
        YVariable variable = new YVariable();

        // Null-safe operations — no NPE expected
        assertDoesNotThrow(() -> {
            String v = variable.getInitialValue();
            boolean present = v != null;
            String safe = v != null ? v : "safe";
        });
    }
}
