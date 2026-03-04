/**
 * QLever Engine API - Layer 3 Pure Java Domain Interface
 *
 * This package provides a high-level, type-safe Java API for interacting with
 * QLever through the Panama FFI bridge. It abstracts away native details and
 * provides familiar Java patterns.
 *
 * <p>API Usage:
 * <pre>{@code
 * // Create engine
 * QLeverEngine engine = QLeverEngineImpl.create(indexPath);
 *
 * // Execute queries
 * boolean result = engine.ask("ASK { ?s ?p ?o }");
 * List<QueryResult> selectResults = engine.select("SELECT ?s WHERE { ?s ?p ?o }");
 * ConstructResult constructResults = engine.construct("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");
 *
 * // Always close resources
 * engine.close();
 * }</pre>
 *
 * @see org.yawlfoundation.yawl.bridge.qlever.engine.QLeverEngine
 * @see org.yawlfoundation.yawl.bridge.qlever.engine.QLeverEngineImpl
 */
@org.netbeans.api.annotations.common.NonNullAPI
package org.yawlfoundation.yawl.bridge.qlever.engine;