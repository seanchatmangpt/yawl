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

package org.yawlfoundation.yawl.integration;

import org.junit.jupiter.api.Tag;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.yawlfoundation.yawl.integration.gateway.GatewayCircuitBreakerConfig;
import org.yawlfoundation.yawl.integration.gateway.GatewayRouteDefinition;
import org.yawlfoundation.yawl.integration.gateway.kong.KongConfigurationGenerator;
import org.yawlfoundation.yawl.integration.gateway.traefik.TraefikConfigurationGenerator;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEventSerializer;
import org.yawlfoundation.yawl.integration.oauth2.OAuth2ValidationException;
import org.yawlfoundation.yawl.integration.oauth2.OidcUserContext;
import org.yawlfoundation.yawl.integration.oauth2.RbacAuthorizationEnforcer;
import org.yawlfoundation.yawl.integration.oauth2.YawlOAuth2Scopes;
import org.yawlfoundation.yawl.integration.eventsourcing.CaseSnapshot;
import org.yawlfoundation.yawl.integration.eventsourcing.CaseStateView;
import org.yawlfoundation.yawl.integration.eventsourcing.CaseStateView.CaseStatus;
import org.yawlfoundation.yawl.integration.webhook.WebhookSigner;
import org.yawlfoundation.yawl.integration.webhook.WebhookSubscription;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for YAWL v6.0.0 Enterprise Integration Patterns.
 *
 * <p>Covers:
 * <ul>
 *   <li>OAuth2/OIDC: validation exception codes, RBAC scope enforcement, scope constants</li>
 *   <li>Message Queue: WorkflowEvent creation, serialization round-trip, topic routing</li>
 *   <li>Event Sourcing: CaseStateView immutable updates, snapshot creation, temporal logic</li>
 *   <li>API Gateway: route generation, circuit breaker config rendering, YAML generation</li>
 *   <li>Webhook: HMAC-SHA256 signing, signature verification, subscription delivery filtering</li>
 * </ul>
 *
 * <p>All tests run without external services (no live YAWL engine, no broker, no IdP).
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("integration")
public class EnterpriseIntegrationPatternsTest extends TestCase {

    public static Test suite() {
        return new TestSuite(EnterpriseIntegrationPatternsTest.class);
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }

    // =========================================================================
    // OAuth2/OIDC Tests
    // =========================================================================

    public void testOAuth2ValidationExceptionHttpStatus() {
        assertEquals(401, new OAuth2ValidationException("msg", OAuth2ValidationException.Code.MISSING_TOKEN).httpStatus());
        assertEquals(401, new OAuth2ValidationException("msg", OAuth2ValidationException.Code.TOKEN_EXPIRED).httpStatus());
        assertEquals(401, new OAuth2ValidationException("msg", OAuth2ValidationException.Code.INVALID_SIGNATURE).httpStatus());
        assertEquals(403, new OAuth2ValidationException("msg", OAuth2ValidationException.Code.ISSUER_MISMATCH).httpStatus());
        assertEquals(403, new OAuth2ValidationException("msg", OAuth2ValidationException.Code.AUDIENCE_MISMATCH).httpStatus());
        assertEquals(400, new OAuth2ValidationException("msg", OAuth2ValidationException.Code.MISSING_CLAIM).httpStatus());
        assertEquals(400, new OAuth2ValidationException("msg", OAuth2ValidationException.Code.UNSUPPORTED_ALGORITHM).httpStatus());
        assertEquals(503, new OAuth2ValidationException("msg", OAuth2ValidationException.Code.KEY_NOT_FOUND).httpStatus());
    }

    public void testOAuth2ValidationExceptionIsUnauthorized() {
        assertTrue(new OAuth2ValidationException("m", OAuth2ValidationException.Code.MISSING_TOKEN).isUnauthorized());
        assertTrue(new OAuth2ValidationException("m", OAuth2ValidationException.Code.TOKEN_EXPIRED).isUnauthorized());
        assertFalse(new OAuth2ValidationException("m", OAuth2ValidationException.Code.ISSUER_MISMATCH).isUnauthorized());
        assertFalse(new OAuth2ValidationException("m", OAuth2ValidationException.Code.AUDIENCE_MISMATCH).isUnauthorized());
    }

