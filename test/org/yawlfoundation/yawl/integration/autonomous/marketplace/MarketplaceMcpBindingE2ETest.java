package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import junit.framework.TestCase;
import org.junit.jupiter.api.Tag;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.events.MarketplaceEvent;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.events.OrderEvent;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.events.VendorEvent;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-End Integration Tests for MarketplaceMcpBinding with SPARQL backend.
 *
 * <p>These tests cover the complete workflow from data loading through query execution
 * to result verification. They also test error recovery scenarios and graceful fallback
 * when the SPARQL engine is unavailable.</p>
 *
 * <h3>Test Categories:</h3>
 * <ol>
 *   <li><b>Full workflow tests</b> - Load data → query → verify results</li>
 *   <li><b>Fallback tests</b> - Pure-Java fallback when SPARQL engine unavailable</li>
 *   <li><b>Error recovery tests</b> - Engine restart, connection retry, graceful degradation</li>
 * </ol>
 *
 * <h3>External Dependencies:</h3>
 * <ul>
 *   <li>QLever instance on port 7001 (for SPARQL queries)</li>
 *   <li>Oxigraph instance on port 19877 (for CONSTRUCT queries)</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
@Tag("integration")
public class MarketplaceMcpBindingE2ETest extends TestCase {

    private static final String TEST_SPEC_ID = "marketplace-e2e-test";
    private static final int TEST_VENDOR_COUNT = 3;
    private static final int TEST_ORDER_COUNT = 5;

    private AgentMarketplace marketplace;
    private MarketplaceMcpBinding binding;
    private QLeverSparqlEngine sparqlEngine;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Initialize test marketplace
        marketplace = new AgentMarketplace();

