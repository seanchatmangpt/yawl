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
 * JAX-RS (Jakarta RESTful Web Services) implementations of YAWL engine interfaces.
 *
 * <p>This package provides modern REST API endpoints using JAX-RS 3.1 (Jakarta EE 10)
 * to replace legacy servlet-based implementations. The REST APIs maintain backward
 * compatibility while offering cleaner, annotation-based resource definitions.</p>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.engine.interfce.rest.YawlRestApplication} -
 *       Application configuration registered at /api</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.interfce.rest.InterfaceBRestResource} -
 *       Client API for work item and case operations (fully implemented)</li>
*   <li>{@link org.yawlfoundation.yawl.engine.interfce.rest.InterfaceARestResource} -
 *       Design API for specification management (delegating to EngineGateway)</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.interfce.rest.InterfaceERestResource} -
 *       Events API for log queries (delegating to YLogServer and EngineGateway)</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.interfce.rest.InterfaceXRestResource} -
 *       Extended API for advanced operations (delegating to EngineGateway)</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.interfce.rest.YawlExceptionMapper} -
 *       Exception mapper for consistent error handling</li>
 * </ul>
 *
 * <h2>API Endpoints</h2>
 * <p>All REST endpoints are mounted under <code>/api</code>:</p>
 * <ul>
 *   <li><code>/api/ib/*</code> - Interface B (Client operations)</li>
 *   <li><code>/api/ia/*</code> - Interface A (Design operations)</li>
 *   <li><code>/api/ie/*</code> - Interface E (Events operations)</li>
 *   <li><code>/api/ix/*</code> - Interface X (Extended operations)</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>
 * // Connect to engine
 * POST /api/ib/connect
 * Body: {"userid": "admin", "password": "&lt;your-password&gt;"}
 * Response: {"sessionHandle": "abc123..."}
 *
 * // Get work items
 * GET /api/ib/workitems?sessionHandle=abc123
 * Response: [{"id": "item1", "status": "enabled", ...}, ...]
 *
 * // Checkout work item
 * POST /api/ib/workitems/item1/checkout?sessionHandle=abc123
 * Response: XML data for work item
 *
 * // Complete work item
 * POST /api/ib/workitems/item1/complete?sessionHandle=abc123
 * Content-Type: application/xml
 * Body: &lt;data&gt;...&lt;/data&gt;
 * Response: {"status": "completed"}
 * </pre>
 *
 * <h2>Migration Strategy</h2>
 * <p>The REST APIs run alongside legacy servlet-based interfaces for backward
 * compatibility. Clients can migrate gradually:</p>
 * <ul>
 *   <li>Legacy endpoints: <code>/yawl/ib</code>, <code>/yawl/ia</code>, etc.</li>
 *   <li>New endpoints: <code>/yawl/api/ib</code>, <code>/yawl/api/ia</code>, etc.</li>
 * </ul>
 *
 * <h2>Technology Stack</h2>
 * <ul>
 *   <li>Jakarta RESTful Web Services (JAX-RS) 3.1.0</li>
 *   <li>Jersey 3.1.5 (Reference Implementation)</li>
 *   <li>HK2 3.0.5 (Dependency Injection)</li>
 *   <li>Jackson 2.18.2 (JSON Processing)</li>
 * </ul>
 *
 * @author Michael Adams
 * @since 5.2
 */
package org.yawlfoundation.yawl.engine.interfce.rest;
