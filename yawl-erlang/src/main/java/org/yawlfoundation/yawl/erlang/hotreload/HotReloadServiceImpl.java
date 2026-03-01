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

import org.yawlfoundation.yawl.erlang.capability.Capability;
import org.yawlfoundation.yawl.erlang.capability.MapsToCapability;
import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.resilience.OtpCircuitBreaker;
import org.yawlfoundation.yawl.erlang.term.ErlAtom;
import org.yawlfoundation.yawl.erlang.term.ErlBinary;
import org.yawlfoundation.yawl.erlang.term.ErlTerm;
import org.yawlfoundation.yawl.erlang.term.ErlTuple;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Production implementation of {@link HotReloadService}.
 *
 * <p>Uses {@link OtpCircuitBreaker.ErlRpcCallable} to call OTP's {@code code} module
 * for module loading and purging. Each module has an independent ring buffer of
 * configurable depth (default: 5).</p>
 *
 * <p>Thread-safe: module operations are guarded by per-module {@link ReentrantLock}
 * instances — never {@code synchronized} blocks, which would pin virtual threads.</p>
 *
 * <p>Usage:
 * <pre>
 *   ErlangBridge bridge = ErlangBridge.connect("yawl_erl@localhost", "secret");
 *   HotReloadService reloader = new HotReloadServiceImpl(bridge.asRpcCallable(), 5);
 *
 *   byte[] beam = Files.readAllBytes(Path.of("yawl_order_routing.beam"));
 *   ModuleVersion v = reloader.loadModule("yawl_order_routing", beam);
 *
 *   // ... later, on error:
 *   reloader.rollback("yawl_order_routing");
 * </pre>
 */
@MapsToCapability(value = Capability.LOAD_BINARY_MODULE, layer = "L3")
@MapsToCapability(value = Capability.ROLLBACK_MODULE, layer = "L3")
public final class HotReloadServiceImpl implements HotReloadService {

    private static final Logger LOG = Logger.getLogger(HotReloadServiceImpl.class.getName());
    static final int DEFAULT_RING_BUFFER_DEPTH = 5;

    private final OtpCircuitBreaker.ErlRpcCallable rpcCallable;
    private final int ringBufferDepth;

    // Per-module ring buffer + lock (ArrayDeque is bounded by manual eviction)
    private final ConcurrentHashMap<String, ArrayDeque<ModuleVersion>> history =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> locks =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> versionCounters =
            new ConcurrentHashMap<>();

    /**
     * Creates a service with the default ring buffer depth of 5 versions.
     *
     * @param rpcCallable the OTP RPC channel (typically {@code bridge.asRpcCallable()})
     */
    public HotReloadServiceImpl(OtpCircuitBreaker.ErlRpcCallable rpcCallable) {
        this(rpcCallable, DEFAULT_RING_BUFFER_DEPTH);
    }

    /**
     * Creates a service with a configurable ring buffer depth.
     *
     * @param rpcCallable     the OTP RPC channel
     * @param ringBufferDepth maximum number of versions to retain per module (≥ 2 for rollback)
     * @throws IllegalArgumentException if rpcCallable is null or ringBufferDepth < 1
     */
    public HotReloadServiceImpl(OtpCircuitBreaker.ErlRpcCallable rpcCallable, int ringBufferDepth) {
        if (rpcCallable == null)
            throw new IllegalArgumentException("rpcCallable must not be null");
        if (ringBufferDepth < 1)
            throw new IllegalArgumentException("ringBufferDepth must be >= 1");

        this.rpcCallable = rpcCallable;
        this.ringBufferDepth = ringBufferDepth;
    }

