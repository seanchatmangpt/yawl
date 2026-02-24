package org.yawlfoundation.yawl.ggen.mining.cloud;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CloudMiningClientFactory.
 * Verifies factory can instantiate all supported cloud mining clients.
 */
@DisplayName("CloudMiningClientFactory Tests")
class CloudMiningClientFactoryTest {

    @Test
    @DisplayName("Factory creates Celonis client")
    void testFactoryCreatesCelonis() {
        Map<String, String> config = new HashMap<>();
        config.put("apiKey", "test-api-key");
        config.put("teamId", "test-team-id");

        CloudMiningClient client = CloudMiningClientFactory.create("celonis", config);

        assertNotNull(client);
        assertTrue(client instanceof CelonicsMiningClient);
    }

    @Test
    @DisplayName("Factory creates UiPath client")
    void testFactoryCreatesUiPath() {
        Map<String, String> config = new HashMap<>();
        config.put("apiToken", "test-api-token");
        config.put("tenantName", "test-tenant");
        config.put("accountName", "test-account");

        CloudMiningClient client = CloudMiningClientFactory.create("uipath", config);

        assertNotNull(client);
        assertTrue(client instanceof UiPathAutomationClient);
    }

    @Test
    @DisplayName("Factory creates Signavio client")
    void testFactoryCreatesSignavio() {
        Map<String, String> config = new HashMap<>();
        config.put("email", "test@example.com");
        config.put("password", "test-password");
        config.put("serverUrl", "https://editor.signavio.com/g");

        CloudMiningClient client = CloudMiningClientFactory.create("signavio", config);

        assertNotNull(client);
        assertTrue(client instanceof SignavioClient);
    }

    @Test
    @DisplayName("Factory throws on unknown platform")
    void testFactoryThrowsOnUnknown() {
        Map<String, String> config = new HashMap<>();

        assertThrows(
            IllegalArgumentException.class,
            () -> CloudMiningClientFactory.create("unknown", config),
            "Should throw IllegalArgumentException for unknown platform"
        );
    }

    @Test
    @DisplayName("Factory throws when required Celonis config missing")
    void testFactoryThrowsOnMissingCelonisConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("apiKey", "test-api-key");

        assertThrows(
            IllegalArgumentException.class,
            () -> CloudMiningClientFactory.create("celonis", config),
            "Should throw when teamId is missing"
        );
    }

    @Test
    @DisplayName("Factory throws when required UiPath config missing")
    void testFactoryThrowsOnMissingUiPathConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("apiToken", "test-api-token");
        config.put("tenantName", "test-tenant");

        assertThrows(
            IllegalArgumentException.class,
            () -> CloudMiningClientFactory.create("uipath", config),
            "Should throw when accountName is missing"
        );
    }

    @Test
    @DisplayName("Factory throws when required Signavio config missing")
    void testFactoryThrowsOnMissingSignavioConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("email", "test@example.com");
        config.put("password", "test-password");

        assertThrows(
            IllegalArgumentException.class,
            () -> CloudMiningClientFactory.create("signavio", config),
            "Should throw when serverUrl is missing"
        );
    }

    @Test
    @DisplayName("Direct UiPath constructor works")
    void testUiPathConstructor() {
        UiPathAutomationClient client = new UiPathAutomationClient(
            "test-api-token",
            "test-tenant",
            "test-account"
        );

        assertNotNull(client);
    }

    @Test
    @DisplayName("Direct UiPath OAuth constructor works")
    void testUiPathOAuthConstructor() {
        UiPathAutomationClient client = new UiPathAutomationClient(
            "test-client-id",
            "test-client-secret",
            "test-tenant"
        );

        assertNotNull(client);
    }

    @Test
    @DisplayName("Direct Signavio constructor works")
    void testSignavioConstructor() {
        SignavioClient client = new SignavioClient(
            "test@example.com",
            "test-password",
            "https://editor.signavio.com/g"
        );

        assertNotNull(client);
    }

    @Test
    @DisplayName("Direct Celonis constructor works")
    void testCelonisConstructor() {
        CelonicsMiningClient client = new CelonicsMiningClient(
            "test-api-key",
            "test-team-id"
        );

        assertNotNull(client);
    }

    @Test
    @DisplayName("Factory method createCelonis works")
    void testCreateCelonisMethod() {
        CelonicsMiningClient client = CloudMiningClientFactory.createCelonis(
            "test-api-key",
            "test-team-id"
        );

        assertNotNull(client);
        assertTrue(client instanceof CelonicsMiningClient);
    }

    @Test
    @DisplayName("Factory method createUiPath works")
    void testCreateUiPathMethod() {
        UiPathAutomationClient client = CloudMiningClientFactory.createUiPath(
            "test-api-token",
            "test-tenant",
            "test-account"
        );

        assertNotNull(client);
        assertTrue(client instanceof UiPathAutomationClient);
    }

    @Test
    @DisplayName("Factory method createSignavio works")
    void testCreateSignavioMethod() {
        SignavioClient client = CloudMiningClientFactory.createSignavio(
            "test@example.com",
            "test-password",
            "https://editor.signavio.com/g"
        );

        assertNotNull(client);
        assertTrue(client instanceof SignavioClient);
    }

    @Test
    @DisplayName("Factory is case-insensitive for platform name")
    void testFactoryCaseInsensitive() {
        Map<String, String> config = new HashMap<>();
        config.put("apiKey", "test-api-key");
        config.put("teamId", "test-team-id");

        CloudMiningClient client1 = CloudMiningClientFactory.create("CELONIS", config);
        CloudMiningClient client2 = CloudMiningClientFactory.create("Celonis", config);
        CloudMiningClient client3 = CloudMiningClientFactory.create("celonis", config);

        assertNotNull(client1);
        assertNotNull(client2);
        assertNotNull(client3);
        assertTrue(client1 instanceof CelonicsMiningClient);
        assertTrue(client2 instanceof CelonicsMiningClient);
        assertTrue(client3 instanceof CelonicsMiningClient);
    }

    @Test
    @DisplayName("SignavioClient throws UnsupportedOperationException for getEventLog")
    void testSignavioGetEventLogThrows() {
        SignavioClient client = new SignavioClient(
            "test@example.com",
            "test-password",
            "https://editor.signavio.com/g"
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> client.getEventLog("process-id"),
            "Signavio should throw UnsupportedOperationException for event logs"
        );
    }

    @Test
    @DisplayName("SignavioClient throws with descriptive message for getEventLog")
    void testSignavioGetEventLogThrowsDescriptive() {
        SignavioClient client = new SignavioClient(
            "test@example.com",
            "test-password",
            "https://editor.signavio.com/g"
        );

        UnsupportedOperationException exception = assertThrows(
            UnsupportedOperationException.class,
            () -> client.getEventLog("process-id")
        );

        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("Signavio"), "Error message should mention Signavio");
        assertTrue(message.contains("event log"), "Error message should mention event logs");
        assertTrue(message.contains("Celonis") || message.contains("UiPath"),
            "Error message should suggest alternatives");
    }
}
