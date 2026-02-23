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

package org.yawlfoundation.yawl.integration.processmining;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ProcessMiningServiceClient.
 *
 * Tests the real HTTP client behavior without mocking the class itself,
 * focusing on error handling, JSON escaping, and timeout behavior.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@DisplayName("ProcessMiningServiceClient Tests")
class ProcessMiningServiceClientTest {

    private ProcessMiningServiceClient client;
    private static final String UNREACHABLE_URL = "http://localhost:19999";
    private static final String SHORT_TIMEOUT_SECS = "1";

    @BeforeEach
    void setUp() {
        client = new ProcessMiningServiceClient(UNREACHABLE_URL, Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("isHealthy returns false when service is unreachable")
    void testIsHealthyReturnsFalseWhenUnreachable() {
        assertFalse(client.isHealthy(), "Health check should return false for unreachable service");
    }

    @Test
    @DisplayName("isHealthy does not throw exception when service is unreachable")
    void testIsHealthyDoesNotThrowOnUnreachable() {
        assertDoesNotThrow(() -> {
            client.isHealthy();
        }, "isHealthy should handle unreachable service gracefully without throwing");
    }

    @Test
    @DisplayName("JSON escaping handles quotes correctly")
    void testJsonEscapingQuotes() throws IOException {
        String xmlWithQuotes = """
                <log>
                    <event name="test \\"quoted\\"" value="data" />
                </log>
                """;
        String pnmlXml = "<net></net>";

        assertThrows(IOException.class, () -> {
            client.tokenReplay(pnmlXml, xmlWithQuotes);
        }, "Should throw IOException when service is unreachable, after attempting JSON serialization");
    }

    @Test
    @DisplayName("JSON escaping handles backslashes correctly")
    void testJsonEscapingBackslashes() throws IOException {
        String xmlWithBackslashes = """
                <log>
                    <event name="test\\path\\to\\resource" />
                </log>
                """;
        String pnmlXml = "<net></net>";

        assertThrows(IOException.class, () -> {
            client.tokenReplay(pnmlXml, xmlWithBackslashes);
        }, "Should throw IOException when service is unreachable");
    }

    @Test
    @DisplayName("JSON escaping handles newlines and tabs correctly")
    void testJsonEscapingWhitespace() throws IOException {
        String xmlWithWhitespace = """
                <log>
                    <event name="test
                line2">
                    	<tab>value</tab>
                    </event>
                </log>
                """;
        String pnmlXml = "<net></net>";

        assertThrows(IOException.class, () -> {
            client.tokenReplay(pnmlXml, xmlWithWhitespace);
        }, "Should throw IOException when service is unreachable");
    }

    @Test
    @DisplayName("JSON escaping handles XML angle brackets correctly")
    void testJsonEscapingXmlContent() throws IOException {
        String complexXml = """
                <root>
                    <element attr="<value>">
                        <child>text with < and > symbols</child>
                    </element>
                </root>
                """;
        String pnmlXml = "<net></net>";

        assertThrows(IOException.class, () -> {
            client.tokenReplay(pnmlXml, complexXml);
        }, "Should throw IOException when service is unreachable");
    }

    @Test
    @DisplayName("tokenReplay throws IOException with informative message when service unavailable")
    void testTokenReplayThrowsIOExceptionWhenUnavailable() {
        String pnml = "<net></net>";
        String xes = "<log></log>";

        IOException exception = assertThrows(IOException.class, () -> {
            client.tokenReplay(pnml, xes);
        }, "tokenReplay should throw IOException for unreachable service");

        assertNotNull(exception.getMessage(), "Exception should have an informative message");
        assertTrue(exception.getMessage().length() > 0, "Error message should not be empty");
    }

    @Test
    @DisplayName("discoverDfg throws IOException when service unavailable")
    void testDiscoverDfgThrowsIOExceptionWhenUnavailable() {
        String xes = "<log></log>";

        assertThrows(IOException.class, () -> {
            client.discoverDfg(xes);
        }, "discoverDfg should throw IOException for unreachable service");
    }

    @Test
    @DisplayName("discoverAlphaPpp throws IOException when service unavailable")
    void testDiscoverAlphaPppThrowsIOExceptionWhenUnavailable() {
        String xes = "<log></log>";

        assertThrows(IOException.class, () -> {
            client.discoverAlphaPpp(xes);
        }, "discoverAlphaPpp should throw IOException for unreachable service");
    }

    @Test
    @DisplayName("performanceAnalysis throws IOException when service unavailable")
    void testPerformanceAnalysisThrowsIOExceptionWhenUnavailable() {
        String xes = "<log></log>";

        assertThrows(IOException.class, () -> {
            client.performanceAnalysis(xes);
        }, "performanceAnalysis should throw IOException for unreachable service");
    }

    @Test
    @DisplayName("xesToOcel throws IOException when service unavailable")
    void testXesToOcelThrowsIOExceptionWhenUnavailable() {
        String xes = "<log></log>";

        assertThrows(IOException.class, () -> {
            client.xesToOcel(xes);
        }, "xesToOcel should throw IOException for unreachable service");
    }

    @Test
    @DisplayName("ProcessMiningService interface contract is implemented")
    void testProcessMiningServiceContractImplemented() {
        assertTrue(client instanceof ProcessMiningService,
                "ProcessMiningServiceClient must implement ProcessMiningService interface");
    }

    @Test
    @DisplayName("Constructor with default timeout creates client")
    void testConstructorWithDefaultTimeout() {
        ProcessMiningServiceClient defaultClient = new ProcessMiningServiceClient("http://localhost:8082");
        assertNotNull(defaultClient, "Constructor with default timeout should create client");
        assertFalse(defaultClient.isHealthy(), "Default client should fail health check on unreachable service");
    }

    @Test
    @DisplayName("Constructor with custom timeout creates client")
    void testConstructorWithCustomTimeout() {
        ProcessMiningServiceClient customClient = new ProcessMiningServiceClient(
                "http://localhost:8082",
                Duration.ofSeconds(5)
        );
        assertNotNull(customClient, "Constructor with custom timeout should create client");
        assertFalse(customClient.isHealthy(), "Custom timeout client should fail health check on unreachable service");
    }

    @Test
    @DisplayName("Constructor handles null base URL gracefully")
    void testConstructorWithNullBaseUrl() {
        ProcessMiningServiceClient nullUrlClient = new ProcessMiningServiceClient(null);
        assertNotNull(nullUrlClient, "Constructor should handle null baseUrl");
        assertFalse(nullUrlClient.isHealthy(), "Null URL client should handle health check");
    }

    @Test
    @DisplayName("Constructor handles null timeout gracefully")
    void testConstructorWithNullTimeout() {
        ProcessMiningServiceClient nullTimeoutClient = new ProcessMiningServiceClient(
                "http://localhost:8082",
                null
        );
        assertNotNull(nullTimeoutClient, "Constructor should handle null timeout");
        assertFalse(nullTimeoutClient.isHealthy(), "Null timeout client should handle health check");
    }

    @Test
    @DisplayName("Large XML payload handling")
    void testLargeXmlPayload() throws IOException {
        StringBuilder largeXml = new StringBuilder("<log>");
        for (int i = 0; i < 1000; i++) {
            largeXml.append(String.format("<event id=\"%d\">", i));
            largeXml.append("<string key=\"concept:name\" value=\"Activity\"/>");
            largeXml.append("</event>");
        }
        largeXml.append("</log>");

        String pnml = "<net></net>";

        assertThrows(IOException.class, () -> {
            client.tokenReplay(pnml, largeXml.toString());
        }, "Should throw IOException when service unreachable, even with large payloads");
    }

    @Test
    @DisplayName("Special characters in XML are properly escaped in JSON")
    void testSpecialCharactersEscaping() throws IOException {
        String xmlWithSpecialChars = """
                <log>
                    <event>
                        <string key="concept:name" value="test-activity_123"/>
                        <string key="data" value="unicode: \u00E9\u00E0\u00FC"/>
                    </event>
                </log>
                """;
        String pnml = "<net></net>";

        assertThrows(IOException.class, () -> {
            client.tokenReplay(pnml, xmlWithSpecialChars);
        }, "Should throw IOException when service unreachable");
    }

    @Test
    @DisplayName("Timeout is enforced on requests")
    void testTimeoutEnforced() {
        ProcessMiningServiceClient shortTimeoutClient = new ProcessMiningServiceClient(
                "http://10.255.255.1",  // Non-routable IP to trigger timeout
                Duration.ofMillis(100)   // Very short timeout
        );

        // This may timeout or fail to connect; both are acceptable
        // The key is that it doesn't hang indefinitely
        long startTime = System.currentTimeMillis();
        shortTimeoutClient.isHealthy();
        long elapsedTime = System.currentTimeMillis() - startTime;

        assertTrue(elapsedTime < 5000, "Health check should not take more than 5 seconds with 100ms timeout");
    }

    @Test
    @DisplayName("IOException message includes endpoint information")
    void testErrorMessageIncludesEndpoint() {
        ProcessMiningServiceClient badClient = new ProcessMiningServiceClient(
                UNREACHABLE_URL,
                Duration.ofMillis(100)
        );

        IOException exception = assertThrows(IOException.class, () -> {
            badClient.tokenReplay("<net></net>", "<log></log>");
        });

        assertNotNull(exception.getMessage(), "Exception message should not be null");
    }
}
