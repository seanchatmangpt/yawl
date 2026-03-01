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
package org.yawlfoundation.yawl.erlang.hotreload;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.resilience.OtpCircuitBreaker;
import org.yawlfoundation.yawl.erlang.term.ErlAtom;
import org.yawlfoundation.yawl.erlang.term.ErlTerm;
import org.yawlfoundation.yawl.erlang.term.ErlTuple;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HotReloadServiceImpl}.
 *
 * <p>Uses a pure-Java {@link OtpCircuitBreaker.ErlRpcCallable} lambda instead
 * of a live OTP node — no Erlang process required.</p>
 */
class HotReloadServiceImplTest {

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    /** Returns a callable that simulates code:load_binary/3 returning {module, Name}. */
    static OtpCircuitBreaker.ErlRpcCallable successCallable() {
        return (module, function, args) -> {
            if ("code".equals(module) && "load_binary".equals(function)) {
                // Simulate {module, 'yawl_order'}
                String moduleName = args.isEmpty() ? "unknown"
                        : (args.get(0) instanceof ErlAtom a ? a.value() : "unknown");
                return new ErlTuple(List.of(new ErlAtom("module"), new ErlAtom(moduleName)));
            }
            if ("code".equals(module) && "purge".equals(function)) {
                return new ErlAtom("true");
            }
            throw new ErlangRpcException(module, function, "unexpected call");
        };
    }

    /** Returns a callable that simulates code:load_binary/3 returning {error, bad_binary}. */
    static OtpCircuitBreaker.ErlRpcCallable errorCallable() {
        return (module, function, args) -> {
            if ("code".equals(module) && "purge".equals(function)) {
                return new ErlAtom("false");
            }
            return new ErlTuple(List.of(new ErlAtom("error"), new ErlAtom("bad_binary")));
        };
    }

