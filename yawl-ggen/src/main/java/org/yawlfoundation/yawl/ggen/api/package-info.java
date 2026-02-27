/*
 * Copyright 2004-2026 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * REST API Layer for Process Conversion
 *
 * Provides a modern REST API layer for converting and generating workflow processes
 * using servlet endpoints, job queues, and asynchronous processing capabilities.
 * This package implements scalable workflow management with Java 25 features:
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>RESTful endpoints for workflow conversion (YAWL, BPMN, etc.)</li>
 *   <li>Asynchronous job processing with CompletableFuture</li>
 *   <li>Virtual thread-based execution for high throughput</li>
 *   <li>Sealed endpoint classes for security guarantees</li>
 *   <li>Records for immutable API request/response objects</li>
 *   <li>Structured concurrency for error handling</li>
 * </ul>
 *
 * <h3>Architecture:</h3>
 * <pre>{@code
 * ┌─────────────────────────────────────────────────────┐
 * │              REST API Layer                          │
 * │                                                     │
 * │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │
 * │  │   Workflow  │  │   Job       │  │   Async     │  │
 * │  │  Converter │  │   Queue     │  │   Processor │  │
 * │  └─────────────┘  └─────────────┘  └─────────────┘  │
 * │                                                     │
 * │  ┌─────────────────────────────────────────────────┐  │
 * │  │            Servlet Endpoints                     │  │
 * │  │  (Virtual Thread Per Request)                    │  │
 * │  └─────────────────────────────────────────────────┘  │
 * └─────────────────────────────────────────────────────┘
 * }</pre>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * // Using virtual threads in servlet endpoints
 * @Sealed
 * public final class WorkflowConverterServlet extends HttpServlet {
 *     private final ExecutorService virtualExecutor =
 *         Executors.newVirtualThreadPerTaskExecutor();
 *
 *     @Override
 *     protected void doPost(HttpServletRequest req, HttpServletResponse resp)
 *         throws ServletException, IOException {
 *
 *         var future = virtualExecutor.submit(() -> {
 *             var converter = new WorkflowConverter();
 *             return converter.convert(parseRequest(req));
 *         });
 *
 *         try {
 *             var result = future.get(30, TimeUnit.SECONDS);
 *             sendResponse(resp, result);
 *         } catch (TimeoutException e) {
 *             sendError(resp, "Conversion timeout", 504);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>Supported Endpoints:</h3>
 * <ul>
 *   <li>{@code POST /api/convert/yawl} - Convert to YAWL format</li>
 *   <li>{@code POST /api/convert/bpmn} - Convert to BPMN format</li>
 *   <li>{@code GET /api/jobs/{id}} - Check job status</li>
 *   <li>{@code GET /api/jobs} - List all jobs</li>
 *   <li>{@code DELETE /api/jobs/{id}} - Cancel job</li>
 * </ul>
 *
 * @since 6.0.0-GA
 * @see org.yawlfoundation.yawl.ggen.memory
 * @see org.yawlfoundation.yawl.ggen
 */
package org.yawlfoundation.yawl.ggen.api;