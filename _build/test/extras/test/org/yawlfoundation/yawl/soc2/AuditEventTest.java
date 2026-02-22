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
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.yawlfoundation.yawl.logging.table.YAuditEvent;
import org.yawlfoundation.yawl.logging.table.YAuditEvent.Action;

import java.util.EnumSet;
import java.util.Set;

/**
 * SOC2 CC7 - Audit Event Logging Tests.
 *
 * <p>Tests map to SOC 2 Trust Service Criteria:
 * CC7.2 - The entity monitors system components to detect anomalies indicating
 *         malicious acts or security control failures.
 * CC7.3 - The entity evaluates security events to determine whether they could
 *         or have resulted in a failure of the entity to meet its objectives.
 *
 * <p>Covers:
 * <ul>
 *   <li>All SOC2-required audit event types are present</li>
 *   <li>logon event captures username and timestamp</li>
 *   <li>logoff event captures username and timestamp</li>
 *   <li>invalid (bad password) event captures username</li>
 *   <li>unknown (unknown user) event captures username</li>
 *   <li>shutdown event captures username</li>
 *   <li>expired event captures username</li>
 *   <li>Timestamp is set on construction (not zero)</li>
 *   <li>Timestamp is in expected range</li>
 *   <li>Default constructor works for ORM/Hibernate</li>
 *   <li>equals() uses ID-based comparison</li>
 *   <li>hashCode() is stable</li>
 *   <li>All Action enum values have names for logging</li>
 * </ul>
 *
 * <p>Chicago TDD: real YAuditEvent with real timestamp.
 *
 * @author YAWL Foundation - SOC2 Compliance 2026-02-17
 */
public class AuditEventTest extends TestCase {

    public AuditEventTest(String name) {
        super(name);
    }

    // =========================================================================
    // CC7.2 - Required audit actions present
    // =========================================================================

    /**
     * SOC2 CC7.2: All security-relevant event types must be present in the Action enum.
     * Each maps to a SOC2 control: logon/logoff track sessions, invalid/unknown track
     * attack attempts, shutdown/expired track session lifecycle.
     */
    public void testAllRequiredAuditActionsExist() {
        Set<Action> required = EnumSet.of(
                Action.logon,    // CC7.2: successful authentication
                Action.logoff,   // CC7.2: intentional logout
                Action.invalid,  // CC7.2: failed authentication (wrong password)
                Action.unknown,  // CC7.2: access attempt by unknown entity
                Action.shutdown, // CC7.3: session terminated by system shutdown
                Action.expired   // CC7.3: session timed out (idle limit enforced)
        );

        for (Action a : required) {
            // If the enum value exists, this loop will find it; if not, test fails
            boolean found = false;
            for (Action existing : Action.values()) {
                if (existing == a) {
                    found = true;
                    break;
                }
            }
            assertTrue("SOC2 required audit action must exist: " + a.name(), found);
        }
    }

    // =========================================================================
    // CC7.2 - logon event
    // =========================================================================

    /**
     * SOC2 CC7.2: logon event must capture username and event name.
     */
    public void testLogonEventCapturesUsername() {
        long before = System.currentTimeMillis();
        YAuditEvent event = new YAuditEvent("alice", Action.logon);
        long after = System.currentTimeMillis();

        assertEquals("logon event must capture username", "alice", event.get_username());
        assertEquals("logon event must have 'logon' as event name", "logon", event.get_event());
        assertTrue("logon timestamp must be >= test start time", event.get_timeStamp() >= before);
        assertTrue("logon timestamp must be <= test end time", event.get_timeStamp() <= after);
    }

    // =========================================================================
    // CC7.2 - logoff event
    // =========================================================================

    /**
     * SOC2 CC7.2: logoff event must capture username.
     */
    public void testLogoffEventCapturesUsername() {
        YAuditEvent event = new YAuditEvent("bob", Action.logoff);
        assertEquals("bob", event.get_username());
        assertEquals("logoff", event.get_event());
    }

    // =========================================================================
    // CC7.2 - invalid event (bad password / attack attempt)
    // =========================================================================

    /**
     * SOC2 CC7.2: invalid event must record the attempted username.
     * This enables detection of brute force attacks.
     */
    public void testInvalidEventRecordsAttemptedUsername() {
        YAuditEvent event = new YAuditEvent("attacker", Action.invalid);
        assertEquals("invalid event must record attempted username", "attacker", event.get_username());
        assertEquals("invalid", event.get_event());
        assertTrue("invalid event timestamp must be non-zero", event.get_timeStamp() > 0);
    }

    // =========================================================================
    // CC7.2 - unknown event (unknown user attempt)
    // =========================================================================

    /**
     * SOC2 CC7.2: unknown event must record the unknown username for security monitoring.
     */
    public void testUnknownEventRecordsUsername() {
        YAuditEvent event = new YAuditEvent("unknown-entity", Action.unknown);
        assertEquals("unknown-entity", event.get_username());
        assertEquals("unknown", event.get_event());
    }

    // =========================================================================
    // CC7.3 - shutdown event
    // =========================================================================

    /**
     * SOC2 CC7.3: shutdown event must capture username for session audit trail.
     */
    public void testShutdownEventCapturesUsername() {
        YAuditEvent event = new YAuditEvent("service-account", Action.shutdown);
        assertEquals("service-account", event.get_username());
        assertEquals("shutdown", event.get_event());
    }

