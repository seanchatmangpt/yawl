/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.engine.interfce.rest;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Test implementation of HttpServletResponse for Chicago TDD testing.
 * Captures response state for verification without mocking.
 */
public class TestHttpServletResponse implements HttpServletResponse {

    private final Map<String, String> headers = new HashMap<>();
    private int status = SC_OK;
    private final StringWriter writer = new StringWriter();

    @Override
    public void setHeader(String name, String value) {
        headers.put(name, value);
    }

    @Override
    public void setStatus(int sc) {
        this.status = sc;
    }

    @Override
    public int getStatus() {
        return status;
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    public Map<String, String> getAllHeaders() {
        return new HashMap<>(headers);
    }

    public boolean hasHeader(String name) {
        return headers.containsKey(name);
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        return new PrintWriter(writer);
    }

    public String getOutput() {
        return writer.toString();
    }

    // Unused interface methods throw UnsupportedOperationException
    @Override
    public void addCookie(jakarta.servlet.http.Cookie cookie) {
        throw new UnsupportedOperationException("addCookie not implemented for CORS filter tests");
    }

    @Override
    public boolean containsHeader(String name) {
        throw new UnsupportedOperationException("containsHeader not implemented for CORS filter tests");
    }

    @Override
    public String encodeURL(String url) {
        throw new UnsupportedOperationException("encodeURL not implemented for CORS filter tests");
    }

    @Override
    public String encodeRedirectURL(String url) {
        throw new UnsupportedOperationException("encodeRedirectURL not implemented for CORS filter tests");
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        throw new UnsupportedOperationException("sendError not implemented for CORS filter tests");
    }

    @Override
    public void sendError(int sc) throws IOException {
        throw new UnsupportedOperationException("sendError not implemented for CORS filter tests");
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        throw new UnsupportedOperationException("sendRedirect not implemented for CORS filter tests");
    }

    @Override
    public void setDateHeader(String name, long date) {
        throw new UnsupportedOperationException("setDateHeader not implemented for CORS filter tests");
    }

    @Override
    public void addDateHeader(String name, long date) {
        throw new UnsupportedOperationException("addDateHeader not implemented for CORS filter tests");
    }

    @Override
    public void addHeader(String name, String value) {
        throw new UnsupportedOperationException("addHeader not implemented for CORS filter tests");
    }

    @Override
    public void setIntHeader(String name, int value) {
        throw new UnsupportedOperationException("setIntHeader not implemented for CORS filter tests");
    }

    @Override
    public void addIntHeader(String name, int value) {
        throw new UnsupportedOperationException("addIntHeader not implemented for CORS filter tests");
    }
}
