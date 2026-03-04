/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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
 * LLM client abstraction for DSPy modules.
 *
 * <p>Provides a unified interface for calling different LLM providers
 * (OpenAI, Anthropic, Ollama, etc.) with consistent error handling
 * and token counting.
 *
 * <h2>Core Types:</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.dspy.llm.LlmClient} - Client interface</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.llm.LlmRequest} - Completion request</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.llm.LlmResponse} - Completion response</li>
 *   <li>{@link org.yawlfoundation.yawl.dspy.llm.ChatMessage} - Chat message</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.dspy.llm;
