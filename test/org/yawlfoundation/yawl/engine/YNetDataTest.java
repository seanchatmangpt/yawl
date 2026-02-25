package org.yawlfoundation.yawl.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive tests for YNetData class using Chicago TDD methodology.
 * Tests real YNetData instances with various scenarios.
 */
@DisplayName("YNetData Tests")
@Tag("unit")
class YNetDataTest {

    private YNetData netData;
    private String testId = "testCase123";
    private String testData = "test data content";

    @BeforeEach
    void setUp() {
        netData = new YNetData();
    }

    // =========================================================================
    // Constructor Tests
    // =========================================================================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor creates instance with null data and ID")
        void defaultConstructorCreatesInstance() {
            assertNotNull(netData, "Default constructor should create non-null instance");
            assertNull(netData.getData(), "Data should be null after default constructor");
            assertNull(netData.getId(), "ID should be null after default constructor");
        }

        @Test
        @DisplayName("Parameterized constructor creates instance with specified ID")
        void parameterizedConstructorCreatesInstanceWithId() {
            YNetData withId = new YNetData(testId);

            assertNotNull(withId, "Parameterized constructor should create non-null instance");
            assertNull(withId.getData(), "Data should be null after parameterized constructor");
            assertEquals(testId, withId.getId(), "ID should match constructor parameter");
        }
    }

    // =========================================================================
    // Getter/Setter Tests
    // =========================================================================

    @Nested
    @DisplayName("Data Getter/Setter Tests")
    class DataGetterSetterTests {

        @Test
        @DisplayName("Set and get data correctly")
        void setDataAndGetData() {
            netData.setData(testData);
            assertEquals(testData, netData.getData(), "Data should match what was set");
        }

        @Test
        @DisplayName("Set null data returns null")
        void setNullDataReturnsNull() {
            netData.setData(null);
            assertNull(netData.getData(), "Data should be null when null was set");
        }

        @Test
        @DisplayName("Set empty string data returns empty string")
        void setEmptyStringDataReturnsEmptyString() {
            netData.setData("");
            assertEquals("", netData.getData(), "Data should be empty string when empty string was set");
        }

        @Test
        @DisplayName("Set data multiple times")
        void setDataMultipleTimes() {
            netData.setData("first");
            netData.setData("second");
            netData.setData("third");

            assertEquals("third", netData.getData(), "Data should be the last value set");
        }
    }

    @Nested
    @DisplayName("ID Getter/Setter Tests")
    class IdGetterSetterTests {

        @Test
        @DisplayName("Set and get ID correctly")
        void setIdAndGetId() {
            netData.setId(testId);
            assertEquals(testId, netData.getId(), "ID should match what was set");
        }

        @Test
        @DisplayName("Set null ID returns null")
        void setNullIdReturnsNull() {
            netData.setId(null);
            assertNull(netData.getId(), "ID should be null when null was set");
        }

        @Test
        @DisplayName("Set empty string ID returns empty string")
        void setEmptyStringIdReturnsEmptyString() {
            netData.setId("");
            assertEquals("", netData.getId(), "ID should be empty string when empty string was set");
        }

        @Test
        @DisplayName("Set ID multiple times")
        void setIdMultipleTimes() {
            netData.setId("first");
            netData.setId("second");
            netData.setId("third");

            assertEquals("third", netData.getId(), "ID should be the last value set");
        }
    }

    // =========================================================================
    // Equality Tests
    // =========================================================================

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("Equal objects with same data and ID")
        void equalObjectsWithSameDataAndId() {
            YNetData first = new YNetData();
            YNetData second = new YNetData();

            first.setData(testData);
            first.setId(testId);
            second.setData(testData);
            second.setId(testId);

            assertEquals(first, second, "Objects with same data and ID should be equal");
        }

        @Test
        @DisplayName("Equal objects with same null data and ID")
        void equalObjectsWithSameNullDataAndId() {
            YNetData first = new YNetData();
            YNetData second = new YNetData();

            first.setData(null);
            first.setId(testId);
            second.setData(null);
            second.setId(testId);

            assertEquals(first, second, "Objects with same null data and ID should be equal");
        }

        @Test
        @DisplayName("Not equal objects with different data")
        void notEqualObjectsWithDifferentData() {
            YNetData first = new YNetData();
            YNetData second = new YNetData();

            first.setData("data1");
            second.setData("data2");
            first.setId(testId);
            second.setId(testId);

            assertNotEquals(first, second, "Objects with different data should not be equal");
        }

        @Test
        @DisplayName("Not equal objects with different IDs")
        void notEqualObjectsWithDifferentIds() {
            YNetData first = new YNetData();
            YNetData second = new YNetData();

            first.setData(testData);
            second.setData(testData);
            first.setId("id1");
            second.setId("id2");

            assertNotEquals(first, second, "Objects with different IDs should not be equal");
        }

        @Test
        @DisplayName("Not equal when comparing to null")
        void notEqualWhenComparingToNull() {
            assertNotEquals(netData, null, "Object should not equal null");
        }

        @Test
        @DisplayName("Not equal when comparing to different type")
        void notEqualWhenComparingToDifferentType() {
            assertNotEquals(netData, "string", "Object should not equal string");
        }

        @Test
        @DisplayName("Self equality")
        void selfEquality() {
            assertEquals(netData, netData, "Object should equal itself");
        }

        @Test
        @DisplayName("Equal objects have same hash codes")
        void equalObjectsHaveSameHashCodes() {
            YNetData first = new YNetData();
            YNetData second = new YNetData();

            first.setData(testData);
            first.setId(testId);
            second.setData(testData);
            second.setId(testId);

            assertEquals(first.hashCode(), second.hashCode(), "Equal objects should have same hash code");
        }

        @Test
        @DisplayName("Hash code is consistent")
        void hashCodeIsConsistent() {
            netData.setId(testId);
            int hashCode1 = netData.hashCode();
            int hashCode2 = netData.hashCode();

            assertEquals(hashCode1, hashCode2, "Hash code should be consistent");
        }

        @Test
        @DisplayName("Hash code with null ID")
        void hashCodeWithNullId() {
            netData.setId(null);
            int hashCode = netData.hashCode();

            // Should not throw and should return a valid hash code
            assertNotEquals(0, hashCode, "Hash code with null ID should not be 0");
        }
    }

    // =========================================================================
    // toString Tests
    // =========================================================================

    @Nested
    @DisplayName("toString Tests")
    class ToStringTests {

        @Test
    @DisplayName("toString format with both ID and data")
        void toStringFormatWithBothIdAndData() {
            netData.setId(testId);
            netData.setData(testData);

            String result = netData.toString();
            assertTrue(result.contains("ID: " + testId), "String should contain ID");
            assertTrue(result.contains("DATA: " + testData), "String should contain data");
        }

        @Test
        @DisplayName("toString format with null ID and data")
        void toStringFormatWithNullIdAndData() {
            netData.setId(null);
            netData.setData(null);

            String result = netData.toString();
            assertTrue(result.contains("ID: null"), "String should contain null ID");
            assertTrue(result.contains("DATA: null"), "String should contain null data");
        }

        @Test
        @DisplayName("toString format with empty ID and data")
        void toStringFormatWithEmptyIdAndData() {
            netData.setId("");
            netData.setData("");

            String result = netData.toString();
            assertTrue(result.contains("ID: "), "String should contain empty ID");
            assertTrue(result.contains("DATA: "), "String should contain empty data");
        }

        @Test
        @DisplayName("toString format with ID only")
        void toStringFormatWithIdOnly() {
            netData.setId(testId);
            netData.setData(null);

            String result = netData.toString();
            assertTrue(result.contains("ID: " + testId), "String should contain ID");
            assertTrue(result.contains("DATA: null"), "String should contain null data");
        }

        @Test
        @DisplayName("toString format with data only")
        void toStringFormatWithDataOnly() {
            netData.setId(null);
            netData.setData(testData);

            String result = netData.toString();
            assertTrue(result.contains("ID: null"), "String should contain null ID");
            assertTrue(result.contains("DATA: " + testData), "String should contain data");
        }

        @Test
        @DisplayName("toString includes semicolon separators")
        void toStringIncludesSemicolonSeparators() {
            netData.setId(testId);
            netData.setData(testData);

            String result = netData.toString();
            assertTrue(result.contains(";"), "String should contain semicolon separators");
        }
    }

    // =========================================================================
    // Boundary and Edge Case Tests
    // =========================================================================

    @Nested
    @DisplayName("Boundary and Edge Case Tests")
    class BoundaryTests {

        @Test
        @DisplayName("Handle very long ID string")
        void handleVeryLongIdString() {
            String longId = "a".repeat(1000);
            netData.setId(longId);
            assertEquals(longId, netData.getId(), "Long ID string should be handled correctly");
        }

        @Test
        @DisplayName("Handle very long data string")
        void handleVeryLongDataString() {
            String longData = "data".repeat(1000);
            netData.setData(longData);
            assertEquals(longData, netData.getData(), "Long data string should be handled correctly");
        }

        @Test
        @DisplayName("Handle special characters in ID")
        void handleSpecialCharactersInId() {
            String specialId = "test!@#$%^&*()_+-=[]{}|;':\",./<>?";
            netData.setId(specialId);
            assertEquals(specialId, netData.getId(), "Special characters in ID should be handled correctly");
        }

        @Test
        @DisplayName("Handle special characters in data")
        void handleSpecialCharactersInData() {
            String specialData = "data with !@#$%^&*()_+-=[]{}|;':\",./<>? characters";
            netData.setData(specialData);
            assertEquals(specialData, netData.getData(), "Special characters in data should be handled correctly");
        }

        @Test
        @DisplayName("Handle Unicode characters in ID")
        void handleUnicodeCharactersInId() {
            String unicodeId = "测试 ID with 🚀 emoji";
            netData.setId(unicodeId);
            assertEquals(unicodeId, netData.getId(), "Unicode characters in ID should be handled correctly");
        }

        @Test
        @DisplayName("Handle Unicode characters in data")
        void handleUnicodeCharactersInData() {
            String unicodeData = "测试 data with 🎯 emoji and 中文 characters";
            netData.setData(unicodeData);
            assertEquals(unicodeData, netData.getData(), "Unicode characters in data should be handled correctly");
        }
    }

    // =========================================================================
    // Concurrency Tests
    // =========================================================================

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Handle concurrent access to getter/setter methods")
        void handleConcurrentAccess() throws InterruptedException {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // Create tasks that will access the YNetData concurrently
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        // Set and get data
                        netData.setData("thread-" + threadId);
                        netData.setId("id-" + threadId);

                        String retrievedData = netData.getData();
                        String retrievedId = netData.getId();

                        // Verify the data is not null
                        assertNotNull(retrievedData, "Retrieved data should not be null");
                        assertNotNull(retrievedId, "Retrieved ID should not be null");

                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // Thread-safe error handling
                        System.err.println("Thread " + threadId + " failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // At least some threads should have succeeded
            assertTrue(successCount.get() > 0, "At least some threads should succeed with concurrent access");
        }
    }
}