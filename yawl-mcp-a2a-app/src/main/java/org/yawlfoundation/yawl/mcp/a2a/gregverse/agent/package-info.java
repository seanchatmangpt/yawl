/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Greg-Verse agent framework for A2A business advisor implementations.
 *
 * <p>This package provides the foundation for Greg-Verse agents, which are
 * AI-powered business advisors that embody expertise from renowned strategists,
 * creators, and entrepreneurs. Each agent offers specialized advice through
 * the A2A (Agent-to-Agent) protocol.</p>
 *
 * <h2>Architecture</h2>
 * <p>The agent framework follows a classic inheritance pattern:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.mcp.a2a.gregverse.GregVerseAgent} - Core interface defining agent capabilities</li>
 *   <li>{@link org.yawlfoundation.yawl.mcp.a2a.gregverse.AbstractGregVerseAgent} - Base class with common LLM integration</li>
 *   <li>{@code agent.impl} package - Concrete agent implementations</li>
 * </ul>
 *
 * <h2>Available Agents</h2>
 * <ul>
 *   <li><strong>Greg Isenberg</strong> - AI skills strategy, product vision, startup advisory</li>
 *   <li><strong>James</strong> - SEO analysis, positioning, newsletter, conversion copy</li>
 *   <li><strong>Nicolas Cole</strong> - Skill creation, digital writing, personal brand</li>
 *   <li><strong>Dickie Bush</strong> - Creator economy, newsletter growth, cohort courses</li>
 *   <li><strong>Leo</strong> - App development, curation strategy, filtering</li>
 *   <li><strong>Justin Welsh</strong> - Solopreneurship, B2B consulting, LinkedIn strategy</li>
 *   <li><strong>Dan Romero</strong> - Agent internet, API-first design, protocol thinking</li>
 *   <li><strong>Blake Anderson</strong> - Gamification, life optimization, quest design</li>
 * </ul>
 *
 * <h2>Integration Points</h2>
 * <ul>
 *   <li>A2A Protocol - AgentCard and AgentSkill definitions for discoverability</li>
 *   <li>ZaiService - LLM integration for generating responses</li>
 *   <li>Simulation Engine - Multi-agent scenario orchestration</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create an agent using environment variable for API key
 * GregVerseAgent agent = new GregIsenbergAgent();
 *
 * // Or with explicit API key
 * GregVerseAgent agent = new GregIsenbergAgent("your-api-key");
 *
 * // Process a query
 * String response = agent.processQuery("How do I validate my startup idea?");
 *
 * // Get advice on a specific topic
 * String advice = agent.provideAdvice("product-market fit", "B2B SaaS startup");
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see org.yawlfoundation.yawl.mcp.a2a.gregverse.GregVerseAgent
 * @see org.yawlfoundation.yawl.mcp.a2a.gregverse.AbstractGregVerseAgent
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.agent;
