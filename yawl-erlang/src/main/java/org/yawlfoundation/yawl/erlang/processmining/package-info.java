/**
 * Layer 3: Domain API for YAWL workflow and process mining operations.
 *
 * <p>{@link org.yawlfoundation.yawl.erlang.processmining.ErlangBridge} is the single
 * entry point for all application code. It exposes zero Panama FFM types — all parameters
 * and return values are standard Java types (String, Map, List, record types).</p>
 *
 * <p>Internally, {@code ErlangBridge} delegates to {@code ErlangNode} (Layer 2) which
 * uses {@code ei_h} MethodHandles (Layer 1) to call the Erlang gen_servers:</p>
 * <ul>
 *   <li>{@code yawl_workflow} — case lifecycle: launch, status, workitem completion</li>
 *   <li>{@code yawl_process_mining} — DFG discovery and token replay conformance</li>
 *   <li>{@code yawl_event_relay} — event subscription via gen_event</li>
 * </ul>
 *
 * @see org.yawlfoundation.yawl.erlang.processmining.ErlangBridge
 */
package org.yawlfoundation.yawl.erlang.processmining;
