/**
 * Layer 2: Erlang distribution connection management.
 *
 * <p>{@link org.yawlfoundation.yawl.erlang.bridge.ErlangNode} manages the erl_interface
 * C-node lifecycle: initialising the {@code ei_cnode} struct in a {@code Arena.ofShared()}
 * arena, connecting via EPMD or direct host:port, performing synchronous RPCs and
 * fire-and-forget sends, maintaining the distribution tick keepalive via a virtual thread,
 * and cleanly closing the file descriptor on {@code close()}.</p>
 *
 * <p>{@link org.yawlfoundation.yawl.erlang.bridge.ErlangNodePool} manages a pool of
 * pre-connected nodes for concurrent request dispatch.</p>
 *
 * <p>{@link org.yawlfoundation.yawl.erlang.bridge.ErlMessage} is an immutable record
 * carrying the decoded received message and its header metadata.</p>
 *
 * @see org.yawlfoundation.yawl.erlang.bridge.ErlangNode
 * @see org.yawlfoundation.yawl.erlang.bridge.ErlangNodePool
 * @see org.yawlfoundation.yawl.erlang.bridge.ErlMessage
 */
package org.yawlfoundation.yawl.erlang.bridge;
