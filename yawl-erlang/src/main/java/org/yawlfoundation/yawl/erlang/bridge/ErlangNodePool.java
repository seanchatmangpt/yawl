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

import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Thread-safe pool of pre-connected Erlang C-nodes.
 *
 * <p>Manages a pool of {@code ErlangNode} instances configured to connect to
 * the same target Erlang node with the same cookie. Threads acquire nodes
 * from the pool for concurrent RPC/send operations, then release them back
 * when done.</p>
 *
 * <p>Usage:
 * <pre>
 * ErlangNodePool pool = new ErlangNodePool("yawl@localhost", "erl@localhost", "secret", 2, 8);
 * pool.initialise();
 * try {
 *     ErlangNode node = pool.acquire();
 *     ErlTerm result = node.rpc("lists", "reverse", List.of(myTerm));
 *     pool.release(node);
 * } finally {
 *     pool.close();
 * }
 * </pre>
 *
 * <p>The pool maintains between {@code minSize} and {@code maxSize} nodes.
 * On construction, exactly {@code minSize} nodes are pre-allocated and connected.
 * If all nodes are in use, {@link #acquire()} blocks until one becomes available.</p>
 *
 * @see ErlangNode for per-node connection details
 */
public class ErlangNodePool implements AutoCloseable {

    private final String localName;
    private final String targetNode;
    private final String cookie;
    private final int minSize;
    private final int maxSize;
    private final BlockingQueue<ErlangNode> pool;
    private final List<ErlangNode> allNodes = new ArrayList<>();
    private volatile boolean closed = false;

    /**
     * Constructs a node pool (not yet initialised).
     *
     * <p>Call {@link #initialise()} after construction to create and connect
     * the minimum set of nodes.</p>
     *
     * @param localName  the local C-node name (e.g., {@code "yawl@localhost"})
     * @param targetNode the target Erlang node name (e.g., {@code "erl@localhost"})
     * @param cookie     the distribution cookie
     * @param minSize    minimum number of pre-connected nodes (≥ 1)
     * @param maxSize    maximum number of nodes in the pool (≥ minSize)
     * @throws IllegalArgumentException if parameters are invalid
     */
    public ErlangNodePool(String localName, String targetNode, String cookie, int minSize, int maxSize) {
        if (localName == null || localName.isBlank()) {
            throw new IllegalArgumentException("localName must be non-blank");
        }
        if (targetNode == null || targetNode.isBlank()) {
            throw new IllegalArgumentException("targetNode must be non-blank");
        }
        if (cookie == null || cookie.isBlank()) {
            throw new IllegalArgumentException("cookie must be non-blank");
        }
        if (minSize < 1) {
            throw new IllegalArgumentException("minSize must be ≥ 1");
        }
        if (maxSize < minSize) {
            throw new IllegalArgumentException("maxSize must be ≥ minSize");
        }

        this.localName = localName;
        this.targetNode = targetNode;
        this.cookie = cookie;
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.pool = new LinkedBlockingQueue<>(maxSize);
    }

    /**
     * Initialises the pool by creating and connecting the minimum set of nodes.
     *
     * <p>Should be called immediately after construction. If any node fails to
     * connect, all successfully created nodes are closed and an exception is thrown.</p>
     *
     * @throws ErlangConnectionException if any node fails to connect
     * @throws IllegalStateException if already initialised or closed
     */
    public void initialise() throws ErlangConnectionException {
        synchronized (this) {
            if (!allNodes.isEmpty()) {
                throw new IllegalStateException("Pool already initialised");
            }
            if (closed) {
                throw new IllegalStateException("Pool is closed");
            }

            try {
                for (int i = 0; i < minSize; i++) {
                    ErlangNode node = new ErlangNode(localName);
                    node.connect(targetNode, cookie);
                    allNodes.add(node);
                    pool.offer(node);
                }
            } catch (ErlangConnectionException e) {
                closeAll();
                throw e;
            }
        }
    }

    /**
     * Acquires a node from the pool.
     *
     * <p>If a node is available, it is returned immediately. Otherwise,
     * blocks until one becomes available or the thread is interrupted.</p>
     *
     * @return a connected ErlangNode ready for use
     * @throws ErlangConnectionException if the pool is empty and cannot create more nodes
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws IllegalStateException if the pool is closed
     */
    public ErlangNode acquire() throws ErlangConnectionException, InterruptedException {
        if (closed) {
            throw new IllegalStateException("Pool is closed");
        }

        ErlangNode node = pool.poll();
        if (node != null) {
            return node;
        }

        synchronized (this) {
            if (allNodes.size() < maxSize) {
                try {
                    node = new ErlangNode(localName);
                    node.connect(targetNode, cookie);
                    allNodes.add(node);
                    return node;
                } catch (ErlangConnectionException e) {
                    throw e;
                }
            }
        }

        node = pool.take();
        if (node == null) {
            throw new ErlangConnectionException(targetNode, "pool exhausted and cannot create more nodes");
        }
        return node;
    }

    /**
     * Releases a node back to the pool.
     *
     * <p>If the pool is not full, the node is put back for reuse. Otherwise,
     * the node is closed to maintain the maximum size.</p>
     *
     * @param node the node to release
     * @throws IllegalStateException if the pool is closed
     */
    public void release(ErlangNode node) {
        if (closed) {
            throw new IllegalStateException("Pool is closed");
        }

        if (node != null && node.isConnected()) {
            if (!pool.offer(node)) {
                node.close();
            }
        }
    }

    /**
     * Closes all nodes in the pool and disables further operations.
     *
     * <p>Any pending {@link #acquire()} calls will receive
     * {@link IllegalStateException}.</p>
     */
    @Override
    public void close() {
        closed = true;
        closeAll();
    }

    /**
     * Returns the current number of nodes in the pool (available for reuse).
     *
     * @return the number of nodes currently in the queue
     */
    public int getAvailableCount() {
        return pool.size();
    }

    /**
     * Returns the total number of nodes created (including those in use).
     *
     * @return the total count of nodes across in-use and available
     */
    public int getTotalCount() {
        synchronized (this) {
            return allNodes.size();
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void closeAll() {
        pool.clear();
        synchronized (this) {
            for (ErlangNode node : allNodes) {
                try {
                    node.close();
                } catch (Exception e) {
                    System.err.println("Error closing node: " + e);
                }
            }
            allNodes.clear();
        }
    }
}
