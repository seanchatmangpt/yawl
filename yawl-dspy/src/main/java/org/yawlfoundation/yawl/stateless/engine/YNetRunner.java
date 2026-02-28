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

package org.yawlfoundation.yawl.stateless.engine;

import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.engine.YData;

import java.util.Map;

/**
 * Stub implementation of YNetRunner for compilation purposes.
 *
 * This is a placeholder implementation that provides the minimum interface
 * required for compilation. In a complete implementation, this class would
 * be the stateless YAWL engine for running workflow instances.
 */
public final class YNetRunner {

    /**
     * Starts the YNet execution.
     *
     * @throws UnsupportedOperationException since this is a compilation stub
     */
    public void start() {
        throw new UnsupportedOperationException(
            "YNetRunner.start() requires real implementation. " +
            "This is a compilation stub that needs to be implemented with proper YAWL execution engine."
        );
    }

    /**
     * Starts the YNet execution with the specified workflow data.
     *
     * @param yNet the YNet workflow to execute
     * @param data the workflow data to use during execution
     * @throws UnsupportedOperationException since this is a compilation stub
     */
    public void start(YNet yNet, Map<String, YData> data) {
        throw new UnsupportedOperationException(
            "YNetRunner.start(YNet, Map) requires real implementation. " +
            "This is a compilation stub that needs to be implemented with proper YAWL execution engine."
        );
    }

    /**
     * Stops the current execution.
     *
     * @throws UnsupportedOperationException since this is a compilation stub
     */
    public void stop() {
        throw new UnsupportedOperationException(
            "YNetRunner.stop() requires real implementation. " +
            "This is a compilation stub that needs to be implemented with proper YAWL execution engine."
        );
    }

    /**
     * Gets the current execution status.
     *
     * @return the execution status (throws since this is a stub)
     * @throws UnsupportedOperationException since this is a compilation stub
     */
    public String getStatus() {
        throw new UnsupportedOperationException(
            "YNetRunner.getStatus() requires real implementation. " +
            "This is a compilation stub that needs to be implemented with proper YAWL execution engine."
        );
    }
}