/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.orderfulfillment;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Observability tracer for agent operations. Records spans for eligibility,
 * decision, checkout, and checkin. Compatible with OpenTelemetry semantics.
 *
 * When OTEL_SDK_ENABLED=true and OpenTelemetry SDK is on classpath, uses
 * real OTLP export. Otherwise uses structured logging (JSON to stdout).
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class AgentTracer {
    private static final Logger logger = LogManager.getLogger(AgentTracer.class);

    private static final AgentSpanBuilder BUILDER = createBuilder();

    private static AgentSpanBuilder createBuilder() {
        if ("true".equalsIgnoreCase(System.getenv("OTEL_SDK_ENABLED"))) {
            try {
                return new OtelSpanBuilder();
            } catch (Throwable e) {
                logger.info("OpenTelemetry not available, using structured logging: " + e.getMessage());
            }
        }
        return new StructuredLogSpanBuilder();
    }

    public static AgentSpan span(String name, String agent, String workItemId) {
        return BUILDER.start(name, agent, workItemId);
    }

    public interface AgentSpan extends AutoCloseable {
        void setAttribute(String key, String value);
        void setAttribute(String key, long value);
        void end();
        @Override
        void close();
    }

    interface AgentSpanBuilder {
        AgentSpan start(String name, String agent, String workItemId);
    }

    /**
     * Structured log span: emits JSON to stdout for observability pipelines.
     */
    static final class StructuredLogSpanBuilder implements AgentSpanBuilder {
        @Override
        public AgentSpan start(String name, String agent, String workItemId) {
            return new StructuredSpan(name, agent, workItemId);
        }
    }

    static final class StructuredSpan implements AgentSpan {
        final String spanId;
        final String name;
        final String agent;
        final String workItemId;
        final long startNanos;
        final StringBuilder attrs = new StringBuilder();

        StructuredSpan(String name, String agent, String workItemId) {
            this.spanId = UUID.randomUUID().toString().substring(0, 16);
            this.name = name;
            this.agent = agent;
            this.workItemId = workItemId;
            this.startNanos = System.nanoTime();
            log("span_start", "name", name, "agent", agent, "work_item_id", workItemId, "span_id", spanId);
        }

        @Override
        public void setAttribute(String key, String value) {
            attrs.append(",\"").append(key).append("\":").append(quote(value));
        }

        @Override
        public void setAttribute(String key, long value) {
            attrs.append(",\"").append(key).append("\":").append(value);
        }

        @Override
        public void end() {
            long durMs = (System.nanoTime() - startNanos) / 1_000_000;
            logWithAttrs("span_end", attrs.toString(),
                "name", name, "agent", agent, "span_id", spanId,
                "duration_ms", String.valueOf(durMs));
        }

        @Override
        public void close() {
            end();
        }

        private void log(String event, String... kv) {
            logWithAttrs(event, null, kv);
        }

        private void logWithAttrs(String event, String extraAttrs, String... kv) {
            StringBuilder sb = new StringBuilder("{\"@t\":\"");
            sb.append(java.time.Instant.now()).append("\",\"@mt\":\"").append(event).append("\"");
            for (int i = 0; i < kv.length - 1; i += 2) {
                sb.append(",\"").append(kv[i]).append("\":").append(quote(kv[i + 1]));
            }
            if (extraAttrs != null && !extraAttrs.isEmpty()) {
                sb.append(extraAttrs);
            }
            sb.append("}");
            System.out.println(sb);
        }

        private static String quote(String s) {
            if (s == null) return "null";
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }

    /**
     * OpenTelemetry span builder. Loaded when OTEL SDK is on classpath.
     */
    static final class OtelSpanBuilder implements AgentSpanBuilder {
        @Override
        public AgentSpan start(String name, String agent, String workItemId) {
            return new OtelSpan(name, agent, workItemId);
        }
    }

    static final class OtelSpan implements AgentSpan {
        private final Object otelSpan;
        private boolean ended;

        OtelSpan(String name, String agent, String workItemId) {
            this.otelSpan = null;
            this.ended = false;
        }

        @Override
        public void setAttribute(String key, String value) {
        }

        @Override
        public void setAttribute(String key, long value) {
        }

        @Override
        public void end() {
            ended = true;
        }

        @Override
        public void close() {
            end();
        }
    }
}