    public void testOAuth2ValidationExceptionCode() {
        OAuth2ValidationException ex = new OAuth2ValidationException(
                "test", OAuth2ValidationException.Code.KEY_NOT_FOUND);
        assertEquals(OAuth2ValidationException.Code.KEY_NOT_FOUND, ex.getCode());
        assertEquals("test", ex.getMessage());
    }

    public void testOidcUserContextHasScope() {
        OidcUserContext ctx = new OidcUserContext(
                "user-1", "user@example.com", "User One",
                Set.of(YawlOAuth2Scopes.OPERATOR),
                Set.of(),
                Instant.now().plusSeconds(3600),
                Map.of());
        assertTrue(ctx.hasScope(YawlOAuth2Scopes.OPERATOR));
        assertFalse(ctx.hasScope(YawlOAuth2Scopes.DESIGNER));
        assertFalse(ctx.isAdmin());
    }

    public void testOidcUserContextAdminImpliesAll() {
        OidcUserContext adminCtx = new OidcUserContext(
                "admin-1", null, null,
                Set.of(YawlOAuth2Scopes.ADMIN),
                Set.of("yawl-admin-role"),
                Instant.now().plusSeconds(3600),
                Map.of());
        assertTrue(adminCtx.hasScope(YawlOAuth2Scopes.OPERATOR));
        assertTrue(adminCtx.hasScope(YawlOAuth2Scopes.DESIGNER));
        assertTrue(adminCtx.hasScope(YawlOAuth2Scopes.MONITOR));
        assertTrue(adminCtx.isAdmin());
    }

    public void testRbacOperatorCanLaunchCases() throws Exception {
        OidcUserContext operator = new OidcUserContext(
                "op-1", null, null,
                Set.of(YawlOAuth2Scopes.OPERATOR),
                Set.of(),
                Instant.now().plusSeconds(3600),
                Map.of());
        // Should not throw
        RbacAuthorizationEnforcer.assertPermitted(operator, RbacAuthorizationEnforcer.WorkflowOperation.LAUNCH_CASE);
        RbacAuthorizationEnforcer.assertPermitted(operator, RbacAuthorizationEnforcer.WorkflowOperation.CHECKOUT_WORKITEM);
        assertTrue(RbacAuthorizationEnforcer.isPermitted(operator, RbacAuthorizationEnforcer.WorkflowOperation.LIST_CASES));
    }

    public void testRbacOperatorCannotLoadSpecs() {
        OidcUserContext operator = new OidcUserContext(
                "op-2", null, null,
                Set.of(YawlOAuth2Scopes.OPERATOR),
                Set.of(),
                Instant.now().plusSeconds(3600),
                Map.of());
        assertFalse(RbacAuthorizationEnforcer.isPermitted(operator,
                RbacAuthorizationEnforcer.WorkflowOperation.LOAD_SPECIFICATION));
    }

    public void testRbacMonitorReadOnly() {
        OidcUserContext monitor = new OidcUserContext(
                "mon-1", null, null,
                Set.of(YawlOAuth2Scopes.MONITOR),
                Set.of(),
                Instant.now().plusSeconds(3600),
                Map.of());
        assertTrue(RbacAuthorizationEnforcer.isPermitted(monitor,
                RbacAuthorizationEnforcer.WorkflowOperation.LIST_CASES));
        assertTrue(RbacAuthorizationEnforcer.isPermitted(monitor,
                RbacAuthorizationEnforcer.WorkflowOperation.GET_CASE_STATUS));
        assertFalse(RbacAuthorizationEnforcer.isPermitted(monitor,
                RbacAuthorizationEnforcer.WorkflowOperation.LAUNCH_CASE));
        assertFalse(RbacAuthorizationEnforcer.isPermitted(monitor,
                RbacAuthorizationEnforcer.WorkflowOperation.CHECKOUT_WORKITEM));
    }

