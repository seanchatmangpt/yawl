/**
 * Panama FFI bindings for embedded QLever SPARQL engine.
 *
 * <p>This package provides Java 25 Foreign Function & Memory API bindings to interface with
 * QLever, a high-performance SPARQL engine that can be embedded in Java applications.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Foreign Function Interface (FFI) for native QLever calls</li>
 *   <li>Memory management with MemorySession/Arena</li>
 *   <li>Type-safe query execution and result handling</li>
 *   <li>Error handling with QLeverFfiException</li>
 * </ul>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * try (QLeverFfiBindings qlever = new QLeverFfiBindings()) {
 *     // Load SPARQL data
 *     qlever.load("PREFIX ex: <http://example.org/>\nINSERT DATA { ex:john ex:name 'John' }");
 *
 *     // Execute query
 *     QLeverResult result = qlever.query("SELECT * WHERE { ?s ?p ?o }");
 *
 *     if (result.isSuccess()) {
 *         System.out.println("Query result: " + result.data());
 *     }
 * }
 * }</pre>
 *
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.qlever;