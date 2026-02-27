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
 * PNML (Petri Net Markup Language) parsing and domain model.
 *
 * Provides immutable records representing PNML concepts:
 * - {@link org.yawlfoundation.yawl.integration.processmining.pnml.PnmlPlace}
 * - {@link org.yawlfoundation.yawl.integration.processmining.pnml.PnmlTransition}
 * - {@link org.yawlfoundation.yawl.integration.processmining.pnml.PnmlArc}
 * - {@link org.yawlfoundation.yawl.integration.processmining.pnml.PnmlProcess}
 *
 * {@link org.yawlfoundation.yawl.integration.processmining.pnml.PnmlParser} parses
 * PNML XML documents (from process mining tools like ProM, Celonis, Disco) into
 * domain objects suitable for downstream synthesis and analysis.
 *
 * Usage:
 * <pre>
 * PnmlParser parser = new PnmlParser();
 * PnmlProcess process = parser.parse(pnmlXml);
 * if (process.isValid()) {
 *     // Process is structurally sound
 * }
 * </pre>
 */
package org.yawlfoundation.yawl.integration.processmining.pnml;
