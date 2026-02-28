package org.yawlfoundation.yawl.engine.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for Petri-net Place class.
 * Tests token management with atomic operations for concurrent safety.
 *
 * @since Java 21
 */
@DisplayName("Petri-Net Place Tests")
class PlaceTest {

    private String placeId;
    private String placeName;

    @BeforeEach
    void setUp() {
        placeId = "place_001";
        placeName = "Start";
    }

    @Nested
    @DisplayName("Construction and Initialization")
    class ConstructionTests {

        @Test
        @DisplayName("Create place with initial tokens")
        void testCreateWithTokens() {
            Place place = new Place(placeId, placeName, 5);

            assertEquals(placeId, place.id());
            assertEquals(placeName, place.name());
            assertEquals(5, place.getTokenCount());
        }

        @Test
        @DisplayName("Create place with zero tokens")
        void testCreateZeroTokens() {
            Place place = new Place(placeId, placeName, 0);

            assertEquals(0, place.getTokenCount());
        }

        @Test
        @DisplayName("Create place with default constructor")
        void testCreateDefault() {
            Place place = new Place(placeId, placeName);

            assertEquals(placeId, place.id());
            assertEquals(placeName, place.name());
            assertEquals(0, place.getTokenCount());
        }

        @Test
        @DisplayName("Reject null ID")
        void testNullId() {
            assertThrows(NullPointerException.class, () ->
                    new Place(null, placeName, 0)
            );
        }

        @Test
        @DisplayName("Reject null name")
        void testNullName() {
            assertThrows(NullPointerException.class, () ->
                    new Place(placeId, null, 0)
            );
        }

