/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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
 * SMS module for YAWL workflow notifications.
 *
 * <p>This package provides SMS notification capabilities for YAWL workflows,
 * allowing tasks to send and receive SMS messages via external gateways.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.smsModule.SMSSender} - Main service for sending/receiving SMS via YAWL tasks</li>
 *   <li>{@link org.yawlfoundation.yawl.smsModule.SMSGateway} - Gateway interface for SMS providers</li>
 * </ul>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Two-way SMS interactions</li>
 *   <li>Polling for SMS responses</li>
 *   <li>Integration with InterfaceB for work item handling</li>
 * </ul>
 */
package org.yawlfoundation.yawl.smsModule;
