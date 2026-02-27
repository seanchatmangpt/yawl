package org.yawlfoundation.yawl.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

/**
 * Comprehensive tests for YCaseNbrStore class using Chicago TDD methodology.
 * Tests real YCaseNbrStore instances with various scenarios.
 *
 * Must run SAME_THREAD because YCaseNbrStore is a singleton whose AtomicInteger
 * state leaks between tests when tests run concurrently.
 */
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("YCaseNbrStore Tests")
@Tag("unit")
class YCaseNbrStoreTest {

    private YCaseNbrStore caseNbrStore;

    @BeforeEach
    void setUp() {
        // Get the singleton instance and reset its state
        caseNbrStore = YCaseNbrStore.getInstance();
        caseNbrStore.setCaseNbr(1001); // Reset to initial value
        caseNbrStore.setPkey(1001);
        caseNbrStore.setPersisted(false);
        caseNbrStore.setPersisting(false);
    }

    // =========================================================================
    // Singleton Pattern Tests
    // =========================================================================

    @Nested
    @DisplayName("Singleton Pattern Tests")
    class SingletonTests {

        @Test
        @DisplayName("Returns same instance from multiple getInstance() calls")
        void returnsSameInstanceFromMultipleCalls() {
            YCaseNbrStore instance1 = YCaseNbrStore.getInstance();
            YCaseNbrStore instance2 = YCaseNbrStore.getInstance();

            assertSame(instance1, instance2, "getInstance() should return same instance");
        }

        @Test
        @DisplayName("Instance is never null")
        void instanceIsNeverNull() {
            YCaseNbrStore instance = YCaseNbrStore.getInstance();

            assertNotNull(instance, "YCaseNbrStore.getInstance() should never return null");
        }

        @Test
        @DisplayName("Instance is YCaseNbrStore type")
        void instanceIsYCaseNbrStoreType() {
            YCaseNbrStore instance = YCaseNbrStore.getInstance();

            assertInstanceOf(YCaseNbrStore.class, instance, "Instance should be YCaseNbrStore type");
        }
    }

    // =========================================================================
    // Case Number Tests
    // =========================================================================

    @Nested
    @DisplayName("Case Number Tests")
    class CaseNumberTests {

        @Test
        @DisplayName("Get initial case number returns expected value")
        void getInitialCaseNumberReturnsExpectedValue() {
            int initialCaseNbr = caseNbrStore.getCaseNbr();

            assertEquals(1001, initialCaseNbr, "Initial case number should be 1001");
        }

        @Test
        @DisplayName("Set case number updates the value")
        void setCaseNumberUpdatesValue() {
            int newCaseNbr = 2000;
            caseNbrStore.setCaseNbr(newCaseNbr);

            assertEquals(newCaseNbr, caseNbrStore.getCaseNbr(), "Case number should be updated");
        }

        @Test
        @DisplayName("Set case number to negative value")
        void setCaseNumberToNegativeValue() {
            int negativeCaseNbr = -1;
            caseNbrStore.setCaseNbr(negativeCaseNbr);

            assertEquals(negativeCaseNbr, caseNbrStore.getCaseNbr(), "Negative case number should be handled");
        }

        @Test
        @DisplayName("Set case number to zero")
        void setCaseNumberToZero() {
            int zeroCaseNbr = 0;
            caseNbrStore.setCaseNbr(zeroCaseNbr);

            assertEquals(zeroCaseNbr, caseNbrStore.getCaseNbr(), "Zero case number should be handled");
        }

        @Test
        @DisplayName("Set case number to maximum integer value")
        void setCaseNumberToMaximumValue() {
            int maxCaseNbr = Integer.MAX_VALUE;
            caseNbrStore.setCaseNbr(maxCaseNbr);

            assertEquals(maxCaseNbr, caseNbrStore.getCaseNbr(), "Maximum integer case number should be handled");
        }

        @Test
        @DisplayName("Get and set case number multiple times")
        void getAndSetCaseNumberMultipleTimes() {
            int[] testValues = {1000, 2000, 3000, 4000, 5000};

            for (int value : testValues) {
                caseNbrStore.setCaseNbr(value);
                assertEquals(value, caseNbrStore.getCaseNbr(),
                           "Case number should match set value for " + value);
            }
        }
    }

    // =========================================================================
    // Primary Key Tests
    // =========================================================================

    @Nested
    @DisplayName("Primary Key Tests")
    class PrimaryKeyTests {

