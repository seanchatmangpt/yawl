/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
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
package org.yawlfoundation.yawl.nativebridge.erlang;

import org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_h;
import org.yawlfoundation.yawl.nativebridge.erlang.term.ErlTerm;
import org.yawlfoundation.yawl.nativebridge.erlang.term.ErlTermCodec;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import java.nio.charset.StandardCharsets;

/**
 * Manages Erlang distribution protocol with ei.h bindings.
 * Unix domain socket, ETF encoding, error term conversion.
 *
 * <p>This class provides a typed bridge between Java and Erlang/OTP.
 * It handles connection management, RPC calls, and automatic conversion
 * of Erlang error terms to Java exceptions.</p>
 */
public final class ErlangNode implements AutoCloseable {

    private final String localName;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean connected = false;
    private int fd = -1;
    private Arena arena;
    private MemorySegment cnode;
    private Thread tickThread;

    /**
     * Constructs an unconnected Erlang C-node.
     *
     * @param localName the local C-node name (e.g., "yawl@localhost")
     */
    public ErlangNode(String localName) {
        if (localName == null || localName.isBlank()) {
            throw new IllegalArgumentException("localName must be non-blank");
        }
        this.localName = localName;
    }

    /**
     * Connects to an Erlang node via Unix domain socket (bypasses EPMD).
     *
     * @param targetHost the hostname or IP address
     * @param targetPort the port number
     * @param cookie the distribution cookie
     * @throws ErlangException if connection fails
     */
    public void connect(String targetHost, int targetPort, String cookie) throws ErlangException {
        if (targetHost == null || targetHost.isBlank() || cookie == null || cookie.isBlank()) {
            throw new IllegalArgumentException("targetHost and cookie must be non-blank");
        }
        if (targetPort < 1 || targetPort > 65535) {
            throw new IllegalArgumentException("targetPort must be 1-65535");
        }

        lock.lock();
        try {
            if (connected) {
                throw new IllegalStateException("Already connected");
            }

            if (ei_h.LIBRARY.isEmpty()) {
                throw new ErlangConnectionException(targetHost, "libei.so not available");
            }

            arena = Arena.ofShared();
            cnode = arena.allocate(ei_h.EI_CNODE_SIZE, 8L);

            MemorySegment localNameSeg = arena.allocateUtf8String(localName);
            MemorySegment cookieSeg = arena.allocateUtf8String(cookie);

            int initRc = ei_h.ei_connect_init(cnode, localNameSeg, cookieSeg, (short) 0);
            if (initRc != 0) {
                arena.close();
                throw new ErlangConnectionException(targetHost, "ei_connect_init failed: " + initRc);
            }

            MemorySegment hostSeg = arena.allocateUtf8String(targetHost);
            fd = ei_h.ei_connect_host_port(cnode, hostSeg, targetPort);

            if (fd < 0) {
                arena.close();
                throw new ErlangConnectionException(targetHost, "ei_connect_host_port failed");
            }

            connected = true;
            startTickLoop();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Performs a synchronous RPC to a remote Erlang function.
     *
     * @param module the Erlang module name
     * @param function the Erlang function name
     * @param args the function arguments as ErlTerm list
     * @return the result term
     * @throws ErlangException if the RPC fails or returns error
     */
    public ErlTerm rpc(String module, String function, List<ErlTerm> args) throws ErlangException {
        if (module == null || module.isBlank() || function == null || function.isBlank()) {
            throw new IllegalArgumentException("module and function must be non-blank");
        }
        if (args == null) {
            args = List.of();
        }

        lock.lock();
        try {
            if (!connected || fd < 0) {
                throw new IllegalStateException("Not connected");
            }

            try (Arena callArena = Arena.ofConfined()) {
                // Encode arguments to ETF bytes
                byte[] argBytes = ErlTermCodec.encodeArgs(args);
                MemorySegment argSeg = callArena.allocate(argBytes.length, 1L);
                argSeg.copyFrom(MemorySegment.ofArray(argBytes));

                MemorySegment modSeg = callArena.allocateUtf8String(module);
                MemorySegment funSeg = callArena.allocateUtf8String(function);

                MemorySegment resultBuf = callArena.allocate(ei_h.EI_X_BUFF_LAYOUT);
                int initRc = ei_h.ei_x_new_with_version(resultBuf);
                if (initRc != 0) {
                    throw new ErlangRpcException(module, function,
                        "ei_x_new_with_version failed: " + initRc);
                }

                int rpcRc = ei_h.ei_rpc(cnode, fd, modSeg, funSeg, argSeg, argBytes.length, resultBuf);
                if (rpcRc != 0) {
                    ei_h.ei_x_free(resultBuf);
                    throw new ErlangRpcException(module, function,
                        "ei_rpc failed: " + rpcRc);
                }

                byte[] resultBytes = extractBytesFromXbuff(resultBuf);
                ei_h.ei_x_free(resultBuf);

                // Decode result and convert Erlang errors to Java exceptions
                ErlTerm result = ErlTermCodec.decodeRpcResult(resultBytes);
                return convertErlangError(result);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sends a message to a registered Erlang process by name.
     *
     * @param registeredName the registered process name
     * @param message the message term to send
     * @throws ErlangException if the send fails
     */
    public void send(String registeredName, ErlTerm message) throws ErlangException {
        if (registeredName == null || registeredName.isBlank() || message == null) {
            throw new IllegalArgumentException("registeredName and message must be non-null");
        }

        lock.lock();
        try {
            if (!connected || fd < 0) {
                throw new IllegalStateException("Not connected");
            }

            try (Arena callArena = Arena.ofConfined()) {
                byte[] msgBytes = ErlTermCodec.encode(message);
                MemorySegment msgSeg = callArena.allocate(msgBytes.length, 1L);
                msgSeg.copyFrom(MemorySegment.ofArray(msgBytes));

                MemorySegment nameSeg = callArena.allocateUtf8String(registeredName);

                int sendRc = ei_h.ei_reg_send(cnode, fd, nameSeg, msgSeg, msgBytes.length);
                if (sendRc != 0) {
                    throw new ErlangSendException(registeredName, "ei_reg_send failed: " + sendRc);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Receives a message from the Erlang distribution.
     *
     * @return an ErlMessage with the decoded payload
     * @throws ErlangException if the receive fails
     */
    public ErlMessage receive() throws ErlangException {
        lock.lock();
        try {
            if (!connected || fd < 0) {
                throw new IllegalStateException("Not connected");
            }

            try (Arena callArena = Arena.ofShared()) {
                MemorySegment msgSeg = callArena.allocate(ei_h.ERLANG_MSG_SIZE, 8L);
                MemorySegment xbuffSeg = callArena.allocate(ei_h.EI_X_BUFF_LAYOUT);

                int initRc = ei_h.ei_x_new(xbuffSeg);
                if (initRc != 0) {
                    throw new ErlangReceiveException("ei_x_new failed: " + initRc);
                }

                int recvRc = ei_h.ei_xreceive_msg(fd, msgSeg, xbuffSeg);

                if (recvRc == ei_h.ERL_TICK) {
                    ei_h.ei_x_free(xbuffSeg);
                    return ErlMessage.fromMsgType(ei_h.ERL_TICK, ErlTerm.Nil());
                }

                if (recvRc != ei_h.ERL_MSG) {
                    ei_h.ei_x_free(xbuffSeg);
                    throw new ErlangReceiveException("ei_xreceive_msg failed: " + recvRc);
                }

                byte[] payload = extractBytesFromXbuff(xbuffSeg);
                ei_h.ei_x_free(xbuffSeg);

                ErlTerm decoded = ErlTermCodec.decode(payload);
                return ErlMessage.fromMsgType(ei_h.ERL_MSG, decoded);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Closes the connection and releases all resources.
     */
    @Override
    public void close() {
        lock.lock();
        try {
            connected = false;

            if (tickThread != null && tickThread != Thread.currentThread()) {
                try {
                    tickThread.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (arena != null) {
                arena.close();
            }

            fd = -1;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns whether this node is currently connected.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Gets the local C-node name.
     */
    public String getLocalName() {
        return localName;
    }

    // Private helper methods

    private void startTickLoop() {
        tickThread = Thread.ofVirtual()
            .name("erlang-tick-" + localName)
            .start(() -> tickLoop());
    }

    private void tickLoop() {
        while (connected) {
            try {
                Thread.sleep(15000);
                if (connected) {
                    lock.lock();
                    try {
                        if (connected && fd >= 0) {
                            ei_h.ei_xreceive_msg(fd, null, null);
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (connected) {
                    System.err.println("Tick loop error: " + e.getMessage());
                }
            }
        }
    }

    private ErlTerm convertErlangError(ErlTerm term) throws ErlangException {
        // Check for badrpc error term
        if (term instanceof ErlTerm.ErlTuple t && t.elements().length >= 2) {
            ErlTerm first = t.elements()[0];
            ErlTerm second = t.elements()[1];

            if (first instanceof ErlTerm.ErlAtom a && "badrpc".equals(a.value())) {
                if (second instanceof ErlTerm.ErlTuple inner && inner.elements().length >= 2) {
                    ErlAtom exitAtom = (ErlAtom) inner.elements()[0];
                    if ("EXIT".equals(exitAtom.value())) {
                        ErlTerm reason = inner.elements()[1];
                        throw new ErlangExitException(reason);
                    }
                }
                throw new ErlangRpcException("Remote call failed: " + second);
            }
        }
        return term;
    }

    private byte[] extractBytesFromXbuff(MemorySegment xbuff) {
        int index = (int) ei_h.EI_X_BUFF$index.get(xbuff, 0L);
        MemorySegment buffPtr = (MemorySegment) ei_h.EI_X_BUFF$buff.get(xbuff, 0L);
        return buffPtr.reinterpret(index).toArray(ValueLayout.JAVA_BYTE);
    }
}