    @Override
    @MapsToCapability(value = Capability.LOAD_BINARY_MODULE, layer = "L3")
    public ModuleVersion loadModule(String moduleName, byte[] beamBytecode) throws ErlangRpcException {
        if (moduleName == null || moduleName.isBlank())
            throw new IllegalArgumentException("moduleName must be non-blank");
        if (beamBytecode == null || beamBytecode.length == 0)
            throw new IllegalArgumentException("beamBytecode must be non-empty");

        ReentrantLock lock = locks.computeIfAbsent(moduleName, k -> new ReentrantLock());
        lock.lock();
        try {
            // Step 1: purge old version (best-effort, non-fatal if module was never loaded)
            try {
                rpcCallable.call("code", "purge", List.of(new ErlAtom(moduleName)));
            } catch (ErlangRpcException | ErlangConnectionException e) {
                LOG.warning("code:purge failed for '" + moduleName + "': " + e.getMessage()
                        + " — continuing with load_binary");
            }

            // Step 2: load from binary via code:load_binary/3
            String filename = moduleName + ".beam";
            ErlTerm result;
            try {
                result = rpcCallable.call("code", "load_binary", List.of(
                        new ErlAtom(moduleName),
                        new ErlBinary(filename.getBytes(StandardCharsets.UTF_8)),
                        new ErlBinary(beamBytecode)));
            } catch (ErlangConnectionException e) {
                throw new ErlangRpcException("code", "load_binary",
                        "Connection lost loading '" + moduleName + "': " + e.getMessage(), e);
            }

            // Step 3: check result — OTP returns {module, Module} | {error, Reason}
            if (result instanceof ErlTuple t && t.elements().size() == 2) {
                ErlTerm tag = t.elements().get(0);
                if (tag instanceof ErlAtom(var tagVal) && "error".equals(tagVal)) {
                    throw new ErlangRpcException("code", "load_binary",
                            "OTP returned error for '" + moduleName + "': " + t.elements().get(1));
                }
            }

            // Step 4: build version record and push to ring buffer
            int version = versionCounters.merge(moduleName, 1, Integer::sum);
            ModuleVersion moduleVersion = new ModuleVersion(
                    moduleName, version, Instant.now(), beamBytecode,
                    Thread.currentThread().getName());

            ArrayDeque<ModuleVersion> buf = history.computeIfAbsent(moduleName,
                    k -> new ArrayDeque<>(ringBufferDepth + 1));
            buf.addFirst(moduleVersion);
            while (buf.size() > ringBufferDepth) {
                buf.removeLast();
            }

            LOG.info("Loaded module '" + moduleName + "' version " + version
                    + " (" + beamBytecode.length + " bytes)");
            return moduleVersion;

        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<ModuleVersion> currentVersion(String moduleName) {
        if (moduleName == null) return Optional.empty();
        ArrayDeque<ModuleVersion> buf = history.get(moduleName);
        if (buf == null || buf.isEmpty()) return Optional.empty();
        ReentrantLock lock = locks.computeIfAbsent(moduleName, k -> new ReentrantLock());
        lock.lock();
        try {
            return buf.isEmpty() ? Optional.empty() : Optional.of(buf.peekFirst());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<ModuleVersion> versionHistory(String moduleName) {
        if (moduleName == null) return List.of();
        ArrayDeque<ModuleVersion> buf = history.get(moduleName);
        if (buf == null) return List.of();
        ReentrantLock lock = locks.computeIfAbsent(moduleName, k -> new ReentrantLock());
        lock.lock();
        try {
            return List.copyOf(buf);
        } finally {
            lock.unlock();
        }
    }

    @Override
    @MapsToCapability(value = Capability.ROLLBACK_MODULE, layer = "L3")
    public void rollback(String moduleName) throws ErlangRpcException {
        if (moduleName == null || moduleName.isBlank())
            throw new IllegalArgumentException("moduleName must be non-blank");

        ReentrantLock lock = locks.computeIfAbsent(moduleName, k -> new ReentrantLock());
        lock.lock();
        try {
            ArrayDeque<ModuleVersion> buf = history.get(moduleName);
            if (buf == null || buf.size() < 2) {
                throw new IllegalStateException(
                        "No previous version available for '" + moduleName + "' to roll back to");
            }

            // Remove current version, peek at the previous
            buf.removeFirst();
            ModuleVersion previous = buf.peekFirst();

            LOG.info("Rolling back '" + moduleName + "' to version " + previous.version());

            // Reload previous version's bytecode
            String filename = moduleName + ".beam";
            ErlTerm result;
            try {
                result = rpcCallable.call("code", "load_binary", List.of(
                        new ErlAtom(moduleName),
                        new ErlBinary(filename.getBytes(StandardCharsets.UTF_8)),
                        new ErlBinary(previous.beamBytecode())));
            } catch (ErlangConnectionException e) {
                throw new ErlangRpcException("code", "load_binary",
                        "Connection lost during rollback of '" + moduleName + "': " + e.getMessage(), e);
            }

            if (result instanceof ErlTuple t && t.elements().size() == 2) {
                ErlTerm tag = t.elements().get(0);
                if (tag instanceof ErlAtom(var tagVal) && "error".equals(tagVal)) {
                    throw new ErlangRpcException("code", "load_binary",
                            "Rollback failed for '" + moduleName + "': " + t.elements().get(1));
                }
            }

            LOG.info("Rolled back '" + moduleName + "' to version " + previous.version());

        } finally {
            lock.unlock();
        }
    }

    @Override
    public Map<String, ModuleVersion> allCurrentVersions() {
        var result = new java.util.HashMap<String, ModuleVersion>();
        for (var entry : history.entrySet()) {
            String name = entry.getKey();
            ReentrantLock lock = locks.computeIfAbsent(name, k -> new ReentrantLock());
            lock.lock();
            try {
                if (!entry.getValue().isEmpty()) {
                    result.put(name, entry.getValue().peekFirst());
                }
            } finally {
                lock.unlock();
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
