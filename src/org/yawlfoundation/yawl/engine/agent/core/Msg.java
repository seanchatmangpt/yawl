package org.yawlfoundation.yawl.engine.agent.core;

/**
 * Root sealed interface for all actor messages.
 *
 * Type hierarchy (exhaustively matched in switch expressions):
 * - Command: Imperative action (no reply expected)
 * - Event: Notification of something that happened
 * - Query: Request for information (reply expected)
 * - Reply: Response to a Query
 * - Internal: Framework-level signals (actor shutdown, heartbeat)
 *
 * Benefits:
 * - Type-safe pattern matching in message handlers
 * - Compiler-verified exhaustiveness in switch expressions
 * - Zero-cost records (no boxing overhead)
 * - Serialization-friendly (records serialize naturally)
 *
 * Example usage:
 * <pre>
 * ActorRef worker = runtime.spawn(msg -> {
 *     switch (msg) {
 *         case Msg.Command cmd -> handleCommand(cmd);
 *         case Msg.Event evt -> handleEvent(evt);
 *         case Msg.Query q -> handleQuery(q);
 *         case Msg.Reply r -> handleReply(r);
 *         case Msg.Internal i -> handleInternal(i);
 *     }
 * });
 * </pre>
 */
public sealed interface Msg permits Msg.Command, Msg.Event, Msg.Query, Msg.Reply, Msg.Internal {

    /**
     * Imperative action: tell the actor to do something.
     * No reply is expected.
     */
    sealed interface Command extends Msg permits Msg.Command.Impl {
        String action();
        Object payload();

        record Impl(String action, Object payload) implements Command {}

        static Command of(String action, Object payload) {
            if (action == null || action.isBlank()) {
                throw new IllegalArgumentException("action cannot be null or blank");
            }
            return new Impl(action, payload);
        }
    }

    /**
     * Notification: something happened in the system.
     * No reply is expected; typically broadcast.
     */
    sealed interface Event extends Msg permits Msg.Event.Impl {
        String eventType();
        Object data();
        long timestamp();

        record Impl(String eventType, Object data, long timestamp) implements Event {}

        static Event of(String eventType, Object data) {
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("eventType cannot be null or blank");
            }
            return new Impl(eventType, data, System.currentTimeMillis());
        }
    }

    /**
     * Request for information: ask the actor to return something.
     * A reply is expected (correlation ID must be handled by behavior code).
     */
    sealed interface Query extends Msg permits Msg.Query.Impl {
        String question();
        Object parameter();

        record Impl(String question, Object parameter) implements Query {}

        static Query of(String question, Object parameter) {
            if (question == null || question.isBlank()) {
                throw new IllegalArgumentException("question cannot be null or blank");
            }
            return new Impl(question, parameter);
        }
    }

    /**
     * Response to a Query: the actor sends this back to the requester.
     * Behavior code must handle correlation with the original Query
     * (correlation ID pattern to be implemented in ask()).
     */
    sealed interface Reply extends Msg permits Msg.Reply.Impl {
        String correlationId();
        Object result();
        Throwable error();

        record Impl(String correlationId, Object result, Throwable error) implements Reply {}

        static Reply success(String correlationId, Object result) {
            if (correlationId == null || correlationId.isBlank()) {
                throw new IllegalArgumentException("correlationId cannot be null or blank");
            }
            return new Impl(correlationId, result, null);
        }

        static Reply failure(String correlationId, Throwable error) {
            if (correlationId == null || correlationId.isBlank()) {
                throw new IllegalArgumentException("correlationId cannot be null or blank");
            }
            if (error == null) {
                throw new IllegalArgumentException("error cannot be null");
            }
            return new Impl(correlationId, null, error);
        }
    }

    /**
     * Framework-level signals: actor shutdown, heartbeat, etc.
     * Not typically sent by user code.
     */
    sealed interface Internal extends Msg permits Msg.Internal.Shutdown, Msg.Internal.Heartbeat {

        record Shutdown(String reason) implements Internal {
            public Shutdown {
                if (reason == null || reason.isBlank()) {
                    throw new IllegalArgumentException("reason cannot be null or blank");
                }
            }
        }

        record Heartbeat(long timestamp) implements Internal {
            public Heartbeat {
                if (timestamp < 0) {
                    throw new IllegalArgumentException("timestamp cannot be negative");
                }
            }

            static Heartbeat now() {
                return new Heartbeat(System.currentTimeMillis());
            }
        }
    }
}
