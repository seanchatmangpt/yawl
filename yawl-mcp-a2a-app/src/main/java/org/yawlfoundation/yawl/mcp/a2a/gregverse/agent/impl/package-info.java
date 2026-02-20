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
 * Concrete implementations of Greg-Verse business advisor agents.
 *
 * <p>This package contains the specific agent implementations that extend
 * {@link org.yawlfoundation.yawl.mcp.a2a.gregverse.AbstractGregVerseAgent}
 * and provide unique personalities, expertise areas, and communication styles
 * based on real-world business strategists and entrepreneurs.</p>
 *
 * <h2>Agent Implementations</h2>
 *
 * <h3>Greg Isenberg Agent</h3>
 * <p>CEO of Late Checkout, AI skills strategist. Embodies the "Skills Era" thesis
 * where products become AI-callable capabilities. Specializes in:</p>
 * <ul>
 *   <li>AI skills strategy - Converting products to AI-callable capabilities</li>
 *   <li>Product vision - Strategic direction and market positioning</li>
 *   <li>Startup advisory - Growth strategies and business model innovation</li>
 *   <li>Idea generation - Systematic ideation based on market signals</li>
 * </ul>
 *
 * <h3>Nicolas Cole Agent</h3>
 * <p>Digital writing expert and personal branding specialist. Known for
 * Ship 30 for 30 and cohort-based courses. Specializes in:</p>
 * <ul>
 *   <li>Digital writing and content creation</li>
 *   <li>Personal brand building</li>
 *   <li>Skill creation and packaging</li>
 *   <li>Category design</li>
 * </ul>
 *
 * <h3>Dickie Bush Agent</h3>
 * <p>Creator economy expert and newsletter growth specialist. Co-founder of
 * Ship 30 for 30. Specializes in:</p>
 * <ul>
 *   <li>Newsletter growth strategies</li>
 *   <li>Cohort-based course design</li>
 *   <li>Creator economy monetization</li>
 *   <li>Audience building</li>
 * </ul>
 *
 * <h3>Justin Welsh Agent</h3>
 * <p>Solopreneur and B2B consultant. Known for LinkedIn strategy and
 * one-person business models. Specializes in:</p>
 * <ul>
 *   <li>Solopreneurship strategies</li>
 *   <li>B2B consulting frameworks</li>
 *   <li>LinkedIn content strategy</li>
 *   <li>Productized services</li>
 * </ul>
 *
 * <h3>Dan Romero Agent</h3>
 * <p>Agent internet pioneer and API-first design advocate. Former Farcaster.
 * Specializes in:</p>
 * <ul>
 *   <li>Agent internet architecture</li>
 *   <li>API-first product design</li>
 *   <li>Protocol thinking</li>
 *   <li>Decentralized systems</li>
 * </ul>
 *
 * <h3>Blake Anderson Agent</h3>
 * <p>Gamification and life optimization expert. Focus on quest-based
 * goal achievement. Specializes in:</p>
 * <ul>
 *   <li>Gamification design</li>
 *   <li>Life optimization systems</li>
 *   <li>Quest design for goal achievement</li>
 *   <li>Behavioral design</li>
 * </ul>
 *
 * <h3>James Agent</h3>
 * <p>SEO and positioning specialist. Focus on conversion-focused content.
 * Specializes in:</p>
 * <ul>
 *   <li>SEO analysis and strategy</li>
 *   <li>Market positioning</li>
 *   <li>Newsletter optimization</li>
 *   <li>Conversion copywriting</li>
 * </ul>
 *
 * <h3>Leo (LeoLeojrr) Agent</h3>
 * <p>App development and curation strategist. Focus on filtering and
 * information management. Specializes in:</p>
 * <ul>
 *   <li>App development strategy</li>
 *   <li>Curation methodology</li>
 *   <li>Information filtering systems</li>
 *   <li>Product curation</li>
 * </ul>
 *
 * <h2>Creating New Agents</h2>
 * <p>To create a new Greg-Verse agent:</p>
 * <ol>
 *   <li>Extend {@link org.yawlfoundation.yawl.mcp.a2a.gregverse.AbstractGregVerseAgent}</li>
 *   <li>Implement all required methods (getAgentId, getDisplayName, getBio, etc.)</li>
 *   <li>Define a comprehensive system prompt that captures the persona</li>
 *   <li>Create AgentSkill definitions for each specialized capability</li>
 *   <li>Register the agent in GregVerseSimulation.AGENT_REGISTRY</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see org.yawlfoundation.yawl.mcp.a2a.gregverse.AbstractGregVerseAgent
 * @see org.yawlfoundation.yawl.mcp.a2a.gregverse.GregVerseAgent
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.agent.impl;
