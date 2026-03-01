/*
 * Comprehensive clean actor code that should pass all H_ACTOR_LEAK guard checks.
 * Demonstrates proper actor lifecycle management and resource cleanup.
 */
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.lang.ref.WeakReference;
import java.util.*;

public class ComprehensiveCleanActorCode {

    private final Queue<Message> messageQueue = new ConcurrentLinkedQueue<>();
    private final Lock lock = new ReentrantLock();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Bounded collections with proper cleanup
    private final BlockingQueue<Message> boundedQueue = new ArrayBlockingQueue<>(1000);
    private final Map<String, Actor> actorCache = new ConcurrentHashMap<>();

    // Pattern 1: Proper actor lifecycle with cleanup
    public Actor createAndCleanupActor() {
        Actor actor = new Actor("clean-actor");

        try {
            // Actor performs work
            actor.doWork();

            // Proper cleanup - no memory leak
            actor.cleanup();
            return null; // Actor properly destroyed
        } catch (Exception e) {
            // Ensure cleanup even on error
            actor.cleanup();
            throw e;
        }
    }

    // Pattern 2: Proper bounded accumulation
    public void processWithBoundedQueue(Queue<Message> queue) {
        // Process messages with bounded queue
        List<Message> tempMessages = new ArrayList<>();
        try {
            while (!queue.isEmpty() && tempMessages.size() < 1000) {
                Message msg = queue.poll();
                if (msg != null) {
                    tempMessages.add(msg);
                    processMessage(msg);
                }
            }
        } finally {
            // Clear temporary storage
            tempMessages.clear();
        }
    }

    // Pattern 3: Proper timeout usage for blocking operations
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

    // Pattern 4: Proper resource cleanup with try-with-resources
    public void processWithResourceCleanup() throws IOException {
        try (Resource resource = new Resource()) {
            resource.use();
        } // Automatic cleanup
    }

