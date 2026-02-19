/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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
 * ZAI integration test package.
 *
 * Contains comprehensive test suite for Z.AI integration functionality
 * including XML generation, validation, workflow instantiation, and data processing.
 *
 * All tests use real Z.AI API when available, gracefully degrade when API is not available.
 * Follows Chicago TDD methodology with no mocks in production code.
 */
package org.yawlfoundation.yawl.integration.zai;