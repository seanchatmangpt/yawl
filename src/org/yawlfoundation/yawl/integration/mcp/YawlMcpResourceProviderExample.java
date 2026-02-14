/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.mcp;

import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

import java.io.IOException;
import java.util.List;

/**
 * Example usage of YawlMcpResourceProvider.
 *
 * This demonstrates how to use the MCP Resource Provider to access YAWL workflow data.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlMcpResourceProviderExample {

    public static void main(String[] args) {
        System.out.println("YAWL MCP Resource Provider Example");
        System.out.println("===================================\n");

        try {
            exampleWithEnvironmentConfig();
            exampleWithExplicitConfig();
            exampleResourceUriAccess();
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Example 1: Using environment-based configuration.
     * Set environment variables:
     * - YAWL_ENGINE_URL=http://localhost:8080/yawl/ib
     * - YAWL_USERNAME=admin
     * - YAWL_PASSWORD=YAWL
     */
    private static void exampleWithEnvironmentConfig() throws IOException {
        System.out.println("Example 1: Environment-based Configuration");
        System.out.println("------------------------------------------");

        YawlMcpResourceProvider provider = new YawlMcpResourceProvider();

        List<SpecificationData> specs = provider.getLoadedSpecifications();
        System.out.println("Loaded specifications: " + specs.size());

        for (SpecificationData spec : specs) {
            System.out.println("  - " + spec.getName() + " (v" + spec.getSchemaVersion() + ")");
        }

        provider.disconnect();
        System.out.println();
    }

    /**
     * Example 2: Using explicit configuration.
     */
    private static void exampleWithExplicitConfig() throws IOException {
        System.out.println("Example 2: Explicit Configuration");
        System.out.println("----------------------------------");

        YawlMcpResourceProvider provider = new YawlMcpResourceProvider(
            "http://localhost:8080/yawl/ib",
            "admin",
            "YAWL"
        );

        String runningCases = provider.getAllRunningCases();
        System.out.println("Running cases XML:");
        System.out.println(runningCases);

        provider.disconnect();
        System.out.println();
    }

    /**
     * Example 3: Using resource URI access pattern.
     */
    private static void exampleResourceUriAccess() throws IOException {
        System.out.println("Example 3: Resource URI Access");
        System.out.println("------------------------------");

        YawlMcpResourceProvider provider = new YawlMcpResourceProvider();

        String[] resourceUris = {
            "specifications://loaded",
            "cases://running"
        };

        for (String uri : resourceUris) {
            System.out.println("\nFetching resource: " + uri);
            try {
                String resource = provider.getResource(uri);
                System.out.println("Resource length: " + resource.length() + " characters");
            } catch (Exception e) {
                System.err.println("  Error: " + e.getMessage());
            }
        }

        List<WorkItemRecord> liveItems = provider.getAllLiveWorkItems();
        System.out.println("\nLive work items: " + liveItems.size());

        for (WorkItemRecord item : liveItems) {
            System.out.println("  - " + item.getTaskName() +
                             " [" + item.getStatus() + "]" +
                             " (Case: " + item.getCaseID() + ")");
        }

        provider.disconnect();
        System.out.println();
    }

    /**
     * Example 4: Accessing specific resources.
     */
    public static void exampleSpecificResources() throws IOException {
        YawlMcpResourceProvider provider = new YawlMcpResourceProvider();

        String specId = "OrderFulfillment";
        String specXML = provider.getSpecification(specId);
        System.out.println("Specification XML for " + specId + ":");
        System.out.println(specXML);

        String caseId = "1.1";
        String caseData = provider.getCaseData(caseId);
        System.out.println("\nCase data for " + caseId + ":");
        System.out.println(caseData);

        String caseState = provider.getCaseState(caseId);
        System.out.println("\nCase state for " + caseId + ":");
        System.out.println(caseState);

        String taskInfo = provider.getTaskInformation(specId, "ProcessOrder");
        System.out.println("\nTask information for ProcessOrder:");
        System.out.println(taskInfo);

        provider.disconnect();
    }

    /**
     * Example 5: Error handling.
     */
    public static void exampleErrorHandling() {
        try {
            YawlMcpResourceProvider provider = new YawlMcpResourceProvider(
                "http://invalid-host:8080/yawl/ib",
                "admin",
                "YAWL"
            );

            provider.getLoadedSpecifications();

        } catch (IOException e) {
            System.err.println("Connection failed as expected: " + e.getMessage());
        }

        try {
            YawlMcpResourceProvider provider = new YawlMcpResourceProvider();
            String invalid = provider.getResource("invalid://uri");
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid URI rejected as expected: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Unexpected IO error: " + e.getMessage());
        }
    }
}
