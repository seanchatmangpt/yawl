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
package org.yawlfoundation.yawl.erlang.integration;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.erlang.bridge.ErlangNode;
import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.error.OtpNodeUnavailableException;
import org.yawlfoundation.yawl.erlang.lifecycle.OtpInstallationVerifier;
import org.yawlfoundation.yawl.erlang.lifecycle.OtpNodeLifecycleManager;
import org.yawlfoundation.yawl.erlang.processmining.ErlangBridge;
import org.yawlfoundation.yawl.erlang.term.*;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests verifying round-trip ETF encoding through a real OTP node.
 * Each test calls yawl_echo:echo(Term) via RPC and asserts the returned term type.
 * All tests skip gracefully when OTP 28 is not installed.
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ErlangBridgeRoundTripTest {

    private static final String NODE_NAME = "yawl_roundtrip@127.0.0.1";
    private static final String COOKIE = "test_cookie";

    private static OtpNodeLifecycleManager lifecycle;
    private static ErlangNode node;

    @BeforeAll
    static void startOtp() throws OtpNodeUnavailableException, ErlangConnectionException {
        assumeTrue(OtpInstallationVerifier.isOtp28Available(), "OTP 28 not installed — skipping integration tests");
        lifecycle = OtpNodeLifecycleManager.forNode(NODE_NAME, COOKIE);
        lifecycle.start();
        node = new ErlangNode("yawl_java_rt@127.0.0.1");
        node.connect(NODE_NAME, COOKIE);
    }

    @AfterAll
    static void stopOtp() {
        if (node != null) node.close();
        if (lifecycle != null) lifecycle.close();
    }

    @Test
    @Order(1)
    void roundtrip_atom() throws ErlangRpcException {
        ErlTerm result = node.rpc("yawl_echo", "echo", List.of(new ErlAtom("hello")));
        assertInstanceOf(ErlAtom.class, result);
        assertEquals("hello", ((ErlAtom) result).value());
    }

    @Test
    @Order(2)
    void roundtrip_integer() throws ErlangRpcException {
        ErlTerm result = node.rpc("yawl_echo", "echo", List.of(new ErlInteger(42)));
        assertInstanceOf(ErlInteger.class, result);
        assertEquals(BigInteger.valueOf(42), ((ErlInteger) result).value());
    }

    @Test
    @Order(3)
    void roundtrip_float() throws ErlangRpcException {
        ErlTerm result = node.rpc("yawl_echo", "echo", List.of(new ErlFloat(3.14)));
        assertInstanceOf(ErlFloat.class, result);
        assertEquals(3.14, ((ErlFloat) result).value(), 1e-6);
    }

    @Test
    @Order(4)
    void roundtrip_binary() throws ErlangRpcException {
        byte[] data = {1, 2, 3, 4, 5};
        ErlTerm result = node.rpc("yawl_echo", "echo", List.of(new ErlBinary(data)));
        assertInstanceOf(ErlBinary.class, result);
        assertArrayEquals(data, ((ErlBinary) result).data());
    }

    @Test
    @Order(5)
    void roundtrip_tuple() throws ErlangRpcException {
        ErlTuple input = new ErlTuple(List.of(new ErlAtom("ok"), new ErlInteger(1)));
        ErlTerm result = node.rpc("yawl_echo", "echo", List.of(input));
        assertInstanceOf(ErlTuple.class, result);
        ErlTuple tuple = (ErlTuple) result;
        assertEquals(2, tuple.elements().size());
        assertInstanceOf(ErlAtom.class, tuple.elements().get(0));
    }

    @Test
    @Order(6)
    void roundtrip_list() throws ErlangRpcException {
        ErlList input = new ErlList(List.of(new ErlAtom("a"), new ErlAtom("b")));
        ErlTerm result = node.rpc("yawl_echo", "echo", List.of(input));
        assertInstanceOf(ErlList.class, result);
        ErlList list = (ErlList) result;
        assertEquals(2, list.elements().size());
    }

    @Test
    @Order(7)
    void roundtrip_map() throws ErlangRpcException {
        Map<ErlTerm, ErlTerm> entries = new HashMap<>();
        entries.put(new ErlAtom("key"), new ErlAtom("value"));
        ErlMap input = new ErlMap(entries);
        ErlTerm result = node.rpc("yawl_echo", "echo", List.of(input));
        assertInstanceOf(ErlMap.class, result);
    }

    @Test
    @Order(8)
    void roundtrip_nil() throws ErlangRpcException {
        ErlTerm result = node.rpc("yawl_echo", "echo", List.of(ErlNil.INSTANCE));
        assertInstanceOf(ErlNil.class, result);
    }
}
