/**
 * Layer 2: Typed bridge between raw Panama FFM (Layer 1) and domain types (Layer 3).
 *
 * <p>This package manages:
 * <ul>
 *   <li>Native library {@link java.lang.foreign.Arena} lifetimes</li>
 *   <li>Rust error string materialization into checked Java exceptions</li>
 *   <li>Zero-copy access to Rust-owned memory via {@link java.lang.foreign.MemorySegment}</li>
 * </ul>
 *
 * <p>Entry point: {@link org.yawlfoundation.yawl.rust4pm.bridge.Rust4pmBridge}
 */
package org.yawlfoundation.yawl.rust4pm.bridge;
