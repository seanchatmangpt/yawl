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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * CONSTRUCT-native agent marketplace for YAWL workflow automation.
 *
 * <p>This package implements a formally typed, multi-dimensional agent exchange where
 * agents, capabilities, and workflows are expressed in the same semantic language,
 * composable by construction, and priced on dimensions that reflect economic value.</p>
 *
 * <h2>The Five Dimensions</h2>
 *
 * <p>Traditional agent marketplaces are one-dimensional: you pay a fixed price for
 * access to a capability that either works or it doesn't. This marketplace operates
 * across five independent axes simultaneously:</p>
 *
 * <ol>
 *   <li><b>Capability</b> ({@link org.yawlfoundation.yawl.integration.autonomous.AgentCapability})
 *       — what the agent does, in domain language. The shallow dimension existing
 *       registries already capture.</li>
 *
 *   <li><b>Ontological coverage</b>
 *       ({@link org.yawlfoundation.yawl.integration.autonomous.marketplace.OntologicalCoverage})
 *       — which RDF namespace IRIs the agent formally understands, which SPARQL Basic
 *       Graph Patterns it can match, and which CONSTRUCT query templates it produces.
 *       Machine-verifiable; not dependent on documentation and hope.</li>
 *
 *   <li><b>Workflow fit</b>
 *       ({@link org.yawlfoundation.yawl.integration.autonomous.marketplace.WorkflowTransitionContract})
 *       — YAWL transition-slot compatibility: input/output place token types and
 *       van der Aalst WCP pattern support. Enables soundness-preserving slot matching.</li>
 *
 *   <li><b>Economic cost</b>
 *       ({@link org.yawlfoundation.yawl.integration.autonomous.marketplace.CoordinationCostProfile})
 *       — CONSTRUCT queries per call, LLM inference ratio, and normalized price per
 *       coordination cycle. Makes the build-vs-infer tradeoff explicitly priceable.</li>
 *
 *   <li><b>Latency</b>
 *       ({@link org.yawlfoundation.yawl.integration.autonomous.marketplace.LatencyProfile})
 *       — p50/p95/p99 at declared graph sizes. Agents competing on narrow ontological
 *       coverage with exceptional latency occupy distinct market positions from
 *       generalist agents.</li>
 * </ol>
 *
 * <h2>ggen.toml as Publication Artifact</h2>
 *
 * <p>The {@link org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplaceSpec}
 * is the Java materialization of {@code ggen.toml}: the TOML manifest's
 * {@code [ontology]} section maps to {@link OntologicalCoverage}, its
 * {@code [[transitions]]} section maps to {@link WorkflowTransitionContract}, and
 * its {@code [performance]} section maps to {@link LatencyProfile}. The
 * {@code ggenTomlHash} field creates an auditable link between published spec and
 * generative artifact — stale specs are detectable without runtime probing.</p>
 *
 * <h2>The Core Query</h2>
 *
 * <p>The primary marketplace operation is
 * {@link org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplace#findForTransitionSlot(TransitionSlotQuery)},
 * which answers: <em>given my YAWL workflow model and my current ontology O, which
 * agents can fill this transition slot while preserving soundness?</em> This is a
 * predicate composition problem — not a search problem — solvable in linear time
 * over the listing set.</p>
 *
 * <h2>Key Types</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplace}
 *       — the marketplace registry and query engine</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplaceSpec}
 *       — the five-dimensional agent specification</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplaceListing}
 *       — a published listing (spec + identity + liveness)</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.marketplace.TransitionSlotQuery}
 *       — a multi-dimensional marketplace query</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
package org.yawlfoundation.yawl.integration.autonomous.marketplace;
