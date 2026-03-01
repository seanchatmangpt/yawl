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

import org.yawlfoundation.yawl.erlang.capability.Capability;
import org.yawlfoundation.yawl.erlang.capability.MapsToCapability;
import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;
import org.yawlfoundation.yawl.erlang.error.ErlangReceiveException;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.error.ErlangSendException;
import org.yawlfoundation.yawl.erlang.generated.ei_h;
import org.yawlfoundation.yawl.erlang.term.ErlTerm;
import org.yawlfoundation.yawl.erlang.term.ErlTermCodec;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages a single Erlang C-node connection via the erl_interface library.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>Create with {@code new ErlangNode(localName)}</li>
 *   <li>Connect via {@link #connect(String, String, String)} or
 *       {@link #connect(String, String, int, String)}</li>
 *   <li>Call {@link #rpc}, {@link #send}, {@link #receive}</li>
 *   <li>Call {@link #close()} to cleanly shut down</li>
 * </ul>
 *
 * <p>The node maintains:
 * <ul>
 *   <li>A shared {@code Arena} for the node lifetime (allocated ei_cnode + fd)</li>
 *   <li>A virtual thread tick loop that sends keepalive heartbeats every 15 seconds</li>
 *   <li>A reentrant lock guarding concurrent RPC/send/receive calls</li>
 * </ul>
 *
 * <p>Thread-safe: all public methods are synchronized via an internal lock.
 * Suitable for multi-threaded dispatcher scenarios.</p>
 *
 * @see ErlangNodePool for pooling multiple pre-connected nodes
 */
@MapsToCapability(value = Capability.LAUNCH_CASE, layer = "L2")
@MapsToCapability(value = Capability.CHECK_CONFORMANCE, layer = "L2")
@MapsToCapability(value = Capability.SUBSCRIBE_TO_EVENTS, layer = "L2")
@MapsToCapability(value = Capability.RELOAD_MODULE, layer = "L2")
@MapsToCapability(value = Capability.LOAD_BINARY_MODULE, layer = "L2")
@MapsToCapability(value = Capability.ROLLBACK_MODULE, layer = "L2")
@MapsToCapability(value = Capability.AS_RPC_CALLABLE, layer = "L2")
public class ErlangNode implements AutoCloseable {

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
     * @param localName the local C-node name (e.g., {@code "yawl@localhost"})
     */
    public ErlangNode(String localName) {
        if (localName == null || localName.isBlank()) {
            throw new IllegalArgumentException("localName must be non-blank");
        }
        this.localName = localName;
    }

    /**
     * Connects to an Erlang node via EPMD (Erlang Port Mapper Daemon).
     *
     * <p>This method initialises the {@code ei_cnode} struct, connects to the
     * target node via EPMD on port 4369, and starts the tick keepalive thread.</p>
     *
     * @param targetNode the target Erlang node name (e.g., {@code "erl@localhost"})
     * @param cookie     the distribution cookie (must match the target node)
     * @throws ErlangConnectionException if libei.so is unavailable or the connection fails
     * @throws IllegalStateException if already connected
     */
    public void connect(String targetNode, String cookie)
            throws ErlangConnectionException {
        if (targetNode == null || targetNode.isBlank() || cookie == null || cookie.isBlank()) {
            throw new IllegalArgumentException("targetNode and cookie must be non-blank");
        }

        lock.lock();
        try {
            if (connected) {
                throw new IllegalStateException("Already connected");
            }

            if (ei_h.LIBRARY.isEmpty()) {
                throw new ErlangConnectionException(targetNode, "libei.so not available");
            }

            arena = Arena.ofShared();
            cnode = arena.allocate(ei_h.EI_CNODE_SIZE, 8L);

            MemorySegment localNameSeg = arena.allocateFrom(localName);
            MemorySegment cookieSeg = arena.allocateFrom(cookie);

            int initRc = ei_h.ei_connect_init(cnode, localNameSeg, cookieSeg, (short) 0);
            if (initRc != 0) {
                arena.close();
                throw new ErlangConnectionException(targetNode, "ei_connect_init failed with rc=" + initRc);
            }

            MemorySegment targetNodeSeg = arena.allocateFrom(targetNode);
            fd = ei_h.ei_connect(cnode, targetNodeSeg);

            if (fd < 0) {
                arena.close();
                throw new ErlangConnectionException(targetNode, "ei_connect failed");
            }

            connected = true;
            startTickLoop();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Connects to an Erlang node at the specified host and port, bypassing EPMD.
     *
     * <p>This method is useful for direct connections when EPMD is unavailable or
     * for testing. The target node must be listening on the given port.</p>
     *
     * @param targetHost the hostname or IP address of the Erlang node
     * @param targetPort the TCP port number the Erlang node is listening on
     * @param cookie     the distribution cookie (must match the target node)
     * @throws ErlangConnectionException if libei.so is unavailable or the connection fails
     * @throws IllegalStateException if already connected
     */
    public void connect(String targetHost, int targetPort, String cookie)
            throws ErlangConnectionException {
        if (targetHost == null || targetHost.isBlank() || cookie == null || cookie.isBlank()) {
            throw new IllegalArgumentException("targetHost and cookie must be non-blank");
        }
        if (targetPort < 1 || targetPort > 65535) {
            throw new IllegalArgumentException("targetPort must be 1-65535, got " + targetPort);
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

            MemorySegment localNameSeg = arena.allocateFrom(localName);
            MemorySegment cookieSeg = arena.allocateFrom(cookie);

            int initRc = ei_h.ei_connect_init(cnode, localNameSeg, cookieSeg, (short) 0);
            if (initRc != 0) {
                arena.close();
                throw new ErlangConnectionException(targetHost, "ei_connect_init failed with rc=" + initRc);
            }

            MemorySegment hostSeg = arena.allocateFrom(targetHost);
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
     * <p>Calls {@code Module:Function(Args)} on the connected node and returns
     * the result term. The RPC is performed in a per-call confined arena to ensure
     * efficient memory management.</p>
     *
     * @param module the Erlang module name
     * @param function the Erlang function name
     * @param args the function arguments as a list of ErlTerm
     * @return the result term
     * @throws ErlangRpcException if the RPC fails or returns badrpc
     * @throws IllegalStateException if not connected
     */
    public ErlTerm rpc(String module, String function, List<ErlTerm> args)
            throws ErlangRpcException {
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
                byte[] argBytes = ErlTermCodec.encodeArgs(args);
                MemorySegment argSeg = callArena.allocate(argBytes.length, 1L);
                argSeg.copyFrom(MemorySegment.ofArray(argBytes));

                MemorySegment modSeg = callArena.allocateFrom(module);
                MemorySegment funSeg = callArena.allocateFrom(function);

                MemorySegment resultBuf = callArena.allocate(ei_h.EI_X_BUFF_LAYOUT);
                int initRc = ei_h.ei_x_new_with_version(resultBuf);
                if (initRc != 0) {
                    throw new ErlangRpcException(module, function,
                        "ei_x_new_with_version failed with rc=" + initRc);
                }

                int rpcRc = ei_h.ei_rpc(cnode, fd, modSeg, funSeg, argSeg, argBytes.length, resultBuf);
                if (rpcRc != 0) {
                    ei_h.ei_x_free(resultBuf);
                    throw new ErlangRpcException(module, function,
                        "ei_rpc failed with rc=" + rpcRc);
                }

                byte[] resultBytes = extractBytesFromXbuff(resultBuf);
                ei_h.ei_x_free(resultBuf);

                try {
                    return ErlTermCodec.decodeRpcResult(resultBytes);
                } catch (ErlangReceiveException e) {
                    throw new ErlangRpcException(module, function, "decode error: " + e.getMessage(), e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sends a message to a registered Erlang process by name.
     *
     * <p>This is a fire-and-forget send. The message term is encoded and sent
     * to the named process on the connected node.</p>
     *
     * @param registeredName the registered process name on the target node
     * @param message the message term to send
     * @throws ErlangSendException if the send fails
     * @throws IllegalStateException if not connected
     */
    public void send(String registeredName, ErlTerm message) throws ErlangSendException {
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

                MemorySegment nameSeg = callArena.allocateFrom(registeredName);

                int sendRc = ei_h.ei_reg_send(cnode, fd, nameSeg, msgSeg, msgBytes.length);
                if (sendRc != 0) {
                    throw new ErlangSendException(registeredName, "ei_reg_send failed with rc=" + sendRc);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Receives a message from the Erlang distribution.
     *
     * <p>Blocks until a message arrives or a tick (distribution keepalive) is received.
     * Returns an {@link ErlMessage} with type {@code ERL_MSG} or {@code ERL_TICK}.
     * For ticks, the payload is an empty atom.</p>
     *
     * @return an ErlMessage carrying the decoded payload and message type
     * @throws ErlangReceiveException if the receive fails or the term is malformed
     * @throws IllegalStateException if not connected
     */
    public ErlMessage receive() throws ErlangReceiveException {
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
                    throw new ErlangReceiveException("ei_x_new failed with rc=" + initRc);
                }

                int recvRc = ei_h.ei_xreceive_msg(fd, msgSeg, xbuffSeg);

                if (recvRc == ei_h.ERL_TICK) {
                    ei_h.ei_x_free(xbuffSeg);
                    return ErlMessage.fromMsgType(ei_h.ERL_TICK,
                        org.yawlfoundation.yawl.erlang.term.ErlNil.INSTANCE);
                }

                if (recvRc != ei_h.ERL_MSG) {
                    ei_h.ei_x_free(xbuffSeg);
                    throw new ErlangReceiveException("ei_xreceive_msg failed with rc=" + recvRc);
                }

                byte[] payload = extractBytesFromXbuff(xbuffSeg);
                ei_h.ei_x_free(xbuffSeg);

                try {
                    ErlTerm decoded = ErlTermCodec.decode(payload);
                    return ErlMessage.fromMsgType(ei_h.ERL_MSG, decoded);
                } catch (ErlangReceiveException e) {
                    throw new ErlangReceiveException("Failed to decode received message: " + e.getMessage(), e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Closes the connection and releases all resources.
     *
     * <p>Stops the tick keepalive thread and closes the arena, releasing
     * the file descriptor and ei_cnode memory. This node cannot be reused
     * after calling close().</p>
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
                try {
                    arena.close();
                } catch (Exception e) {
                    System.err.println("Error closing arena: " + e);
                }
            }

            fd = -1;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns whether this node is currently connected.
     *
     * @return true if connected and ready for RPC/send/receive, false otherwise
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Returns the local C-node name.
     *
     * @return the local node name (e.g., {@code "yawl@localhost"})
     */
    public String getLocalName() {
        return localName;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

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
                    System.err.println("Tick loop error: " + e);
                }
            }
        }
    }

    private byte[] extractBytesFromXbuff(MemorySegment xbuff) {
        int index = (int) ei_h.EI_X_BUFF$index.get(xbuff, 0L);
        MemorySegment buffPtr = (MemorySegment) ei_h.EI_X_BUFF$buff.get(xbuff, 0L);
        return buffPtr.reinterpret(index).toArray(ValueLayout.JAVA_BYTE);
    }
}
