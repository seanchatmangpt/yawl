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
 * Multi-layer validation for YAWL specifications.
 *
 * <p>This package provides three validation layers:
 * <ol>
 *   <li><b>XSD Validation</b>: Schema conformance checking</li>
 *   <li><b>rust4pm Soundness</b>: Deadlock and lack of sync detection via JOR4J</li>
 *   <li><b>Virtual Execution</b>: Token flow simulation</li>
 * </ol>
 *
 * <h2>Validation Flow:</h2>
 * <pre>
 * YawlSpec
 *     ↓ XSD Schema
 *     ↓ rust4pm Soundness (via ProcessMining)
 *     ↓ Virtual Execution
 * ValidationResult
 * </pre>
 *
 * <h2>Usage with JOR4J:</h2>
 * <pre>{@code
 * try (ProcessMining pm = ProcessMining.connect("yawl_erl@localhost", "secret")) {
 *     YawlValidator validator = new YawlValidator(pm);
 *
 *     ValidationResult result = validator.validate(spec);
 *
 *     if (result.valid()) {
 *         // Safe to load into YEngine
 *     } else {
 *         // Fix issues: result.deadlocks(), result.lackOfSync()
 *     }
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see org.yawlfoundation.yawl.ggen.validation.YawlValidator
 */
package org.yawlfoundation.yawl.ggen.validation;
