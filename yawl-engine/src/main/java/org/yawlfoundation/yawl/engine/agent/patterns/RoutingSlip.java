package org.yawlfoundation.yawl.engine.agent.patterns;

import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.Msg;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routing Slip Pattern — Dynamic case routing through a chain of actors.
 *
 * Use case: Workflow case flows through multiple actors in sequence,
 * with routing decisions made at runtime based on case data.
 * Each actor can add/remove actors from the routing slip.
 *
 * Design:
 * - Immutable Deque<ActorRef> attached to message envelope
 * - Each actor pops next destination and forwards message
 * - Empty deque triggers completion handler
 * - Envelope carries case ID, history, and routing slip
 *
 * Thread-safe. Immutable routing slips (no mutation after creation).
 *
 * Usage:
 *
 *     // Create routing slip
 *     Deque<ActorRef> slip = RoutingSlip.create(validator, processor, aggregator);
 *     Msg.Command work = new Msg.Command("PROCESS_CASE", caseData);
 *     RoutingSlip.Envelope envelope = RoutingSlip.envelope(caseId, work, slip);
 *
 *     // Send to first actor
 *     slip.peekFirst().tell(envelope);
 *
 *     // Inside first actor:
 *     case RoutingSlip.Envelope env -> {
 *         Object result = processCase(env.payload());
 *         RoutingSlip.forward(env.forward(), env.withPayload(result));
 *     }
 *
 *     // Last actor completes
 *     case RoutingSlip.Envelope env -> {
 *         if (env.slip().isEmpty()) {
 *             completionHandler.accept(env);  // Case complete
 *         }
 *     }
 */
public final class RoutingSlip {

    private RoutingSlip() {
        // Utility class
    }

    /**
     * Message envelope carrying case data and routing slip.
     *
     * Immutable. Contains:
     * - Case ID (unique identifier)
     * - Payload (work data, evolves as it moves through actors)
     * - Routing slip (Deque of next ActorRefs)
     * - History (audit trail of visited actors)
     *
     * @param caseId Unique workflow case identifier
     * @param payload Work data to process
     * @param slip Immutable deque of remaining actors
     * @param history List of visited actor IDs (for audit)
     */
    public record Envelope(
        String caseId,
        Object payload,
        Deque<ActorRef> slip,
        List<String> history
    ) {

        public Envelope {
            if (caseId == null) throw new NullPointerException("caseId");
            if (payload == null) throw new NullPointerException("payload");
            if (slip == null) throw new NullPointerException("slip");
            if (history == null) throw new NullPointerException("history");
        }

        /**
         * Return next actor in slip without advancing.
         * Null if slip is empty (routing complete).
         */
        public ActorRef peekNext() {
            return slip.peekFirst();
        }

        /**
         * Create new envelope with updated payload.
         * Routing slip and history unchanged.
         */
        public Envelope withPayload(Object newPayload) {
            return new Envelope(caseId, newPayload, slip, history);
        }

        /**
         * Create new envelope advancing to next actor.
         * Removes first actor from slip, adds it to history.
         */
        public Envelope advance(String actorName) {
            if (slip.isEmpty()) {
                return this;  // Already at end
            }

            LinkedList<ActorRef> newSlip = new LinkedList<>(slip);
            ActorRef visited = newSlip.removeFirst();  // Remove from copy
            List<String> newHistory = new ArrayList<>(history);
            newHistory.add(actorName + "#" + visited.id());

            return new Envelope(caseId, payload,
                Collections.unmodifiableDeque(newSlip),
                Collections.unmodifiableList(newHistory)
            );
        }

        /**
         * Check if routing is complete (no more actors).
         */
        public boolean isComplete() {
            return slip.isEmpty();
        }

        /**
         * Get audit history as comma-separated actor names.
         */
        public String historyAsString() {
            return String.join(" -> ", history);
        }
    }

    /**
     * Create an empty routing slip (for testing).
     */
    public static Deque<ActorRef> empty() {
        return Collections.unmodifiableDeque(new LinkedList<>());
    }

