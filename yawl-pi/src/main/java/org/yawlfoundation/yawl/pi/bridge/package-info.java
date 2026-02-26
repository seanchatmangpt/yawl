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
 * Event Data Preparation bridge package (Connection 5 of PI architecture).
 *
 * <p>Converts proprietary event log formats (CSV, JSON, XML) to OCEL2
 * (Object-Centric Event Log v2.0) format for process mining ingestion.
 *
 * <p>Key classes:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.pi.bridge.OcedBridgeFactory} - Factory for format-specific bridges</li>
 *   <li>{@link org.yawlfoundation.yawl.pi.bridge.OcedBridge} - Bridge interface</li>
 *   <li>{@link org.yawlfoundation.yawl.pi.bridge.EventDataValidator} - OCEL2 validation</li>
 *   <li>{@link org.yawlfoundation.yawl.pi.bridge.SchemaInferenceEngine} - AI schema inference</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.pi.bridge;
