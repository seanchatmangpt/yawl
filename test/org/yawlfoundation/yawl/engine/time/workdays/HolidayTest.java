/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.time.workdays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for Holiday class.
 *
 * <p>Chicago TDD: Tests use real Holiday instances and real date operations.
 * No mocks for domain objects.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("Holiday Tests")
@Tag("unit")
class HolidayTest {

    private Holiday newYearsDay;
    private christmasDay;
    private static final int TEST_YEAR = 2023;

    @BeforeEach
    void setUp() {
        // Create holidays for testing
        newYearsDay = new Holiday(1, 1, TEST_YEAR, "New Year's Day");
        christmasDay = new Holiday(25, 12, TEST_YEAR, "Christmas Day");
    }

    // ==================== Constructor Tests ====================

    @Test
    @DisplayName("Constructor with day, month, year, and name")
    void constructorWithDayMonthYearAndName() {
        // Arrange & Act
        Holiday holiday = new Holiday(4, 7, TEST_YEAR, "Independence Day");

        // Assert
        assertEquals(4, holiday.getDay());
        assertEquals(7, holiday.getMonth());
        assertEquals(TEST_YEAR, holiday.getYear());
        assertEquals("Independence Day", holiday.getName());
    }

    @Test
    @DisplayName("Constructor with day, month, year - verifies date calculation")
    void constructorWithDayMonthYear_verifiesDateCalculation() {
        // Arrange & Act
        Holiday holiday = new Holiday(29, 2, TEST_YEAR, "Leap Day"); // 2023 is not a leap year

        // Assert - should handle invalid dates gracefully
        assertEquals(29, holiday.getDay());
        assertEquals(2, holiday.getMonth());
        assertEquals(TEST_YEAR, holiday.getYear());
        assertEquals("Leap Day", holiday.getName());
    }

    // ==================== Getters Tests ====================

    @Test
    @DisplayName("Get day")
    void getDay() {
        // Act & Assert
        assertEquals(1, newYearsDay.getDay());
        assertEquals(25, christmasDay.getDay());
    }

    @Test
    @DisplayName("Get month")
    void getMonth() {
        // Act & Assert
        assertEquals(1, newYearsDay.getMonth());
        assertEquals(12, christmasDay.getMonth());
    }

    @Test
    @DisplayName("Get year")
    void getYear() {
        // Act & Assert
        assertEquals(TEST_YEAR, newYearsDay.getYear());
        assertEquals(TEST_YEAR, christmasDay.getYear());
    }

    @Test
    @DisplayName("Get name")
    void getName() {
        // Act & Assert
        assertEquals("New Year's Day", newYearsDay.getName());
        assertEquals("Christmas Day", christmasDay.getName());
    }

    @Test
    @DisplayName("Get time")
    void getTime() {
        // Act
        long newYearsTime = newYearsDay.getTime();
        long christmasTime = christmasDay.getTime();

        // Assert
        assertTrue(christmasTime > newYearsTime,
                   "Christmas should be after New Year's Day");
        assertTrue(newYearsTime > 0, "Time should be positive");
        assertTrue(christmasTime > 0, "Time should be positive");
    }

    // ==================== Setters Tests ====================

    @Test
    @DisplayName("Set name")
    void setName() {
        // Arrange
        String newName = "New Year's Holiday";

        // Act
        newYearsDay.setName(newName);

        // Assert
        assertEquals(newName, newYearsDay.getName());
    }

    @Test
    @DisplayName("Set time")
    void setTime() {
        // Arrange
        long newTime = System.currentTimeMillis();

        // Act
        christmasDay.setTime(newTime);

        // Assert
        assertEquals(newTime, christmasDay.getTime());
    }

    @Test
    @DisplayName("Set time - negative value")
    void setTime_negativeValue() {
        // Arrange
        long originalTime = christmasDay.getTime();

        // Act
        christmasDay.setTime(-1);

        // Assert
        // Should handle negative time gracefully by not updating
        assertEquals(originalTime, christmasDay.getTime(),
                    "Should not update with negative time");
    }

    @Test
    @DisplayName("Set time - zero value")
    void setTime_zeroValue() {
        // Arrange
        long originalTime = christmasDay.getTime();

        // Act
        christmasDay.setTime(0);

        // Assert
        // Should handle zero time gracefully
        assertEquals(0, christmasDay.getTime(), "Should update with zero time");
    }

    // ==================== Matches Method Tests ====================

    @Test
    @DisplayName("Matches - same date")
    void matches_sameDate() {
        // Arrange
        Calendar sameDate = new GregorianCalendar(TEST_YEAR, Calendar.JANUARY, 1);
        Calendar differentDate = new GregorianCalendar(TEST_YEAR, Calendar.DECEMBER, 25);

        // Act & Assert
        assertTrue(newYearsDay.matches(sameDate), "Should match same date");
        assertFalse(newYearsDay.matches(differentDate), "Should not match different date");
    }