    /**
     * Create a routing slip from variable number of actors.
     *
     * @param actors Sequence of actors to visit in order
     * @return Immutable deque (copy made)
     */
    public static Deque<ActorRef> create(ActorRef... actors) {
        LinkedList<ActorRef> deque = new LinkedList<>(Arrays.asList(actors));
        return Collections.unmodifiableDeque(deque);
    }

    /**
     * Create a routing slip from a collection.
     *
     * @param actors Collection of actors
     * @return Immutable deque
     */
    public static Deque<ActorRef> create(Collection<ActorRef> actors) {
        LinkedList<ActorRef> deque = new LinkedList<>(actors);
        return Collections.unmodifiableDeque(deque);
    }

    /**
     * Create an envelope for a new routing slip case.
     *
     * @param caseId Unique case identifier
     * @param payload Initial work data
     * @param slip Routing slip (sequence of actors)
     * @return New envelope ready to send
     */
    public static Envelope envelope(String caseId, Object payload, Deque<ActorRef> slip) {
        return new Envelope(caseId, payload, slip, new ArrayList<>());
    }

    /**
     * Forward an envelope to the next actor in slip.
     *
     * Pops the next actor from slip and sends envelope.
     * If slip is empty, calls completion handler instead.
     *
     * @param envelope Envelope to forward
     * @param completionHandler Called if slip is empty
     */
    public static void forward(Envelope envelope, java.util.function.Consumer<Envelope> completionHandler) {
        if (envelope.isComplete()) {
            // No more actors, case is complete
            completionHandler.accept(envelope);
            return;
        }

        ActorRef nextActor = envelope.peekNext();
        if (nextActor != null) {
            nextActor.tell(envelope);
        }
    }

    /**
     * Insert an actor into the routing slip at a specific position.
     *
     * Creates a new envelope with modified slip.
     * Original envelope unchanged (immutable).
     *
     * @param envelope Original envelope
     * @param position Index to insert (0 = first)
     * @param actor Actor to insert
     * @return New envelope with modified slip
     */
    public static Envelope insertActor(Envelope envelope, int position, ActorRef actor) {
        LinkedList<ActorRef> newSlip = new LinkedList<>(envelope.slip());
        newSlip.add(position, actor);
        return new Envelope(
            envelope.caseId(),
            envelope.payload(),
            Collections.unmodifiableDeque(newSlip),
            envelope.history()
        );
    }

    /**
     * Skip next actor in the routing slip.
     *
     * Creates new envelope with next actor removed.
     * Original envelope unchanged.
     *
     * @param envelope Original envelope
     * @return New envelope with first actor skipped
     */
    public static Envelope skip(Envelope envelope) {
        LinkedList<ActorRef> newSlip = new LinkedList<>(envelope.slip());
        if (!newSlip.isEmpty()) {
            newSlip.removeFirst();
        }
        return new Envelope(
            envelope.caseId(),
            envelope.payload(),
            Collections.unmodifiableDeque(newSlip),
            envelope.history()
        );
    }

    /**
     * Clear remaining actors from routing slip (shortcut to completion).
     *
     * @param envelope Original envelope
     * @return New envelope with empty slip
     */
    public static Envelope complete(Envelope envelope) {
        return new Envelope(
            envelope.caseId(),
            envelope.payload(),
            empty(),
            envelope.history()
        );
    }

    /**
     * Registry for tracking active routing slip cases.
     * Used for monitoring and debugging.
     */
    public static class CaseRegistry {
        private final ConcurrentHashMap<String, Envelope> active = new ConcurrentHashMap<>();

        /**
         * Register a case as it enters the routing slip.
         */
        public void register(Envelope envelope) {
            active.put(envelope.caseId(), envelope);
        }

        /**
         * Update case state as it moves through slip.
         */
        public void update(Envelope envelope) {
            active.put(envelope.caseId(), envelope);
        }

        /**
         * Deregister a case when complete.
         */
        public void deregister(String caseId) {
            active.remove(caseId);
        }

        /**
         * Get current state of a case.
         */
        public Envelope get(String caseId) {
            return active.get(caseId);
        }

        /**
         * Get count of active cases.
         */
        public int size() {
            return active.size();
        }

        /**
         * Get all active cases.
         */
        public Collection<Envelope> all() {
            return active.values();
        }
    }
}