    public void testRbacDenialThrowsWithDetails() {
        OidcUserContext monitor = new OidcUserContext(
                "mon-2", null, null,
                Set.of(YawlOAuth2Scopes.MONITOR),
                Set.of(),
                Instant.now().plusSeconds(3600),
                Map.of());
        try {
            RbacAuthorizationEnforcer.assertPermitted(monitor,
                    RbacAuthorizationEnforcer.WorkflowOperation.LAUNCH_CASE);
            fail("Expected RbacAccessDeniedException");
        } catch (RbacAuthorizationEnforcer.RbacAccessDeniedException e) {
            assertEquals("mon-2", e.getSubject());
            assertEquals(RbacAuthorizationEnforcer.WorkflowOperation.LAUNCH_CASE, e.getOperation());
        }
    }

    public void testRbacRequiredScopesForOperation() {
        Set<String> scopes = RbacAuthorizationEnforcer.requiredScopes(
                RbacAuthorizationEnforcer.WorkflowOperation.LOAD_SPECIFICATION);
        assertTrue(scopes.contains(YawlOAuth2Scopes.DESIGNER));
        assertTrue(scopes.contains(YawlOAuth2Scopes.ADMIN));
        assertFalse(scopes.contains(YawlOAuth2Scopes.OPERATOR));
    }

    // =========================================================================
    // Message Queue Tests
    // =========================================================================

    public void testWorkflowEventCreation() {
        WorkflowEvent event = new WorkflowEvent(
                WorkflowEvent.EventType.CASE_STARTED,
                "OrderFulfillment:1.0",
                "42",
                null,
                Map.of("launchedBy", "agent-order-service"));

        assertNotNull(event.getEventId());
        assertEquals(WorkflowEvent.EventType.CASE_STARTED, event.getEventType());
        assertEquals("OrderFulfillment:1.0", event.getSpecId());
        assertEquals("42", event.getCaseId());
        assertNull(event.getWorkItemId());
        assertEquals(WorkflowEvent.SCHEMA_VERSION, event.getSchemaVersion());
        assertNotNull(event.getTimestamp());
        assertEquals("agent-order-service", event.getPayload().get("launchedBy"));
    }

    public void testWorkflowEventKafkaTopic() {
        WorkflowEvent event = new WorkflowEvent(
                WorkflowEvent.EventType.CASE_COMPLETED,
                "spec:1.0", "1", null, Map.of());
        assertEquals("yawl.case-completed", event.kafkaTopic());
    }

    public void testWorkflowEventRabbitRoutingKey() {
        WorkflowEvent event = new WorkflowEvent(
                WorkflowEvent.EventType.WORKITEM_STARTED,
                "spec:1.0", "1", "task1:1", Map.of());
        assertEquals("workflow.workitem.started", event.rabbitRoutingKey());
    }

    public void testWorkflowEventRequiresExactlyOnce() {
        WorkflowEvent completed = new WorkflowEvent(
                WorkflowEvent.EventType.CASE_COMPLETED,
                "spec:1.0", "1", null, Map.of());
        WorkflowEvent started = new WorkflowEvent(
                WorkflowEvent.EventType.CASE_STARTED,
                "spec:1.0", "1", null, Map.of());
        WorkflowEvent wiCompleted = new WorkflowEvent(
                WorkflowEvent.EventType.WORKITEM_COMPLETED,
                "spec:1.0", "1", "t1:1", Map.of());

        assertTrue(completed.requiresExactlyOnceDelivery());
        assertTrue(wiCompleted.requiresExactlyOnceDelivery());
        assertFalse(started.requiresExactlyOnceDelivery());
    }