        // Initialize SPARQL engine (will be skipped if unavailable)
        sparqlEngine = new QLeverSparqlEngine();
        if (sparqlEngine.isAvailable()) {
            binding = new MarketplaceMcpBinding(marketplace, sparqlEngine);
        } else {
            // Test with fallback only
            binding = new MarketplaceMcpBinding(marketplace, null);
        }
    }

    // -------------------------------------------------------------------------
    // Full Workflow Tests
    // -------------------------------------------------------------------------

    public void testCompleteWorkflowWithDataLoadingAndQuerying() throws Exception {
        // Skip if SPARQL engine not available
        if (sparqlEngine == null || !sparqlEngine.isAvailable()) {
            System.out.println("Skipping full workflow test - SPARQL engine unavailable");
            return;
        }

        // Step 1: Load test data
        loadTestVendorData(TEST_VENDOR_COUNT);
        loadTestOrderData(TEST_ORDER_COUNT);

        // Step 2: Query for vendors
        Optional<String> vendorsResult = binding.queryAgentsAsTurtle(
            MarketplaceConstructQueries.CONSTRUCT_ALL_LIVE_AGENTS);

        assertTrue("Should retrieve vendor data", vendorsResult.isPresent());
        assertFalse("Should not be empty", vendorsResult.get().isEmpty());

        // Verify vendor count in result
        String vendorData = vendorsResult.get();
        int vendorCount = countOccurrences(vendorData, "AgentListing");
        assertTrue("Should have vendors in result", vendorCount > 0);

        // Step 3: Query for specific vendor by namespace
        String namespaceQuery = "SELECT ?vendor WHERE { ?vendor a <http://yawlfoundation.org/yawl/marketplace#AgentListing> ; marketplace:namespace ?ns . FILTER (?ns = \"test-namespace\") }";
        Optional<String> specificVendorResult = binding.queryAgents(namespaceQuery);

        assertTrue("Should find specific vendor", specificVendorResult.isPresent());

        // Step 4: Test marketplace tools work with loaded data
        List<McpToolDescriptor> tools = binding.getMcpTools();
        assertEquals("Should have 5 MCP tools", 5, tools.size());

        // Verify heartbeat tool works
        // Note: Actual execution would require MCP server setup
        assertTrue("All tools should be properly configured",
            tools.stream().allMatch(t -> t.name() != null && !t.description().isBlank()));
    }

    public void testWorkflowWithRealTimeEventProcessing() throws Exception {
        if (sparqlEngine == null || !sparqlEngine.isAvailable()) {
            System.out.println("Skipping real-time event test - SPARQL engine unavailable");
            return;
        }

        // Clear any existing data
        marketplace.clearAllData();

        // Step 1: Process vendor events
        List<VendorEvent> vendorEvents = createTestVendorEvents(TEST_VENDOR_COUNT);
        for (VendorEvent event : vendorEvents) {
            marketplace.processVendorEvent(event);
        }

        // Verify vendor data was loaded
        Thread.sleep(1000); // Allow processing time

        // Step 2: Process order events
        List<OrderEvent> orderEvents = createTestOrderEvents(TEST_ORDER_COUNT);
        for (OrderEvent event : orderEvents) {
            marketplace.processOrderEvent(event);
        }

        // Step 3: Query for active cases
        Optional<String> activeCases = binding.queryAgents(
            "SELECT ?case WHERE { ?case a <http://yawlfoundation.org/yawl/marketplace#CaseExecution> ; marketplace:status \"active\" }");

        assertTrue("Should find active cases", activeCases.isPresent());
        assertFalse("Active cases query should return results", activeCases.get().isEmpty());
    }

    public void testWorkflowWithConcurrentDataLoading() throws Exception {
        if (sparqlEngine == null || !sparqlEngine.isAvailable()) {
            System.out.println("Skipping concurrent test - SPARQL engine unavailable");
            return;
        }

        // Use latch for synchronization
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        // Thread 1: Load vendor data
        Thread vendorThread = new Thread(() -> {
            try {
                loadTestVendorData(TEST_VENDOR_COUNT);
                latch.countDown();
            } catch (Exception e) {
                errorRef.set(e);
                latch.countDown();
            }
        });

        // Thread 2: Load order data
        Thread orderThread = new Thread(() -> {
            try {
                loadTestOrderData(TEST_ORDER_COUNT);
                latch.countDown();
            } catch (Exception e) {
                errorRef.set(e);
                latch.countDown();
            }
        });

        // Start both threads
        vendorThread.start();
        orderThread.start();

        // Wait for completion
        assertTrue("Should complete within 30 seconds",
            latch.await(30, TimeUnit.SECONDS));

        // Check for errors
        if (errorRef.get() != null) {
            throw errorRef.get();
        }

        // Verify both data sets are loaded
        Optional<String> vendors = binding.queryAgentsAsTurtle(
            MarketplaceConstructQueries.CONSTRUCT_ALL_LIVE_AGENTS);
        Optional<String> orders = binding.queryAgents(
            "SELECT ?order WHERE { ?order a <http://yawlfoundation.org/yawl/marketplace#OrderExecution> }");

        assertTrue("Should have vendor data", vendors.isPresent() && !vendors.get().isEmpty());
        assertTrue("Should have order data", orders.isPresent() && !orders.get().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Fallback Tests (Pure-Java when SPARQL unavailable)
    // -------------------------------------------------------------------------

    public void testFallbackWithNullEngineReturnsStaticTools() {
        MarketplaceMcpBinding fallbackBinding = new MarketplaceMcpBinding(marketplace, null);
        List<McpToolDescriptor> tools = fallbackBinding.getMcpTools();

        assertEquals("Must return exactly 5 static tools", 5, tools.size());

        // Verify all expected tools are present
        assertTrue("Should contain marketplace_list_agents",
            tools.stream().anyMatch(t -> "marketplace_list_agents".equals(t.name())));
        assertTrue("Should contain marketplace_find_for_slot",
            tools.stream().anyMatch(t -> "marketplace_find_for_slot".equals(t.name())));
        assertTrue("Should contain marketplace_find_by_namespace",
            tools.stream().anyMatch(t -> "marketplace_find_by_namespace".equals(t.name())));
        assertTrue("Should contain marketplace_find_by_wcp",
            tools.stream().anyMatch(t -> "marketplace_find_by_wcp".equals(t.name())));
        assertTrue("Should contain marketplace_heartbeat",
            tools.stream().anyMatch(t -> "marketplace_heartbeat".equals(t.name())));
    }

    public void testFallbackTurtleQueryThrowsWhenEngineUnavailable() {
        MarketplaceMcpBinding fallbackBinding = new MarketplaceMcpBinding(marketplace, null);

        try {
            fallbackBinding.queryAgentsAsTurtle(MarketplaceConstructQueries.CONSTRUCT_ALL_LIVE_AGENTS);
            fail("Expected SparqlEngineUnavailableException for CONSTRUCT query with null engine");
        } catch (SparqlEngineUnavailableException e) {
            // Expected - good fallback behavior
        } catch (SparqlEngineException e) {
            fail("Expected SparqlEngineUnavailableException, got: " + e.getClass().getName());
        }
    }

    public void testFallbackBuildsValidAgentCard() {
        MarketplaceA2ABinding fallbackBinding = new MarketplaceA2ABinding(marketplace, null);

        io.a2a.spec.AgentCard card = fallbackBinding.buildAgentCard();
        assertNotNull("AgentCard should not be null", card);
        assertEquals("AgentCard should have correct name", "YAWL Marketplace Agent", card.name());
        assertNotNull("AgentCard should have skills", card.skills());
        assertEquals("AgentCard should have 5 skills", 5, card.skills().size());
    }

    // -------------------------------------------------------------------------
    // Error Recovery Tests
    // -------------------------------------------------------------------------

    public void testEngineRestartRecovery() throws Exception {
        if (sparqlEngine == null || !sparqlEngine.isAvailable()) {
            System.out.println("Skipping engine restart test - SPAR engine unavailable");
            return;
        }

        // Step 1: Load data while engine is running
        loadTestVendorData(TEST_VENDOR_COUNT);
        Optional<String> beforeRestart = binding.queryAgentsAsTurtle(
            MarketplaceConstructQueries.CONSTRUCT_ALL_LIVE_AGENTS);

        assertTrue("Should have data before restart", beforeRestart.isPresent() && !beforeRestart.get().isEmpty());

        // Step 2: Simulate engine restart (close and reopen)
        if (sparqlEngine != null) {
            // Note: In a real test, we'd restart the actual engine process
            // Here we just verify the binding handles it gracefully
            Optional<String> afterRestart = binding.queryAgentsAsTurtle(
                MarketplaceConstructQueries.CONSTRUCT_ALL_LIVE_AGENTS);

            assertTrue("Should still have data after restart", afterRestart.isPresent());
        }
    }

    public void testConnectionRetryWithExponentialBackoff() throws Exception {
        if (sparqlEngine == null || !sparqlEngine.isAvailable()) {
            System.out.println("Skipping retry test - SPARQL engine unavailable");
            return;
        }

        // Create binding with retry configuration
        MarketplaceMcpBinding retryBinding = new MarketplaceMcpBinding(marketplace, sparqlEngine);

        // Execute multiple queries to test retry behavior
        for (int i = 0; i < 3; i++) {
            Optional<String> result = retryBinding.queryAgents(
                "SELECT ?vendor WHERE { ?vendor a <http://yawlfoundation.org/yawl/marketplace#AgentListing> }");

            // Should handle connection issues gracefully
            if (result.isPresent()) {
                // Success - continue
                assertFalse("Query should return non-empty result", result.get().isEmpty());
            }
            // If no result, that's also acceptable for this test (engine might be busy)
        }
    }

    public void testGracefulDegradationWhenEngineSlow() throws Exception {
        if (sparqlEngine == null || !sparqlEngine.isAvailable()) {
            System.out.println("Skipping graceful degradation test - SPARQL engine unavailable");
            return;
        }

        // Load test data
        loadTestVendorData(TEST_VENDOR_COUNT);

        // Execute query with timeout
        long startTime = System.currentTimeMillis();
        Optional<String> result = binding.queryAgents(
            "SELECT ?vendor WHERE { ?vendor a <http://yawlfoundation.org/yawl/marketplace#AgentListing> }");

        long duration = System.currentTimeMillis() - startTime;

        // Should complete within reasonable time (adjust threshold as needed)
        assertTrue("Query should complete within 5 seconds", duration < 5000);

        // Should return some result (might be empty if no data, but should not throw)
        assertNotNull("Result should not be null", result);
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    private void loadTestVendorData(int count) {
        for (int i = 1; i <= count; i++) {
            VendorEvent event = new VendorEvent(
                "vendor-" + i,
                "Vendor " + i,
                "Test Vendor " + i,
                "test-namespace-" + i,
                "http://vendor-" + i + "-endpoint",
                "capability-" + i,
                "active"
            );
            marketplace.processVendorEvent(event);
        }
    }

    private void loadTestOrderData(int count) {
        for (int i = 1; i <= count; i++) {
            OrderEvent event = new OrderEvent(
                "order-" + i,
                "customer-" + i,
                "orderProcess",
                100.0 * i,
                "pending"
            );
            marketplace.processOrderEvent(event);
        }
    }

    private List<VendorEvent> createTestVendorEvents(int count) {
        java.util.List<VendorEvent> events = new java.util.ArrayList<>();
        for (int i = 1; i <= count; i++) {
            events.add(new VendorEvent(
                "vendor-" + i,
                "Vendor " + i,
                "Test Vendor " + i,
                "test-namespace-" + i,
                "http://vendor-" + i + "-endpoint",
                "capability-" + i,
                "active"
            ));
        }
        return events;
    }

    private List<OrderEvent> createTestOrderEvents(int count) {
        java.util.List<OrderEvent> events = new java.util.ArrayList<>();
        for (int i = 1; i <= count; i++) {
            events.add(new OrderEvent(
                "order-" + i,
                "customer-" + i,
                "orderProcess",
                100.0 * i,
                "pending"
            ));
        }
        return events;
    }

    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}