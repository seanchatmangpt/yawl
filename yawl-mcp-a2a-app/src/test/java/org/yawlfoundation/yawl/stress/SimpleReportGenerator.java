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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Simple JSON report generator for stress tests.
 *
 * @author YAWL Stress Testing Framework
 * @version 1.0
 */
public class SimpleReportGenerator {

    /**
     * Generates a simple JSON report from test results.
     *
     * @param status the overall test status
     * @param testClasses list of test classes
     * @param duration test duration
     * @param criticalPoints list of critical breaking points
     * @param warningPoints list of warning breaking points
     * @return JSON string
     */
    public static String generateJSONReport(String status, List<String> testClasses,
                                           Duration duration, List<String> criticalPoints,
                                           List<String> warningPoints) {
        StringBuilder json = new StringBuilder();

        json.append("{\n");
        json.append("  \"metadata\": {\n");
        json.append("    \"version\": \"1.0\",\n");
        json.append("    \"generatedAt\": \"" + Instant.now() + "\"\n");
        json.append("  },\n");
        json.append("  \"overallStatus\": \"" + status + "\",\n");
        json.append("  \"testClasses\": [\n");
        for (int i = 0; i < testClasses.size(); i++) {
            json.append("    \"").append(testClasses.get(i)).append("\"");
            if (i < testClasses.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ],\n");
        json.append("  \"duration\": \"" + duration + "\",\n");
        json.append("  \"criticalBreakingPoints\": [\n");
        for (int i = 0; i < criticalPoints.size(); i++) {
            json.append("    \"").append(criticalPoints.get(i)).append("\"");
            if (i < criticalPoints.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ],\n");
        json.append("  \"warningBreakingPoints\": [\n");
        for (int i = 0; i < warningPoints.size(); i++) {
            json.append("    \"").append(warningPoints.get(i)).append("\"");
            if (i < warningPoints.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}");

        return json.toString();
    }
}