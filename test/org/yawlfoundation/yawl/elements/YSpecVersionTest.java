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

package org.yawlfoundation.yawl.elements;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Comprehensive test coverage for YSpecVersion class.
 * 
 * Tests cover:
 * - Constructors (default, int parameters, string parameter)
 * - Version parsing (valid and invalid formats)
 * - Version manipulation (increment, rollback)
 * - Comparison operations
 * - Edge cases (null, invalid formats, negative numbers)
 * 
 * Target: 100% line and branch coverage
 * 
 * @author YAWL Test Team
 * @since 5.2
 */
class YSpecVersionTest {

    // ===== Constructor Tests =====

    @Test
    void testDefaultConstructor_createsVersion0_1() {
        YSpecVersion version = new YSpecVersion();
        assertEquals("0.1", version.getVersion());
        assertEquals(0, version.getMajorVersion());
        assertEquals(1, version.getMinorVersion());
    }

    @Test
    void testIntConstructor_validValues() {
        YSpecVersion version = new YSpecVersion(2, 5);
        assertEquals("2.5", version.getVersion());
        assertEquals(2, version.getMajorVersion());
        assertEquals(5, version.getMinorVersion());
    }

    @Test
    void testStringConstructor_validDottedFormat() {
        YSpecVersion version = new YSpecVersion("3.14");
        assertEquals("3.14", version.getVersion());
        assertEquals(3, version.getMajorVersion());
        assertEquals(14, version.getMinorVersion());
    }

    @Test
    void testStringConstructor_nullValue_usesDefault() {
        YSpecVersion version = new YSpecVersion((String) null);
        assertEquals("0.1", version.getVersion());
    }

    @Test
    void testStringConstructor_singleNumber_setsMinorToZero() {
        YSpecVersion version = new YSpecVersion("5");
        assertEquals("5.0", version.getVersion());
        assertEquals(5, version.getMajorVersion());
        assertEquals(0, version.getMinorVersion());
    }

    @Test
    void testStringConstructor_zeroMajor_setsMinorToOne() {
        YSpecVersion version = new YSpecVersion("0");
        assertEquals("0.1", version.getVersion());
        assertEquals(0, version.getMajorVersion());
        assertEquals(1, version.getMinorVersion());
    }

    // ===== Version Parsing Tests =====

    @ParameterizedTest
    @CsvSource({
        "1.0, 1, 0",
        "2.5, 2, 5",
        "10.99, 10, 99",
        "0.1, 0, 1",
        "100.200, 100, 200"
    })
    void testSetVersion_validFormats(String input, int expectedMajor, int expectedMinor) {
        YSpecVersion version = new YSpecVersion();
        version.setVersion(input);
        assertEquals(expectedMajor, version.getMajorVersion());
        assertEquals(expectedMinor, version.getMinorVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "a.b", "1.2.3", "", "  ", "x"})
    void testSetVersion_invalidFormats_usesDefault(String invalid) {
        YSpecVersion version = new YSpecVersion();
        version.setVersion(invalid);
        assertEquals("0.1", version.getVersion());
    }

    @Test
    void testSetVersion_withIntegers() {
        YSpecVersion version = new YSpecVersion();
        String result = version.setVersion(7, 3);
        assertEquals("7.3", result);
        assertEquals(7, version.getMajorVersion());
        assertEquals(3, version.getMinorVersion());
    }

    // ===== Version Manipulation Tests =====

    @Test
    void testMinorIncrement() {
        YSpecVersion version = new YSpecVersion(1, 5);
        String result = version.minorIncrement();
        assertEquals("1.6", result);
        assertEquals(6, version.getMinorVersion());
    }

    @Test
    void testMajorIncrement() {
        YSpecVersion version = new YSpecVersion(1, 5);
        String result = version.majorIncrement();
        assertEquals("2.5", result);
        assertEquals(2, version.getMajorVersion());
    }

    @Test
    void testMinorRollback() {
        YSpecVersion version = new YSpecVersion(1, 5);
        String result = version.minorRollback();
        assertEquals("1.4", result);
        assertEquals(4, version.getMinorVersion());
    }

    @Test
    void testMajorRollback() {
        YSpecVersion version = new YSpecVersion(2, 5);
        String result = version.majorRollback();
        assertEquals("1.5", result);
        assertEquals(1, version.getMajorVersion());
    }

    @Test
    void testMultipleIncrements() {
        YSpecVersion version = new YSpecVersion(1, 0);
        version.minorIncrement();
        version.minorIncrement();
        version.majorIncrement();
        assertEquals("2.2", version.getVersion());
    }

    // ===== Comparison Tests =====

    @Test
    void testCompareTo_equalVersions_returnsZero() {
        YSpecVersion v1 = new YSpecVersion(2, 5);
        YSpecVersion v2 = new YSpecVersion(2, 5);
        assertEquals(0, v1.compareTo(v2));
    }

    @Test
    void testCompareTo_differentMajor_comparesCorrectly() {
        YSpecVersion v1 = new YSpecVersion(3, 0);
        YSpecVersion v2 = new YSpecVersion(2, 9);
        assertTrue(v1.compareTo(v2) > 0);
        assertTrue(v2.compareTo(v1) < 0);
    }

    @Test
    void testCompareTo_sameMajorDifferentMinor_comparesCorrectly() {
        YSpecVersion v1 = new YSpecVersion(2, 5);
        YSpecVersion v2 = new YSpecVersion(2, 3);
        assertTrue(v1.compareTo(v2) > 0);
        assertTrue(v2.compareTo(v1) < 0);
    }

    @Test
    void testEqualsMajorVersion_sameMajor_returnsTrue() {
        YSpecVersion v1 = new YSpecVersion(2, 5);
        YSpecVersion v2 = new YSpecVersion(2, 9);
        assertTrue(v1.equalsMajorVersion(v2));
    }

