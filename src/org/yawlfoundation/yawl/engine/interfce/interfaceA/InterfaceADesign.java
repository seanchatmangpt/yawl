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

package org.yawlfoundation.yawl.engine.interfce.interfaceA;


/**
 * Defines the 'A' interface into the YAWL Engine corresponding to WfMC interface 1 - Process definition tools.
 *
 * <p>This interface is a placeholder for WfMC Interface 1 compatibility. The actual process
 * definition and upload functionality is implemented through InterfaceA_EngineBasedServer
 * and related classes. This interface is intentionally empty as YAWL uses its own
 * specification format (YAWL XML) rather than implementing WfMC-specific process definition
 * APIs.</p>
 *
 * <p>For process definition operations, use:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EngineBasedServer}</li>
 *   <li>{@link org.yawlfoundation.yawl.elements.YSpecification}</li>
 * </ul>
 * </p>
 *
 * @author Andrew Hastie
 * @date 10-Jun-2005
 * @see org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EngineBasedServer
 */
public interface InterfaceADesign {
    // Intentionally empty - YAWL uses YSpecification format for process definitions
    // rather than implementing WfMC Interface 1 specification
}