    @Test
    @DisplayName("Matches - different year")
    void matches_differentYear() {
        // Arrange
        Calendar differentYear = new GregorianCalendar(TEST_YEAR + 1, Calendar.JANUARY, 1);

        // Act & Assert
        assertFalse(newYearsDay.matches(differentYear), "Should not match different year");
    }

    @Test
    @DisplayName("Matches - null calendar")
    void matches_nullCalendar() {
        // Act & Assert
        assertFalse(newYearsDay.matches(null), "Should not match null calendar");
    }

    @Test
    @DisplayName("Matches - null holiday date")
    void matches_nullHolidayDate() {
        // Arrange
        Holiday invalidHoliday = new Holiday(0, 0, 0, "Invalid");
        Calendar testDate = new GregorianCalendar(TEST_YEAR, Calendar.JANUARY, 1);

        // Act & Assert
        assertFalse(invalidHoliday.matches(testDate), "Should not match with invalid holiday date");
        assertFalse(newYearsDay.matches(null), "Should handle null test date");
    }

    // ==================== Is After Method Tests ====================

    @Test
    @DisplayName("Is after - later date")
    void isAfter_laterDate() {
        // Arrange
        Calendar earlierDate = new GregorianCalendar(TEST_YEAR, Calendar.JANUARY, 1);
        Calendar laterDate = new GregorianCalendar(TEST_YEAR, Calendar.DECEMBER, 31);

        // Act & Assert
        assertTrue(christmasDay.isAfter(earlierDate), "Christmas should be after New Year's");
        assertFalse(newYearsDay.isAfter(laterDate), "New Year's should not be after Christmas");
    }

    @Test
    @DisplayName("Is after - same date")
    void isAfter_sameDate() {
        // Arrange
        Calendar sameDate = new GregorianCalendar(TEST_YEAR, Calendar.JANUARY, 1);

        // Act & Assert
        assertFalse(newYearsDay.isAfter(sameDate), "Should not be after same date");
    }

    @Test
    @DisplayName("Is after - null calendar")
    void isAfter_nullCalendar() {
        // Act & Assert
        assertFalse(newYearsDay.isAfter(null), "Should not be after null calendar");
    }

    @Test
    @DisplayName("Is after - null holiday date")
    void isAfter_nullHolidayDate() {
        // Arrange
        Holiday invalidHoliday = new Holiday(0, 0, 0, "Invalid");
        Calendar testDate = new GregorianCalendar(TEST_YEAR, Calendar.JANUARY, 1);

        // Act & Assert
        assertFalse(invalidHoliday.isAfter(testDate), "Invalid holiday should not be after any date");
        assertFalse(newYearsDay.isAfter(null), "Should handle null test date");
    }

    // ==================== Compare To Tests ====================

    @Test
    @DisplayName("Compare to - same holiday")
    void compareTo_sameHoliday() {
        // Act
        int result = newYearsDay.compareTo(newYearsDay);

        // Assert
        assertEquals(0, result, "Should be equal to itself");
    }

    @Test
    @DisplayName("Compare to - earlier holiday")
    void compareTo_earlierHoliday() {
        // Act
        int result = newYearsDay.compareTo(christmasDay);

        // Assert
        assertTrue(result < 0, "New Year's should be before Christmas");
    }

    @Test
    @DisplayName("Compare to - later holiday")
    void compareTo_laterHoliday() {
        // Act
        int result = christmasDay.compareTo(newYearsDay);

        // Assert
        assertTrue(result > 0, "Christmas should be after New Year's");
    }

    @Test
    @DisplayName("Compare to - null holiday")
    void compareTo_nullHoliday() {
        // Act
        int result = newYearsDay.compareTo(null);

        // Assert
        assertTrue(result > 0, "Should be greater than null");
    }

    @Test
    @DisplayName("Compare to - same day different year")
    void compareTo_sameDayDifferentYear() {
        // Arrange
        Holiday nextYear = new Holiday(1, 1, TEST_YEAR + 1, "Next New Year");

        // Act
        int result = newYearsDay.compareTo(nextYear);

        // Assert
        assertTrue(result < 0, "Earlier year should be before later year");
    }

    // ==================== To String Tests ====================

    @Test
    @DisplayName("To string")
    void toStringTest() {
        // Act
        String str1 = newYearsDay.toString();
        String str2 = christmasDay.toString();

        // Assert
        assertNotNull(str1, "String representation should not be null");
        assertNotNull(str2, "String representation should not be null");
        assertTrue(str1.contains("New Year's Day"), "Should contain holiday name");
        assertTrue(str2.contains("Christmas Day"), "Should contain holiday name");
        assertTrue(str1.contains("2023"), "Should contain year");
        assertTrue(str2.contains("2023"), "Should contain year");
    }

    // ==================== Edge Cases Tests ====================

