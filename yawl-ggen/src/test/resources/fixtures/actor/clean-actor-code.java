/*
 * Clean actor code that should pass all guard checks.
 * No actor memory leaks or deadlock risks.
 */
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class CleanActorCode {
    
    private final Queue<Message> messageQueue = new ConcurrentLinkedQueue<>();
    private final Lock lock = new ReentrantLock();
    
    // Good: Proper actor lifecycle with cleanup
    public Actor createAndCleanupActor() {
        Actor actor = new Actor("clean-actor");
        
        // Actor performs work
        actor.doWork();
        
        // Proper cleanup - no memory leak
        actor.cleanup();
        return null; // Actor properly destroyed
    }
    
    // Good: Accumulating state with proper cleanup
    public void processWithCleanup() {
        // Process messages but ensure cleanup
        List<Message> tempMessages = new ArrayList<>();
        try {
            while (!messageQueue.isEmpty()) {
                Message msg = messageQueue.poll();
                tempMessages.add(msg);
                processMessage(msg);
            }
        } finally {
            // Clear temporary storage
            tempMessages.clear();
        }
    }
    
    // Good: Proper timeout usage for blocking operations
    public void processWithTimeout(Queue<Message> queue) {
        try {
            Message msg = queue.poll(5, TimeUnit.SECONDS); // With timeout
            if (msg != null) {
                processMessage(msg);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    // Good: Avoiding blocking operations in actors
    public void asyncProcessMessages(Actor actor) {
        // Use virtual threads for async processing
        Thread.ofVirtual().name("processor").start(() -> {
            while (!actor.isFinished()) {
                processNextMessage(actor);
                // Non-blocking check
                Thread.onSpinWait();
            }
        });
    }
    
    // Good: Proper lock ordering (same order always)
    public void safeLocking() {
        // Always acquire locks in same order to prevent deadlock
        lock.lock();
        try {
            processInCriticalSection();
        } finally {
            lock.unlock();
        }
    }
    
    // Good: Using try-with-resources for cleanup
    public void resourceCleanup() {
        try (Resource resource = new Resource()) {
            resource.use();
        } // Automatic cleanup
    }
    
    private void processMessage(Message msg) {
        // Message processing logic
    }
    
    private void processNextMessage(Actor actor) {
        // Process message logic
    }
    
    private void processInCriticalSection() {
        // Critical section logic
    }
}

// Clean actor implementation
class Actor {
    private String name;
    private volatile boolean finished = false;
    
    public Actor(String name) { this.name = name; }
    public void doWork() { /* Work implementation */ }
    public void cleanup() { /* Cleanup implementation */ }
    public boolean isFinished() { return finished; }
    public void finish() { finished = true; }
}

// Clean resource class
class Resource implements AutoCloseable {
    public void use() { /* Use resource */ }
    public void close() { /* Cleanup resource */ }
}

class Message {}
