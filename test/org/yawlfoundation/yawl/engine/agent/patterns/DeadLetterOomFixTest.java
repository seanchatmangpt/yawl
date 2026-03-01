package org.yawlfoundation.yawl.engine.agent.patterns;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for DeadLetter OOM fix (bounded log with ring-buffer eviction).
 *
 * Detroit School: Tests verify observable behavior through public API.
 * Tests verify:
 * - Log can handle 100K entries without OutOfMemoryError (real stress test)
 * - MAX_LOG_SIZE = 100_000 constant is enforced
 * - Oldest entries are evicted when log reaches capacity (ring-buffer)
 * - Stress test with 200K entries: log stays bounded at 100K
 * - getLog() returns snapshot of current entries (independent copy)
 * - clearBefore() removes entries older than cutoff (uses Iterator.remove())
 * - stats() returns correct reason counts (aggregated map)
 * - count(), countByReason(), getByReason() filtering works
 * - clear() removes all entries
 * - DeadLetterEntry records message details (message, reason, cause, timestamp)
 *
 * Tests use the real DeadLetter singleton with concurrent deque log.
 * No mocks. Timing verifies proper entry lifecycle.
 *
 * SAME_THREAD: DeadLetter uses a JVM-level singleton; parallel test execution
 * would cause races between @BeforeEach clear() and concurrent log() calls.
 */
@Execution(ExecutionMode.SAME_THREAD)
class DeadLetterOomFixTest {

    @BeforeEach
    void setUp() {
        DeadLetter.clear();
    }

    @AfterEach
    void tearDown() {
        DeadLetter.clear();
    }

    @Test
    @DisplayName("log accepts entries up to 100K without OOM")
    @Timeout(30)
    void log_accepts_100K_entries_withoutOOM() {
        // If this test passes without OutOfMemoryError, the ring-buffer fix works
        for (int i = 0; i < 100_000; i++) {
            DeadLetter.log("msg-" + i, DeadLetter.ACTOR_NOT_FOUND, null);
        }

        // Count may be exactly 100K due to ring-buffer eviction
        assertTrue(DeadLetter.count() <= 100_000,
            "Log should never exceed 100K entries");
        assertTrue(DeadLetter.count() > 0,
            "Log should have entries");
        assertEquals(100_000, DeadLetter.count(),
            "Log should be at maximum capacity");
    }

    @Test
    @DisplayName("ring-buffer eviction: oldest entry evicted when at capacity")
    @Timeout(10)
    void ringBuffer_evictsOldest_whenAtCapacity() {
        // Fill to capacity (MAX_LOG_SIZE = 100_000)
        for (int i = 0; i < 100_000; i++) {
            DeadLetter.log("msg-" + i, DeadLetter.ACTOR_NOT_FOUND, null);
        }

        assertEquals(100_000, DeadLetter.count(),
            "Log should be at MAX_LOG_SIZE");

        // Add one more — should evict oldest and keep count at 100K
        DeadLetter.log("new-msg", DeadLetter.TIMEOUT, null);

        // Capacity should still be at 100K (one was evicted)
        assertEquals(100_000, DeadLetter.count(),
            "Log must stay at MAX_LOG_SIZE after overflow");

        // The new entry (TIMEOUT reason) should be present
        assertTrue(DeadLetter.countByReason(DeadLetter.TIMEOUT) > 0,
            "New entry should be present after eviction");

        // And the oldest (msg-0) should be gone
        List<DeadLetter.DeadLetterEntry> log = DeadLetter.getLog();
        boolean foundMsg0 = log.stream()
            .anyMatch(e -> e.originalMessage().equals("msg-0"));
        assertFalse(foundMsg0,
            "Oldest entry (msg-0) should be evicted");
    }

    @Test
    @DisplayName("200K entries: no OOM, log bounded")
    @Timeout(30)
    void log_200K_entries_boundedAtMaxSize() {
        // Stress test: insert 2× capacity
        for (int i = 0; i < 200_000; i++) {
            DeadLetter.log("stress-" + i, DeadLetter.UNKNOWN, null);
        }

        // Log should be bounded at 100K even after 200K inserts
        assertTrue(DeadLetter.count() <= 100_000,
            "Log must be bounded at 100K even after 200K inserts, got: " + DeadLetter.count());
        assertEquals(100_000, DeadLetter.count(),
            "Log should be exactly at MAX_LOG_SIZE after stress test");
    }

    @Test
    @DisplayName("getLog() returns snapshot of current entries")
    @Timeout(5)
    void getLog_returnsSnapshot() {
        DeadLetter.log("msg1", DeadLetter.ACTOR_NOT_FOUND, null);
        DeadLetter.log("msg2", DeadLetter.TIMEOUT, null);

        List<DeadLetter.DeadLetterEntry> log = DeadLetter.getLog();
        assertEquals(2, log.size(),
            "getLog() should return 2 entries");
        assertEquals(DeadLetter.ACTOR_NOT_FOUND, log.get(0).reason(),
            "First entry should have ACTOR_NOT_FOUND reason");
        assertEquals(DeadLetter.TIMEOUT, log.get(1).reason(),
            "Second entry should have TIMEOUT reason");
    }