    @Test
    @DisplayName("Constructor with invalid date values")
    void constructorWithInvalidDateValues() {
        // Act & Assert - should handle invalid dates gracefully
        Holiday invalidDate1 = new Holiday(32, 1, TEST_YEAR, "Invalid Day");
        Holiday invalidDate2 = new Holiday(1, 13, TEST_YEAR, "Invalid Month");
        Holiday invalidDate3 = new Holiday(0, 0, 0, "Invalid All");

        assertEquals(32, invalidDate1.getDay(), "Should store invalid day value");
        assertEquals(1, invalidDate1.getMonth(), "Should store invalid month");
        assertEquals(TEST_YEAR, invalidDate1.getYear(), "Should store valid year");

        assertEquals(1, invalidDate2.getDay(), "Should store valid day");
        assertEquals(13, invalidDate2.getMonth(), "Should store invalid month");
        assertEquals(TEST_YEAR, invalidDate2.getYear(), "Should store valid year");

        assertEquals(0, invalidDate3.getDay(), "Should store invalid day");
        assertEquals(0, invalidDate3.getMonth(), "Should store invalid month");
        assertEquals(0, invalidDate3.getYear(), "Should store invalid year");
    }

    @Test
    @DisplayName("Matches with leap year")
    void matchesWithLeapYear() {
        // Arrange - 2024 is a leap year
        Holiday leapDay = new Holiday(29, 2, 2024, "Leap Day");
        Calendar leapDate = new GregorianCalendar(2024, Calendar.FEBRUARY, 29);
        Calendar nonLeapDate = new GregorianCalendar(2023, Calendar.FEBRUARY, 29);

        // Act & Assert
        assertTrue(leapDay.matches(leapDate), "Should match leap day in leap year");
        assertFalse(leapDay.matches(nonLeapDate), "Should not match leap day in non-leap year");
    }

    @Test
    @DisplayName("Time calculations across years")
    void timeCalculationsAcrossYears() {
        // Arrange
        Holiday lastDayOfYear = new Holiday(31, 12, TEST_YEAR, "Last Day");
        Holiday firstDayNextYear = new Holiday(1, 1, TEST_YEAR + 1, "First Day");

        // Act
        long lastDayTime = lastDayOfYear.getTime();
        long firstDayTime = firstDayNextYear.getTime();

        // Assert
        assertTrue(firstDayTime > lastDayTime,
                   "First day of next year should be after last day of current year");
    }

    // ==================== Performance Tests ====================

    @Test
    @DisplayName("Matches method performance")
    void matchesMethodPerformance() {
        // Arrange
        Calendar testDate = new GregorianCalendar(TEST_YEAR, Calendar.JANUARY, 1);
        final int iterations = 10000;

        // Act - measure matches performance
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            newYearsDay.matches(testDate);
        }
        long endTime = System.nanoTime();

        // Assert
        long duration = endTime - startTime;
        double avgDuration = (double) duration / iterations;
        assertTrue(avgDuration < 100, // Average should be less than 100 nanoseconds
                  String.format("Matches should be fast: avg %.2f ns", avgDuration));
    }

    @Test
    @DisplayName("Compare to method performance")
    void compareToMethodPerformance() {
        // Arrange
        final int iterations = 10000;

        // Act - measure compareTo performance
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            christmasDay.compareTo(newYearsDay);
        }
        long endTime = System.nanoTime();

        // Assert
        long duration = endTime - startTime;
        double avgDuration = (double) duration / iterations;
        assertTrue(avgDuration < 50, // Average should be less than 50 nanoseconds
                  String.format("CompareTo should be fast: avg %.2f ns", avgDuration));
    }

    // ==================== Cross-Year Boundary Tests ====================

    @Test
    @DisplayName("Holiday at year boundary")
    void holidayAtYearBoundary() {
        // Arrange
        Holiday newYearEve = new Holiday(31, 12, TEST_YEAR, "New Year's Eve");
        Holiday newYearDay = new Holiday(1, 1, TEST_YEAR + 1, "New Year's Day");

        // Act & Assert
        assertTrue(newYearDay.isAfter(newYearEve), "New Year's Day should be after New Year's Eve");
        assertFalse(newYearEve.isAfter(newYearDay), "New Year's Eve should not be after New Year's Day");
        assertTrue(newYearDay.compareTo(newYearEve) > 0, "New Year's Day should be after New Year's Eve");
    }

    @Test
    @DisplayName("Same day different names")
    void sameDayDifferentNames() {
        // Arrange
        Holiday holiday1 = new Holiday(1, 1, TEST_YEAR, "Holiday One");
        Holiday holiday2 = new Holiday(1, 1, TEST_YEAR, "Holiday Two");

        // Act & Assert
        assertTrue(holiday1.matches(holiday2), "Should match same date");
        assertEquals(0, holiday1.compareTo(holiday2), "Should be equal same date");
        assertNotEquals(holiday1.getName(), holiday2.getName(), "Should have different names");
    }
}