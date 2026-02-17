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

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.yawlfoundation.yawl.engine.interfce.rest.TestCorsFilterValidation;
import org.yawlfoundation.yawl.mailSender.TestWebMailErrorHandling;
import org.yawlfoundation.yawl.util.TestDOMUtilXxeProtection;

/**
 * SOC2 Compliance Test Suite for YAWL v5.2.
 *
 * <p>Aggregates all security, audit, and access-control tests that map to
 * SOC 2 Trust Service Criteria (TSC):
 * <ul>
 *   <li>CC6 - Logical and Physical Access Controls</li>
 *   <li>CC7 - System Operations</li>
 *   <li>CC8 - Change Management</li>
 *   <li>CC9 - Risk Mitigation</li>
 *   <li>A1  - Availability</li>
 * </ul>
 *
 * <p>CC6.6 tests (Input Validation / External Entity Protection):
 * <ul>
 *   <li>{@link TestDOMUtilXxeProtection} - XXE injection protection on all 4 DOMUtil parsers</li>
 *   <li>{@link TestCorsFilterValidation} - CORS wildcard rejection and origin whitelist enforcement</li>
 * </ul>
 *
 * <p>CC7.2 tests (System Operations / Exception Monitoring):
 * <ul>
 *   <li>{@link TestWebMailErrorHandling} - WebMail/MailSender exception logging and propagation</li>
 * </ul>
 *
 * <p>Chicago TDD: all tests use real implementations, no mocks.
 *
 * @author YAWL Foundation - SOC2 Compliance 2026-02-17
 */
public class Soc2ComplianceTestSuite {

    public static Test suite() {
        TestSuite suite = new TestSuite("SOC2 Compliance Tests");

        // CC6 - Logical Access Controls / Authentication
        suite.addTestSuite(CsrfTokenManagerTest.class);
        suite.addTestSuite(CsrfProtectionFilterTest.class);
        suite.addTestSuite(AuthenticatedPrincipalTest.class);
        suite.addTestSuite(ApiKeyAuthenticationProviderTest.class);
        suite.addTestSuite(JwtAuthenticationProviderTest.class);
        suite.addTestSuite(CredentialManagerFactoryTest.class);
        suite.addTestSuite(CompositeAuthenticationProviderTest.class);
        suite.addTestSuite(SpiffeAuthenticationProviderTest.class);
        suite.addTestSuite(DocumentSignerTest.class);

        // CC6.6 - Input Validation / XXE Protection / CORS Security
        suite.addTestSuite(TestDOMUtilXxeProtection.class);
        suite.addTestSuite(TestCorsFilterValidation.class);

        // CC7 - System Operations / Audit Logging
        suite.addTestSuite(AuditEventTest.class);

        // CC7.2 - Exception Monitoring / Silent Failure Detection
        suite.addTestSuite(TestWebMailErrorHandling.class);

        // Remediation verification (all 4 critical findings)
        suite.addTestSuite(Soc2RemediationTest.class);

        return suite;
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
