/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

/**
 * Conflict Resolution System for YAWL Multi-Agent Workflows.
 *
 * This package provides a comprehensive conflict resolution framework that enables
 * autonomous agents to resolve disagreements through configurable strategies:
 *
 * <h3>Core Components:</h3>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.conflict.ConflictResolver} - Core interface for conflict resolution strategies</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.conflict.MajorityVoteConflictResolver} - Majority vote implementation</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.conflict.EscalatingConflictResolver} - Escalation to arbiter implementation</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.conflict.HumanFallbackConflictResolver} - Human fallback implementation</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.conflict.ConflictResolutionService} - Central coordination service</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.conflict.ConflictResolutionIntegrationService} - Integration with YAWL framework</li>
 * </ul>
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li><strong>Multiple Resolution Strategies:</strong> Majority vote, human escalation, and human fallback</li>
 *   <li><strong>Severity-Based Handling:</strong> Automatic routing based on conflict severity</li>
 *   <li><strong>Async Processing:</strong> Non-blocking conflict resolution with futures</li>
 *   <li><strong>Monitoring & Metrics:</strong> Comprehensive tracking and reporting</li>
 *   <li><strong>Extensible Architecture:</strong> Easy to add new resolution strategies</li>
 *   <li><strong>YAWL Integration:</strong> Seamless integration with autonomous agents and workflows</li>
 * </ul>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // Create conflict context
 * List<AgentDecision> decisions = ...; // Get from agents
 * ConflictContext context = new ConflictContext(
 *     "conflict-123", "workflow-456", "task-789",
 *     ConflictResolver.Severity.MEDIUM, decisions, Map.of()
 * );
 *
 * // Resolve conflict
 * ConflictResolutionService service = ConflictResolutionService.getInstance();
 * ResolutionResult result = service.resolveConflict(context);
 * }</pre>
 *
 * <h3>Configuration:</h3>
 * <p>All resolvers support runtime configuration through updateConfiguration().
 * Common parameters include thresholds, timeouts, and behavioral flags.</p>
 *
 * <h3>Thread Safety:</h3>
 * <p>All components are designed for concurrent access with proper synchronization.
 * The integration service maintains thread-safe registries and pending operations tracking.</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @since 5.2
 */