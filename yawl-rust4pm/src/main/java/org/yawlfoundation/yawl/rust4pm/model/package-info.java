/**
 * Layer 2 domain model types for OCEL2 process mining.
 *
 * <p>All types are immutable Java records. Native memory is materialized into
 * Java objects here — zero-copy field reads via {@link java.lang.foreign.MemorySegment}.
 *
 * <p>Sealed interfaces enforce exhaustive pattern matching:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.rust4pm.model.OcelValue} — attribute values</li>
 *   <li>{@link org.yawlfoundation.yawl.rust4pm.model.ProcessModel} — discovered process models</li>
 * </ul>
 */
package org.yawlfoundation.yawl.rust4pm.model;
