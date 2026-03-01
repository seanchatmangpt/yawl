/**
 * Layer 3: Domain API for process mining operations.
 *
 * <p>No Panama FFM types leak through here: no {@link java.lang.foreign.MemorySegment},
 * no {@link java.lang.foreign.Arena}, no raw pointers.
 * Callers see only Java records, sealed interfaces, and checked exceptions.
 *
 * <p>Entry point: {@link org.yawlfoundation.yawl.processmining.ProcessMiningEngine}
 */
package org.yawlfoundation.yawl.processmining;