        @Test
        @DisplayName("Reject negative initial tokens")
        void testNegativeTokens() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Place(placeId, placeName, -1)
            );
        }
    }

    @Nested
    @DisplayName("Token Addition")
    class TokenAdditionTests {

        @Test
        @DisplayName("Add single token")
        void testAddToken() {
            Place place = new Place(placeId, placeName);
            int result = place.addToken();

            assertEquals(1, result);
            assertEquals(1, place.getTokenCount());
        }

        @Test
        @DisplayName("Add multiple tokens sequentially")
        void testAddMultipleTokens() {
            Place place = new Place(placeId, placeName);

            place.addToken();
            place.addToken();
            place.addToken();

            assertEquals(3, place.getTokenCount());
        }

        @Test
        @DisplayName("Add multiple tokens at once")
        void testAddTokensBatch() {
            Place place = new Place(placeId, placeName);
            int result = place.addTokens(5);

            assertEquals(5, result);
            assertEquals(5, place.getTokenCount());
        }

        @Test
        @DisplayName("Reject negative token addition")
        void testNegativeAddTokens() {
            Place place = new Place(placeId, placeName);

            assertThrows(IllegalArgumentException.class, () ->
                    place.addTokens(-1)
            );
        }

        @Test
        @DisplayName("Add zero tokens is valid")
        void testAddZeroTokens() {
            Place place = new Place(placeId, placeName, 5);
            int result = place.addTokens(0);

            assertEquals(5, result);
            assertEquals(5, place.getTokenCount());
        }
    }

    @Nested
    @DisplayName("Token Removal")
    class TokenRemovalTests {

        @Test
        @DisplayName("Remove token from non-empty place")
        void testRemoveToken() {
            Place place = new Place(placeId, placeName, 5);
            int result = place.removeToken();

            assertEquals(4, result);
            assertEquals(4, place.getTokenCount());
        }

        @Test
        @DisplayName("Cannot remove token from empty place")
        void testRemoveFromEmpty() {
            Place place = new Place(placeId, placeName);
            int result = place.removeToken();

            assertEquals(-1, result);
            assertEquals(0, place.getTokenCount());
        }

        @Test
        @DisplayName("Remove multiple tokens atomically")
        void testRemoveTokens() {
            Place place = new Place(placeId, placeName, 10);
            boolean result = place.removeTokens(3);

            assertTrue(result);
            assertEquals(7, place.getTokenCount());
        }

        @Test
        @DisplayName("Fail to remove more tokens than available")
        void testRemoveExcessive() {
            Place place = new Place(placeId, placeName, 3);
            boolean result = place.removeTokens(5);

            assertFalse(result);
            assertEquals(3, place.getTokenCount()); // Unchanged
        }

        @Test
        @DisplayName("Remove exactly all tokens")
        void testRemoveAll() {
            Place place = new Place(placeId, placeName, 5);
            boolean result = place.removeTokens(5);

            assertTrue(result);
            assertEquals(0, place.getTokenCount());
        }

        @Test
        @DisplayName("Reject negative removal count")
        void testNegativeRemoveTokens() {
            Place place = new Place(placeId, placeName, 5);

            assertThrows(IllegalArgumentException.class, () ->
                    place.removeTokens(-1)
            );
        }
    }

    @Nested
    @DisplayName("Token Queries")
    class TokenQueryTests {

        @Test
        @DisplayName("hasTokens returns true when tokens available")
        void testHasTokens() {
            Place place = new Place(placeId, placeName, 3);

            assertTrue(place.hasTokens());
        }

        @Test
        @DisplayName("hasTokens returns false when empty")
        void testHasTokensEmpty() {
            Place place = new Place(placeId, placeName);

            assertFalse(place.hasTokens());
        }

        @Test
        @DisplayName("hasTokens(int) with sufficient tokens")
        void testHasTokensRequired() {
            Place place = new Place(placeId, placeName, 10);

            assertTrue(place.hasTokens(5));
            assertTrue(place.hasTokens(10));
            assertFalse(place.hasTokens(11));
        }

        @Test
        @DisplayName("Reject negative token requirement")
        void testNegativeTokenRequirement() {
            Place place = new Place(placeId, placeName, 5);

            assertThrows(IllegalArgumentException.class, () ->
                    place.hasTokens(-1)
            );
        }
    }

    @Nested
    @DisplayName("Token Clearing")
    class TokenClearingTests {

        @Test
        @DisplayName("Clear tokens returns previous count")
        void testClearTokens() {
            Place place = new Place(placeId, placeName, 5);
            int previousCount = place.clearTokens();

            assertEquals(5, previousCount);
            assertEquals(0, place.getTokenCount());
        }

        @Test
        @DisplayName("Clear already empty place")
        void testClearEmpty() {
            Place place = new Place(placeId, placeName);
            int previousCount = place.clearTokens();

            assertEquals(0, previousCount);
            assertEquals(0, place.getTokenCount());
        }
    }

    @Nested
    @DisplayName("Equality and Identity")
    class EqualityTests {

        @Test
        @DisplayName("Places with same ID and name are equal")
        void testEquality() {
            Place place1 = new Place(placeId, placeName, 5);
            Place place2 = new Place(placeId, placeName, 10);

            assertEquals(place1, place2);
            assertEquals(place1.hashCode(), place2.hashCode());
        }

        @Test
        @DisplayName("Token count excluded from equality")
        void testEqualityIgnoresTokens() {
            Place place1 = new Place(placeId, placeName, 0);
            Place place2 = new Place(placeId, placeName, 100);

            assertEquals(place1, place2);
        }

        @Test
        @DisplayName("Different IDs are not equal")
        void testDifferentIds() {
            Place place1 = new Place("place_001", placeName);
            Place place2 = new Place("place_002", placeName);

            assertNotEquals(place1, place2);
        }

        @Test
        @DisplayName("Different names are not equal")
        void testDifferentNames() {
            Place place1 = new Place(placeId, "Start");
            Place place2 = new Place(placeId, "End");

            assertNotEquals(place1, place2);
        }
    }

    @Nested
    @DisplayName("String Representation")
    class ToStringTests {

        @Test
        @DisplayName("toString includes place information")
        void testToString() {
            Place place = new Place(placeId, placeName, 5);
            String str = place.toString();

            assertTrue(str.contains("Place"));
            assertTrue(str.contains(placeId));
            assertTrue(str.contains(placeName));
            assertTrue(str.contains("5"));
        }

        @Test
        @DisplayName("toString updates with token changes")
        void testToStringDynamic() {
            Place place = new Place(placeId, placeName, 0);
            String str1 = place.toString();

            place.addToken();
            place.addToken();
            String str2 = place.toString();

            assertNotEquals(str1, str2);
            assertTrue(str2.contains("2"));
        }
    }

    @Nested
    @DisplayName("Concurrent Token Operations")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent token additions are atomic")
        void testConcurrentAdditions() throws InterruptedException {
            Place place = new Place(placeId, placeName);
            int threadCount = 10;
            int tokensPerThread = 100;

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < tokensPerThread; j++) {
                        place.addToken();
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            assertEquals(threadCount * tokensPerThread, place.getTokenCount());
        }

        @Test
        @DisplayName("Concurrent mixed add/remove operations")
        void testConcurrentMixed() throws InterruptedException {
            Place place = new Place(placeId, placeName, 1000);
            int threadCount = 4;

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount / 2; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 100; j++) {
                        place.addToken();
                    }
                });
            }

            for (int i = threadCount / 2; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 100; j++) {
                        place.removeToken();
                    }
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join();
            }

            // Final count depends on timing, but should be consistent
            assertTrue(place.getTokenCount() >= 0);
        }

        @Test
        @DisplayName("Atomic removeTokens prevents over-removal")
        void testAtomicRemoveTokens() throws InterruptedException {
            Place place = new Place(placeId, placeName, 50);
            int threadCount = 5;
            int removalPerThread = 20;

            Thread[] threads = new Thread[threadCount];
            int[] successCount = new int[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                threads[i] = new Thread(() -> {
                    if (place.removeTokens(removalPerThread)) {
                        successCount[threadId]++;
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // Only 2-3 threads should succeed (50 tokens / 20 per removal)
            int totalSuccess = 0;
            for (int count : successCount) {
                totalSuccess += count;
            }

            assertTrue(totalSuccess <= 3, "Only some threads should succeed removal");
            assertEquals(place.getTokenCount(), 50 - (totalSuccess * removalPerThread));
        }
    }
}
