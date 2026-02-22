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

package org.yawlfoundation.yawl.integration.a2a;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * A2A test suite aggregating all Agent-to-Agent protocol tests.
 *
 * Includes:
 * - YawlA2AServerTest: constructor validation, HTTP lifecycle, agent card endpoint
 * - A2AAuthenticationTest: AuthenticatedPrincipal, ApiKeyProvider, JwtProvider,
 *   CompositeProvider - all using real HttpServer captures (no mocks)
 * - A2AProtocolTest: full HTTP transport layer tests with real server instances
 * - A2AClientTest: client construction, pre-connection guards, close idempotency
 * - A2AComplianceTest: A2A protocol compliance verification
 * - A2AIntegrationTest: end-to-end workflow lifecycle tests
 * - AutonomousAgentScenarioTest: autonomous agent scenario tests
 * - VirtualThreadConcurrencyTest: virtual thread performance and concurrency tests
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class A2ATestSuite {

    private A2ATestSuite() {
        throw new UnsupportedOperationException("A2ATestSuite is a suite runner, not instantiable");
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("YAWL A2A Protocol Tests");
        suite.addTestSuite(YawlA2AServerTest.class);
        suite.addTestSuite(A2AAuthenticationTest.class);
        suite.addTestSuite(A2AProtocolTest.class);
        suite.addTestSuite(A2AClientTest.class);
        suite.addTestSuite(A2AComplianceTest.class);
        suite.addTestSuite(A2AIntegrationTest.class);
        suite.addTestSuite(AutonomousAgentScenarioTest.class);
        suite.addTestSuite(VirtualThreadConcurrencyTest.class);
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
}
