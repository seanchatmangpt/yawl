/**
 * Copyright 2004-2026 YAWL Foundation
 *
 * YAWL Resourcing Module v6.0.0-GA
 *
 * This package provides intelligent resource management capabilities for the YAWL workflow engine,
 * featuring autonomous agent routing, AI-powered work item dispatch, and dynamic participant
 * discovery through agent marketplace integration. The module implements advanced resource
 * allocation strategies and manages work queue state transitions with Java 25 virtual
 * thread support for high-performance I/O operations.
 *
 * <h2>Core Features</h2>
 * <ul>
 *   <li><b>Autonomous Agent Routing</b> - AI-powered work item dispatch with intelligent
 *       capability matching and priority-based routing</li>
 *   <li><b>Agent Marketplace Integration</b> - Dynamic participant discovery through marketplace
 *       protocols with real-time capability querying</li>
 *   <li><b>Resource Allocation Strategies</b> - Multiple allocation algorithms including
 *       RoundRobin, LeastLoaded, and RoleBased strategies</li>
 *   <li><b>Work Queue Management</b> - Thread-safe work item state transitions with atomic
 *       queue operations and state synchronization</li>
 *   <li><b>Virtual Thread Integration</b> - Java 25 virtual threads for high-performance
 *       I/O operations with minimal memory footprint</li>
 *   <li><b>Thread Safety Guarantees</b> - ReentrantLock mechanisms and atomic counters
 *       for concurrent access guarantees</li>
 *   <li><b>MCP/A2A Integration</b> - ResourceManager as YWorkItemEventListener for
 *       event-driven workflow coordination</li>
 *   <li><b>OpenSage Integration</b> - AI agent capability matching with semantic analysis</li>
 * </ul>
 *
 * <h2>Key Components</h2>
 *
 * <h3>Resource Management</h3>
 * <p>The ResourceManager serves as the central orchestrator for participant management,
 * work queue operations, and event coordination. It implements the YWorkItemEventListener
 * interface to integrate with the YAWL execution engine.</p>
 *
 * <pre>{@code
 * ResourceManager manager = new ResourceManager();
 * manager.initialize("participants.xml");
 * manager.setAllocationStrategy(new LeastLoadedAllocator());
 * }</pre>
 *
 * <h3>Allocation Strategies</h3>
 * <ul>
 *   <li>{@link ResourceManager#setAllocationStrategy(ResourceAllocator)} - Sets the
 *       primary allocation strategy</li>
 *   <li>{@link RoundRobinAllocator} - Distributes work items in round-robin fashion</li>
 *   <li>{@link LeastLoadedAllocator} - Routes to participants with lowest workload</li>
 *   <li>{@link RoleBasedAllocator} - Matches work items to participant capabilities</li>
 * </ul>
 *
 * <h3>Work Item Routing</h3>
 * <p>Resource routing decisions are based on:
 * <ul>
 *   <li>Participant capabilities and role assignments</li>
 *   <li>Current workload distribution across agents</li>
 *   <li>Priority levels and service level agreements</li>
 *   <li>Historical performance metrics</li>
 *   <li>Geographical and network proximity</li>
 * </ul>
 * </p>
 *
 * <h3>Virtual Thread Integration</h3>
 * <p>Java 25 virtual threads provide scalable I/O operations for:
 * <ul>
 *   <li>Participant capability queries</li>
 *   <li>Marketplace service calls</li>
 *   <li>Work item state transitions</li>
 *   <li>Event-driven notifications</li>
 *   <li>AI capability matching operations</li>
 * </ul>
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>All operations in this package maintain thread safety through:
 * <ul>
 *   <li><b>ReentrantLock</b> - Fine-grained locking for queue operations</li>
 *   <li><b>Atomic counters</b> - Thread-safe workload tracking</li>
 *   <li><b>Volatile references</b> - Visibility guarantees for shared state</li>
 *   <li><b>Immutable collections</li> - Defensive copying for configuration</li>
 *   <li><b>Concurrent collections</b> - Thread-safe data structures</li>
 * </ul>
 * </p>
 *
 * <h3>Concurrent Access Guarantees</h3>
 * <pre>{@code
 * // Thread-safe operations
 * // Multiple threads can safely call these concurrently:
 * participant.registerCapability("Java Programming");
 * workQueue.enqueueWorkItem(item);
 * allocator.setStrategy(new RoleBasedAllocator());
 * }</pre>
 *
 * <h2>AI Integration Patterns</h2>
 * <h3>Autonomous Agent Routing</h3>
 * <p>Work items are routed using AI-powered algorithms that analyze:
 * <ul>
 *   <li>Participant skill profiles and historical performance</li>
 *   <li>Work item complexity and required capabilities</li>
 *   <li>Network latency and geographical factors</li>
 *   <li>Current load distribution across agents</li>
 *   <li>Service level agreements and priority constraints</li>
 * </ul>
 * </p>
 *
 * <h3>OpenSage Integration</h3>
 * <p>The CapabilityMatcher class integrates with OpenSage for:
 * <ul>
 *   <li>Semantic analysis of work item requirements</li>
 *   <li>AI-powered participant capability scoring</li>
 *   <li>Dynamic capability discovery and learning</li>
 *   <li>Predictive routing based on historical patterns</li>
 *   <li>Automated skill gap identification</li>
 * </ul>
 * </p>
 *
 * <pre>{@code
 * // AI-powered routing example
 * CapabilityMatcher matcher = new CapabilityMatcher(opensageClient);
 * RoutingDecision decision = matcher.matchWorkItem(workItem, availableParticipants);
 * participant.assignWorkItem(workItem, decision.getScore());
 * }</pre>
 *
 * <h2>Event Integration</h2>
 * <p>The ResourceManager implements YWorkItemEventListener to handle:
 * <ul>
 *   <li>Work item arrival notifications</li>
 *   <li>Participant status changes</li>
 *   <li>Work item completion events</li>
 *   <li>Resource allocation failures</li>
 *   <li>System health monitoring</li>
 * </ul>
 * </p>
 *
 * <h2>Configuration</h2>
 * <p>Resource management is configured through:
 * <ul>
 *   <li>participants.xml - Participant definitions and capabilities</li>
 *   <li>allocation-config.xml - Allocation strategy parameters</li>
 *   <li>marketplace-config.xml - Marketplace integration settings</li>
 *   <li>ai-config.properties - AI model and service configuration</li>
 * </ul>
 * </p>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Setup</h3>
 * <pre>{@code
 * // Initialize resource manager
 * ResourceManager manager = new ResourceManager();
 * manager.initialize("config/participants.xml");
 *
 * // Configure allocation strategy
 * ResourceAllocator allocator = new LeastLoadedAllocator()
 *     .withLoadThreshold(0.8)
 *     .withPrioritizeExperienced(true);
 * manager.setAllocationStrategy(allocator);
 *
 * // Register participants
 * Participant developer = new Participant("dev1", "Developer");
 * developer.registerCapability("Java Programming");
 * developer.registerCapability("YAWL Workflow");
 * manager.registerParticipant(developer);
 * }</pre>
 *
 * <h3>Work Item Routing</h3>
 * <pre>{@code
 * // Create work item
 * WorkItem item = new WorkItem("processOrder", "Order Processing");
 *
 * // Route using AI-powered allocation
 * Participant assigned = manager.routeWorkItem(item);
 * if (assigned != null) {
 *     assigned.assignWorkItem(item);
 *     // Work item now in progress
 * } else {
 *     // Handle allocation failure
 * }</pre>
 *
 * <h3>Marketplace Integration</h3>
 * <pre>{@code
 * // Connect to agent marketplace
 * MarketplaceClient client = new MarketplaceClient("marketplace.yawl.org");
 * client.registerCapabilities("Java Programming", "Spring Boot", "SQL");
 *
 * // Query for available participants
 * List<Participant> available = client.queryAvailable(
 *     new CapabilityQuery("Java Programming", "Senior")
 *     .withLocation("US/East")
 *     .withMaxResponseTime(5000)
 * );
 *
 * // Dynamically register discovered participants
 * for (Participant participant : available) {
 *     manager.registerParticipant(participant);
 * }</pre>
 *
 * <h2>Monitoring and Observability</h2>
 * <p>The module provides comprehensive monitoring through:
 * <ul>
 *   <li>Work queue metrics (size, wait times, throughput)</li>
 *   <li>Participant workload distribution</li>
 *   <li>Allocation strategy effectiveness</li>
 *   <li>AI capability matching accuracy</li>
 *   <li>Virtual thread pool utilization</li>
 *   <li>Resource allocation performance</li>
 * </ul>
 * </p>
 *
 * @since 6.0.0-GA
 * @version 6.0.0-GA
 *
 * @see ResourceManager
 * @see ResourceAllocator
 * @see CapabilityMatcher
 * @see LeastLoadedAllocator
 * @see RoleBasedAllocator
 * @see RoundRobinAllocator
 * @see Participant
 * @see RoutingDecision
 */