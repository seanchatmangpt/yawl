/**
 * REST API Controllers for the YAWL Pure Java 25 Agent Engine.
 *
 * This package contains Spring Boot @RestController implementations for:
 * - Agent lifecycle management (AgentController)
 * - Work item queue management (WorkItemController)
 * - Health checks and Kubernetes probes (HealthController)
 *
 * All endpoints return JSON responses and follow REST conventions:
 * - GET: read operations (idempotent)
 * - POST: create operations (201 Created)
 * - PUT: update operations (200 OK or 204 No Content)
 * - DELETE: removal operations (204 No Content)
 *
 * Error responses use standard HTTP status codes:
 * - 400 Bad Request: invalid input
 * - 404 Not Found: resource not found
 * - 500 Internal Server Error: server error
 *
 * @since Java 25
 */
package org.yawlfoundation.yawl.engine.api.controller;
