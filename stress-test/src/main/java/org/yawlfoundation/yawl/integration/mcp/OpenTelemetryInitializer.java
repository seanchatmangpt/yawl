/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.mcp;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.api.common.Attributes;

import java.util.Collections;

/**
 * Helper class for initializing OpenTelemetry.
 */
public class OpenTelemetryInitializer {

    private static OpenTelemetrySdk openTelemetry;

    /**
     * Initializes OpenTelemetry with default configuration.
     */
    public static synchronized void initialize() {
        if (openTelemetry != null) {
            return;
        }

        // Create resource
        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(
                "service.name", "yawl-slack-mcp-server",
                "service.version", "6.0.0"
            )));

        // Configure tracer provider
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(LoggingSpanExporter.create()).build())
            .setResource(resource)
            .build();

        // Configure meter provider
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
            .setResource(resource)
            .build();

        // Build OpenTelemetry SDK
        openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .build();
    }

    /**
     * Gets the initialized OpenTelemetry instance.
     */
    public static synchronized OpenTelemetry getSdk() {
        if (openTelemetry == null) {
            initialize();
        }
        return openTelemetry;
    }

    /**
     * Gets the tracer provider.
     */
    public static TracerProvider getTracerProvider() {
        return getSdk().getTracerProvider();
    }

    /**
     * Gets the meter provider.
     */
    public static MeterProvider getMeterProvider() {
        return getSdk().getMeterProvider();
    }
}