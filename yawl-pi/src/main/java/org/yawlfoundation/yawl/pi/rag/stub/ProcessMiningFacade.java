/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 */

package org.yawlfoundation.yawl.pi.rag.stub;

import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningService;
import org.yawlfoundation.yawl.pi.PIException;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Stub implementation of ProcessMiningFacade that throws UnsupportedOperationException
 * for all operations. This follows H-Guards standards - no mocks or fake data.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class ProcessMiningFacade {

    /**
     * Process mining report containing analysis results.
     */
    public static final class ProcessMiningReport {
        public final String xesXml;
        public final ConformanceResult conformance;
        public final PerformanceResult performance;
        public final Map<String, Long> variantFrequencies;
        public final int variantCount;
        public final String ocelJson;
        public final int traceCount;
        public final String specificationId;
        public final Instant analysisTime;

        public ProcessMiningReport(String xesXml, ConformanceResult conformance,
                                  PerformanceResult performance, Map<String, Long> variantFrequencies,
                                  String ocelJson, int traceCount, String specId) {
            this.xesXml = xesXml;
            this.conformance = conformance;
            this.performance = performance;
            this.variantFrequencies = variantFrequencies;
            this.variantCount = variantFrequencies.size();
            this.ocelJson = ocelJson;
            this.traceCount = traceCount;
            this.specificationId = specId;
            this.analysisTime = Instant.now();
        }
    }

    /**
     * Conformance checking result.
     */
    public record ConformanceResult(double fitness, String rawJson) {
        public double computeFitness() { return fitness; }
    }

    /**
     * Performance analysis result.
     */
    public record PerformanceResult(int traceCount, double avgFlowTimeMs,
                                    double throughputPerHour, Map<String, Integer> activityCounts,
                                    String rawJson) {}

    /**
     * Stub constructor - throws UnsupportedOperationException.
     *
     * @throws UnsupportedOperationException Always - GraalPy not available
     */
    public ProcessMiningFacade(String engineUrl, String username, String password) {
        throw new UnsupportedOperationException(
            "ProcessMiningFacade requires GraalPy integration. " +
            "The original implementation was disabled due to dependencies on GraalPy. " +
            "Either restore the original implementation or remove this dependency."
        );
    }

    /**
     * Stub analysis method - throws UnsupportedOperationException.
     *
     * @return Never returns - always throws
     * @throws UnsupportedOperationException Always - GraalPy not available
     */
    public ProcessMiningReport analyze(YSpecificationID specId, YNet net, boolean withData) {
        throw new UnsupportedOperationException(
            "Process mining analysis requires GraalPy integration. " +
            "The original implementation was disabled due to dependencies on GraalPy. " +
            "Either restore the original implementation or remove this dependency."
        );
    }

    /**
     * Stub method for analyzing from event store - throws UnsupportedOperationException.
     *
     * @return Never returns - always throws
     * @throws UnsupportedOperationException Always - GraalPy not available
     */
    public ProcessMiningReport analyzePerformance(YSpecificationID specId, boolean withData) {
        throw new UnsupportedOperationException(
            "Performance analysis requires GraalPy integration. " +
            "The original implementation was disabled due to dependencies on GraalPy. " +
            "Either restore the original implementation or remove this dependency."
        );
    }

    /**
     * Stub close method - throws UnsupportedOperationException.
     *
     * @throws UnsupportedOperationException Always - GraalPy not available
     */
    public void close() throws IOException {
        throw new UnsupportedOperationException(
            "ProcessMiningFacade requires GraalPy integration. " +
            "The original implementation was disabled due to dependencies on GraalPy. " +
            "Either restore the original implementation or remove this dependency."
        );
    }
}