/*
 * Test fixture for H_ACTOR_LEAK violation detection.
 * This file contains intentional violations that should be detected.
 */
public class ActorLeakViolations {
    
    // Pattern 1: Creating but not destroying actors
    public Actor createActorWithoutCleanup() {
        // Violation: actor created but not destroyed
        Actor newActor = new Actor("example");
        return newActor; // No destroy/cleanup call
    }
    
    // Pattern 2: Accumulating state without cleanup
    public void accumulateActorMessages(Actor actor) {
        // Violation: accumulating state without cleanup
        for (int i = 0; i < 1000; i++) {
            actor.putMessage("msg-" + i); // Accumulates messages
        }
        
        // But no clearing of the accumulated state
        while (!actor.isEmpty()) {
            actor.getMessage(); // Removal without proper cleanup
        }
    }
    
    // Pattern 3: Holding references to dead actors
    public void holdWeakReference(Actor actor) {
        // Violation: weak reference not managed
        WeakReference<Actor> weakRef = new WeakReference<>(actor);
        // No get() or cleanup call
    }
}

// Mock actor class for testing
class Actor {
    private String name;
    private Queue<String> messages = new LinkedList<>();
    
    public Actor(String name) { this.name = name; }
    public void putMessage(String msg) { messages.add(msg); }
    public String getMessage() { return messages.poll(); }
    public boolean isEmpty() { return messages.isEmpty(); }
}
