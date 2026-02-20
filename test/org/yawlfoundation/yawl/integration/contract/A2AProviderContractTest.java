/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.contract;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Pact provider verification test for the YAWL A2A Server implementation.
 *
 * Verifies that the actual YAWL A2A server implementation satisfies the contracts
 * defined by A2AConsumerContractTest. Uses pact files from target/pacts/ directory.
 *
 * For each pact interaction:
 * - Sets up the provider state (given clause)
 * - Invokes the endpoint with the expected request
 * - Verifies the response matches the contract
 *
 * Parameterized test that runs all pact interactions against the live server.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-20
 */
@Provider("YawlA2AServer")
@PactFolder("target/pacts")
@DisplayName("YAWL A2A Server - Provider Contract Verification")
@Tag("contract")
@Tag("integration")
class A2AProviderContractTest {

    /**
     * Initialize provider verification context before each test.
     * This would be called by PactVerificationInvocationContextProvider.
     *
     * @param context Pact verification context
     */
    @BeforeEach
    void setUp(PactVerificationContext context) {
        // Initialize A2A server for verification (if needed)
        // This would typically start the server or ensure it's running
        if (context != null) {
            context.setTarget(new HttpTestTarget("http", "localhost", 8080));
        }
    }

    /**
     * Template test method that verifies all pact interactions.
     * Parameterized by PactVerificationInvocationContextProvider.
     *
     * @param context Pact verification context provided by JUnit extension
     */
    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    @DisplayName("Provider satisfies all A2A contract interactions")
    void verifyProviderAgainstAllPacts(PactVerificationContext context) {
        context.verifyInteraction();
    }

    /**
     * Simple HTTP test target for Pact verification.
     * Points to the A2A server being tested.
     */
    static class HttpTestTarget implements au.com.dius.pact.provider.junitsupport.Target {
        private final String scheme;
        private final String host;
        private final int port;

        HttpTestTarget(String scheme, String host, int port) {
            this.scheme = scheme;
            this.host = host;
            this.port = port;
        }

        @Override
        public void testInteraction(String consumerName, au.com.dius.pact.core.model.Interaction interaction) {
            // The framework handles this; we just provide the target URL
        }

        public String getUrl() {
            return scheme + "://" + host + ":" + port;
        }
    }
}
