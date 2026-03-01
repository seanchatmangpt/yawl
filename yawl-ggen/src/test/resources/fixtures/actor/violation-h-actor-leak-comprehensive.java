/*
 * Comprehensive test fixture for H_ACTOR_LEAK violation detection.
 * This file contains all known actor memory leak patterns that should be detected.
 */
public class ComprehensiveActorLeakViolations {

    // Pattern 1a: Creating but not destroying actors
    public Actor createActorWithoutCleanup1() {
        // Violation: actor created but not destroyed
        Actor newActor = new Actor("example1");
        return newActor; // No destroy/cleanup call
    }

    public Actor createActorWithoutCleanup2() {
        // Violation: using builder pattern without cleanup
        Actor actor = Actor.builder("example2").build();
        return actor; // No cleanup
    }

    public void spawnActorWithoutCleanup() {
        // Violation: spawn actor without tracking
        Actor spawned = Actor.spawn("worker");
        // Lost reference - potential memory leak
    }

    // Pattern 1b: Actor creation with static references
    private static Actor cachedActor; // Violation: static actor reference
    private static Actor[] actorArray; // Violation: array of actors

    public void cacheActor() {
        // Violation: caching actor without cleanup
        cachedActor = new Actor("cached");
        // No cleanup in shutdown
    }

    // Pattern 2a: Accumulating state without cleanup
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

    // Pattern 2b: Unbounded collection growth
    public void growUnboundedCollections() {
        // Violation: ArrayList grows without bounds
        List<String> unboundedList = new ArrayList<>();
        for (int i = 0; i < 100000; i++) {
            unboundedList.add("item-" + i);
        }
        // No clearing or size check

        // Violation: HashMap accumulates without cleanup
        Map<String, Actor> actorMap = new HashMap<>();
        while (true) {
            actorMap.put("key-" + System.currentTimeMillis(),
                        new Actor("dynamic"));
        }
    }

    // Pattern 2c: Infinite loops accumulating data
    public void infiniteAccumulation(Actor actor) {
        // Violation: infinite accumulation without bounds
        while (true) {
            actor.putMessage("continuous");
            // No break condition or cleanup
        }
    }

    // Pattern 3a: Holding strong references to dead actors
    private Actor retainedReference;

    public void retainStrongReference(Actor actor) {
        // Violation: strong reference not cleared
        retainedReference = actor;
        // No null check or cleanup
    }

    public void multipleRetainedReferences() {
        // Violation: multiple retained references
        Actor ref1 = new Actor("ref1");
        Actor ref2 = new Actor("ref2");
        // References never cleared
    }

    // Pattern 3b: Weak reference not managed
    public void holdWeakReference(Actor actor) {
        // Violation: weak reference not managed
        WeakReference<Actor> weakRef = new WeakReference<>(actor);
        // No get() or cleanup call
        // No null check for cleared reference
    }

    public void holdSoftReference(Actor actor) {
        // Violation: soft reference not managed
        SoftReference<Actor> softRef = new SoftReference<>(actor);
        // No cleanup or null check
    }

    // Pattern 4a: Thread resource leaks
    public void createUnboundedThreads() {
        // Violation: creating many threads without shutdown
        for (int i = 0; i < 1000; i++) {
            new Thread(() -> {
                while (true) {
                    // Infinite loop without cleanup
                }
            }).start();
        }
    }

    public void createExecutorLeak() {
        // Violation: executor without shutdown
        ExecutorService executor = Executors.newCachedThreadPool();
        executor.submit(() -> processTask());
        executor.submit(() -> processTask());
        // No executor.shutdown()
    }

    // Pattern 4b: Unclosed resources
    public void fileLeak() throws IOException {
        // Violation: file stream not closed
        FileInputStream fis = new FileInputStream("large-file.dat");
        byte[] data = fis.readAllBytes();
        // No fis.close() - resource leak
    }

    public void socketLeak() throws IOException {
        // Violation: socket not closed
        Socket socket = new Socket("localhost", 8080);
        OutputStream os = socket.getOutputStream();
        os.write("data".getBytes());
        // No socket.close()
    }