    @Test
    @DisplayName("clearBefore removes entries older than cutoff (Iterator.remove())")
    @Timeout(5)
    void clearBefore_removesOldEntries() throws InterruptedException {
        DeadLetter.log("old-msg", DeadLetter.ACTOR_NOT_FOUND, null);
        Thread.sleep(50);
        Instant cutoff = Instant.now();
        Thread.sleep(10);
        DeadLetter.log("new-msg", DeadLetter.TIMEOUT, null);

        int removed = DeadLetter.clearBefore(cutoff);
        assertEquals(1, removed,
            "One entry before cutoff should be removed");
        assertEquals(1, DeadLetter.count(),
            "One entry after cutoff should remain");
        assertEquals(DeadLetter.TIMEOUT, DeadLetter.getLog().get(0).reason(),
            "Remaining entry should be the new-msg (TIMEOUT)");
    }

    @Test
    @DisplayName("stats() returns correct reason counts")
    @Timeout(5)
    void stats_returnsCorrectReasonCounts() {
        DeadLetter.log("a", DeadLetter.TIMEOUT, null);
        DeadLetter.log("b", DeadLetter.TIMEOUT, null);
        DeadLetter.log("c", DeadLetter.ACTOR_NOT_FOUND, null);

        var stats = DeadLetter.stats();
        assertEquals(2, stats.get(DeadLetter.TIMEOUT),
            "Should have 2 TIMEOUT entries");
        assertEquals(1, stats.get(DeadLetter.ACTOR_NOT_FOUND),
            "Should have 1 ACTOR_NOT_FOUND entry");
    }

    @Test
    @DisplayName("count() returns current log size")
    @Timeout(5)
    void count_returnsCurrentSize() {
        assertEquals(0, DeadLetter.count(), "Fresh log should be empty");

        DeadLetter.log("msg1", DeadLetter.ACTOR_NOT_FOUND, null);
        assertEquals(1, DeadLetter.count(), "Count should be 1");

        DeadLetter.log("msg2", DeadLetter.TIMEOUT, null);
        assertEquals(2, DeadLetter.count(), "Count should be 2");

        DeadLetter.clear();
        assertEquals(0, DeadLetter.count(), "Count should be 0 after clear");
    }

    @Test
    @DisplayName("countByReason() filters by reason")
    @Timeout(5)
    void countByReason_filtersCorrectly() {
        DeadLetter.log("a", DeadLetter.ACTOR_NOT_FOUND, null);
        DeadLetter.log("b", DeadLetter.ACTOR_NOT_FOUND, null);
        DeadLetter.log("c", DeadLetter.ACTOR_NOT_FOUND, null);
        DeadLetter.log("d", DeadLetter.TIMEOUT, null);
        DeadLetter.log("e", DeadLetter.TIMEOUT, null);

        assertEquals(3, DeadLetter.countByReason(DeadLetter.ACTOR_NOT_FOUND),
            "Should have 3 ACTOR_NOT_FOUND entries");
        assertEquals(2, DeadLetter.countByReason(DeadLetter.TIMEOUT),
            "Should have 2 TIMEOUT entries");
        assertEquals(0, DeadLetter.countByReason(DeadLetter.UNKNOWN),
            "Should have 0 UNKNOWN entries");
    }

    @Test
    @DisplayName("getByReason() returns entries matching reason")
    @Timeout(5)
    void getByReason_returnsMatchingEntries() {
        DeadLetter.log("a", DeadLetter.TIMEOUT, null);
        DeadLetter.log("b", DeadLetter.ACTOR_NOT_FOUND, null);
        DeadLetter.log("c", DeadLetter.TIMEOUT, null);

        List<DeadLetter.DeadLetterEntry> timeouts = DeadLetter.getByReason(DeadLetter.TIMEOUT);
        assertEquals(2, timeouts.size(), "Should have 2 TIMEOUT entries");
        assertTrue(timeouts.stream()
            .allMatch(e -> e.reason().equals(DeadLetter.TIMEOUT)),
            "All entries should have TIMEOUT reason");
    }

    @Test
    @DisplayName("clear() removes all entries")
    @Timeout(5)
    void clear_removesAllEntries() {
        DeadLetter.log("msg1", DeadLetter.ACTOR_NOT_FOUND, null);
        DeadLetter.log("msg2", DeadLetter.TIMEOUT, null);
        assertEquals(2, DeadLetter.count(), "Should have 2 entries");

        DeadLetter.clear();
        assertEquals(0, DeadLetter.count(), "Should have 0 entries after clear");
        assertTrue(DeadLetter.getLog().isEmpty(), "Log should be empty after clear");
    }

    @Test
    @DisplayName("DeadLetterEntry records message details (message, reason, cause)")
    @Timeout(5)
    void deadLetterEntry_recordsMessageDetails() {
        Exception cause = new RuntimeException("Connection timeout");
        DeadLetter.log("work-item-123", DeadLetter.TIMEOUT, cause);

        List<DeadLetter.DeadLetterEntry> log = DeadLetter.getLog();
        assertEquals(1, log.size(), "Should have 1 entry");

        DeadLetter.DeadLetterEntry entry = log.get(0);
        assertEquals("work-item-123", entry.originalMessage(),
            "Entry should record original message");
        assertEquals(DeadLetter.TIMEOUT, entry.reason(),
            "Entry should record reason");
        assertEquals("RuntimeException", entry.exceptionType(),
            "Entry should record exception type");
        assertEquals("Connection timeout", entry.exceptionMsg(),
            "Entry should record exception message");
        assertTrue(entry.timestamp() != null,
            "Entry should have timestamp");
    }
}
