package org.yawlfoundation.yawl.engine.agent.core;

/**
 * Sealed message hierarchy for zero-overhead polymorphism.
 *
 * Design rationale:
 * 1. Sealed hierarchy — compiler verifies exhaustiveness
 * 2. Records for data — auto equals/hashCode/toString
 * 3. Pattern matching — zero-cost cast elimination
 * 4. Hot path: dispatcher uses switch on type, not instanceof
 *
 * Message types:
 * - Command: instruction to perform work (no reply expected)
 * - Event: notification (no reply expected)
 * - Query: request for information (reply expected)
 * - Reply: response to a Query (correlation ID included)
 * - Internal: control messages (supervision, shutdown)
 *
 * Usage in message loop:
 *
 *     while (!interrupted()) {
 *         Object raw = actor.recv();
 *         switch (raw) {
 *             case Msg.Command cmd -> handleCommand(cmd);
 *             case Msg.Event evt -> handleEvent(evt);
 *             case Msg.Query qry -> {
 *                 Object result = handleQuery(qry);
 *                 sender.tell(new Msg.Reply(qry.correlationId(), result));
 *             }
 *             case Msg.Internal ctl -> handleControl(ctl);
 *             default -> ignoreUnknown(raw);
 *         }
 *     }
 *
 * Compiler inlines pattern matching — no virtual dispatch overhead.
 */
public sealed interface Msg permits Msg.Command, Msg.Event, Msg.Query, Msg.Reply, Msg.Internal {

    /**
     * Instruction to perform work (fire-and-forget).
     * No correlation ID needed.
     *
     * @param action Descriptive name of the command
     * @param payload Arbitrary data (null OK)
     */
    record Command(String action, Object payload) implements Msg {
        public Command {
            if (action == null) {
                throw new NullPointerException("action cannot be null");
            }
        }
    }

    /**
     * Notification of state change (fire-and-forget).
     * No correlation ID needed.
     *
     * @param type Event type (e.g., "WORK_COMPLETED", "AGENT_READY")
     * @param timestamp System.nanoTime() when event occurred
     * @param source Source actor ID (for tracing)
     * @param data Event payload (null OK)
     */
    record Event(String type, long timestamp, int source, Object data) implements Msg {
        public Event {
            if (type == null) {
                throw new NullPointerException("type cannot be null");
            }
        }
    }

    /**
     * Request for information (expect Reply).
     * Sender must be included so receiver can send reply.
     *
     * @param correlationId Unique ID for this query (sender's nanoTime or UUID)
     * @param sender ActorRef of sender (for reply routing)
     * @param question Descriptive name of the query
     * @param params Query parameters (null OK)
     */
    record Query(long correlationId, ActorRef sender, String question, Object params) implements Msg {
        public Query {
            if (sender == null) {
                throw new NullPointerException("sender cannot be null");
            }
            if (question == null) {
                throw new NullPointerException("question cannot be null");
            }
        }
    }

    /**
     * Response to a Query (reply-to-sender).
     * Correlation ID links reply to original query.
     *
     * @param correlationId Matches Query.correlationId()
     * @param result Result value (null OK)
     * @param error Throwable if query failed (null OK)
     */
    record Reply(long correlationId, Object result, Throwable error) implements Msg {
        // At least one of result or error should be non-null
    }

    /**
     * Control message for supervision, restart, or shutdown.
     * Internal use only (not sent by user code).
     *
     * @param type Control type (RESTART, SHUTDOWN, PAUSE, RESUME)
     * @param reason Reason for control action (for logging)
     * @param supervisor Supervisor ActorRef (null for engine-initiated)
     */
    record Internal(String type, String reason, ActorRef supervisor) implements Msg {
        public Internal {
            if (type == null) {
                throw new NullPointerException("type cannot be null");
            }
        }

        // Common control types as constants
        public static final String RESTART = "RESTART";
        public static final String SHUTDOWN = "SHUTDOWN";
        public static final String PAUSE = "PAUSE";
        public static final String RESUME = "RESUME";
    }
}