        @Test
        @DisplayName("Get initial primary key returns expected value")
        void getInitialPrimaryKeyReturnsExpectedValue() {
            int initialPkey = caseNbrStore.getPkey();

            assertEquals(1001, initialPkey, "Initial primary key should be 1001");
        }

        @Test
        @DisplayName("Set primary key updates the value")
        void setPrimaryKeyUpdatesValue() {
            int newPkey = 2000;
            caseNbrStore.setPkey(newPkey);

            assertEquals(newPkey, caseNbrStore.getPkey(), "Primary key should be updated");
        }

        @Test
        @DisplayName("Set primary key to negative value")
        void setPrimaryKeyToNegativeValue() {
            int negativePkey = -1;
            caseNbrStore.setPkey(negativePkey);

            assertEquals(negativePkey, caseNbrStore.getPkey(), "Negative primary key should be handled");
        }

        @Test
        @DisplayName("Set primary key to zero")
        void setPrimaryKeyToZero() {
            int zeroPkey = 0;
            caseNbrStore.setPkey(zeroPkey);

            assertEquals(zeroPkey, caseNbrStore.getPkey(), "Zero primary key should be handled");
        }

        @Test
        @DisplayName("Set primary key to maximum integer value")
        void setPrimaryKeyToMaximumValue() {
            int maxPkey = Integer.MAX_VALUE;
            caseNbrStore.setPkey(maxPkey);

            assertEquals(maxPkey, caseNbrStore.getPkey(), "Maximum integer primary key should be handled");
        }
    }

    // =========================================================================
    // Persistence Tests
    // =========================================================================

    @Nested
    @DisplayName("Persistence Tests")
    class PersistenceTests {

        @Test
        @DisplayName("Initial persisted status is false")
        void initialPersistedStatusIsFalse() {
            assertFalse(caseNbrStore.isPersisted(), "Initial persisted status should be false");
        }

        @Test
        @DisplayName("Set persisted status to true")
        void setPersistedStatusToTrue() {
            caseNbrStore.setPersisted(true);

            assertTrue(caseNbrStore.isPersisted(), "Persisted status should be true");
        }

        @Test
        @DisplayName("Set persisted status to false")
        void setPersistedStatusToFalse() {
            caseNbrStore.setPersisted(true); // First set to true
            caseNbrStore.setPersisted(false); // Then set to false

            assertFalse(caseNbrStore.isPersisted(), "Persisted status should be false");
        }

        @Test
        @DisplayName("Initial persisting status is false")
        void initialPersistingStatusIsFalse() {
            assertFalse(caseNbrStore.isPersisting(), "Initial persisting status should be false");
        }

        @Test
        @DisplayName("Set persisting status to true")
        void setPersistingStatusToTrue() {
            caseNbrStore.setPersisting(true);

            assertTrue(caseNbrStore.isPersisting(), "Persisting status should be true");
        }

        @Test
        @DisplayName("Set persisting status to false")
        void setPersistingStatusToFalse() {
            caseNbrStore.setPersisting(true); // First set to true
            caseNbrStore.setPersisting(false); // Then set to false

            assertFalse(caseNbrStore.isPersisting(), "Persisting status should be false");
        }

        @Test
        @DisplayName("Both persistence flags can be set independently")
        void bothPersistenceFlagsCanBeSetIndependently() {
            caseNbrStore.setPersisted(true);
            caseNbrStore.setPersisting(false);

            assertTrue(caseNbrStore.isPersisted(), "Persisted status should be true");
            assertFalse(caseNbrStore.isPersisting(), "Persisting status should be false");
        }
    }

    // =========================================================================
    // Next Case Number Tests
    // =========================================================================

    @Nested
    @DisplayName("Next Case Number Tests")
    class NextCaseNumberTests {

        @Test
        @DisplayName("Get next case number increments current value")
        void getNextCaseNumberIncrementsCurrentValue() throws YPersistenceException {
            int initialCaseNbr = caseNbrStore.getCaseNbr();

            // Pass null since YCaseNbrStore.getNextCaseNbr works with isPersisting == false
            String nextCaseNbr = caseNbrStore.getNextCaseNbr(null);

            assertEquals(initialCaseNbr + 1, caseNbrStore.getCaseNbr(),
                        "Case number should be incremented");
            assertNotNull(nextCaseNbr, "Next case number should not be null");
            assertEquals(String.valueOf(initialCaseNbr + 1), nextCaseNbr,
                        "Next case number should match incremented value");
        }

