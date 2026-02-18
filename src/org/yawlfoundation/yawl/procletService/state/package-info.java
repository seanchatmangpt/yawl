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
 * Performative state management for Proclet Service.
 *
 * <p>This package provides the state management for performatives (speech acts)
 * that are exchanged between proclets during workflow interactions.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.procletService.state.Performative} - Speech act representation with time, channel, sender, receiver, action, and content</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.state.Performatives} - Collection of performatives</li>
 * </ul>
 *
 * <p>Performative structure: time, channel, sender, receivers, action, content, scope, direction, entity IDs.</p>
 */
package org.yawlfoundation.yawl.procletService.state;
