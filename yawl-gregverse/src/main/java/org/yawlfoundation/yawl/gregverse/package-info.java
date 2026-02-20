/**
 * YAWL Gregverse Application Package.
 *
 * <p>This package provides the main Spring Boot application that integrates
 * Gregverse workflow patterns and extended YAWL capabilities with persistence
 * and observability.</p>
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li>{@code config} - Spring configuration classes for beans and properties</li>
 *   <li>{@code controller} - REST controllers for API endpoints</li>
 *   <li>{@code model} - JPA entities and domain models</li>
 *   <li>{@code repository} - Spring Data JPA repositories</li>
 *   <li>{@code service} - Business services for workflow operations</li>
 * </ul>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Extended YAWL workflow patterns</li>
 *   <li>Persistent workflow state management</li>
 *   <li>Health checks and metrics via Spring Actuator</li>
 *   <li>OpenTelemetry tracing integration</li>
 * </ul>
 *
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.gregverse;
