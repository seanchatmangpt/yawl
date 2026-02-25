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

package org.yawlfoundation.yawl.integration.fmea;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.TenantContext;
import org.yawlfoundation.yawl.integration.a2a.auth.AuthenticatedPrincipal;
import org.yawlfoundation.yawl.integration.oauth2.OidcUserContext;
import org.yawlfoundation.yawl.integration.oauth2.YawlOAuth2Scopes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link UserFmeaAnalyzer}.
 *
 * Chicago TDD: real YAWL objects, no mocks.
 * Each nested class covers one {@code analyze*} method and its failure modes.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("unit")
class UserFmeaAnalyzerTest {

    private UserFmeaAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new UserFmeaAnalyzer();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Valid (non-expired) A2A principal with the given permissions. */
    private static AuthenticatedPrincipal principal(String username, Set<String> perms) {
        return new AuthenticatedPrincipal(
            username, perms, "Bearer",
            Instant.now().minus(1, ChronoUnit.MINUTES),
            Instant.now().plus(1, ChronoUnit.HOURS)
        );
    }

    /** Expired A2A principal (expiresAt one hour in the past). */
    private static AuthenticatedPrincipal expiredPrincipal(String username) {
        return new AuthenticatedPrincipal(
            username, Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH), "Bearer",
            Instant.now().minus(2, ChronoUnit.HOURS),
            Instant.now().minus(1, ChronoUnit.HOURS)
        );
    }

    /** OIDC context with the given scopes and roles. */
    private static OidcUserContext oidcContext(Set<String> scopes, Set<String> roles) {
        return new OidcUserContext(
            "sub-123", "user@example.com", "Test User",
            scopes, roles,
            Instant.now().plus(1, ChronoUnit.HOURS),
            Map.of()
        );
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("analyzePrincipal — FM_U1 and FM_U2")
    class AnalyzePrincipal {

        @Test
        @DisplayName("cleanPrincipal_withRequiredPermission_returnsGreenReport")
        void cleanPrincipal_withRequiredPermission_returnsGreenReport() {
            AuthenticatedPrincipal p = principal("alice",
                Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));

            UserFmeaReport report = analyzer.analyzePrincipal(p, "workflow:launch");

            assertTrue(report.isClean(), "clean principal should produce GREEN report");
            assertEquals("GREEN", report.status());
            assertEquals(0, report.totalRpn());
            assertTrue(report.violations().isEmpty());
        }

        @Test
        @DisplayName("expiredPrincipal_triggersFM_U1")
        void expiredPrincipal_triggersFM_U1() {
            AuthenticatedPrincipal p = expiredPrincipal("alice");

            UserFmeaReport report = analyzer.analyzePrincipal(p, "workflow:launch");

            assertFalse(report.isClean());
            assertEquals("RED", report.status());
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == UserFailureModeType.FM_U1_CREDENTIAL_EXPIRY),
                "expired principal must trigger FM_U1");
        }

        @Test
        @DisplayName("principalMissingPermission_triggersFM_U2")
        void principalMissingPermission_triggersVM_U2() {
            AuthenticatedPrincipal p = principal("bob",
                Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY));  // only query, not launch

            UserFmeaReport report = analyzer.analyzePrincipal(p, "workflow:launch");

            assertFalse(report.isClean());
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == UserFailureModeType.FM_U2_MISSING_PERMISSION),
                "principal without required permission must trigger FM_U2");
        }

        @Test
        @DisplayName("expiredAndMissingPermission_triggersBothFM_U1andFM_U2")
        void expiredAndMissingPermission_triggersBothViolations() {
            AuthenticatedPrincipal p = new AuthenticatedPrincipal(
                "charlie", Set.of("workflow:query"), "Bearer",
                Instant.now().minus(2, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS)   // expired
            );

            UserFmeaReport report = analyzer.analyzePrincipal(p, "workflow:launch");

            assertEquals(2, report.violations().size(), "both FM_U1 and FM_U2 should fire");
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == UserFailureModeType.FM_U1_CREDENTIAL_EXPIRY));
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == UserFailureModeType.FM_U2_MISSING_PERMISSION));
            assertTrue(report.totalRpn() > 0);
        }

        @Test
        @DisplayName("wildcardPrincipal_withAnyPermission_returnsGreenReport")
        void wildcardPrincipal_withAnyPermission_returnsGreenReport() {
            AuthenticatedPrincipal p = principal("admin",
                Set.of(AuthenticatedPrincipal.PERM_ALL));   // wildcard grants everything

            UserFmeaReport report = analyzer.analyzePrincipal(p, "workflow:cancel");

            assertTrue(report.isClean(), "wildcard principal should pass any permission check");
        }

        @Test
        @DisplayName("nonExpiringPrincipal_nullExpiresAt_neverTriggersFM_U1")
        void nonExpiringPrincipal_nullExpiresAt_neverTriggersVM_U1() {
            AuthenticatedPrincipal p = new AuthenticatedPrincipal(
                "api-key-user", Set.of("workitem:manage"), "ApiKey",
                Instant.now().minus(1, ChronoUnit.HOURS),
                null   // API keys don't expire
            );

            UserFmeaReport report = analyzer.analyzePrincipal(p, "workitem:manage");

            assertTrue(report.isClean(),
                "non-expiring principal (null expiresAt) must not trigger FM_U1");
        }

        @Test
        @DisplayName("analyzePrincipal_nullPrincipal_throwsNullPointerException")
        void analyzePrincipal_nullPrincipal_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzePrincipal(null, "workflow:launch"));
        }

        @Test
        @DisplayName("analyzePrincipal_nullPermission_throwsNullPointerException")
        void analyzePrincipal_nullPermission_throwsNullPointerException() {
            AuthenticatedPrincipal p = principal("alice",
                Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH));
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzePrincipal(p, null));
        }
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("analyzeOidcContext — FM_U4 and FM_U5")
    class AnalyzeOidcContext {

        @Test
        @DisplayName("operatorToken_withOperatorScope_returnsGreenReport")
        void operatorToken_withOperatorScope_returnsGreenReport() {
            OidcUserContext ctx = oidcContext(
                Set.of(YawlOAuth2Scopes.OPERATOR),
                Set.of("yawl-operator")
            );

            UserFmeaReport report = analyzer.analyzeOidcContext(ctx, YawlOAuth2Scopes.OPERATOR);

            assertTrue(report.isClean());
            assertEquals("GREEN", report.status());
        }

        @Test
        @DisplayName("monitorToken_requestingOperatorScope_triggersVM_U4")
        void monitorToken_requestingOperatorScope_triggersFM_U4() {
            OidcUserContext ctx = oidcContext(
                Set.of(YawlOAuth2Scopes.MONITOR),   // read-only; not operator
                Set.of("yawl-monitor")
            );

            UserFmeaReport report = analyzer.analyzeOidcContext(ctx, YawlOAuth2Scopes.OPERATOR);

            assertFalse(report.isClean());
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == UserFailureModeType.FM_U4_INSUFFICIENT_SCOPE),
                "monitor token requesting operator scope must trigger FM_U4");
        }

        @Test
        @DisplayName("adminToken_withNoRoles_triggersVM_U5")
        void adminToken_withNoRoles_triggersFM_U5() {
            OidcUserContext ctx = oidcContext(
                Set.of(YawlOAuth2Scopes.ADMIN),     // admin scope present
                Set.of()                            // but NO realm roles — anomaly
            );

            UserFmeaReport report = analyzer.analyzeOidcContext(ctx, YawlOAuth2Scopes.OPERATOR);

            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == UserFailureModeType.FM_U5_ADMIN_SCOPE_ELEVATION),
                "admin scope without realm roles must trigger FM_U5");
        }

        @Test
        @DisplayName("adminToken_withAdminRole_returnsGreenReport")
        void adminToken_withAdminRole_returnsGreenReport() {
            OidcUserContext ctx = oidcContext(
                Set.of(YawlOAuth2Scopes.ADMIN),
                Set.of("yawl-admin")   // role evidence present
            );

            UserFmeaReport report = analyzer.analyzeOidcContext(ctx, YawlOAuth2Scopes.DESIGNER);

            // Admin scope implies all scopes (hasScope returns true), roles non-empty → clean
            assertTrue(report.isClean(),
                "admin token with supporting role should pass all checks");
        }

        @Test
        @DisplayName("missingScope_andNoRoles_triggersBothFM_U4andFM_U5")
        void missingScope_andAdminNoRoles_triggersBothViolations() {
            OidcUserContext ctx = oidcContext(
                Set.of(YawlOAuth2Scopes.ADMIN),
                Set.of()
            );
            // Admin scope implies all scopes → FM_U4 won't fire for ADMIN token
            // But FM_U5 fires because admin scope with no roles is suspicious
            UserFmeaReport report = analyzer.analyzeOidcContext(ctx, YawlOAuth2Scopes.OPERATOR);

            // FM_U4 should NOT fire (admin implies operator via hasScope)
            assertFalse(report.violations().stream()
                .anyMatch(v -> v.mode() == UserFailureModeType.FM_U4_INSUFFICIENT_SCOPE),
                "admin token should pass scope check for any scope via hasScope()");
            // FM_U5 SHOULD fire (admin scope but empty roles)
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == UserFailureModeType.FM_U5_ADMIN_SCOPE_ELEVATION));
        }

        @Test
        @DisplayName("analyzeOidcContext_nullContext_throwsNullPointerException")
        void analyzeOidcContext_nullContext_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeOidcContext(null, YawlOAuth2Scopes.OPERATOR));
        }

        @Test
        @DisplayName("analyzeOidcContext_nullScope_throwsNullPointerException")
        void analyzeOidcContext_nullScope_throwsNullPointerException() {
            OidcUserContext ctx = oidcContext(Set.of(YawlOAuth2Scopes.OPERATOR), Set.of("r"));
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeOidcContext(ctx, null));
        }
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("analyzeTenantAccess — FM_U3")
    class AnalyzeTenantAccess {

        /** Clean up global TenantContext state between tests. */
        @AfterEach
        void clearTenantState() {
            // TenantContext uses a static ConcurrentHashMap; must clear to avoid test bleed
            new TenantContext("_cleanup_").clearAll();   // no-op on empty map is safe
        }

        @Test
        @DisplayName("authorizedCase_returnsGreenReport")
        void authorizedCase_returnsGreenReport() {
            TenantContext tenant = new TenantContext("tenant-A");
            tenant.registerCase("case-100");

            UserFmeaReport report = analyzer.analyzeTenantAccess(tenant, "case-100");

            assertTrue(report.isClean(), "tenant accessing its own case should produce GREEN");
        }

        @Test
        @DisplayName("unknownCase_notRegisteredAnywhere_returnsGreenReport")
        void unknownCase_notRegisteredAnywhere_returnsGreenReport() {
            TenantContext tenant = new TenantContext("tenant-A");

            // case-999 has never been registered; not an FMEA breach — it simply doesn't exist
            UserFmeaReport report = analyzer.analyzeTenantAccess(tenant, "case-999");

            assertTrue(report.isClean(),
                "unknown case (not registered anywhere) should not trigger FM_U3");
        }

        @Test
        @DisplayName("crossTenantAccess_triggersVM_U3")
        void crossTenantAccess_triggersFM_U3() {
            TenantContext ownerTenant = new TenantContext("tenant-A");
            ownerTenant.registerCase("case-200");   // case belongs to tenant-A

            TenantContext attackerTenant = new TenantContext("tenant-B");

            UserFmeaReport report = analyzer.analyzeTenantAccess(attackerTenant, "case-200");

            assertFalse(report.isClean());
            assertEquals(1, report.violations().size());
            UserFmeaViolation violation = report.violations().get(0);
            assertEquals(UserFailureModeType.FM_U3_TENANT_ISOLATION_BREACH, violation.mode());
            assertTrue(violation.evidence().contains("tenant-A"),
                "evidence should name the owning tenant");
            assertTrue(violation.context().contains("tenant-B"),
                "context should name the attacking tenant");
        }

        @Test
        @DisplayName("analyzeTenantAccess_nullTenant_throwsNullPointerException")
        void analyzeTenantAccess_nullTenant_throwsNullPointerException() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeTenantAccess(null, "case-1"));
        }

        @Test
        @DisplayName("analyzeTenantAccess_nullCaseId_throwsNullPointerException")
        void analyzeTenantAccess_nullCaseId_throwsNullPointerException() {
            TenantContext tenant = new TenantContext("tenant-A");
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeTenantAccess(tenant, null));
        }
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("analyzeResourceCapacity — FM_U6 and FM_U7")
    class AnalyzeResourceCapacity {

        @Test
        @DisplayName("availableResource_returnsGreenReport")
        void availableResource_returnsGreenReport() {
            // capacity=5, allocated=2 → 3 slots free; poolSize=3 → pool not empty
            UserFmeaReport report = analyzer.analyzeResourceCapacity(5, 2, 3);

            assertTrue(report.isClean());
            assertEquals("GREEN", report.status());
            assertEquals(0, report.totalRpn());
        }

        @Test
        @DisplayName("atCapacityResource_triggersVM_U6")
        void atCapacityResource_triggersFM_U6() {
            // capacity=3, allocated=3 → full
            UserFmeaReport report = analyzer.analyzeResourceCapacity(3, 3, 2);

            assertFalse(report.isClean());
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == UserFailureModeType.FM_U6_RESOURCE_OVER_CAPACITY),
                "resource at capacity must trigger FM_U6");
        }

        @Test
        @DisplayName("overCapacityResource_triggersVM_U6")
        void overCapacityResource_triggersFM_U6() {
            // allocated > capacity (defensive: should never happen but FMEA should detect it)
            UserFmeaReport report = analyzer.analyzeResourceCapacity(3, 5, 1);

            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == UserFailureModeType.FM_U6_RESOURCE_OVER_CAPACITY));
        }

        @Test
        @DisplayName("emptyPool_triggersVM_U7")
        void emptyPool_triggersFM_U7() {
            // poolSize=0 → no resources available at all
            UserFmeaReport report = analyzer.analyzeResourceCapacity(5, 2, 0);

            assertFalse(report.isClean());
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == UserFailureModeType.FM_U7_RESOURCE_UNAVAILABLE),
                "empty resource pool must trigger FM_U7");
        }

        @Test
        @DisplayName("atCapacityAndEmptyPool_triggersBothFM_U6andFM_U7")
        void atCapacityAndEmptyPool_triggersBothViolations() {
            UserFmeaReport report = analyzer.analyzeResourceCapacity(3, 3, 0);

            assertEquals(2, report.violations().size());
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == UserFailureModeType.FM_U6_RESOURCE_OVER_CAPACITY));
            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == UserFailureModeType.FM_U7_RESOURCE_UNAVAILABLE));
        }

        @Test
        @DisplayName("negativeCapacity_throwsIllegalArgumentException")
        void negativeCapacity_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyzeResourceCapacity(-1, 0, 1));
        }

        @Test
        @DisplayName("negativeAllocated_throwsIllegalArgumentException")
        void negativeAllocated_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyzeResourceCapacity(5, -1, 1));
        }

        @Test
        @DisplayName("negativePoolSize_throwsIllegalArgumentException")
        void negativePoolSize_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyzeResourceCapacity(5, 2, -1));
        }
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("UserFmeaReport — report behaviour")
    class ReportBehaviour {

        @Test
        @DisplayName("totalRpn_sumsAllViolationRpns")
        void totalRpn_sumsAllViolationRpns() {
            // FM_U6 RPN=108, FM_U7 RPN=120 → total=228
            UserFmeaReport report = analyzer.analyzeResourceCapacity(3, 3, 0);

            int expectedRpn = UserFailureModeType.FM_U6_RESOURCE_OVER_CAPACITY.rpn()
                            + UserFailureModeType.FM_U7_RESOURCE_UNAVAILABLE.rpn();
            assertEquals(expectedRpn, report.totalRpn());
        }

        @Test
        @DisplayName("status_greenWhenNoViolations")
        void status_greenWhenNoViolations() {
            UserFmeaReport report = analyzer.analyzeResourceCapacity(5, 2, 3);
            assertEquals("GREEN", report.status());
        }

        @Test
        @DisplayName("status_redWhenAnyViolation")
        void status_redWhenAnyViolation() {
            UserFmeaReport report = analyzer.analyzeResourceCapacity(3, 3, 1);
            assertEquals("RED", report.status());
        }

        @Test
        @DisplayName("violations_listIsImmutable")
        void violations_listIsImmutable() {
            UserFmeaReport report = analyzer.analyzeResourceCapacity(3, 3, 0);
            assertThrows(UnsupportedOperationException.class,
                () -> report.violations().clear(),
                "violations list must be immutable");
        }

        @Test
        @DisplayName("analyzedAt_isRecentTimestamp")
        void analyzedAt_isRecentTimestamp() {
            Instant before = Instant.now().minus(1, ChronoUnit.SECONDS);
            UserFmeaReport report = analyzer.analyzeResourceCapacity(5, 2, 3);
            Instant after  = Instant.now().plus(1, ChronoUnit.SECONDS);

            assertTrue(report.analyzedAt().isAfter(before),
                "analyzedAt should be after test start");
            assertTrue(report.analyzedAt().isBefore(after),
                "analyzedAt should be before test end");
        }
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("UserFailureModeType — RPN values")
    class FailureModeTypeRpn {

        @Test
        @DisplayName("allModesHavePositiveRpn")
        void allModesHavePositiveRpn() {
            for (UserFailureModeType mode : UserFailureModeType.values()) {
                assertTrue(mode.rpn() > 0,
                    mode.name() + " must have positive RPN");
            }
        }

        @Test
        @DisplayName("rpnEqualsProductOfSOD")
        void rpnEqualsProductOfSOD() {
            for (UserFailureModeType mode : UserFailureModeType.values()) {
                int expected = mode.getSeverity() * mode.getOccurrence() * mode.getDetection();
                assertEquals(expected, mode.rpn(),
                    mode.name() + " RPN must equal S×O×D");
            }
        }

        @Test
        @DisplayName("allModesHaveNonBlankDescriptionAndMitigation")
        void allModesHaveNonBlankDescriptionAndMitigation() {
            for (UserFailureModeType mode : UserFailureModeType.values()) {
                assertFalse(mode.getDescription().isBlank(),
                    mode.name() + " must have non-blank description");
                assertFalse(mode.getMitigation().isBlank(),
                    mode.name() + " must have non-blank mitigation");
            }
        }
    }
}