    // Pattern 5: Message/mailbox overflow
    public void messageQueueOverflow(Actor actor) {
        // Violation: putting without bounds checking
        while (true) {
            actor.putMessage("overflow-" + System.currentTimeMillis());
            // No backpressure or size check
        }
    }

    public void blockingQueueLeak() {
        // Violation: queue without capacity limit
        BlockingQueue<Message> queue = new LinkedBlockingQueue<>();
        while (true) {
            queue.put(new Message()); // Can grow indefinitely
        }
    }

    // Additional complex patterns
    public void complexLeakScenario() {
        // Violation: complex combination of leaks
        Map<String, List<Actor>> actorMap = new HashMap<>();

        // Create and accumulate actors
        for (int i = 0; i < 100; i++) {
            Actor actor = new Actor("complex-" + i);

            // Accumulate messages
            for (int j = 0; j < 1000; j++) {
                actor.putMessage("msg-" + j);
            }

            // Store in map without cleanup
            actorMap.put("key-" + i, List.of(actor));
        }

        // Hold weak reference without management
        WeakReference<List<Actor>> weakRef = new WeakReference<>(actorMap.get("key-1"));

        // Create threads for processing
        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    // Processing without cleanup
                }
            }).start();
        }
    }

    public void databaseConnectionLeak() {
        // Violation: connections not closed
        Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/db");
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM large_table");

        // Process results but don't close resources
        while (rs.next()) {
            // Processing...
        }
        // No conn.close(), stmt.close(), rs.close()
    }

    // Pattern 6: Listener/subscription leaks
    private List<EventListener> listeners = new ArrayList<>();

    public void addListenerWithoutRemoval(Actor actor) {
        // Violation: adding listeners without removal
        EventListener listener = new EventListener(actor);
        listeners.add(listener);
        // No removal in cleanup
    }

    public void subscriptionLeak(MessageQueue queue) {
        // Violation: subscription not unsubscribed
        Subscription sub = queue.subscribe("topic", this::handleMessage);
        // No sub.unsubscribe() on cleanup
    }

    // Helper methods
    private void processTask() {
        // Task processing
    }

    private void handleMessage(Message msg) {
        // Message handling
    }

    // Pattern 7: Cache without eviction
    private static Map<String, CacheEntry> cache = new HashMap<>();

    public void cacheWithoutEviction(String key, Object value) {
        // Violation: cache grows without eviction
        cache.put(key, new CacheEntry(value));
        // No size check or eviction policy
    }

    public void circularReferenceLeak() {
        // Violation: circular references preventing GC
        Actor actor1 = new Actor("actor1");
        Actor actor2 = new Actor("actor2");

        actor1.setReference(actor2);
        actor2.setReference(actor1);

        // Circular reference prevents GC
    }

    // Pattern 8: Native resource leaks
    public native void createNativeResource(); // JNI resource not freed

    public void nativeLeak() {
        createNativeResource();
        // No cleanup for native resource
    }
}

// Actor class with additional methods for testing
class Actor {
    private String name;
    private Queue<String> messages = new LinkedList<>();
    private Actor reference;

    public Actor(String name) { this.name = name; }

    public static Actor builder(String name) {
        return new Actor(name);
    }

    public static Actor spawn(String name) {
        return new Actor(name);
    }

    public void putMessage(String msg) { messages.add(msg); }
    public String getMessage() { return messages.poll(); }
    public boolean isEmpty() { return messages.isEmpty(); }

    public void setReference(Actor ref) { this.reference = ref; }
    public void cleanup() { /* Cleanup implementation */ }
}

// Additional classes for testing
class Message {}
class EventListener { EventListener(Actor actor) {} }
class CacheEntry { CacheEntry(Object value) {} }
interface Subscription { void unsubscribe(); }
interface MessageQueue { Subscription subscribe(String topic, Handler handler); }
interface Handler { void handle(Message msg); }