    // Pattern 5: Proper thread management
    public void managedThreadPool() {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            for (int i = 0; i < 10; i++) {
                executor.submit(this::processTask);
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // Pattern 6: Proper weak reference management
    public void managedWeakReference(Actor actor) {
        WeakReference<Actor> weakRef = new WeakReference<>(actor);

        // Check if reference is still valid
        Actor strongRef = weakRef.get();
        if (strongRef != null) {
            // Use the reference
            strongRef.doWork();
        }

        // Clear when done
        weakRef.clear();
    }

    // Pattern 7: Proper subscription management
    public void managedSubscription(MessageQueue queue) {
        Subscription sub = queue.subscribe("topic", this::handleMessage);
        try {
            // Process messages
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // Always unsubscribe
            sub.unsubscribe();
        }
    }

    // Pattern 8: Proper cache with eviction
    public void managedCache(String key, Object value) {
        // Use computeIfAbsent to avoid memory leaks
        actorCache.computeIfAbsent(key, k -> {
            Actor actor = new Actor("cached-" + k);
            return actor;
        });

        // Run periodic cleanup
        scheduler.scheduleAtFixedRate(this::cleanupCache, 1, 1, TimeUnit.HOURS);
    }

    // Pattern 9: Proper circular reference handling
    public void safeCircularReferences() {
        Actor actor1 = new Actor("actor1");
        Actor actor2 = new Actor("actor2");

        // Set references with cleanup planning
        actor1.setReference(actor2);
        actor2.setReference(actor1);

        // Plan for cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            actor1.cleanup();
            actor2.cleanup();
        }));
    }

    // Pattern 10: Proper message queue management with backpressure
    public void backpressureManagement(Actor actor) {
        // Use bounded queue with backpressure
        while (!Thread.currentThread().isInterrupted()) {
            if (boundedQueue.remainingCapacity() > 0) {
                boundedQueue.offer(new Message(), 100, TimeUnit.MILLISECONDS);
            } else {
                // Backpressure: wait or drop when full
                Thread.sleep(50);
            }
        }
    }

    // Pattern 11: Proper database connection management
    public void databaseConnectionManagement() throws SQLException {
        // Use try-with-resources for connections
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/db");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM table")) {

            while (rs.next()) {
                // Process results
            }
        }
    }

    // Pattern 12: Proper listener management
    private final Set<EventListener> listeners = new HashSet<>();

    public void managedListener(Actor actor) {
        EventListener listener = new EventListener(actor);
        listeners.add(listener);

        // Remove listener when no longer needed
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            listeners.remove(listener);
        }));
    }

    // Pattern 13: Proper native resource management
    public void nativeResourceManagement() {
        NativeResource resource = createNativeResource();
        try {
            // Use resource
            resource.use();
        } finally {
            // Always clean up native resources
            resource.cleanup();
        }
    }

    // Pattern 14: Proper actor pooling
    private final BlockingQueue<Actor> actorPool = new ArrayBlockingQueue<>(10);

    public void pooledActorManagement() {
        try {
            Actor actor = actorPool.take();
            try {
                actor.doWork();
            } finally {
                // Return to pool
                actorPool.offer(actor, 1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Pattern 15: Proper memory monitoring
    public void memoryAwareProcessing() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();

        // Stop processing if memory is too high
        if (usedMemory > maxMemory * 0.8) {
            System.gc(); // Suggest GC
            throw new MemoryLimitExceeded("Memory usage too high");
        }

        // Process with memory awareness
        processWithMemoryAwareness();
    }

    // Pattern 16: Proper virtual thread management
    public void virtualThreadManagement() {
        Thread.Builder builder = Thread.ofVirtual()
            .name("processor")
            .inheritInheritableThreadLocals(false);

        Thread processor = builder.start(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    processNextMessage();
                    NonBlockingSpinLock.spin();
                }
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        });

        // Ensure shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            processor.interrupt();
        }));
    }

    // Helper methods
    private void processMessage(Message msg) {
        // Message processing logic with proper error handling
    }

    private void processTask() {
        // Task processing
    }

    private void processNextMessage() {
        // Process next message logic
    }

    private void cleanupCache() {
        // Evict old entries
        actorCache.entrySet().removeIf(entry -> {
            // Check if actor should be evicted
            return entry.getKey().startsWith("old-");
        });
    }

    private void processWithMemoryAwareness() {
        // Process with memory constraints
    }

    private void handleMessage(Message msg) {
        // Handle message with proper cleanup
    }

    // Cleanup method
    public void shutdown() {
        // Shutdown all resources
        scheduler.shutdown();
        actorCache.clear();
        messageQueue.clear();
        boundedQueue.clear();
        listeners.clear();
    }
}

// Clean implementations
class Actor {
    private String name;
    private volatile boolean finished = false;

    public Actor(String name) { this.name = name; }

    public void doWork() { /* Work implementation */ }
    public void cleanup() {
        finished = true;
        /* Cleanup implementation */
    }
    public boolean isFinished() { return finished; }
    public void setReference(Actor ref) { /* Set reference */ }
    public void finish() { finished = true; }
}

class Resource implements AutoCloseable {
    public void use() { /* Use resource */ }
    public void close() { /* Cleanup resource */ }
}

class Message {}
class EventListener { EventListener(Actor actor) {} }
interface Subscription { void unsubscribe(); }
interface MessageQueue { Subscription subscribe(String topic, Handler handler); }
interface Handler { void handle(Message msg); }

// Memory and resource management classes
class MemoryLimitExceeded extends RuntimeException {
    public MemoryLimitExceeded(String message) { super(message); }
}

class NonBlockingSpinLock {
    public static void spin() {
        // Non-blocking spin wait for virtual threads
        if (Thread.onSpinWait()) {
            Thread.yield();
        }
    }
}

class NativeResource {
    public void use() { /* Use native resource */ }
    public void cleanup() { /* Cleanup native resource */ }
}