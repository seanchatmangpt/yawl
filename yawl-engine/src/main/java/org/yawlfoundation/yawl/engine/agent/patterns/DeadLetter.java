package org.yawlfoundation.yawl.engine.agent.patterns;

import org.yawlfoundation.yawl.engine.agent.core.ActorRef;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dead Letter Channel Pattern — Monitoring and debugging failed messages.
 *
 * Use case: Messages cannot be delivered or processed.
 * - Addressed to dead actors (undeliverable)
 * - Request-reply timeouts (no response within duration)
 * - Processing errors (actor crashes while handling)
 * - Corrupted messages
 *
 * Design:
 * - Singleton DEAD_LETTER ActorRef (shared across runtime)
 * - Dead letter handler receives all undeliverable messages
 * - DeadLetterEntry records: original message, failure reason, timestamp
 * - Thread-safe bounded ring-buffer log (max 100K entries)
 *
 * Responsibilities:
 * 1. Undeliverable messages are sent here
 * 2. Logging for debugging and auditing
 * 3. Metrics collection (error rates, patterns)
 * 4. Optionally re-route or escalate
 *
 * Thread-safe. Lock-free reads. Bounded ring-buffer log (max 100K entries, evicts oldest on overflow).
 *
 * Usage:
 *
 *     // In ActorRuntime, catch undeliverable cases:
 *     try {
 *         actor.tell(msg);
 *     } catch (ActorNotFoundException e) {
 *         DeadLetter.log(msg, "ACTOR_NOT_FOUND", e);
 *     }
 *
 *     // Monitor dead letters
 *     for (DeadLetterEntry entry : DeadLetter.getLog()) {
 *         System.out.println(entry.reason() + ": " + entry.originalMessage());
 *     }
 *
 *     // Clear old entries
 *     DeadLetter.clearBefore(Instant.now().minus(Duration.ofDays(7)));
 */
public final class DeadLetter {

    /**
     * Maximum number of entries in the dead letter log.
     * When capacity is reached, oldest entries are dropped.
     */
    public static final int MAX_LOG_SIZE = 100_000;

    private static final DeadLetterChannel INSTANCE = new DeadLetterChannel();

    private DeadLetter() {
        // Utility class
    }

    /**
     * Log a dead letter (undeliverable message).
     *
     * @param originalMessage Message that couldn't be delivered
     * @param reason Why delivery failed (e.g., "ACTOR_NOT_FOUND", "TIMEOUT", "CRASH")
     * @param cause Exception if applicable (can be null)
     */
    public static void log(Object originalMessage, String reason, Throwable cause) {
        INSTANCE.record(originalMessage, reason, cause);
    }

    /**
     * Get all logged dead letters.
     *
     * @return Immutable copy of dead letter log
     */
    public static List<DeadLetterEntry> getLog() {
        return INSTANCE.getEntries();
    }

    /**
     * Get count of dead letters since creation.
     */
    public static int count() {
        return INSTANCE.size();
    }

    /**
     * Get count of dead letters for a specific reason.
     *
     * @param reason Failure reason (e.g., "TIMEOUT")
     * @return Count of matching entries
     */
    public static int countByReason(String reason) {
        return INSTANCE.countByReason(reason);
    }

    /**
     * Get all dead letters matching a reason.
     *
     * @param reason Failure reason
     * @return List of matching entries
     */
    public static List<DeadLetterEntry> getByReason(String reason) {
        return INSTANCE.getByReason(reason);
    }

    /**
     * Clear all dead letter entries.
     */
    public static void clear() {
        INSTANCE.clear();
    }

    /**
     * Clear dead letter entries before a timestamp.
     * Useful for cleanup of old entries.
     *
     * @param before Cutoff timestamp
     * @return Number of entries removed
     */
    public static int clearBefore(Instant before) {
        return INSTANCE.clearBefore(before);
    }

    /**
     * Get summary statistics about dead letters.
     *
     * @return Map of reason -> count
     */
    public static Map<String, Integer> stats() {
        return INSTANCE.getStats();
    }

    // ============= DeadLetterEntry Record =============

