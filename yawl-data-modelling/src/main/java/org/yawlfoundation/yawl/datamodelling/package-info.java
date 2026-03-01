/**
 * YAWL Data Modelling — Native FFM Bridge.
 *
 * <p>Three-layer architecture bridging {@code libdata_modelling_ffi.so}:
 * <ol>
 *   <li>{@code generated} — raw Panama FFM bindings (Layer 1)</li>
 *   <li>{@code bridge} — typed bridge with Arena management (Layer 2)</li>
 *   <li>{@code api} — pure Java service interface (Layer 3)</li>
 * </ol>
 *
 * @see org.yawlfoundation.yawl.datamodelling.DataModellingModule
 */
package org.yawlfoundation.yawl.datamodelling;
