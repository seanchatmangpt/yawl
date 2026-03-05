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
 * WSIF (Web Services Invocation Framework) integration for YAWL.
 *
 * <p><strong>DEPRECATED</strong>: Apache WSIF was abandoned in 2007 and has no modern replacement.
 * This package is kept for backward compatibility but may throw UnsupportedOperationException.</p>
 *
 * <p><strong>Migration path</strong>: Use Jakarta JAX-WS (jakarta.xml.ws) or modern REST APIs instead.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.wsif.WSIFInvoker} - Invoker for WSDL-based web services (deprecated)</li>
 *   <li>{@link org.yawlfoundation.yawl.wsif.WSIFController} - Controller for WSIF operations (deprecated)</li>
 * </ul>
 *
 * @deprecated since 5.2 - Apache WSIF abandoned, use JAX-WS or REST
 */
package org.yawlfoundation.yawl.wsif;
