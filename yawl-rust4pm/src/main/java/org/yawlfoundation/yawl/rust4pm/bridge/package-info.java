/**
 * Layer 2: Typed bridge between raw Panama FFM (Layer 1) and domain types (Layer 3).
 *
 * <p><b>Correct-by-Construction (CbC) Properties</b>:
 * <ul>
 *   <li>Arena lifetime enforcement: {@link java.lang.foreign.MemorySegment#asSlice(long, long)}
 *       automatically throws {@link IllegalStateException} if the owning {@code Arena} is closed,
 *       preventing use-after-free without explicit null checks</li>
 *   <li>{@link java.util.concurrent.atomic.AtomicBoolean closed} ensures idempotent close operations</li>
 *   <li>Per-handle {@code Arena} (allocated in {@link org.yawlfoundation.yawl.rust4pm.bridge.OcelLogHandle})
 *       scopes all borrowed Rust memory segments, making lifetimes observable and automatic</li>
 * </ul>
 *
 * <p><b>Arena Hierarchy</b>:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.rust4pm.bridge.Rust4pmBridge#arena Rust4pmBridge.arena}
 *       — thread-safe shared arena for bridge lifetime</li>
 *   <li>{@link org.yawlfoundation.yawl.rust4pm.bridge.OcelLogHandle#ownedArena OcelLogHandle.ownedArena}
 *       — per-handle shared arena, closed when handle is closed</li>
 *   <li>Per-call {@link java.lang.foreign.Arena#ofConfined() Arena.ofConfined()} —
 *       temporary allocations for interop parameters, closed on method return</li>
 * </ul>
 *
 * <p><b>Responsibilities</b>:
 * <ul>
 *   <li>Native library {@link java.lang.foreign.Arena} lifetime management</li>
 *   <li>Rust error string materialization into checked Java exceptions</li>
 *   <li>Zero-copy access to Rust-owned memory via {@link java.lang.foreign.MemorySegment}</li>
 * </ul>
 *
 * <p><b>Entry point</b>: {@link org.yawlfoundation.yawl.rust4pm.bridge.Rust4pmBridge}
 *
 * <p><b>Thread safety</b>:
 * <ul>
 *   <li>{@code Rust4pmBridge} is thread-safe (shared arena)</li>
 *   <li>{@code OcelLogHandle} is NOT thread-safe (per-handle arena); do not share across threads</li>
 *   <li>{@code OcelEventView} and {@code OcelObjectView} are NOT thread-safe; borrowed memory is scoped to their parent handle</li>
 * </ul>
 */
package org.yawlfoundation.yawl.rust4pm.bridge;
