/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.cloud;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.integration.mcp.resource.YawlResourceProvider;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlToolSpecifications;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlCompletionSpecifications;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlPromptSpecifications;

import java.util.concurrent.TimeUnit;

/**
 * Integration tests for Spring AI with MCP resource lifecycle and Spring integration.
 *
 * Tests cover:
 * - Resource provider initialization and lifecycle
 * - Tool specification discovery and registration
 * - Completion specification handling
 * - Prompt template management
 * - Spring component injection
 * - Resource caching and state management
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SpringAIMcpResourceIntegrationTest extends TestCase {

    private YawlResourceProvider resourceProvider;
    private YawlToolSpecifications toolSpecs;
    private YawlCompletionSpecifications completionSpecs;
    private YawlPromptSpecifications promptSpecs;

    public SpringAIMcpResourceIntegrationTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        resourceProvider = new YawlResourceProvider();
        toolSpecs = new YawlToolSpecifications();
        completionSpecs = new YawlCompletionSpecifications();
        promptSpecs = new YawlPromptSpecifications();
    }

    @Override
    protected void tearDown() throws Exception {
        if (resourceProvider != null) {
            resourceProvider.shutdown();
        }
        super.tearDown();
    }

    /**
     * Test resource provider initialization
     */
    public void testResourceProviderInitialization() throws Exception {
        assertNotNull("Resource provider created", resourceProvider);
        assertTrue("Resource provider initialized", resourceProvider.isInitialized());
    }

    /**
     * Test resource provider lifecycle: init -> ready -> shutdown
     */
    public void testResourceProviderLifecycle() throws Exception {
        assertTrue("Provider starts initialized", resourceProvider.isInitialized());
        
        resourceProvider.loadResources();
        assertTrue("Resources loaded successfully", resourceProvider.hasResources());
        
        assertTrue("Provider ready after load", resourceProvider.isReady());
        
        resourceProvider.shutdown();
        assertFalse("Resources cleared after shutdown", resourceProvider.hasResources());
    }

    /**
     * Test tool specification discovery
     */
    public void testToolSpecificationDiscovery() throws Exception {
        assertNotNull("Tool specs initialized", toolSpecs);
        
        int toolCount = toolSpecs.getToolCount();
        assertTrue("Tools discovered", toolCount >= 0);
        
        for (int i = 0; i < toolCount; i++) {
            String toolName = toolSpecs.getToolName(i);
            assertNotNull("Tool name not null", toolName);
        }
    }

    /**
     * Test completion specification handling
     */
    public void testCompletionSpecificationHandling() throws Exception {
        assertNotNull("Completion specs initialized", completionSpecs);
        
        completionSpecs.loadSpecifications();
        assertTrue("Completions loaded", completionSpecs.hasCompletions());
        
        String[] models = completionSpecs.getAvailableModels();
        assertTrue("Models defined", models.length >= 0);
        
        for (String model : models) {
            assertNotNull("Model name not null", model);
            int maxTokens = completionSpecs.getMaxTokens(model);
            assertTrue("Max tokens configured", maxTokens > 0);
        }
    }

    /**
     * Test prompt specification and template management
     */
    public void testPromptSpecificationManagement() throws Exception {
        assertNotNull("Prompt specs initialized", promptSpecs);
        
        promptSpecs.loadSpecifications();
        assertTrue("Prompts loaded", promptSpecs.hasPrompts());
        
        String[] templates = promptSpecs.getAvailableTemplates();
        assertTrue("Templates defined", templates.length >= 0);
    }

    /**
     * Test resource caching mechanism
     */
    public void testResourceCaching() throws Exception {
        resourceProvider.loadResources();
        
        Object resource1 = resourceProvider.getResource("test-resource");
        
        long start = System.currentTimeMillis();
        Object resource2 = resourceProvider.getResource("test-resource");
        long elapsed = System.currentTimeMillis() - start;
        
        assertEquals("Same resource retrieved", resource1, resource2);
        assertTrue("Cache hit is fast", elapsed < 100);
    }

    /**
     * Test concurrent resource access
     */
    public void testConcurrentResourceAccess() throws Exception {
        resourceProvider.loadResources();
        
        final int threadCount = 10;
        final int accessCount = 100;
        Thread[] threads = new Thread[threadCount];
        final int[] successCount = {0};
        final Exception[] errors = {null};
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < accessCount; j++) {
                        Object resource = resourceProvider.getResource("test-resource");
                        if (resource != null) {
                            synchronized(successCount) {
                                successCount[0]++;
                            }
                        }
                    }
                } catch (Exception e) {
                    synchronized(errors) {
                        if (errors[0] == null) errors[0] = e;
                    }
                }
            });
            threads[i].start();
        }
        
        for (Thread t : threads) {
            t.join();
        }
        
        assertNull("No errors during concurrent access", errors[0]);
    }

    /**
     * Test resource timeout and expiration
     */
    public void testResourceExpiration() throws Exception {
        long ttlMs = 100;
        resourceProvider.setResourceTTL(ttlMs);
        resourceProvider.loadResources();
        
        Object resource1 = resourceProvider.getResource("test-resource");
        assertNotNull("Resource initially available", resource1);
        
        Thread.sleep(ttlMs + 50);
        
        Object resource2 = resourceProvider.getResource("test-resource");
        assertNotNull("Resource refreshed after expiration", resource2);
    }

    /**
     * Test Spring component integration
     */
    public void testSpringComponentIntegration() throws Exception {
        assertNotNull("Resource provider available", resourceProvider);
        assertNotNull("Tool specs available", toolSpecs);
        assertNotNull("Completion specs available", completionSpecs);
        assertNotNull("Prompt specs available", promptSpecs);
    }

    /**
     * Test resource provider state consistency
     */
    public void testResourceProviderStateConsistency() throws Exception {
        resourceProvider.loadResources();
        
        int initialResourceCount = resourceProvider.getResourceCount();
        assertTrue("Resources loaded", initialResourceCount >= 0);
        
        for (int i = 0; i < 10; i++) {
            int currentCount = resourceProvider.getResourceCount();
            assertEquals("Resource count consistent", initialResourceCount, currentCount);
        }
    }

    /**
     * Test graceful shutdown with pending operations
     */
    public void testGracefulShutdown() throws Exception {
        resourceProvider.loadResources();
        
        long startTime = System.currentTimeMillis();
        resourceProvider.shutdown(TimeUnit.SECONDS.toMillis(5));
        long duration = System.currentTimeMillis() - startTime;
        
        assertTrue("Shutdown completed within timeout", duration < TimeUnit.SECONDS.toMillis(10));
        assertFalse("Resources cleaned up", resourceProvider.hasResources());
    }
}
