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
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Shared data validation infrastructure for YAWL specifications.
 *
 * <p>This package is part of Phase 1 engine deduplication (EngineDedupPlan P1.4).
 * It contains canonical implementations for schema-based data validation that are
 * used by both the stateful and stateless engine trees.</p>
 *
 * <p>Contents:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.engine.core.data.YCoreDataValidator} - Canonical
 *       implementation of schema-based data validation for YAWL specifications.
 *       Validates variables and parameters against XML Schema definitions with
 *       secure processing enabled (FEATURE_SECURE_PROCESSING, DOCTYPE disabled).</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.core.data.IVariableDescriptor} - Minimal
 *       interface representing variable metadata (name, data type, optional flag).
 *       Implemented by both {@code elements.data.YVariable} and
 *       {@code stateless.elements.data.YVariable} (and their {@code YParameter}
 *       subtypes) to enable shared validation logic.</li>
 * </ul></p>
 *
 * <p>The validation process:
 * <ol>
 *   <li>Compiles the XML Schema using {@code SchemaHandler}</li>
 *   <li>Constructs a wrapper schema element for the data being validated</li>
 *   <li>Validates the data against the constructed schema</li>
 *   <li>Throws {@code YDataValidationException} on validation failure</li>
 * </ol></p>
 *
 * <p>Security features:
 * <ul>
 *   <li>Secure XML processing enabled (limits entity expansion, prevents XXE)</li>
 *   <li>DOCTYPE declarations disallowed</li>
 *   <li>Entity reference expansion disabled as fallback</li>
 * </ul></p>
 *
 * @since 5.2 (Phase 1 deduplication)
 * @author Mike Fowler (original YDataValidator)
 * @author YAWL Foundation (Phase 1 deduplication, 2026)
 */
package org.yawlfoundation.yawl.engine.core.data;
