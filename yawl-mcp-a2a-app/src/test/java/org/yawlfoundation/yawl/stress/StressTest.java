/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stress;

/**
 * Interface for stress tests.
 *
 * @author YAWL Stress Testing Framework
 * @version 1.0
 */
public interface StressTest {

    /**
     * Gets the name of the stress test.
     *
     * @return the test name
     */
    String getTestName();

    /**
     * Prepares the test environment.
     *
     * @throws StressTestException if preparation fails
     */
    void prepare() throws StressTestException;

    /**
     * Runs the stress test.
     *
     * @return the test result
     * @throws StressTestException if the test fails
     */
    StressTestResult run() throws StressTestException;

    /**
     * Cleans up after the test.
     *
     * @throws StressTestException if cleanup fails
     */
    void cleanup() throws StressTestException;
}