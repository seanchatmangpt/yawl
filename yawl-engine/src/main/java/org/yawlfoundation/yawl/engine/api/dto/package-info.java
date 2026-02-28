/**
 * Data Transfer Objects (DTOs) for REST API communication.
 *
 * This package contains immutable Java records used for:
 * - Request/response serialization via Jackson JSON
 * - Type-safe API contracts between client and server
 * - Built-in equals/hashCode/toString via record synthesis
 *
 * Response DTOs:
 * - AgentDTO: Agent state and metrics
 * - WorkItemDTO: Work item details
 * - MetricsDTO: Engine-wide performance metrics
 * - HealthDTO: Health check responses
 *
 * Request DTOs:
 * - WorkflowDefDTO: Workflow specification for agent creation
 * - WorkItemCreateDTO: Work item creation request
 *
 * All DTOs include:
 * - Constructor validation via compact constructor
 * - Factory methods for common creation patterns
 * - Helper methods for formatted output (e.g., getFormattedUptime)
 * - Null-safety checks and bounds validation
 *
 * @since Java 25 (records)
 */
package org.yawlfoundation.yawl.engine.api.dto;
