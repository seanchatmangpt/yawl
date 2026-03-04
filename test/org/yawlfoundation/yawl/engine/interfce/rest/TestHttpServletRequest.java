/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.engine.interfce.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Test implementation of HttpServletRequest for Chicago TDD testing.
 * Provides real servlet request behavior without mocking.
 */
public class TestHttpServletRequest implements HttpServletRequest {

    private final Map<String, String> headers = new HashMap<>();
    private String method = "GET";
    private String requestURI = "/api/v1/cases";

    public void setHeader(String name, String value) {
        headers.put(name, value);
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setRequestURI(String uri) {
        this.requestURI = uri;
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name);
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getRequestURI() {
        return requestURI;
    }

    private final Map<String, Object> attributes = new HashMap<>();
    private String contextPath = "";
    private String servletPath = "";
    private String queryString;

    public void setContextPath(String path) {
        this.contextPath = path;
    }

    public void setServletPath(String path) {
        this.servletPath = path;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    @Override
    public String getContextPath() {
        return contextPath;
    }

    @Override
    public String getServletPath() {
        return servletPath;
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    // Remaining interface methods - throw UnsupportedOperationException for unused methods
    @Override
    public String getAuthType() {
        throw new UnsupportedOperationException("getAuthType not implemented for CORS filter tests");
    }

    @Override
    public jakarta.servlet.http.Cookie[] getCookies() {
        throw new UnsupportedOperationException("getCookies not implemented for CORS filter tests");
    }

    @Override
    public long getDateHeader(String name) {
        throw new UnsupportedOperationException("getDateHeader not implemented for CORS filter tests");
    }

    @Override
    public int getIntHeader(String name) {
        throw new UnsupportedOperationException("getIntHeader not implemented for CORS filter tests");
    }

    @Override
    public java.util.Enumeration<String> getHeaders(String name) {
        throw new UnsupportedOperationException("getHeaders not implemented for CORS filter tests");
    }

    @Override
    public java.util.Enumeration<String> getHeaderNames() {
        throw new UnsupportedOperationException("getHeaderNames not implemented for CORS filter tests");
    }
}
