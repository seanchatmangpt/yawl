/**
 * REST controllers for YAWL MCP-A2A application.
 *
 * <p>This package contains Spring MVC controllers that expose HTTP endpoints
 * for application management, health checks, and metrics.</p>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code /actuator/health} - Application health status</li>
 *   <li>{@code /actuator/prometheus} - Prometheus metrics export</li>
 *   <li>{@code /api/v1/status} - MCP/A2A service status</li>
 *   <li>{@code /api/v1/agent} - Agent card and capabilities</li>
 * </ul>
 *
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.mcp.a2a.controller;
