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
package org.yawlfoundation.yawl.erlang.bridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.erlang.generated.ei_h;
import org.yawlfoundation.yawl.erlang.term.ErlAtom;
import org.yawlfoundation.yawl.erlang.term.ErlTerm;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ErlMessage} record.
 * Tests message type constants and factory validation.
 */
@Tag("unit")
@DisplayName("ErlMessage tests")
class ErlMessageTest {

    @Test
    @DisplayName("ERL_MSG constant equals 1")
    void erl_msg_constant_is_1() {
        assertEquals(1, ErlMessage.ERL_MSG);
    }

    @Test
    @DisplayName("ERL_TICK constant equals 2")
    void erl_tick_constant_is_2() {
        assertEquals(2, ErlMessage.ERL_TICK);
    }

    @Test
    @DisplayName("ERL_MSG matches ei_h.ERL_MSG")
    void erl_msg_matches_ei_h() {
        assertEquals(ei_h.ERL_MSG, ErlMessage.ERL_MSG);
    }

    @Test
    @DisplayName("ERL_TICK matches ei_h.ERL_TICK")
    void erl_tick_matches_ei_h() {
        assertEquals(ei_h.ERL_TICK, ErlMessage.ERL_TICK);
    }

    @Test
    @DisplayName("fromMsgType(1) creates real message")
    void fromMsgType_1_isRealMessage() {
        ErlTerm payload = new ErlAtom("test");
        ErlMessage msg = ErlMessage.fromMsgType(ErlMessage.ERL_MSG, payload);

        assertEquals(ErlMessage.ERL_MSG, msg.type());
        assertSame(payload, msg.payload());
    }

    @Test
    @DisplayName("fromMsgType(2) creates tick message")
    void fromMsgType_2_isTick() {
        ErlTerm payload = new ErlAtom("tick");
        ErlMessage msg = ErlMessage.fromMsgType(ErlMessage.ERL_TICK, payload);

        assertEquals(ErlMessage.ERL_TICK, msg.type());
        assertSame(payload, msg.payload());
    }

    @Test
    @DisplayName("isRealMessage() returns true for ERL_MSG")
    void isRealMessage_true_for_msg() {
        ErlMessage msg = ErlMessage.fromMsgType(ErlMessage.ERL_MSG, new ErlAtom("test"));
        assertTrue(msg.isRealMessage());
    }

    @Test
    @DisplayName("isRealMessage() returns false for ERL_TICK")
    void isRealMessage_false_for_tick() {
        ErlMessage msg = ErlMessage.fromMsgType(ErlMessage.ERL_TICK, new ErlAtom("tick"));
        assertFalse(msg.isRealMessage());
    }

    @Test
    @DisplayName("isTick() returns false for ERL_MSG")
    void isTick_false_for_realMessage() {
        ErlMessage msg = ErlMessage.fromMsgType(ErlMessage.ERL_MSG, new ErlAtom("test"));
        assertFalse(msg.isTick());
    }

    @Test
    @DisplayName("isTick() returns true for ERL_TICK")
    void isTick_true_for_tick() {
        ErlMessage msg = ErlMessage.fromMsgType(ErlMessage.ERL_TICK, new ErlAtom("tick"));
        assertTrue(msg.isTick());
    }

    @Test
    @DisplayName("fromMsgType(-1) throws IllegalArgumentException")
    void fromMsgType_negative_throwsIllegalArgument() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ErlMessage.fromMsgType(-1, new ErlAtom("test"))
        );
        assertTrue(ex.getMessage().contains("Invalid message type"));
    }

    @Test
    @DisplayName("fromMsgType(3) throws IllegalArgumentException")
    void fromMsgType_3_throwsIllegalArgument() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ErlMessage.fromMsgType(3, new ErlAtom("test"))
        );
        assertTrue(ex.getMessage().contains("Invalid message type"));
    }

    @Test
    @DisplayName("fromMsgType(0) throws IllegalArgumentException")
    void fromMsgType_0_throwsIllegalArgument() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ErlMessage.fromMsgType(0, new ErlAtom("test"))
        );
        assertTrue(ex.getMessage().contains("Invalid message type"));
    }

    @Test
    @DisplayName("Record equality based on type and payload")
    void record_equality_value_based() {
        ErlAtom payload = new ErlAtom("same");
        ErlMessage msg1 = ErlMessage.fromMsgType(ErlMessage.ERL_MSG, payload);
        ErlMessage msg2 = ErlMessage.fromMsgType(ErlMessage.ERL_MSG, payload);

        assertEquals(msg1, msg2);
        assertEquals(msg1.hashCode(), msg2.hashCode());
    }

    @Test
    @DisplayName("Record inequality for different types")
    void record_inequality_different_type() {
        ErlAtom payload = new ErlAtom("test");
        ErlMessage msg1 = ErlMessage.fromMsgType(ErlMessage.ERL_MSG, payload);
        ErlMessage msg2 = ErlMessage.fromMsgType(ErlMessage.ERL_TICK, payload);

        assertNotEquals(msg1, msg2);
    }

    @Test
    @DisplayName("toString() includes type and payload")
    void toString_includes_fields() {
        ErlMessage msg = ErlMessage.fromMsgType(ErlMessage.ERL_MSG, new ErlAtom("test"));
        String str = msg.toString();

        assertTrue(str.contains("type") || str.contains("1"));
        assertTrue(str.contains("payload") || str.contains("test"));
    }
}