    // =========================================================================
    // CC7.3 - expired event
    // =========================================================================

    /**
     * SOC2 CC7.3: expired event must capture username for session lifecycle audit.
     * Session expiry enforces idle timeout controls.
     */
    public void testExpiredEventCapturesUsername() {
        YAuditEvent event = new YAuditEvent("idle-user", Action.expired);
        assertEquals("idle-user", event.get_username());
        assertEquals("expired", event.get_event());
    }

    // =========================================================================
    // CC7.2 - Timestamp correctness
    // =========================================================================

    /**
     * SOC2 CC7.2: Timestamp must be set at construction time (not zero/null).
     * Audit records without timestamps are useless for incident investigation.
     */
    public void testTimestampIsSetAtConstruction() {
        long before = System.currentTimeMillis();
        YAuditEvent event = new YAuditEvent("user", Action.logon);
        long after = System.currentTimeMillis();

        assertTrue("Audit event timestamp must be > 0", event.get_timeStamp() > 0);
        assertTrue("Timestamp must be in range [before, after]",
                event.get_timeStamp() >= before && event.get_timeStamp() <= after);
    }

    /**
     * SOC2 CC7.2: Timestamp must be in milliseconds (not seconds or nanoseconds).
     * A value between 2020-01-01 and 2100-01-01 confirms epoch milliseconds.
     */
    public void testTimestampIsEpochMilliseconds() {
        YAuditEvent event = new YAuditEvent("user", Action.logon);
        long ts = event.get_timeStamp();

        // 2020-01-01 in epoch milliseconds
        long MIN_EPOCH_MS = 1_577_836_800_000L;
        // 2100-01-01 in epoch milliseconds
        long MAX_EPOCH_MS = 4_102_444_800_000L;

        assertTrue("Timestamp looks like epoch milliseconds (>= 2020)",
                ts >= MIN_EPOCH_MS);
        assertTrue("Timestamp looks like epoch milliseconds (<= 2100)",
                ts <= MAX_EPOCH_MS);
    }

    // =========================================================================
    // CC7.2 - Default constructor for ORM
    // =========================================================================

    /**
     * SOC2 CC7.2: Default constructor must exist for Hibernate/JPA persistence.
     * Without ORM compatibility, audit events cannot be persisted to the database.
     */
    public void testDefaultConstructorExistsForOrm() {
        YAuditEvent event = new YAuditEvent();
        assertNotNull("Default constructor must produce non-null instance", event);
        // ID defaults to 0 from long primitive
        assertEquals("Default id must be 0", 0L, event.get_id());
    }

    // =========================================================================
    // CC7.2 - Setters for ORM reconstitution
    // =========================================================================

    /**
     * SOC2 CC7.2: All setters must correctly update the audit record fields,
     * enabling Hibernate to reconstitute persisted audit events.
     */
    public void testSettersUpdateFields() {
        YAuditEvent event = new YAuditEvent();
        event.set_id(42L);
        event.set_username("test-user");
        event.set_event("logon");
        event.set_timeStamp(1700000000000L);

        assertEquals(42L, event.get_id());
        assertEquals("test-user", event.get_username());
        assertEquals("logon", event.get_event());
        assertEquals(1700000000000L, event.get_timeStamp());
    }

    // =========================================================================
    // Object contract
    // =========================================================================

    /**
     * SOC2 CC7.2: equals() uses ID-based comparison, enabling deduplication
     * in the audit persistence layer.
     */
    public void testEqualsUsesIdComparison() {
        YAuditEvent a = new YAuditEvent();
        a.set_id(1L);
        a.set_username("user1");

        YAuditEvent b = new YAuditEvent();
        b.set_id(1L);
        b.set_username("user2"); // different username, same id

        assertEquals("Events with same id must be equal", a, b);
    }

    public void testEventsWithDifferentIdsAreNotEqual() {
        YAuditEvent a = new YAuditEvent();
        a.set_id(1L);

        YAuditEvent b = new YAuditEvent();
        b.set_id(2L);

        assertFalse("Events with different ids must not be equal", a.equals(b));
    }

    public void testHashCodeIsStable() {
        YAuditEvent event = new YAuditEvent();
        event.set_id(99L);

        int hc1 = event.hashCode();
        int hc2 = event.hashCode();
        assertEquals("hashCode must be stable", hc1, hc2);
    }

    // =========================================================================
    // CC7.2 - Action enum name resolution
    // =========================================================================

    /**
     * SOC2 CC7.2: All Action enum values must have non-empty names,
     * because these names are written to the audit log.
     */
    public void testAllActionEnumValuesHaveNames() {
        for (Action a : Action.values()) {
            assertNotNull("Action enum value must have non-null name", a.name());
            assertFalse("Action enum value must have non-empty name", a.name().isEmpty());
            // Verify the name is lowercase for consistent log filtering
            assertEquals("Action name must be lowercase for consistent filtering",
                    a.name().toLowerCase(), a.name());
        }
    }

    // =========================================================================
    // Test suite
    // =========================================================================

    public static Test suite() {
        return new TestSuite(AuditEventTest.class);
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
