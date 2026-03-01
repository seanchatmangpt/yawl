/**
 * Erlang External Term Format (ETF) type hierarchy and codec.
 *
 * <p>The sealed {@link org.yawlfoundation.yawl.erlang.term.ErlTerm} interface permits
 * 13 concrete record types covering all ETF term tags used by OTP 28:</p>
 *
 * <ul>
 *   <li>Scalar: {@code ErlAtom}, {@code ErlInteger}, {@code ErlFloat}, {@code ErlNil}</li>
 *   <li>Binary: {@code ErlBinary}, {@code ErlBitstring}</li>
 *   <li>Compound: {@code ErlList}, {@code ErlTuple}, {@code ErlMap}</li>
 *   <li>Process: {@code ErlPid}, {@code ErlRef}, {@code ErlPort}</li>
 *   <li>Function: {@code ErlFun} (sealed: {@code ErlExternalFun}, {@code ErlClosure})</li>
 * </ul>
 *
 * <p>{@link org.yawlfoundation.yawl.erlang.term.ErlTermCodec} encodes/decodes
 * via {@code ei_h} MethodHandles. All encode paths write the ETF version byte 131.</p>
 *
 * @see org.yawlfoundation.yawl.erlang.term.ErlTerm
 * @see org.yawlfoundation.yawl.erlang.term.ErlTermCodec
 */
package org.yawlfoundation.yawl.erlang.term;
