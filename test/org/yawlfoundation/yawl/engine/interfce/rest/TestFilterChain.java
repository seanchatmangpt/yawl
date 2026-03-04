/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.engine.interfce.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test implementation of FilterChain for Chicago TDD testing.
 * Tracks filter chain invocations without mocking.
 */
public class TestFilterChain implements FilterChain {

    private final AtomicBoolean chainInvoked = new AtomicBoolean(false);
    private final AtomicInteger invocationCount = new AtomicInteger(0);
    private ServletRequest lastRequest;
    private ServletResponse lastResponse;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response) {
        chainInvoked.set(true);
        invocationCount.incrementAndGet();
        this.lastRequest = request;
        this.lastResponse = response;
    }

    /**
     * Check if the filter chain was invoked.
     */
    public boolean wasInvoked() {
        return chainInvoked.get();
    }

    /**
     * Get the number of times the filter chain was invoked.
     */
    public int getInvocationCount() {
        return invocationCount.get();
    }

    /**
     * Get the last request passed to the filter chain.
     */
    public ServletRequest getLastRequest() {
        return lastRequest;
    }

    /**
     * Get the last response passed to the filter chain.
     */
    public ServletResponse getLastResponse() {
        return lastResponse;
    }

    /**
     * Reset the chain state for reuse in tests.
     */
    public void reset() {
        chainInvoked.set(false);
        invocationCount.set(0);
        lastRequest = null;
        lastResponse = null;
    }
}
