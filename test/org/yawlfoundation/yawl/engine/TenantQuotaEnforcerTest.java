/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.observability.SLOAlertManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Chicago-style integration tests for TenantQuotaEnforcer.
 *
 * Tests real quota enforcement with actual SLOAlertManager integration.
 */
class TenantQuotaEnforcerTest {

    private TenantQuotaEnforcer quotaEnforcer;
    private SLOAlertManager sloAlertManager;

    @BeforeEach
    void setUp() {
        sloAlertManager = mock(SLOAlertManager.class);

        // Simple settings provider with defaults
        TenantQuotaEnforcer.QuotaSettingsProvider settingsProvider =
                new TenantQuotaEnforcer.QuotaSettingsProvider() {
                    @Override
                    public long getHardLimit(String tenantId) {
                        // customer-123 gets 100 agents, others get 10,000
                        return "customer-123".equals(tenantId) ? 100L : 10_000L;
                    }

                    @Override
                    public double getSoftLimitPercentage(String tenantId) {
                        return 0.80;  // 80% of hard limit
                    }
                };

        quotaEnforcer = new TenantQuotaEnforcer(sloAlertManager, settingsProvider);
    }

    @Test
    @DisplayName("Should allow dispatch when below hard limit")
    void testAllowDispatchBelowHardLimit() {
        // Act
        quotaEnforcer.checkAndIncrement("tenant-1");

        // Assert
        assertEquals(1, quotaEnforcer.getActiveAgentCount("tenant-1"));
        verify(sloAlertManager, never()).alertQuotaThreshold(anyString(), anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Should throw exception when hard limit exceeded")
    void testThrowExceptionOnHardLimitExceeded() {
        String tenantId = "customer-123";

        // Fill up to hard limit (100 agents)
        for (int i = 0; i < 100; i++) {
            quotaEnforcer.checkAndIncrement(tenantId);
        }

        // Act & Assert: 101st should throw
        assertThrows(
                TenantQuotaEnforcer.QuotaExceededException.class,
                () -> quotaEnforcer.checkAndIncrement(tenantId),
                "Should throw QuotaExceededException when hard limit exceeded"
        );

        // Verify quota state is correct
        assertEquals(100, quotaEnforcer.getActiveAgentCount(tenantId));
        assertEquals(100, quotaEnforcer.getHardLimit(tenantId));
    }

    @Test
    @DisplayName("Should alert at soft limit but allow dispatch")
    void testAlertAtSoftLimitButAllowDispatch() {
        String tenantId = "customer-123";
        long hardLimit = 100;
        long softLimit = 80;  // 80% of 100

        // Fill up to soft limit (80 agents)
        for (int i = 0; i < softLimit; i++) {
            quotaEnforcer.checkAndIncrement(tenantId);
        }

        // Verify no alert yet (still one below)
        verify(sloAlertManager, never()).alertQuotaThreshold(anyString(), anyLong(), anyLong(), anyLong());

        // Act: Dispatch one more to reach soft limit
        quotaEnforcer.checkAndIncrement(tenantId);

        // Assert: Alert should have been triggered exactly once
        verify(sloAlertManager, times(1)).alertQuotaThreshold(
                eq(tenantId),
                eq(81L),
                eq(hardLimit),
                eq(softLimit)
        );

        // Verify count is correct
        assertEquals(81, quotaEnforcer.getActiveAgentCount(tenantId));
    }

    @Test
    @DisplayName("Should not re-alert after soft limit already reached")
    void testSoftLimitAlertOnlyOnce() {
        String tenantId = "customer-123";
        long softLimit = 80;

        // Fill to soft limit + 1
        for (int i = 0; i < softLimit + 1; i++) {
            quotaEnforcer.checkAndIncrement(tenantId);
        }

        // Reset the mock to clear the first alert
        reset(sloAlertManager);

        // Act: Add more agents while still above soft limit
        for (int i = 0; i < 5; i++) {
            quotaEnforcer.checkAndIncrement(tenantId);
        }

        // Assert: No additional alerts should be generated
        verify(sloAlertManager, never()).alertQuotaThreshold(anyString(), anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Should reset soft limit alert when below threshold")
    void testResetSoftLimitAlertWhenDecremented() {
        String tenantId = "customer-123";
        long softLimit = 80;

        // Fill to soft limit
        for (int i = 0; i < softLimit; i++) {
            quotaEnforcer.checkAndIncrement(tenantId);
        }

        // Reset mock
        reset(sloAlertManager);

        // Decrement back below soft limit
        for (int i = 0; i < 5; i++) {
            quotaEnforcer.decrement(tenantId);
        }

        // Act: Increment again to reach soft limit
        quotaEnforcer.checkAndIncrement(tenantId);
        quotaEnforcer.checkAndIncrement(tenantId);
        quotaEnforcer.checkAndIncrement(tenantId);
        quotaEnforcer.checkAndIncrement(tenantId);
        quotaEnforcer.checkAndIncrement(tenantId);

        // Assert: Alert should be triggered again (not suppressed)
        verify(sloAlertManager, times(1)).alertQuotaThreshold(anyString(), anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Should decrement active agent count correctly")
    void testDecrementAgentCount() {
        String tenantId = "tenant-1";

        // Add 10 agents
        for (int i = 0; i < 10; i++) {
            quotaEnforcer.checkAndIncrement(tenantId);
        }
        assertEquals(10, quotaEnforcer.getActiveAgentCount(tenantId));

        // Remove 3
        for (int i = 0; i < 3; i++) {
            quotaEnforcer.decrement(tenantId);
        }

        assertEquals(7, quotaEnforcer.getActiveAgentCount(tenantId));
    }

    @Test
    @DisplayName("Should return correct usage percentage")
    void testGetUsagePercentage() {
        String tenantId = "customer-123";
        long hardLimit = 100;

        // Add 50 agents (50%)
        for (int i = 0; i < 50; i++) {
            quotaEnforcer.checkAndIncrement(tenantId);
        }

        assertEquals(0.50, quotaEnforcer.getUsagePercentage(tenantId), 0.01);

        // Add 30 more (80%)
        for (int i = 0; i < 30; i++) {
            quotaEnforcer.checkAndIncrement(tenantId);
        }

        assertEquals(0.80, quotaEnforcer.getUsagePercentage(tenantId), 0.01);
    }

    @Test
    @DisplayName("Should handle null tenant ID gracefully")
    void testNullTenantIdThrowsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> quotaEnforcer.checkAndIncrement(null)
        );
    }

    @Test
    @DisplayName("Should handle empty tenant ID gracefully")
    void testEmptyTenantIdThrowsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> quotaEnforcer.checkAndIncrement("")
        );
    }

    @Test
    @DisplayName("Should support multiple independent tenants")
    void testIndependentTenantQuotas() {
        String tenant1 = "tenant-1";
        String tenant2 = "tenant-2";

        // Fill tenant-1 to 50
        for (int i = 0; i < 50; i++) {
            quotaEnforcer.checkAndIncrement(tenant1);
        }

        // Fill tenant-2 to 100
        for (int i = 0; i < 100; i++) {
            quotaEnforcer.checkAndIncrement(tenant2);
        }

        // Verify independent counts
        assertEquals(50, quotaEnforcer.getActiveAgentCount(tenant1));
        assertEquals(100, quotaEnforcer.getActiveAgentCount(tenant2));

        // Verify both are below their default hard limit (10,000)
        assertFalse(quotaEnforcer.getUsagePercentage(tenant1) >= 1.0);
        assertFalse(quotaEnforcer.getUsagePercentage(tenant2) >= 1.0);
    }

    @Test
    @DisplayName("Should reset tenant quota state")
    void testResetTenant() {
        String tenantId = "tenant-to-reset";

        // Add some agents
        for (int i = 0; i < 50; i++) {
            quotaEnforcer.checkAndIncrement(tenantId);
        }

        assertEquals(50, quotaEnforcer.getActiveAgentCount(tenantId));

        // Act: Reset the tenant
        quotaEnforcer.resetTenant(tenantId);

        // Assert: Count should be back to 0
        assertEquals(0, quotaEnforcer.getActiveAgentCount(tenantId));

        // And should be able to add again
        quotaEnforcer.checkAndIncrement(tenantId);
        assertEquals(1, quotaEnforcer.getActiveAgentCount(tenantId));
    }

    @Test
    @DisplayName("Should get correct hard limit from settings provider")
    void testGetHardLimitFromProvider() {
        assertEquals(100, quotaEnforcer.getHardLimit("customer-123"));
        assertEquals(10_000, quotaEnforcer.getHardLimit("other-tenant"));
        assertEquals(10_000, quotaEnforcer.getHardLimit("default-tenant"));
    }
}
