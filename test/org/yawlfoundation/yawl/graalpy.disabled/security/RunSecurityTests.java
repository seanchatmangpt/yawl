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

package org.yawlfoundation.yawl.graalpy.security;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;

/**
 * Test runner for OWASP security tests.
 *
 * <p>This class provides a simple way to run all security tests and
 * generate a comprehensive security report.</p>
 *
 * @author YAWL Foundation - Security Team 2026-02-25
 */
public class RunSecurityTests {

    public static void main(String[] args) {
        // Create a test summary listener
        SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
        
        // Create a discovery request for all security tests
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(OwaspVulnerabilityTest.class))
            .selectors(DiscoverySelectors.selectClass(OwaspSecurityTestSuite.class))
            .build();
        
        // Create the launcher and run the tests
        Launcher launcher = LauncherFactory.create(LauncherConfig.builder()
            .enableTestExecutionListenerFactory(summaryListener)
            .build());
        
        System.out.println("=== OWASP Security Test Runner ===");
        System.out.println("Running security tests...");
        
        launcher.execute(request);
        
        // Generate and print the test summary
        TestExecutionSummary summary = summaryListener.getSummary();
        PrintWriter writer = new PrintWriter(System.out);
        
        System.out.println("\n=== Test Summary ===");
        summary.printTo(writer);
        
        // Generate security compliance report
        System.out.println("\n=== Security Compliance Report ===");
        SecurityAuditHelper.generateSecurityReport(summary);
        
        // Exit with appropriate code
        if (summary.getFailureCount() > 0) {
            System.out.println("\n❌ SECURITY TESTS FAILED - Vulnerabilities detected!");
            System.exit(1);
        } else {
            System.out.println("\n✅ All security tests passed!");
            System.exit(0);
        }
    }
}