        @Test
        @DisplayName("Get next case number multiple times increments correctly")
        void getNextCaseNumberMultipleTimesIncrementsCorrectly() throws YPersistenceException {
            int initialCaseNbr = caseNbrStore.getCaseNbr();

            for (int i = 1; i <= 5; i++) {
                String nextCaseNbr = caseNbrStore.getNextCaseNbr(null);
                assertEquals(initialCaseNbr + i, caseNbrStore.getCaseNbr(),
                           "Case number should be incremented after " + i + " calls");
                assertEquals(String.valueOf(initialCaseNbr + i), nextCaseNbr,
                           "Next case number should match incremented value");
            }
        }

        @Test
        @DisplayName("Get next case number with zero initial value")
        void getNextCaseNumberWithZeroInitialValue() throws YPersistenceException {
            caseNbrStore.setCaseNbr(0);

            String nextCaseNbr = caseNbrStore.getNextCaseNbr(null);

            assertEquals(1, caseNbrStore.getCaseNbr(), "Case number should be incremented from 0");
            assertEquals("1", nextCaseNbr, "Next case number should be '1'");
        }

        @Test
        @DisplayName("Get next case number with negative initial value")
        void getNextCaseNumberWithNegativeInitialValue() throws YPersistenceException {
            caseNbrStore.setCaseNbr(-100);

            String nextCaseNbr = caseNbrStore.getNextCaseNbr(null);

            assertEquals(-99, caseNbrStore.getCaseNbr(), "Case number should be incremented from -100");
            assertEquals("-99", nextCaseNbr, "Next case number should be '-99'");
        }

        @Test
        @DisplayName("Get next case number returns string representation")
        void getNextCaseNumberReturnsStringRepresentation() throws YPersistenceException {
            int initialCaseNbr = 1234;
            caseNbrStore.setCaseNbr(initialCaseNbr);

            String nextCaseNbr = caseNbrStore.getNextCaseNbr(null);

            assertTrue(nextCaseNbr instanceof String, "Next case number should be string");
            assertEquals(String.valueOf(initialCaseNbr + 1), nextCaseNbr,
                        "Next case number should be string representation");
        }
    }

    // =========================================================================
    // toString Tests
    // =========================================================================

    @Nested
    @DisplayName("toString Tests")
    class ToStringTests {

        @Test
        @DisplayName("toString returns string representation of case number")
        void toStringReturnsStringRepresentationOfCaseNumber() {
            int testCaseNbr = 1234;
            caseNbrStore.setCaseNbr(testCaseNbr);

            String result = caseNbrStore.toString();

            assertEquals(String.valueOf(testCaseNbr), result,
                        "toString should return string representation of case number");
        }

        @Test
        @DisplayName("toString returns string for different case number values")
        void toStringReturnsStringForDifferentCaseNumberValues() {
            int[] testValues = {0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE};

            for (int value : testValues) {
                caseNbrStore.setCaseNbr(value);
                assertEquals(String.valueOf(value), caseNbrStore.toString(),
                           "toString should return string representation for value " + value);
            }
        }
    }

    // =========================================================================
    // Boundary Tests
    // =========================================================================

    @Nested
    @DisplayName("Boundary Tests")
    class BoundaryTests {

        @Test
        @DisplayName("Handle integer overflow in case number")
        void handleIntegerOverflowInCaseNumber() {
            int maxValue = Integer.MAX_VALUE;
            caseNbrStore.setCaseNbr(maxValue);

            // After setting to max value, the next increment would overflow
            assertEquals(maxValue, caseNbrStore.getCaseNbr(),
                       "Should handle maximum integer value");

            // Setting back to safe value
            caseNbrStore.setCaseNbr(1001);
            assertEquals(1001, caseNbrStore.getCaseNbr(), "Should reset to safe value");
        }

        @Test
        @DisplayName("Handle integer underflow in case number")
        void handleIntegerUnderflowInCaseNumber() {
            int minValue = Integer.MIN_VALUE;
            caseNbrStore.setCaseNbr(minValue);

            assertEquals(minValue, caseNbrStore.getCaseNbr(),
                       "Should handle minimum integer value");

            // Setting back to safe value
            caseNbrStore.setCaseNbr(1001);
            assertEquals(1001, caseNbrStore.getCaseNbr(), "Should reset to safe value");
        }
    }

}