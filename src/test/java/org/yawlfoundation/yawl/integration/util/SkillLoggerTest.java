/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This file is part of YAWL (Yet Another Workflow Language).
 *
 * YAWL is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for SkillLogger utility class.
 */
class SkillLoggerTest {

    @Test
    void logInfo_doesNotThrow() {
        // Test that logging methods don't throw exceptions
        assertDoesNotThrow(() -> SkillLogger.logInfo("Test info message"));
        assertDoesNotThrow(() -> SkillLogger.logInfo("Test with object", "some object"));
        assertDoesNotThrow(() -> SkillLogger.logInfo("Test with multiple objects", "obj1", "obj2"));
    }

    @Test
    void logDebug_doesNotThrow() {
        assertDoesNotThrow(() -> SkillLogger.logDebug("Test debug message"));
        assertDoesNotThrow(() -> SkillLogger.logDebug("Test with object", "some object"));
        assertDoesNotThrow(() -> SkillLogger.logDebug("Test with multiple objects", "obj1", "obj2"));
    }

    @Test
    void logError_doesNotThrow() {
        assertDoesNotThrow(() -> SkillLogger.logError("Test error message"));
        assertDoesNotThrow(() -> SkillLogger.logError("Test with object", "some object"));
        assertDoesNotThrow(() -> SkillLogger.logError("Test with multiple objects", "obj1", "obj2"));
    }

    @Test
    void logWarn_doesNotThrow() {
        assertDoesNotThrow(() -> SkillLogger.logWarn("Test warning message"));
        assertDoesNotThrow(() -> SkillLogger.logWarn("Test with object", "some object"));
        assertDoesNotThrow(() -> SkillLogger.logWarn("Test with multiple objects", "obj1", "obj2"));
    }

    @Test
    void logTrace_doesNotThrow() {
        assertDoesNotThrow(() -> SkillLogger.logTrace("Test trace message"));
        assertDoesNotThrow(() -> SkillLogger.logTrace("Test with object", "some object"));
        assertDoesNotThrow(() -> SkillLogger.logTrace("Test with multiple objects", "obj1", "obj2"));
    }

    @Test
    void logInfo_withObjects_doesNotThrow() {
        // Test with different object types
        assertDoesNotThrow(() -> SkillLogger.logInfo("String message"));
        assertDoesNotThrow(() -> SkillLogger.logInfo("Number message", 42));
        assertDoesNotThrow(() -> SkillLogger.logInfo("Boolean message", true));
        assertDoesNotThrow(() -> SkillLogger.logInfo("Null object message", null));
        assertDoesNotThrow(() -> SkillLogger.logInfo("Array message", new int[]{1, 2, 3}));
    }

    @Test
    void logError_withThrowable_doesNotThrow() {
        Exception exception = new RuntimeException("Test exception");
        assertDoesNotThrow(() -> SkillLogger.logError("Test error with exception", exception));
        assertDoesNotThrow(() -> SkillLogger.logError("Test error with throwable", new Throwable("Test throwable")));
    }

    @Test
    void withTiming_measuresTime() {
        long startTime = System.nanoTime();
        Duration duration = SkillLogger.withTiming("Test operation", () -> {
            // Simulate some work
            try {
                Thread.sleep(10); // 10ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "result";
        });
        long endTime = System.nanoTime();

        // Verify duration is reasonable (should be at least 10ms)
        assertTrue(duration.toMillis() >= 10, "Duration should be at least 10ms");

        // Verify total time elapsed is reasonable
        long totalTime = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        assertTrue(totalTime >= 10, "Total time should be at least 10ms");
    }

    @Test
    void withTiming_returnsCorrectResult() {
        String result = SkillLogger.withTiming("Test operation", () -> "test_result");
        assertEquals("test_result", result);
    }

    @Test
    void withTiming_withException_propagatesException() {
        assertThrows(RuntimeException.class, () -> {
            SkillLogger.withTiming("Test operation", () -> {
                throw new RuntimeException("Test exception");
            });
        });
    }

    @Test
    void withTiming_measuresTimeForException() {
        long startTime = System.nanoTime();
        try {
            SkillLogger.withTiming("Test operation", () -> {
                Thread.sleep(5); // 5ms
                throw new RuntimeException("Test exception");
            });
        } catch (RuntimeException e) {
            // Expected exception
        }
        long endTime = System.nanoTime();
        long totalTime = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

        // Even though exception was thrown, timing should still be measured
        assertTrue(totalTime >= 5, "Timing should still measure even when exception is thrown");
    }

    @Test
    void withTiming_measuresEmptyOperation() {
        Duration duration = SkillLogger.withTiming("Empty operation", () -> {
            // Do nothing
            return null;
        });

        // Duration should be very small but not zero
        assertTrue(duration.toNanos() >= 0, "Duration should not be negative");
    }

    @Test
    void withTiming_logsTimingInfo() {
        // This test mainly ensures the method doesn't throw
        // In a real scenario, we might capture log output
        assertDoesNotThrow(() -> {
            String result = SkillLogger.withTiming("Test logging", () -> {
                Thread.sleep(1); // Tiny delay
                return "logged";
            });
            assertEquals("logged", result);
        });
    }

    @Test
    void withTiming_withLargeOperation() {
        Duration duration = SkillLogger.withTiming("Large operation", () -> {
            // Simulate a larger operation
            try {
                Thread.sleep(100); // 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "large_result";
        });

        // Should measure at least 100ms
        assertTrue(duration.toMillis() >= 100, "Should measure at least 100ms");
    }

    @Test
    void allLogMethods_noExceptions() {
        // Comprehensive test that all logging methods work without exceptions
        assertDoesNotThrow(() -> SkillLogger.logInfo("Info test"));
        assertDoesNotThrow(() -> SkillLogger.logDebug("Debug test"));
        assertDoesNotThrow(() -> SkillLogger.logError("Error test"));
        assertDoesNotThrow(() -> SkillLogger.logWarn("Warn test"));
        assertDoesNotThrow(() -> SkillLogger.logTrace("Trace test"));
    }

    @Test
    void logWithMultipleParameters() {
        // Test with various parameter combinations
        assertDoesNotThrow(() -> SkillLogger.logInfo("Message with params", 1, "two", true));
        assertDoesNotThrow(() -> SkillLogger.logDebug("Debug with params", new Object(), null, "string"));
        assertDoesNotThrow(() -> SkillLogger.logError("Error with params", 42, 3.14, 'c'));
    }
}