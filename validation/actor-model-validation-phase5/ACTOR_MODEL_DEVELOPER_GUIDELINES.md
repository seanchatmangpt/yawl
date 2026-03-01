# YAWL Actor Model Developer Guidelines

**Version:** 6.0.0  
**Date:** 2026-02-28  
**Status:** PRODUCTION READY  

---

## Table of Contents

1. [Overview](#overview)
2. [Actor Model Fundamentals](#actor-model-fundamentals)
3. [Performance Best Practices](#performance-best-practices)
4. [Scalability Patterns](#scalability-patterns)
5. [Memory Management](#memory-management)
6. [Troubleshooting Guide](#troubleshooting-guide)
7. [Integration with YAWL Workflows](#integration-with-yawl-workflows)
8. [Monitoring & Observability](#monitoring--observability)
9. [Code Examples](#code-examples)
10. [Testing Guidelines](#testing-guidelines)

---

## 1. Overview

The YAWL Actor Model implementation provides a robust foundation for building scalable, concurrent workflow systems. These guidelines help developers leverage the actor model effectively while maintaining high performance and reliability.

### Key Benefits

- **High Concurrency**: 1,000+ actors on a single JVM
- **Scalability**: Linear performance up to production scale
- **Fault Tolerance**: Built-in resilience patterns
- **Performance**: Sub-50ms latencies for typical operations
- **Memory Efficiency**: Optimized for long-running processes

### When to Use Actors

✅ **Use Cases:**
- High-throughput workflow processing
- Stateful business logic with complex interactions
- Event-driven architectures
- Real-time data processing
- Microservice orchestration

❌ **Avoid Cases:**
- Simple CRUD operations
- CPU-intensive tasks (use compute pools)
- Short-lived, stateless operations
- Batch processing without state

---

## 2. Actor Model Fundamentals

### 2.1 Core Concepts

```java
// Actor definition example
public class WorkflowActor extends AbstractActor {
    // Actor state (thread-safe)
    private final Map<String, WorkItem> workItems = new ConcurrentHashMap<>();
    
    // Message handling
    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(WorkItemCreated.class, this::handleWorkItemCreated)
            .match(WorkItemProcessed.class, this::handleWorkItemProcessed)
            .match(Terminate.class, this::handleTerminate)
            .build();
    }
    
    private void handleWorkItemCreated(WorkItemCreated msg) {
        // Handle work item creation
        workItems.put(msg.getItemId(), msg.getWorkItem());
    }
    
    // ... other handlers
}
```

### 2.2 Actor Lifecycle

```java
// Actor lifecycle management
public class ActorLifecycleExample {
    // 1. Create actor system
    ActorSystem system = ActorSystem.create("yawl-system");
    
    // 2. Create actor
    ActorRef actor = system.actorOf(
        Props.create(WorkflowActor.class), 
        "workflow-actor"
    );
    
    // 3. Send message
    actor.tell(new WorkItemCreated(item), ActorRef.noSender());
    
    // 4. Graceful shutdown
    system.terminate();
}
```

### 2.3 Message Patterns

#### Request-Response Pattern
```java
public class RequestResponseExample {
    public void requestResponsePattern() {
        // Create actor
        ActorRef processor = system.actorOf(Props.create(WorkItemProcessor.class));
        
        // Send request
        processor.tell(new ProcessRequest(item), getSelf());
        
        // Handle response
        receiveBuilder()
            .match(ProcessResponse.class, response -> {
                // Handle response
                System.out.println("Processed: " + response.getResult());
            })
            .build();
    }
}
```

#### Fire-and-Forget Pattern
```java
public class FireForgetExample {
    public void fireAndForgetPattern() {
        ActorRef logger = system.actorOf(Props.create(LoggerActor.class));
        
        // Send without waiting for response
        logger.tell(new LogMessage("Processing started"));
        
        // Continue processing
        processWork();
    }
}
```

---

## 3. Performance Best Practices

### 3.1 Message Optimization

#### Batch Messages
```java
// ❌ Inefficient - many small messages
for (WorkItem item : items) {
    actor.tell(new ProcessItem(item), ActorRef.noSender());
}

// ✅ Efficient - batched messages
actor.tell(new BatchProcessItems(items), ActorRef.noSender());
```

#### Use Efficient Serialization
```java
// ❌ JSON serialization (slow)
String json = objectMapper.writeValueAsString(item);

// ✅ Binary serialization (fast)
byte[] bytes = protobufSerializer.serialize(item);
```

#### Message Pooling
```java
public class MessagePool {
    private final ConcurrentLinkedQueue<WorkItem> pool = new ConcurrentLinkedQueue<>();
    
    public WorkItem borrow() {
        WorkItem item = pool.poll();
        return item != null ? item : new WorkItem();
    }
    
    public void release(WorkItem item) {
        item.reset();
        pool.offer(item);
    }
}
```

### 3.2 Concurrency Patterns

#### Virtual Threads (Java 21+)
```java
// ✅ Use virtual threads for I/O-bound operations
public CompletableFuture<WorkItem> processAsync(WorkItem item) {
    return CompletableFuture.supplyAsync(() -> {
        // I/O-bound operation (slow)
        return databaseService.fetchRelatedData(item);
    }, VirtualThreadExecutor.INSTANCE);
}
```

#### Shared State Management
```java
// ✅ Use concurrent collections
private final ConcurrentMap<String, WorkItem> workItems = new ConcurrentHashMap<>();
private final ConcurrentLinkedQueue<WorkItem> queue = new ConcurrentLinkedQueue<>();

// ✅ Use atomic operations
private final AtomicInteger processingCount = new AtomicInteger(0);
```

### 3.3 Resource Management

#### Limit Actor Creation
```java
// ✅ Use object pools for actors
private final ActorRef[] actorPool;
private final AtomicInteger poolIndex = new AtomicInteger(0);

public ActorRef getActor() {
    int index = poolIndex.getAndIncrement() % actorPool.length;
    return actorPool[index];
}
```

#### Clean Up Resources
```java
@Override
public void postStop() {
    // Clean up resources
    databaseConnection.close();
    fileHandle.close();
    
    // Cancel pending operations
    scheduledFuture.cancel(true);
}
```

---

## 4. Scalability Patterns

### 4.1 Load Balancing Patterns

#### Round Robin Load Balancing
```java
public class RoundRobinRouter {
    private final List<ActorRef> actors;
    private final AtomicInteger counter = new AtomicInteger(0);
    
    public void route(Message message) {
        int index = counter.getAndIncrement() % actors.size();
        actors.get(index).tell(message, ActorRef.noSender());
    }
}
```

#### Consistent Hashing
```java
public class ConsistentHashRouter {
    private final ConsistentHash consistentHash;
    
    public ConsistentHashRouter(List<ActorRef> actors) {
        this.consistentHash = new ConsistentHash(actors);
    }
    
    public void route(String key, Message message) {
        ActorRef actor = consistentHash.getActor(key);
        actor.tell(message, ActorRef.noSender());
    }
}
```

### 4.2 Partitioning Patterns

#### Sharded Actors
```java
public class ShardedActorSystem {
    private final Map<String, ActorRef> shards = new ConcurrentHashMap<>();
    private final int shardCount;
    
    private int getShard(String key) {
        return Math.abs(key.hashCode()) % shardCount;
    }
    
    public void sendToShard(String key, Message message) {
        int shard = getShard(key);
        String shardName = "shard-" + shard;
        
        shards.computeIfAbsent(shardName, name -> 
            system.actorOf(Props.create(ShardActor.class, name))
        ).tell(message, ActorRef.noSender());
    }
}
```

#### Dynamic Scaling
```java
public class DynamicScaler {
    private final ActorRef[] actors;
    private final int maxActors;
    private final AtomicInteger loadFactor = new AtomicInteger(0);
    
    public void adjustScale() {
        int currentLoad = loadFactor.get();
        int targetActors = calculateOptimalActors(currentLoad);
        
        if (targetActors > actors.length && actors.length < maxActors) {
            // Scale up
            addActor();
        } else if (targetActors < actors.length) {
            // Scale down
            removeActor();
        }
    }
}
```

### 4.3 Circuit Breaker Pattern

```java
public class CircuitBreakerActor extends AbstractActor {
    private final CircuitBreaker circuitBreaker;
    private final ActorRef targetActor;
    
    public CircuitBreakerActor(ActorRef targetActor) {
        this.targetActor = targetActor;
        this.circuitBreaker = CircuitBreaker.create(
            circuitBreakerConfig(),
            this::onCircuitBreakerOpen
        );
    }
    
    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Message.class, msg -> 
                circuitBreaker.execute(() -> {
                    targetActor.tell(msg, getSender());
                    return null;
                })
            )
            .build();
    }
    
    private void onCircuitBreakerOpen(Throwable failure) {
        // Handle failure
        getSender().tell(new Failure(failure), getSelf());
    }
}
```

---

## 5. Memory Management

### 5.1 Memory Optimization Techniques

#### Object Pooling
```java
public class WorkItemPool {
    private final ConcurrentLinkedQueue<WorkItem> pool = new ConcurrentLinkedQueue<>();
    
    public WorkItem borrow() {
        WorkItem item = pool.poll();
        return item != null ? item.reset() : new WorkItem();
    }
    
    public void release(WorkItem item) {
        pool.offer(item);
    }
}
```

#### Lazy Initialization
```java
public class LazyInitializationExample {
    private volatile DatabaseConnection connection;
    
    public DatabaseConnection getConnection() {
        DatabaseConnection conn = connection;
        if (conn == null) {
            synchronized (this) {
                conn = connection;
                if (conn == null) {
                    connection = conn = createConnection();
                }
            }
        }
        return conn;
    }
}
```

#### Memory Profiling
```java
// Use JVM tools for memory profiling
public class MemoryProfiler {
    public void logMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        
        System.out.printf("Memory: %d MB / %d MB%n", 
            usedMemory / (1024 * 1024),
            maxMemory / (1024 * 1024)
        );
    }
}
```

### 5.2 Garbage Collection Tuning

#### JVM Configuration
```bash
# Optimize for low latency
java -Xms4g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:InitiatingHeapOccupancyPercent=45 \
     -XX:+ParallelRefProcEnabled \
     -XX:+AlwaysPreTouch \
     -jar yawl-actor-system.jar
```

#### Avoid Memory Leaks
```java
// ✅ Clean up references
public void processWork() {
    List<WorkItem> items = fetchItems();
    try {
        // Process items
        for (WorkItem item : items) {
            process(item);
        }
    } finally {
        // Clear references to help GC
        items.clear();
    }
}

// ✅ Use weak references for caches
private final Map<String, WeakReference<WorkItem>> cache = new WeakHashMap<>();
```

---

## 6. Troubleshooting Guide

### 6.1 Common Issues and Solutions

#### High Memory Usage
**Symptom:** Memory grows continuously
```bash
# Monitor memory usage
jstat -gcutil <pid> 1s

# Heap dump analysis
jmap -dump:format=b,file=heapdump.hprof <pid>
```

**Solutions:**
1. Implement object pooling
2. Use weak references for caches
3. Check for circular references
4. Monitor for memory leaks

```java
// Fix: Use object pooling
public class MemoryFixExample {
    private final WorkItemPool pool = new WorkItemPool();
    
    public WorkItem process(WorkItem item) {
        WorkItem borrowed = pool.borrow();
        try {
            // Copy data to borrowed object
            borrowed.copyFrom(item);
            return processInternal(borrowed);
        } finally {
            pool.release(borrowed);
        }
    }
}
```

#### High Latency
**Symptom:** Response times exceed 50ms
```bash
# Monitor latency
curl -w "@format.txt" -o /dev/null -s http://localhost:8080/metrics

# Check thread dumps
jstack <pid> > threads.txt
```

**Solutions:**
1. Implement caching
2. Use async processing
3. Optimize database queries
4. Scale horizontally

```java
// Fix: Implement caching
public class LatencyFixExample {
    private final Cache<String, WorkItem> cache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build();
    
    public WorkItem getWorkItem(String id) {
        return cache.get(id, key -> databaseService.loadItem(key));
    }
}
```

#### Actor Deadlock
**Symptom:** Actors stop responding
```bash
# Monitor actor system
curl http://localhost:8080/actors/status

# Check actor queues
jstack <pid> | grep "Actor"
```

**Solutions:**
1. Implement timeout handling
2. Use non-blocking patterns
3. Add circuit breakers
4. Monitor actor health

```java
// Fix: Add timeout handling
public class DeadlockFixExample {
    private final Timeout timeout = new Timeout(Duration.ofSeconds(5));
    
    public void processWithTimeout(WorkItem item) {
        Future<Object> future = Patterns.ask(
            targetActor, 
            new ProcessRequest(item), 
            timeout
        );
        
        try {
            Object result = future.result();
            // Handle result
        } catch (TimeoutException e) {
            // Handle timeout
            handleTimeout(item);
        }
    }
}
```

### 6.2 Performance Analysis

#### Load Testing
```java
// Use JMH for load testing
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class ActorLoadTest {
    private ActorSystem system;
    private ActorRef actor;
    
    @Setup
    public void setup() {
        system = ActorSystem.create("test-system");
        actor = system.actorOf(Props.create(TestActor.class));
    }
    
    @Benchmark
    public void testThroughput() {
        actor.tell(new TestMessage(), ActorRef.noSender());
    }
    
    @TearDown
    public void tearDown() {
        system.terminate();
    }
}
```

#### Profiling Tools
```bash
# CPU profiling
async-profiler -e cpu -f profile.html <pid>

# Memory profiling
jcmd <pid> GC.heap_info

# Thread profiling
jcmd <pid> Thread.print
```

---

## 7. Integration with YAWL Workflows

### 7.1 Actor-Workflow Integration

#### Creating Workflow-Aware Actors
```java
public class WorkflowAwareActor extends AbstractActor {
    private final WorkflowEngine workflowEngine;
    private final Map<String, ActorRef> caseActors = new ConcurrentHashMap<>();
    
    public WorkflowAwareActor(WorkflowEngine engine) {
        this.workflowEngine = engine;
    }
    
    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(LaunchWorkflow.class, this::handleLaunchWorkflow)
            .match(ProcessWorkItem.class, this::handleProcessWorkItem)
            .build();
    }
    
    private void handleLaunchWorkflow(LaunchWorkflow msg) {
        // Launch workflow
        String caseId = workflowEngine.launchWorkflow(msg.getWorkflowSpec());
        
        // Create case-specific actor
        ActorRef caseActor = context().actorOf(
            Props.create(CaseActor.class, caseId)
        );
        
        caseActors.put(caseId, caseActor);
    }
}
```

#### Workflow Event Handling
```java
public class WorkflowEventHandler {
    private final ActorRef workflowActor;
    
    public void handleWorkflowEvent(WorkflowEvent event) {
        switch (event.getType()) {
            case WORK_ITEM_CREATED:
                workflowActor.tell(new WorkItemCreated(event), ActorRef.noSender());
                break;
            case WORK_ITEM_COMPLETED:
                workflowActor.tell(new WorkItemCompleted(event), ActorRef.noSender());
                break;
            case CASE_TERMINATED:
                workflowActor.tell(new CaseTerminated(event), ActorRef.noSender());
                break;
        }
    }
}
```

### 7.2 YAWL Service Integration

#### Custom YAWL Services
```java
@Service("actorBasedService")
public class ActorBasedYawlService implements YawlService {
    private final ActorSystem actorSystem;
    private final ActorRef serviceActor;
    
    public ActorBasedYawlService() {
        this.actorSystem = ActorSystem.create("yawl-service");
        this.serviceActor = actorSystem.actorOf(
            Props.create(ActorServiceImpl.class), 
            "service-actor"
        );
    }
    
    @Override
    public void executeTask(TaskParameters params) {
        serviceActor.tell(new ExecuteTask(params), ActorRef.noSender());
    }
}
```

#### Async Service Implementation
```java
public class AsyncActorService extends AbstractActor {
    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(ExecuteTask.class, this::executeTask)
            .build();
    }
    
    private void executeTask(ExecuteTask msg) {
        // Execute asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                TaskResult result = performTask(msg.getParameters());
                getSender().tell(new TaskCompleted(result), getSelf());
            } catch (Exception e) {
                getSender().tell(new TaskFailed(e), getSelf());
            }
        });
    }
}
```

---

## 8. Monitoring & Observability

### 8.1 Metrics Collection

#### Prometheus Metrics
```java
public class ActorMetrics {
    private final Counter processedItems = Counter.build()
        .name("yawl_actor_processed_items_total")
        .help("Total items processed by actors")
        .register();
    
    private final Histogram processingTime = Histogram.build()
        .name("yawl_actor_processing_time_seconds")
        .help("Time spent processing items")
        .register();
    
    public void recordProcessing(WorkItem item, long durationMs) {
        processedItems.inc();
        processingTime.observe(durationMs / 1000.0);
    }
}
```

#### Micrometer Integration
```java
public class ActorMonitoring {
    private final MeterRegistry registry;
    private final Timer processingTimer;
    
    public ActorMonitoring(MeterRegistry registry) {
        this.registry = registry;
        this.processingTimer = Timer.builder("yawl.actor.processing")
            .description("Time spent processing work items")
            .register(registry);
    }
    
    public void recordProcessing(WorkItem item) {
        processingTimer.record(() -> {
            // Process item
            processItem(item);
        });
    }
}
```

### 8.2 Logging Best Practices

#### Structured Logging
```java
public class ActorLogging {
    private final Logger logger = LoggerFactory.getLogger(Actor.class);
    
    public void logWorkItemCreated(WorkItem item) {
        MDC.put("caseId", item.getCaseId());
        MDC.put("workItemId", item.getId());
        MDC.put("workflow", item.getWorkflowName());
        
        logger.info("Work item created: {}", item);
        
        MDC.clear();
    }
}
```

#### Distributed Tracing
```java
public class TracedActor extends AbstractActor {
    private final Tracer tracer;
    
    public TracedActor(Tracer tracer) {
        this.tracer = tracer;
    }
    
    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(ProcessMessage.class, this::processWithTracing)
            .build();
    }
    
    private void processWithTracing(ProcessMessage msg) {
        Span span = tracer.buildSpan("process-message")
            .withTag("caseId", msg.getCaseId())
            .start();
        
        try (Scope scope = tracer.makeActive(span)) {
            // Process message
            processInternal(msg);
        } finally {
            span.finish();
        }
    }
}
```

---

## 9. Code Examples

### 9.1 Complete Actor Implementation

```java
public class OrderProcessingActor extends AbstractActor {
    // State
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    
    // Metrics
    private final Counter ordersProcessed = Counter.build()
        .name("orders_processed_total")
        .help("Total orders processed")
        .register();
    
    public OrderProcessingActor(OrderRepository repo, 
                               PaymentService payment,
                               InventoryService inventory) {
        this.orderRepository = repo;
        this.paymentService = payment;
        this.inventoryService = inventory;
    }
    
    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(CreateOrder.class, this::handleCreateOrder)
            .match(ProcessPayment.class, this::handleProcessPayment)
            .match(UpdateInventory.class, this::handleUpdateInventory)
            .match(GetOrderStatus.class, this::handleGetOrderStatus)
            .match(Terminate.class, this::handleTerminate)
            .build();
    }
    
    private void handleCreateOrder(CreateOrder msg) {
        Order order = new Order(msg.getCustomerId(), msg.getItems());
        
        // Validate order
        if (!validateOrder(order)) {
            getSender().tell(new OrderFailed("Invalid order"), getSelf());
            return;
        }
        
        // Save to database
        orderRepository.save(order);
        orders.put(order.getId(), order);
        
        // Process payment asynchronously
        paymentService.processPayment(order.getId(), order.getTotal());
        
        // Respond
        getSender().tell(new OrderCreated(order), getSelf());
    }
    
    private void handleProcessPayment(ProcessPayment msg) {
        Order order = orders.get(msg.getOrderId());
        if (order == null) {
            return;
        }
        
        boolean paymentSuccess = paymentService.charge(order.getCustomerId(), 
                                                    order.getTotal());
        
        if (paymentSuccess) {
            order.setStatus(OrderStatus.PROCESSING);
            orderRepository.update(order);
            
            // Update inventory
            inventoryService.reserveItems(order.getItems());
            
            // Notify shipping
            notifyShippingService(order);
        } else {
            order.setStatus(OrderStatus.FAILED);
            orderRepository.update(order);
        }
        
        ordersProcessed.inc();
    }
    
    // ... other handlers
    
    private boolean validateOrder(Order order) {
        // Check inventory
        for (OrderItem item : order.getItems()) {
            if (!inventoryService.isAvailable(item.getProductId(), item.getQuantity())) {
                return false;
            }
        }
        
        // Check customer credit
        if (!paymentService.hasCredit(order.getCustomerId(), order.getTotal())) {
            return false;
        }
        
        return true;
    }
}
```

### 9.2 Actor System Setup

```java
public class ActorSystemSetup {
    public static void main(String[] args) {
        // Create actor system
        ActorSystem system = ActorSystem.create("order-processing-system");
        
        // Create dependencies
        OrderRepository orderRepository = new OrderRepositoryImpl();
        PaymentService paymentService = new PaymentServiceImpl();
        InventoryService inventoryService = new InventoryServiceImpl();
        
        // Create actors
        ActorRef orderProcessor = system.actorOf(
            Props.create(OrderProcessingActor.class,
                       () -> new OrderProcessingActor(
                           orderRepository,
                           paymentService,
                           inventoryService
                       )),
            "order-processor"
        );
        
        ActorRef inventoryActor = system.actorOf(
            Props.create(InventoryActor.class, inventoryService),
            "inventory-processor"
        );
        
        // Create routers for scaling
        ActorRef orderRouter = system.actorOf(
            Props.empty()
                .withRouter(new RoundRobinRouter(5))
                .withDispatcher("akka.actor.blocking-io-dispatcher"),
            "order-router"
        );
        
        // Start monitoring
        startMonitoring(system);
        
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            system.terminate();
        }));
    }
    
    private static void startMonitoring(ActorSystem system) {
        // Start metrics collection
        MetricsCollector collector = new MetricsCollector(system);
        collector.start();
        
        // Start health checks
        HealthChecker healthChecker = new HealthChecker(system);
        healthChecker.start();
    }
}
```

---

## 10. Testing Guidelines

### 10.1 Unit Testing Actors

#### Testing Actor Behavior
```java
public class OrderProcessingActorTest {
    private ActorSystem system;
    private TestProbe probe;
    private OrderRepository mockRepository;
    private PaymentService mockPayment;
    private InventoryService mockInventory;
    
    @Before
    public void setUp() {
        system = ActorSystem.create("test-system");
        probe = new TestProbe(system);
        
        mockRepository = mock(OrderRepository.class);
        mockPayment = mock(PaymentService.class);
        mockInventory = mock(InventoryService.class);
    }
    
    @Test
    public void testCreateOrder() {
        // Create actor
        ActorRef actor = system.actorOf(
            Props.create(OrderProcessingActor.class,
                       () -> new OrderProcessingActor(
                           mockRepository,
                           mockPayment,
                           mockInventory
                       )),
            "test-processor"
        );
        
        // Test message
        CreateOrder msg = new CreateOrder("customer1", Arrays.asList(
            new OrderItem("product1", 2, 100.0)
        ));
        
        // Send message
        actor.tell(msg, probe.ref());
        
        // Verify response
        OrderCreated response = probe.expectMsgClass(OrderCreated.class);
        assertNotNull(response.getOrder());
        assertEquals("customer1", response.getOrder().getCustomerId());
    }
    
    @After
    public void tearDown() {
        TestKit.shutdownActorSystem(system);
    }
}
```

### 10.2 Integration Testing

#### Testing Actor Interactions
```java
public class ActorIntegrationTest {
    private ActorSystem system;
    private TestKit orderKit;
    private TestKit inventoryKit;
    private TestKit paymentKit;
    
    @Before
    public void setUp() {
        system = ActorSystem.create("integration-test");
        orderKit = new TestKit(system);
        inventoryKit = new TestKit(system);
        paymentKit = new TestKit(system);
        
        // Create test services that respond to messages
        OrderRepository testRepo = new TestOrderRepository();
        PaymentService testPayment = new TestPaymentService(paymentKit.getRef());
        InventoryService testInventory = new TestInventoryService(inventoryKit.getRef());
        
        // Create actors
        ActorRef orderProcessor = system.actorOf(
            Props.create(OrderProcessingActor.class,
                       () -> new OrderProcessingActor(
                           testRepo,
                           testPayment,
                           testInventory
                       )),
            "order-processor"
        );
        
        // Connect actors
        orderProcessor.tell(new CreateOrder("customer1", Arrays.asList(
            new OrderItem("product1", 1, 50.0)
        )), orderKit.getRef());
    }
    
    @Test
    public void testOrderProcessingFlow() {
        // Wait for payment processing
        PaymentRequest paymentRequest = paymentKit.expectMsgClass(PaymentRequest.class);
        
        // Simulate payment success
        paymentKit.getRef().tell(new PaymentSuccess(paymentRequest.getOrderId()), ActorRef.noSender());
        
        // Wait for inventory update
        InventoryRequest inventoryRequest = inventoryKit.expectMsgClass(InventoryRequest.class);
        
        // Simulate inventory success
        inventoryKit.getRef().tell(new InventorySuccess(inventoryRequest.getOrderId()), ActorRef.noSender());
        
        // Verify order completion
        OrderCompleted completion = orderKit.expectMsgClass(OrderCompleted.class);
        assertEquals(OrderStatus.COMPLETED, completion.getOrder().getStatus());
    }
}
```

### 10.3 Performance Testing

#### JMH Benchmark Testing
```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class ActorPerformanceTest {
    private ActorSystem system;
    private ActorRef actor;
    
    @Param({"1", "10", "100", "1000"})
    private int concurrentActors;
    
    @Setup
    public void setup() {
        system = ActorSystem.create("benchmark-system");
        actor = system.actorOf(Props.create(BenchmarkActor.class), "benchmark-actor");
    }
    
    @Benchmark
    public void testThroughput() {
        for (int i = 0; i < concurrentActors; i++) {
            actor.tell(new BenchmarkMessage("test-" + i), ActorRef.noSender());
        }
    }
    
    @TearDown
    public void tearDown() {
        TestKit.shutdownActorSystem(system);
    }
}
```

---

## Conclusion

The YAWL Actor Model provides a powerful foundation for building scalable, concurrent workflow systems. By following these guidelines, developers can:

1. **Build high-performance systems** with sub-50ms latencies
2. **Achieve linear scalability** up to 1,000+ actors
3. **Ensure fault tolerance** through built-in resilience patterns
4. **Optimize memory usage** with efficient data structures
5. **Maintain observability** with comprehensive monitoring

Remember to:
- Always test performance at scale
- Monitor memory usage and GC behavior
- Use appropriate patterns for your use case
- Implement proper error handling and recovery
- Keep monitoring and alerting in place

For additional support, visit the YAWL documentation or contact the engineering team.

---

**Last Updated:** 2026-02-28  
**Version:** 6.0.0  
**Status:** PRODUCTION READY  
