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
 * Provides for maintaining the internal state and markings of nets via their tokens.
 *
 * <p><b>Since:</b> 6.0.0-GA</p>
 * <p>This package implements scoped values for thread-safe state management,
 * replacing thread-local variables with immutable, automatically inherited
 * context across virtual thread boundaries.</p>
 * <p>Enhanced in version 6.0.0-GA with Java 25 scoped values for improved
 * concurrency safety and performance in token-based state management.</p>
 */
package org.yawlfoundation.yawl.elements.state;