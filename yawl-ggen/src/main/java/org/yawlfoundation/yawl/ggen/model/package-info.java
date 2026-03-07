/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Model classes for YAWL process generation.
 *
 * <p>This package contains the intermediate representations used in the
 * 3-stage pipeline:
 * <ul>
 *   <li>{@link ProcessSpec} - Structured specification from NL</li>
 *   <li>{@link ProcessGraph} - Graph representation with control flow</li>
 *   <li>{@link YawlSpec} - Final YAWL XML specification</li>
 *   <li>{@link ValidationResult} - Multi-layer validation result</li>
 * </ul>
 *
 * <h2>Pipeline Flow:</h2>
 * <pre>
 * Natural Language
 *       ↓ Stage 1 (DSPy CoT)
 * ProcessSpec (tasks, constraints, OR-joins, cancellation)
 *       ↓ Stage 2 (DSPy CoT)
 * ProcessGraph (nodes, edges, gateways)
 *       ↓ Stage 3 (DSPy Predict)
 * YawlSpec (YAWL XML)
 *       ↓ Validation
 * ValidationResult (XSD + rust4pm + virtual exec)
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see org.yawlfoundation.yawl.ggen.pipeline.YawlGenerator
 */
package org.yawlfoundation.yawl.ggen.model;
