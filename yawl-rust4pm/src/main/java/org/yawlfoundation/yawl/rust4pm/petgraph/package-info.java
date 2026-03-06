/**
 * JNI bindings to petgraph for high-performance graph operations.
 *
 * <p>This package provides {@link org.yawlfoundation.yawl.rust4pm.petgraph.PetriNetGraph},
 * a zero-copy, thread-safe wrapper around Rust's petgraph {@code DiGraph} implementation.
 *
 * <p><strong>Use Cases</strong>:
 * <ul>
 *   <li><strong>Process Mining</strong>: Efficiently represent and query directly-follows graphs
 *       from OCEL2 logs with millions of edges.
 *   <li><strong>Workflow Analysis</strong>: Fast reachability checking and path traversal in
 *       Petri nets.
 *   <li><strong>Conformance Checking</strong>: Token replay and alignment algorithms on
 *       memory-efficient graph structures.
 * </ul>
 *
 * <p><strong>Architecture</strong>:
 * <ul>
 *   <li><strong>Native Layer</strong> (Rust): {@code petgraph-jni} crate provides
 *       {@code DiGraph<Value, Value>} with JNI bindings.
 *   <li><strong>Bridge Layer</strong> (Java): {@code PetriNetGraph} wraps the Rust graph
 *       with thread-safe access and convenient JSON marshaling.
 *   <li><strong>Domain Layer</strong>: {@code DirectlyFollowsGraph} and {@code PetriNet}
 *       models use {@code PetriNetGraph} as an optional high-performance backend.
 * </ul>
 *
 * @see org.yawlfoundation.yawl.rust4pm.petgraph.PetriNetGraph
 * @see org.yawlfoundation.yawl.rust4pm.model.DirectlyFollowsGraph
 */
package org.yawlfoundation.yawl.rust4pm.petgraph;
