/**
 * Checked exception hierarchy for the Erlang/OTP 28 bridge.
 * All exceptions extend {@link org.yawlfoundation.yawl.erlang.error.ErlangException}
 * and include actionable context for diagnosing distribution failures.
 *
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.erlang.error.ErlangConnectionException} — handshake / cookie failures</li>
 *   <li>{@link org.yawlfoundation.yawl.erlang.error.ErlangRpcException} — RPC call failures / badrpc</li>
 *   <li>{@link org.yawlfoundation.yawl.erlang.error.ErlangSendException} — registered-name send failures</li>
 *   <li>{@link org.yawlfoundation.yawl.erlang.error.ErlangReceiveException} — decode / receive failures</li>
 * </ul>
 */
package org.yawlfoundation.yawl.erlang.error;