    /**
     * Immutable entry in the dead letter log.
     */
    public record DeadLetterEntry(
        Instant timestamp,
        Object originalMessage,
        String messageType,  // Class name of message
        String reason,       // Why delivery failed
        String exceptionType,// Exception class name (can be null)
        String exceptionMsg  // Exception message
    ) {

        public DeadLetterEntry {
            if (timestamp == null) throw new NullPointerException("timestamp");
            if (originalMessage == null) throw new NullPointerException("originalMessage");
            if (reason == null) throw new NullPointerException("reason");
        }

        /**
         * Pretty-print the entry.
         */
        @Override
        public String toString() {
            return String.format(
                "[%s] %s: %s (%s: %s)",
                timestamp, reason, messageType,
                exceptionType != null ? exceptionType : "N/A",
                exceptionMsg != null ? exceptionMsg : ""
            );
        }
    }

    // ============= DeadLetterChannel Implementation =============

    private static final class DeadLetterChannel {
        private final Deque<DeadLetterEntry> log = new ConcurrentLinkedDeque<>();
        // AtomicInteger tracks size in O(1) — avoids O(N) ConcurrentLinkedDeque.size() call
        private final AtomicInteger count = new AtomicInteger(0);

        void record(Object originalMessage, String reason, Throwable cause) {
            String exceptionType = cause != null ? cause.getClass().getSimpleName() : null;
            String exceptionMsg = cause != null ? cause.getMessage() : null;

            DeadLetterEntry entry = new DeadLetterEntry(
                Instant.now(),
                originalMessage,
                originalMessage.getClass().getSimpleName(),
                reason,
                exceptionType,
                exceptionMsg
            );

            log.addLast(entry);
            // Enforce bounded log size: evict oldest if we exceeded capacity
            if (count.incrementAndGet() > MAX_LOG_SIZE) {
                if (log.pollFirst() != null) {
                    count.decrementAndGet();
                }
            }
        }

        List<DeadLetterEntry> getEntries() {
            return new ArrayList<>(log);
        }

        int size() {
            return count.get();
        }

        int countByReason(String reason) {
            return (int) log.stream()
                .filter(e -> e.reason().equals(reason))
                .count();
        }

        List<DeadLetterEntry> getByReason(String reason) {
            return log.stream()
                .filter(e -> e.reason().equals(reason))
                .toList();
        }

        void clear() {
            log.clear();
            count.set(0);
        }

        int clearBefore(Instant before) {
            var iterator = log.iterator();
            int removed = 0;
            while (iterator.hasNext()) {
                if (iterator.next().timestamp().isBefore(before)) {
                    iterator.remove();
                    removed++;
                    count.decrementAndGet();
                }
            }
            return removed;
        }

        Map<String, Integer> getStats() {
            Map<String, Integer> stats = new ConcurrentHashMap<>();
            for (DeadLetterEntry entry : log) {
                stats.merge(entry.reason(), 1, Integer::sum);
            }
            return stats;
        }
    }

    // ============= Common Failure Reasons =============

    /** Message sent to non-existent actor */
    public static final String ACTOR_NOT_FOUND = "ACTOR_NOT_FOUND";

    /** Request-reply timeout (no response within duration) */
    public static final String TIMEOUT = "TIMEOUT";

    /** Actor crashed while processing message */
    public static final String ACTOR_CRASHED = "ACTOR_CRASHED";

    /** Message is malformed or invalid */
    public static final String INVALID_MESSAGE = "INVALID_MESSAGE";

    /** Actor explicitly rejected the message */
    public static final String REJECTED = "REJECTED";

    /** Queue full, message dropped */
    public static final String QUEUE_FULL = "QUEUE_FULL";

    /** Actor is suspended or paused */
    public static final String ACTOR_SUSPENDED = "ACTOR_SUSPENDED";

    /** Runtime is shutting down */
    public static final String RUNTIME_SHUTDOWN = "RUNTIME_SHUTDOWN";

    /** Message serialization error */
    public static final String SERIALIZATION_ERROR = "SERIALIZATION_ERROR";

    /** Unknown or uncategorized failure */
    public static final String UNKNOWN = "UNKNOWN";
}
