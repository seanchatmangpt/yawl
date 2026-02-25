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
 * Java 11 → 25 migration pipeline — ontology-driven codebase transformation.
 *
 * <h2>Architecture</h2>
 * <p>The full pipeline is specified in {@code ggen-java-migration.toml} and orchestrated
 * by YAWL ({@code exampleSpecs/JavaMigrationPipeline.xml}). This package provides the
 * Java implementation of the pipeline phases:</p>
 *
 * <ul>
 *   <li><strong>Phase 1 (Analyze)</strong>: parse Java source → RDF codebase graph
 *       (vocabulary: {@code ontology/migration/java-code.ttl})</li>
 *   <li><strong>Phase 2 (Detect)</strong>: SPARQL SELECT → {@link Java11Pattern} instances
 *       (queries: {@code query/migration/detect-*.sparql})</li>
 *   <li><strong>Phase 3 (Plan)</strong>: SPARQL CONSTRUCT → {@link Java25Pattern} instances
 *       (queries: {@code query/migration/construct-*.sparql})</li>
 *   <li><strong>Phase 4 (Generate)</strong>: Tera templates → Java 25 replacement code
 *       (templates: {@code templates/java25-migration/*.tera})</li>
 *   <li><strong>Phase 5 (Validate)</strong>: compile + test generated code</li>
 *   <li><strong>Phase 6 (Commit)</strong>: apply validated migrations, create PR</li>
 * </ul>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link Java11Pattern} — detected Java 11 source pattern (record)</li>
 *   <li>{@link Java25Pattern} — derived Java 25 target pattern (record)</li>
 *   <li>{@link MigrationPlan} — complete migration plan aggregating all patterns</li>
 *   <li>{@link JavaMigrationAnalyzer} — Phase 2: detects Java 11 patterns in source</li>
 *   <li>{@link MigrationPlanBuilder} — Phase 3: constructs migration plan from patterns</li>
 * </ul>
 *
 * <h2>Ontology References</h2>
 * <ul>
 *   <li>{@code ontology/migration/java-code.ttl} — Java code RDF vocabulary</li>
 *   <li>{@code ontology/migration/java11-patterns.ttl} — Java 11 pattern classes</li>
 *   <li>{@code ontology/migration/java25-patterns.ttl} — Java 25 target pattern classes</li>
 *   <li>{@code ontology/migration/migration-rules.ttl} — rule instances (R01–R13)</li>
 *   <li>{@code ontology/migration/yawl-migration-workflow.ttl} — YAWL workflow ontology</li>
 * </ul>
 *
 * <h2>Pipeline Entry Point</h2>
 * <pre>{@code
 * JavaMigrationAnalyzer analyzer = new JavaMigrationAnalyzer();
 * List<Java11Pattern> patterns = analyzer.analyzeDirectory(Path.of("src/"));
 *
 * MigrationPlanBuilder builder = new MigrationPlanBuilder();
 * MigrationPlan plan = builder.build("src/", fileCount, patterns);
 *
 * System.out.println(plan.summarize());
 * }</pre>
 */
package org.yawlfoundation.yawl.mcp.a2a.migration;
