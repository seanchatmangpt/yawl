/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Package for GregVerse marketplace artifacts.
 *
 * <p>This package contains artifact classes for services delivered through the
 * GregVerse marketplace. Artifacts are the deliverables produced by service
 * providers and published to the marketplace for consumer access.</p>
 *
 * <h2>Artifact Types</h2>
 * <ul>
 *   <li>TherapyPlan - Comprehensive occupational therapy plans</li>
 *   <li>ProgressReport - Service outcome summaries and recommendations</li>
 *   <li>ArtifactPublisher - Component for publishing artifacts</li>
 * </ul>
 *
 * <h2>Publishing Workflow</h2>
 * <ol>
 *   <li>Create artifact with metadata</li>
 *   <li>Validate artifact content</li>
 *   <li>Store in configured backend</li>
 *   <li>Update marketplace catalog</li>
 *   <li>Notify stakeholders</li>
 * </ol>
 *
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.artifacts;