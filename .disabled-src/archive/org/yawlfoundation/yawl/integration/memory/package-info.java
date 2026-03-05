/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Memory system for YAWL self-upgrading codebase.
 *
 * <p>This package provides persistent storage and learning capture capabilities
 * for tracking upgrade history, success/failure patterns, and agent performance
 * metrics. The memory system enables the codebase to learn from past upgrades
 * and improve future operations.</p>
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.memory.UpgradeMemoryStore} -
 *       Thread-safe storage and retrieval of upgrade history with JSON persistence</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.memory.LearningCapture} -
 *       Captures outcomes and extracts patterns for continuous improvement</li>
 * </ul>
 *
 * <h2>Data Model</h2>
 * <p>Memory data is stored as JSON files in {@code docs/v6/latest/memory/}:</p>
 * <ul>
 *   <li>{@code upgrade_history.json} - Complete history of all upgrade attempts</li>
 *   <li>{@code success_patterns.json} - Extracted patterns from successful upgrades</li>
 *   <li>{@code failure_patterns.json} - Documented failure modes with root causes</li>
 *   <li>{@code agent_metrics.json} - Per-agent performance statistics</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All classes use {@link java.util.concurrent.ConcurrentHashMap} for thread-safe
 * in-memory operations. File writes use atomic file replacement via temporary files
 * and rename operations.</p>
 *
 * <h2>Java 25 Features</h2>
 * <ul>
 *   <li>Records for immutable data types (UpgradeRecord, Pattern, AgentMetric)</li>
 *   <li>Sealed classes for outcome classification (UpgradeOutcome)</li>
 *   <li>Pattern matching in switch expressions for exhaustive handling</li>
 *   <li>Virtual threads via StructuredTaskScope for batch operations</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.integration.memory;
