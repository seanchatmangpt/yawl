/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.engine.interfce.rest;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Test implementation of FilterConfig for Chicago TDD testing.
 * Provides real configuration behavior without mocking.
 */
public class TestFilterConfig implements FilterConfig {

    private final String filterName;
    private final Map<String, String> initParameters;

    public TestFilterConfig(String filterName) {
        this.filterName = filterName;
        this.initParameters = new HashMap<>();
    }

    public TestFilterConfig(String filterName, Map<String, String> initParameters) {
        this.filterName = filterName;
        this.initParameters = new HashMap<>(initParameters);
    }

    public void setInitParameter(String name, String value) {
        initParameters.put(name, value);
    }

    @Override
    public String getFilterName() {
        return filterName;
    }

    @Override
    public ServletContext getServletContext() {
        throw new UnsupportedOperationException("getServletContext not implemented for CORS filter tests");
    }

    @Override
    public String getInitParameter(String name) {
        return initParameters.get(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(initParameters.keySet());
    }
}