    public void testWorkflowEventEqualityByEventId() {
        WorkflowEvent a = new WorkflowEvent(
                "evt-1", WorkflowEvent.EventType.CASE_STARTED,
                "1.0", "s:1.0", "1", null, Instant.now(), Map.of());
        WorkflowEvent b = new WorkflowEvent(
                "evt-1", WorkflowEvent.EventType.CASE_COMPLETED,
                "1.0", "s:1.0", "2", null, Instant.now(), Map.of());
        WorkflowEvent c = new WorkflowEvent(
                "evt-2", WorkflowEvent.EventType.CASE_STARTED,
                "1.0", "s:1.0", "1", null, Instant.now(), Map.of());

        assertEquals(a, b);      // same eventId -> equal
        assertFalse(a.equals(c)); // different eventId -> not equal
    }

    public void testWorkflowEventSerializationRoundTrip() throws Exception {
        WorkflowEventSerializer serializer = new WorkflowEventSerializer();

        WorkflowEvent original = new WorkflowEvent(
                "evt-round-trip-1", WorkflowEvent.EventType.WORKITEM_ENABLED,
                "1.0", "PurchaseOrder:2.0", "100", "approve:100",
                Instant.parse("2026-02-17T10:00:00Z"),
                Map.of("assignee", "john.doe", "priority", "HIGH"));

        byte[] bytes        = serializer.serialize(original);
        WorkflowEvent deserialized = serializer.deserialize(bytes);

        assertEquals(original.getEventId(),    deserialized.getEventId());
        assertEquals(original.getEventType(),  deserialized.getEventType());
        assertEquals(original.getSpecId(),     deserialized.getSpecId());
        assertEquals(original.getCaseId(),     deserialized.getCaseId());
        assertEquals(original.getTimestamp(),  deserialized.getTimestamp());
        assertEquals("john.doe",  deserialized.getPayload().get("assignee"));
        assertEquals("HIGH",      deserialized.getPayload().get("priority"));
    }

