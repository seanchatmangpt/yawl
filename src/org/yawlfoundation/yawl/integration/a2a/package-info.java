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
 * Agent-to-Agent (A2A) protocol implementation for YAWL.
 * Exposes YAWL workflow engine capabilities as an A2A agent over HTTP REST transport,
 * enabling discovery and invocation by other A2A agents. Provides skills for launching
 * workflows, querying specifications, managing work items, and canceling cases.
 */
package org.yawlfoundation.yawl.integration.a2a;
