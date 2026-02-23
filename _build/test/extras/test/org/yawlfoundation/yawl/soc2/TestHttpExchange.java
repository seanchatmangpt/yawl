/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.soc2;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

/**
 * Minimal concrete HttpExchange implementation for use in SOC2 tests.
 *
 * <p>HttpExchange is an abstract class (not an interface), so tests that exercise
 * authentication providers must use this concrete subclass rather than a Proxy.
 * Only getRequestHeaders() is implemented with real data; all other methods
 * delegate to the minimum required for compilation.
 *
 * <p>Usage:
 * <pre>
 *   TestHttpExchange exchange = new TestHttpExchange();
 *   exchange.requestHeaders().add("X-API-Key", "my-key");
 *   provider.authenticate(exchange);
 * </pre>
 *
 * @author YAWL Foundation - SOC2 Compliance 2026-02-17
 */
public class TestHttpExchange extends HttpExchange {

    private final Headers requestHeaders = new Headers();
    private final Headers responseHeaders = new Headers();
    private int responseCode = 200;

    /**
     * Returns the mutable request headers map.
     * Callers can add headers before passing to the provider under test.
     */
    public Headers requestHeaders() {
        return requestHeaders;
    }

    // =========================================================================
    // HttpExchange abstract methods - minimal implementations for testing
    // =========================================================================

    @Override
    public Headers getRequestHeaders() {
        return requestHeaders;
    }

    @Override
    public Headers getResponseHeaders() {
        return responseHeaders;
    }

    @Override
    public URI getRequestURI() {
        try {
            return new URI("/test");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getRequestMethod() {
        return "POST";
    }

    @Override
    public HttpContext getHttpContext() {
        return null;
    }

    @Override
    public void close() {
        // no-op for tests
    }

    @Override
    public InputStream getRequestBody() {
        return InputStream.nullInputStream();
    }

    @Override
    public OutputStream getResponseBody() {
        return OutputStream.nullOutputStream();
    }

    @Override
    public void sendResponseHeaders(int rCode, long responseLength) throws IOException {
        this.responseCode = rCode;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return new InetSocketAddress("127.0.0.1", 12345);
    }

    @Override
    public int getResponseCode() {
        return responseCode;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return new InetSocketAddress("127.0.0.1", 19876);
    }

    @Override
    public String getProtocol() {
        return "HTTP/1.1";
    }

    @Override
    public Object getAttribute(String name) {
        return null;
    }

    @Override
    public void setAttribute(String name, Object value) {
        // no-op for tests
    }

    @Override
    public void setStreams(InputStream i, OutputStream o) {
        // no-op for tests
    }

    @Override
    public HttpPrincipal getPrincipal() {
        return null;
    }
}