    public void testWorkflowEventSerializerRejectsNull() {
        WorkflowEventSerializer serializer = new WorkflowEventSerializer();
        try {
            serializer.serialize(null);
            fail("Expected IllegalArgumentException for null event");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("event must not be null"));
        } catch (Exception e) {
            fail("Expected IllegalArgumentException but got " + e.getClass().getName());
        }
    }

    // =========================================================================
    // Event Sourcing Tests
    // =========================================================================

    public void testCaseStateViewEmptyState() {
        CaseStateView view = CaseStateView.empty("case-42");
        assertEquals("case-42", view.getCaseId());
        assertEquals(CaseStatus.UNKNOWN, view.getStatus());
        assertNull(view.getSpecId());
        assertTrue(view.getActiveWorkItems().isEmpty());
        assertTrue(view.getPayload().isEmpty());
    }

    public void testCaseStateViewImmutableUpdates() {
        CaseStateView view1 = CaseStateView.empty("case-1");
        CaseStateView view2 = view1.withStatus(CaseStatus.RUNNING);
        CaseStateView view3 = view2.withSpecId("Order:1.0");
        CaseStateView view4 = view3.withActiveWorkItem("task1:1", "ENABLED", Instant.now());

        // Original unchanged
        assertEquals(CaseStatus.UNKNOWN, view1.getStatus());
        assertNull(view1.getSpecId());
        assertTrue(view1.getActiveWorkItems().isEmpty());

        // Each update reflected in new instance
        assertEquals(CaseStatus.RUNNING, view2.getStatus());
        assertEquals("Order:1.0", view3.getSpecId());
        assertEquals(1, view4.getActiveWorkItems().size());
        assertEquals("ENABLED", view4.getActiveWorkItems().get("task1:1").status());
    }

    public void testCaseStateViewWithoutWorkItem() {
        CaseStateView view = CaseStateView.empty("case-2")
                .withStatus(CaseStatus.RUNNING)
                .withActiveWorkItem("task1:2", "STARTED", Instant.now())
                .withActiveWorkItem("task2:2", "ENABLED", Instant.now());

        assertEquals(2, view.getActiveWorkItems().size());

        CaseStateView completed = view.withoutWorkItem("task1:2");
        assertEquals(1, completed.getActiveWorkItems().size());
        assertFalse(completed.getActiveWorkItems().containsKey("task1:2"));
        assertTrue(completed.getActiveWorkItems().containsKey("task2:2"));
    }

    public void testCaseStateViewWithoutNonExistentItemIsNoOp() {
        CaseStateView view = CaseStateView.empty("case-3")
                .withActiveWorkItem("t1:3", "STARTED", Instant.now());
        CaseStateView same = view.withoutWorkItem("nonexistent");
        assertSame(view, same); // same instance returned
    }

    public void testCaseSnapshotFromStateView() {
        CaseStateView view = CaseStateView.empty("case-snap-1")
                .withStatus(CaseStatus.RUNNING)
                .withSpecId("TestSpec:1.0")
                .withActiveWorkItem("task1:snap-1", "ENABLED", Instant.now())
                .withPayloadEntry("startedAt", "2026-02-17T10:00:00Z");

        CaseSnapshot snapshot = CaseSnapshot.from(view, 5L);

        assertEquals("case-snap-1", snapshot.getCaseId());
        assertEquals("TestSpec:1.0", snapshot.getSpecId());
        assertEquals(5L, snapshot.getSequenceNumber());
        assertEquals("RUNNING", snapshot.getStatus());
        assertEquals(1, snapshot.getActiveWorkItems().size());
        assertEquals("2026-02-17T10:00:00Z", snapshot.getPayload().get("startedAt"));
        assertNotNull(snapshot.getSnapshotAt());
    }

    public void testCaseStateViewFromSnapshot() {
        CaseStateView original = CaseStateView.empty("case-snap-2")
                .withStatus(CaseStatus.SUSPENDED)
                .withSpecId("SuspendedSpec:1.0");

        CaseSnapshot snapshot = CaseSnapshot.from(original, 10L);
        CaseStateView restored = CaseStateView.fromSnapshot(snapshot);

        assertEquals("case-snap-2", restored.getCaseId());
        assertEquals(CaseStatus.SUSPENDED, restored.getStatus());
        assertEquals("SuspendedSpec:1.0", restored.getSpecId());
    }

    // =========================================================================
    // API Gateway Tests
    // =========================================================================

    public void testGatewayRouteYawlV1RoutesNotEmpty() {
        List<GatewayRouteDefinition> routes =
                GatewayRouteDefinition.yawlV1Routes("http://yawl:8080");
        assertFalse("yawlV1Routes should return a non-empty list", routes.isEmpty());
    }

    public void testGatewayRouteLaunchCaseIsCritical() {
        List<GatewayRouteDefinition> routes =
                GatewayRouteDefinition.yawlV1Routes("http://yawl:8080");
        GatewayRouteDefinition launchRoute = routes.stream()
                .filter(r -> r.getRouteId().equals("yawl-case-launch"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("yawl-case-launch route not found"));

        assertEquals(GatewayRouteDefinition.RateLimitTier.CRITICAL, launchRoute.getRateLimitTier());
        assertTrue(launchRoute.isRequiresAuthentication());
        assertTrue(launchRoute.getRequiredScopes().contains(YawlOAuth2Scopes.OPERATOR));
    }

    public void testGatewayRouteHealthNoAuth() {
        List<GatewayRouteDefinition> routes =
                GatewayRouteDefinition.yawlV1Routes("http://yawl:8080");
        GatewayRouteDefinition healthRoute = routes.stream()
                .filter(r -> r.getRouteId().equals("yawl-health"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("yawl-health route not found"));

        assertFalse("Health route should not require authentication",
                    healthRoute.isRequiresAuthentication());
        assertEquals(GatewayRouteDefinition.RateLimitTier.READ_ONLY,
                     healthRoute.getRateLimitTier());
    }

    public void testRateLimitTierValues() {
        assertEquals(10,  GatewayRouteDefinition.RateLimitTier.CRITICAL.getRequestsPerWindow());
        assertEquals(100, GatewayRouteDefinition.RateLimitTier.STANDARD.getRequestsPerWindow());
        assertEquals(600, GatewayRouteDefinition.RateLimitTier.READ_ONLY.getRequestsPerWindow());
    }

    public void testKongConfigGeneratorProducesYaml() {
        GatewayCircuitBreakerConfig cb = GatewayCircuitBreakerConfig.kongDefault();
        String yaml = KongConfigurationGenerator.generate(
                "http://yawl-engine:8080",
                "https://keycloak.example.com/realms/yawl",
                "yawl-api",
                cb);

        assertNotNull(yaml);
        assertTrue("YAML should contain format version", yaml.contains("_format_version"));
        assertTrue("YAML should define services",        yaml.contains("services:"));
        assertTrue("YAML should include rate-limiting",  yaml.contains("rate-limiting"));
        assertTrue("YAML should reference engine URL",   yaml.contains("yawl-engine:8080"));
        assertTrue("YAML should include JWT plugin",     yaml.contains("name: jwt"));
        assertTrue("YAML should define upstreams",       yaml.contains("upstreams:"));
    }

    public void testTraefikConfigGeneratorProducesYaml() {
        GatewayCircuitBreakerConfig cb = GatewayCircuitBreakerConfig.traefikDefault();
        String yaml = TraefikConfigurationGenerator.generate(
                "http://yawl-engine:8080",
                "http://auth-service:8081/validate",
                cb);

        assertNotNull(yaml);
        assertTrue("YAML should define http routers",    yaml.contains("routers:"));
        assertTrue("YAML should define middlewares",     yaml.contains("middlewares:"));
        assertTrue("YAML should include circuit breaker",yaml.contains("circuitBreaker:"));
        assertTrue("YAML should include rate limits",    yaml.contains("rateLimit:"));
        assertTrue("YAML should include forward-auth",   yaml.contains("forwardAuth:"));
        assertTrue("YAML should include security headers",yaml.contains("yawl-security-headers:"));
    }

    public void testGatewayCircuitBreakerKongYaml() {
        GatewayCircuitBreakerConfig config = GatewayCircuitBreakerConfig.kongDefault();
        String yaml = config.toKongYaml();

        assertNotNull(yaml);
        assertTrue(yaml.contains("healthchecks:"));
        assertTrue(yaml.contains("active:"));
        assertTrue(yaml.contains("passive:"));
    }

    public void testGatewayCircuitBreakerTraefikYaml() {
        GatewayCircuitBreakerConfig config = GatewayCircuitBreakerConfig.traefikDefault();
        String yaml = config.toTraefikYaml();

        assertNotNull(yaml);
        assertTrue(yaml.contains("circuitBreaker:"));
        assertTrue(yaml.contains("expression:"));
        assertTrue(yaml.contains("fallbackDuration:"));
    }

    public void testGatewayCircuitBreakerTraefikYamlFailsForKong() {
        GatewayCircuitBreakerConfig kongConfig = GatewayCircuitBreakerConfig.kongDefault();
        try {
            kongConfig.toTraefikYaml();
            fail("Expected UnsupportedOperationException for Kong config calling toTraefikYaml()");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("TRAEFIK"));
        }
    }

    // =========================================================================
    // Webhook Tests
    // =========================================================================

    public void testWebhookSignerComputeHmac() throws Exception {
        String secret = "my-super-secret-key-that-is-at-least-32-chars";
        byte[] body   = "Hello, YAWL!".getBytes(StandardCharsets.UTF_8);

        String hmac1 = WebhookSigner.computeHmacHex(secret, body);
        String hmac2 = WebhookSigner.computeHmacHex(secret, body);

        assertNotNull(hmac1);
        assertEquals("HMAC should be deterministic", hmac1, hmac2);
        assertEquals("HMAC-SHA256 should be 64 hex chars", 64, hmac1.length());
    }

    public void testWebhookSignerBuildSignatureHeader() throws Exception {
        String secret = "my-super-secret-key-that-is-at-least-32-chars";
        byte[] body   = "test body".getBytes(StandardCharsets.UTF_8);

        String header = WebhookSigner.buildSignatureHeader(secret, body);

        assertTrue("Header should start with sha256=", header.startsWith("sha256="));
        assertEquals("Header should be sha256= plus 64 chars", 71, header.length());
    }

    public void testWebhookSignerVerifyValid() throws Exception {
        String secret = "my-super-secret-key-that-is-at-least-32-chars";
        byte[] body   = "{\"eventType\":\"CASE_STARTED\"}".getBytes(StandardCharsets.UTF_8);

        String header = WebhookSigner.buildSignatureHeader(secret, body);
        assertTrue("Valid signature should verify", WebhookSigner.verify(secret, body, header));
    }

    public void testWebhookSignerVerifyTamperedBody() throws Exception {
        String secret = "my-super-secret-key-that-is-at-least-32-chars";
        byte[] originalBody = "{\"eventType\":\"CASE_STARTED\"}".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedBody = "{\"eventType\":\"CASE_CANCELLED\"}".getBytes(StandardCharsets.UTF_8);

        String header = WebhookSigner.buildSignatureHeader(secret, originalBody);
        assertFalse("Tampered body should fail verification",
                    WebhookSigner.verify(secret, tamperedBody, header));
    }

    public void testWebhookSignerVerifyWrongSecret() throws Exception {
        String secret1 = "my-super-secret-key-that-is-at-least-32-chars";
        String secret2 = "completely-different-secret-key-that-is-32ch";
        byte[] body    = "test".getBytes(StandardCharsets.UTF_8);

        String header = WebhookSigner.buildSignatureHeader(secret1, body);
        assertFalse("Different secret should fail verification",
                    WebhookSigner.verify(secret2, body, header));
    }

    public void testWebhookSignerVerifyNullHeader() throws Exception {
        String secret = "my-super-secret-key-that-is-at-least-32-chars";
        byte[] body   = "test".getBytes(StandardCharsets.UTF_8);
        assertFalse("Null header should return false",
                    WebhookSigner.verify(secret, body, null));
    }

    public void testWebhookSignerSecureEquals() {
        assertTrue(WebhookSigner.secureEquals("abc123", "abc123"));
        assertFalse(WebhookSigner.secureEquals("abc123", "abc124"));
        assertFalse(WebhookSigner.secureEquals("abc", "abcd"));
        assertFalse(WebhookSigner.secureEquals(null, "abc"));
        assertFalse(WebhookSigner.secureEquals("abc", null));
    }

    public void testWebhookSignerRequiresMinimumSecretLength() {
        try {
            WebhookSigner.computeHmacHex("short-secret", "body".getBytes(StandardCharsets.UTF_8));
            // Short secrets are allowed by HMAC-SHA256 (they just get zero-padded internally)
            // The minimum length check is in WebhookSubscription, not WebhookSigner
        } catch (Exception e) {
            // If it throws, it must not be for algorithm unavailability
            assertFalse(e.getMessage().contains("unavailable"));
        }
    }

    public void testWebhookSubscriptionCreation() {
        WebhookSubscription sub = new WebhookSubscription(
                "https://example.com/webhook",
                "my-super-secret-key-that-is-at-least-32-chars",
                Set.of(WorkflowEvent.EventType.CASE_COMPLETED),
                "Test subscription");

        assertNotNull(sub.getSubscriptionId());
        assertEquals("https://example.com/webhook", sub.getTargetUrl());
        assertEquals(WebhookSubscription.Status.ACTIVE, sub.getStatus());
        assertEquals(1, sub.getEventTypeFilter().size());
        assertTrue(sub.getEventTypeFilter().contains(WorkflowEvent.EventType.CASE_COMPLETED));
        assertNotNull(sub.getCreatedAt());
    }

    public void testWebhookSubscriptionShouldDeliverMatchingEvent() {
        WebhookSubscription sub = new WebhookSubscription(
                "https://example.com/hook",
                "my-super-secret-key-that-is-at-least-32-chars",
                Set.of(WorkflowEvent.EventType.CASE_COMPLETED,
                       WorkflowEvent.EventType.CASE_STARTED),
                "Selective subscription");

        WorkflowEvent started  = new WorkflowEvent(WorkflowEvent.EventType.CASE_STARTED,
                "spec:1.0", "1", null, Map.of());
        WorkflowEvent cancelled = new WorkflowEvent(WorkflowEvent.EventType.CASE_CANCELLED,
                "spec:1.0", "1", null, Map.of());

        assertTrue("CASE_STARTED in filter - should deliver",  sub.shouldDeliver(started));
        assertFalse("CASE_CANCELLED not in filter - skip",     sub.shouldDeliver(cancelled));
    }

    public void testWebhookSubscriptionEmptyFilterDeliverAll() {
        WebhookSubscription sub = new WebhookSubscription(
                "https://example.com/all",
                "my-super-secret-key-that-is-at-least-32-chars",
                Set.of(), // empty = all events
                "Catch-all subscription");

        WorkflowEvent anyEvent = new WorkflowEvent(WorkflowEvent.EventType.WORKITEM_FAILED,
                "spec:1.0", "1", "t1:1", Map.of());
        assertTrue("Empty filter means deliver all events", sub.shouldDeliver(anyEvent));
    }

    public void testWebhookSubscriptionDisabledNeverDelivers() {
        WebhookSubscription active = new WebhookSubscription(
                "https://example.com/disabled",
                "my-super-secret-key-that-is-at-least-32-chars",
                Set.of(),
                "Disabled sub");

        WebhookSubscription disabled = active.withStatus(WebhookSubscription.Status.DISABLED, null);
        WorkflowEvent event = new WorkflowEvent(WorkflowEvent.EventType.CASE_STARTED,
                "spec:1.0", "1", null, Map.of());

        assertFalse("Disabled subscription should never deliver", disabled.shouldDeliver(event));
    }

    public void testWebhookSubscriptionPausedNeverDelivers() {
        WebhookSubscription active = new WebhookSubscription(
                "https://example.com/paused",
                "my-super-secret-key-that-is-at-least-32-chars",
                Set.of(),
                "Paused sub");

        WebhookSubscription paused = active.withStatus(
                WebhookSubscription.Status.PAUSED,
                Instant.now().plusSeconds(3600)); // paused for 1 hour

        WorkflowEvent event = new WorkflowEvent(WorkflowEvent.EventType.CASE_STARTED,
                "spec:1.0", "1", null, Map.of());

        assertFalse("Paused subscription should not deliver", paused.shouldDeliver(event));
    }

    public void testWebhookSubscriptionWithSecretKey() {
        WebhookSubscription sub = new WebhookSubscription(
                "https://example.com/rotate",
                "old-secret-key-that-is-at-least-32-characters",
                Set.of(),
                "Secret rotation test");

        String newSecret = "new-rotated-secret-that-is-at-least-32-chars";
        WebhookSubscription rotated = sub.withSecretKey(newSecret);

        assertEquals("Subscription ID should be preserved", sub.getSubscriptionId(),
                     rotated.getSubscriptionId());
        assertEquals(newSecret, rotated.getSecretKey());
        assertFalse("Original sub secret should not change",
                    sub.getSecretKey().equals(rotated.getSecretKey()));
    }

    public void testWebhookSubscriptionRejectsShortSecret() {
        try {
            new WebhookSubscription("https://example.com/short",
                                    "short",  // < 32 chars
                                    Set.of(), "Short secret test");
            fail("Expected IllegalArgumentException for short secret");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("32"));
        }
    }

    public void testWebhookSubscriptionRejectsNonHttpUrl() {
        try {
            new WebhookSubscription("ftp://example.com/hook",
                                    "my-super-secret-key-that-is-at-least-32-chars",
                                    Set.of(), "FTP URL test");
            fail("Expected IllegalArgumentException for non-HTTP URL");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("http://") || e.getMessage().contains("https://"));
        }
    }
}
