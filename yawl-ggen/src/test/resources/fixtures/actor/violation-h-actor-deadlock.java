/*
 * Test fixture for H_ACTOR_DEADLOCK violation detection.
 * This file contains intentional violations that should be detected.
 */
import java.util.concurrent.locks.Lock;

public class ActorDeadlockViolations {
    
    private final Lock lockA = new ReentrantLock();
    private final Lock lockB = new ReentrantLock();
    
    // Pattern 1: Circular waiting with synchronized and wait/notify
    public void circularWait(Actor actor) {
        synchronized (actor) {
            try {
                // Violation: synchronized with wait/notify potential deadlock
                actor.wait(); // Can cause deadlock if not properly synchronized
                actor.notify();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    // Pattern 2: Nested locking patterns
    public void nestedLocking() {
        // Violation: nested locking deadlock risk
        lockA.lock();
        try {
            synchronized (lockB) {
                // Deadlock risk: holding lockA, trying to acquire lockB
                // Another thread could do the reverse
            }
        } finally {
            lockA.unlock();
        }
    }
    
    // Pattern 3: Unbounded blocking on message queues
    public void unboundedBlocking(Queue<Message> queue) {
        // Violation: blocking operations without timeout
        while (true) {
            Message msg = queue.poll(); // Can block indefinitely
            if (msg != null) {
                processMessage(msg);
            }
        }
        
        // Better alternative with timeout
        /*
        while (true) {
            Message msg = queue.poll(1000, TimeUnit.MILLISECONDS);
            if (msg != null) {
                processMessage(msg);
            }
        }
        */
    }
    
    // Pattern 4: Actor blocking indefinitely
    public void blockingOperation(Actor actor) {
        try {
            // Violation: indefinite sleep with actor operations
            Thread.sleep(10000); // Long sleep while actor might be waiting
            actor.join(); // Can block indefinitely
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    // Pattern 5: Resource ordering violation
    public void resourceOrdering() {
        // Violation: inconsistent lock ordering
        lockA.lock();
        try {
            lockB.lock(); // If another thread does lockB then lockA, deadlock
            try {
                // Critical section
            } finally {
                lockB.unlock();
            }
        } finally {
            lockA.unlock();
        }
    }
    
    private void processMessage(Message msg) {
        // Message processing logic
    }
}

// Mock classes for testing
class Actor extends Thread {
    public void wait() throws InterruptedException { super.wait(); }
    public void notify() { super.notify(); }
    public void join() throws InterruptedException { super.join(); }
}

class Message {}
class ReentrantLock implements Lock {
    public void lock() {}
    public void unlock() {}
}