    @Test
    void testEqualsMajorVersion_differentMajor_returnsFalse() {
        YSpecVersion v1 = new YSpecVersion(2, 5);
        YSpecVersion v2 = new YSpecVersion(3, 5);
        assertFalse(v1.equalsMajorVersion(v2));
    }

    @Test
    void testEqualsMinorVersion_sameMinor_returnsTrue() {
        YSpecVersion v1 = new YSpecVersion(2, 5);
        YSpecVersion v2 = new YSpecVersion(3, 5);
        assertTrue(v1.equalsMinorVersion(v2));
    }

    @Test
    void testEqualsMinorVersion_differentMinor_returnsFalse() {
        YSpecVersion v1 = new YSpecVersion(2, 5);
        YSpecVersion v2 = new YSpecVersion(2, 6);
        assertFalse(v1.equalsMinorVersion(v2));
    }

    // ===== Equals and HashCode Tests =====

    @Test
    void testEquals_sameValues_returnsTrue() {
        YSpecVersion v1 = new YSpecVersion(2, 5);
        YSpecVersion v2 = new YSpecVersion(2, 5);
        assertTrue(v1.equals(v2));
    }

    @Test
    void testEquals_differentMajor_returnsFalse() {
        YSpecVersion v1 = new YSpecVersion(2, 5);
        YSpecVersion v2 = new YSpecVersion(3, 5);
        assertFalse(v1.equals(v2));
    }

    @Test
    void testEquals_differentMinor_returnsFalse() {
        YSpecVersion v1 = new YSpecVersion(2, 5);
        YSpecVersion v2 = new YSpecVersion(2, 6);
        assertFalse(v1.equals(v2));
    }

    @Test
    void testEquals_nullObject_returnsFalse() {
        YSpecVersion v1 = new YSpecVersion(2, 5);
        assertFalse(v1.equals(null));
    }

    @Test
    void testEquals_differentType_returnsFalse() {
        YSpecVersion v1 = new YSpecVersion(2, 5);
        assertFalse(v1.equals("2.5"));
    }

    @Test
    void testEquals_sameObject_returnsTrue() {
        YSpecVersion v1 = new YSpecVersion(2, 5);
        assertTrue(v1.equals(v1));
    }

    @Test
    void testHashCode_equalObjects_haveSameHashCode() {
        YSpecVersion v1 = new YSpecVersion(2, 5);
        YSpecVersion v2 = new YSpecVersion(2, 5);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void testHashCode_differentObjects_mayHaveDifferentHashCode() {
        YSpecVersion v1 = new YSpecVersion(2, 5);
        YSpecVersion v2 = new YSpecVersion(3, 6);
        // Note: Different objects may have same hash (collision), but typically don't
        // We just verify hashCode() doesn't throw an exception
        assertNotNull(v1.hashCode());
        assertNotNull(v2.hashCode());
    }

    // ===== Legacy Method Tests =====

    @Test
    void testToDouble_validVersion() {
        YSpecVersion version = new YSpecVersion(2, 5);
        assertEquals(2.5, version.toDouble(), 0.001);
    }

    @Test
    void testToDouble_defaultVersion() {
        YSpecVersion version = new YSpecVersion();
        assertEquals(0.1, version.toDouble(), 0.001);
    }

    // ===== toString Tests =====

    @Test
    void testToString_formatsCorrectly() {
        YSpecVersion version = new YSpecVersion(12, 34);
        assertEquals("12.34", version.toString());
    }

    @Test
    void testGetVersion_returnsSameAsToString() {
        YSpecVersion version = new YSpecVersion(5, 7);
        assertEquals(version.toString(), version.getVersion());
    }

    // ===== Edge Case Tests =====

    @Test
    void testLargeVersionNumbers() {
        YSpecVersion version = new YSpecVersion(9999, 9999);
        assertEquals("9999.9999", version.getVersion());
    }

    @Test
    void testNegativeVersionNumbers_acceptedButNotRecommended() {
        YSpecVersion version = new YSpecVersion(-1, -1);
        assertEquals("-1.-1", version.getVersion());
    }

    @Test
    void testRollbackBelowZero() {
        YSpecVersion version = new YSpecVersion(0, 0);
        version.minorRollback();
        assertEquals("-1", String.valueOf(version.getMinorVersion()));
    }

    @Test
    void testVersionWithLeadingZeros() {
        YSpecVersion version = new YSpecVersion("001.002");
        assertEquals("1.2", version.getVersion());
    }

    // ===== Integration Tests =====

    @Test
    void testCompleteVersionLifecycle() {
        YSpecVersion version = new YSpecVersion();
        assertEquals("0.1", version.getVersion());
        
        version.minorIncrement();
        assertEquals("0.2", version.getVersion());
        
        version.majorIncrement();
        assertEquals("1.2", version.getVersion());
        
        version.setVersion(5, 0);
        assertEquals("5.0", version.getVersion());
        
        version.minorIncrement();
        version.minorIncrement();
        version.minorIncrement();
        assertEquals("5.3", version.getVersion());
    }

    @Test
    void testVersionComparison_sortOrder() {
        YSpecVersion v1 = new YSpecVersion(1, 0);
        YSpecVersion v2 = new YSpecVersion(1, 5);
        YSpecVersion v3 = new YSpecVersion(2, 0);
        YSpecVersion v4 = new YSpecVersion(2, 3);
        
        assertTrue(v1.compareTo(v2) < 0);
        assertTrue(v2.compareTo(v3) < 0);
        assertTrue(v3.compareTo(v4) < 0);
        assertTrue(v4.compareTo(v1) > 0);
    }
}