    static byte[] beamBytes(String tag) {
        return ("BEAM-" + tag).getBytes();
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void loadModule_returnsVersionWithCorrectFields() throws ErlangRpcException {
        HotReloadServiceImpl service = new HotReloadServiceImpl(successCallable(), 5);
        byte[] beam = beamBytes("v1");

        ModuleVersion version = service.loadModule("yawl_order", beam);

        assertEquals("yawl_order", version.moduleName());
        assertEquals(1, version.version());
        assertNotNull(version.loadedAt());
        assertArrayEquals(beam, version.beamBytecode());
    }

    @Test
    void loadModule_incrementsVersionOnSuccessiveCalls() throws ErlangRpcException {
        HotReloadServiceImpl service = new HotReloadServiceImpl(successCallable(), 5);

        ModuleVersion v1 = service.loadModule("yawl_route", beamBytes("v1"));
        ModuleVersion v2 = service.loadModule("yawl_route", beamBytes("v2"));
        ModuleVersion v3 = service.loadModule("yawl_route", beamBytes("v3"));

        assertEquals(1, v1.version());
        assertEquals(2, v2.version());
        assertEquals(3, v3.version());
    }

    @Test
    void loadModule_throwsOnOtpError() {
        HotReloadServiceImpl service = new HotReloadServiceImpl(errorCallable(), 5);

        assertThrows(ErlangRpcException.class,
                () -> service.loadModule("bad_module", beamBytes("broken")),
                "OTP error reply must be translated to ErlangRpcException");
    }

    @Test
    void currentVersion_returnsEmptyForUnknownModule() {
        HotReloadServiceImpl service = new HotReloadServiceImpl(successCallable(), 5);
        assertTrue(service.currentVersion("unknown_module").isEmpty());
    }

    @Test
    void currentVersion_returnsLatestAfterLoad() throws ErlangRpcException {
        HotReloadServiceImpl service = new HotReloadServiceImpl(successCallable(), 5);

        service.loadModule("yawl_audit", beamBytes("v1"));
        service.loadModule("yawl_audit", beamBytes("v2"));

        Optional<ModuleVersion> current = service.currentVersion("yawl_audit");
        assertTrue(current.isPresent());
        assertEquals(2, current.get().version());
    }

    @Test
    void versionHistory_returnsNewestFirst() throws ErlangRpcException {
        HotReloadServiceImpl service = new HotReloadServiceImpl(successCallable(), 5);

        service.loadModule("yawl_log", beamBytes("v1"));
        service.loadModule("yawl_log", beamBytes("v2"));
        service.loadModule("yawl_log", beamBytes("v3"));

        List<ModuleVersion> history = service.versionHistory("yawl_log");

        assertEquals(3, history.size());
        // Newest first: version 3 is first
        assertEquals(3, history.get(0).version());
        assertEquals(2, history.get(1).version());
        assertEquals(1, history.get(2).version());
    }

    @Test
    void ringBuffer_evictsOldestBeyondDepth() throws ErlangRpcException {
        int depth = 3;
        HotReloadServiceImpl service = new HotReloadServiceImpl(successCallable(), depth);

        for (int i = 1; i <= 5; i++) {
            service.loadModule("yawl_ring", beamBytes("v" + i));
        }

        List<ModuleVersion> history = service.versionHistory("yawl_ring");
        assertEquals(depth, history.size(), "Ring buffer must cap at depth=" + depth);
        assertEquals(5, history.get(0).version(), "Current must be v5");
        assertEquals(3, history.get(depth - 1).version(), "Oldest kept must be v3");
    }

    @Test
    void rollback_restoresPreviousVersion() throws ErlangRpcException {
        HotReloadServiceImpl service = new HotReloadServiceImpl(successCallable(), 5);

        service.loadModule("yawl_core", beamBytes("v1"));
        service.loadModule("yawl_core", beamBytes("v2"));

        service.rollback("yawl_core");

        Optional<ModuleVersion> current = service.currentVersion("yawl_core");
        assertTrue(current.isPresent());
        assertEquals(1, current.get().version(),
                "After rollback, current version must be v1");
    }

    @Test
    void rollback_throwsWhenNoPreviousVersion() throws ErlangRpcException {
        HotReloadServiceImpl service = new HotReloadServiceImpl(successCallable(), 5);
        service.loadModule("yawl_solo", beamBytes("v1"));

        assertThrows(IllegalStateException.class,
                () -> service.rollback("yawl_solo"),
                "Rollback with only one version must throw IllegalStateException");
    }

    @Test
    void rollback_throwsForBlankModuleName() {
        HotReloadServiceImpl service = new HotReloadServiceImpl(successCallable(), 5);

        assertThrows(IllegalArgumentException.class,
                () -> service.rollback("  "));
    }

    @Test
    void allCurrentVersions_returnsAllModules() throws ErlangRpcException {
        HotReloadServiceImpl service = new HotReloadServiceImpl(successCallable(), 5);

        service.loadModule("mod_a", beamBytes("v1"));
        service.loadModule("mod_b", beamBytes("v1"));

        Map<String, ModuleVersion> all = service.allCurrentVersions();
        assertEquals(2, all.size());
        assertTrue(all.containsKey("mod_a"));
        assertTrue(all.containsKey("mod_b"));
    }

    @Test
    void loadModule_rejectsNullModuleName() {
        HotReloadServiceImpl service = new HotReloadServiceImpl(successCallable(), 5);

        assertThrows(IllegalArgumentException.class,
                () -> service.loadModule(null, beamBytes("v1")));
    }

    @Test
    void loadModule_rejectsNullBytecode() {
        HotReloadServiceImpl service = new HotReloadServiceImpl(successCallable(), 5);

        assertThrows(IllegalArgumentException.class,
                () -> service.loadModule("yawl_x", null));
    }

    @Test
    void loadModule_recordsCallerViaThreadName() throws ErlangRpcException {
        Thread.currentThread().setName("test-loader-thread");
        HotReloadServiceImpl service = new HotReloadServiceImpl(successCallable(), 5);

        ModuleVersion version = service.loadModule("yawl_threaded", beamBytes("v1"));

        // loadedBy reflects the calling thread name
        assertNotNull(version.loadedBy());
        assertFalse(version.loadedBy().isBlank());
    }

    @Test
    void moduleVersion_bytecodeIsDefensivelyCopied() throws ErlangRpcException {
        HotReloadServiceImpl service = new HotReloadServiceImpl(successCallable(), 5);
        byte[] original = beamBytes("v1");

        ModuleVersion version = service.loadModule("yawl_defensive", original);

        // Mutate the original — must not affect stored version
        original[0] = 0x00;
        assertNotEquals(original[0], version.beamBytecode()[0],
                "ModuleVersion must store a defensive copy of the bytecode");
    }
}
