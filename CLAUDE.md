# YAWL v6.0.0 | A = μ(O)

O = {engine, elements, stateless, integration, schema, test}
Σ = Java25 + Maven + JUnit + XML/XSD | Λ = compile ≺ test ≺ validate ≺ deploy
**Yet Another Workflow Language** - Enterprise BPM/Workflow system based on rigorous Petri net semantics.

## Quick Commands

```bash
# Agent DX — Fast Build-Test Loop (PREFERRED)
bash scripts/dx.sh                       # Compile + test CHANGED modules only
bash scripts/dx.sh compile               # Compile changed modules (fastest feedback)
bash scripts/dx.sh test                  # Test changed modules (assumes compiled)
bash scripts/dx.sh all                   # Compile + test ALL modules
bash scripts/dx.sh -pl yawl-engine       # Target specific module(s)

# Standard Build (Optimized for Java 25)
mvn -T 1.5C clean compile               # Parallel compile (~45 seconds)
mvn -T 1.5C clean package               # Parallel build (~90 seconds, was ~180s)
mvn clean                                # Clean build artifacts

# Test & Validate
mvn -T 1.5C clean test                  # Parallel tests (JUnit 5.14.0 LTS)
xmllint --schema schema/YAWL_Schema4.0.xsd spec.xml  # Validate specifications

# Before Committing
bash scripts/dx.sh all                   # Fast: agent-dx profile, all modules
mvn -T 1.5C clean compile && mvn -T 1.5C clean test  # Full: standard profiles

# Security & Analysis
mvn clean verify -P analysis             # Run static analysis (SpotBugs, PMD, SonarQube)
jdeprscan --for-removal build/libs/yawl.jar  # Detect deprecated APIs
```

## Java 25 Features & Best Practices

**J25 = {records, sealed_classes, pattern_matching, virtual_threads, scoped_values, structured_concurrency}**

### Features in Use
- **Records**: Immutable data types for events, work items, API responses
- **Sealed Classes**: Domain model hierarchies (YElement, YWorkItemStatus, YEvent)
- **Pattern Matching**: Exhaustive switch on event types, sealed class hierarchies
- **Virtual Threads**: Task execution, agent discovery loops (via `Executors.newVirtualThreadPerTaskExecutor()`)
- **Scoped Values**: Context propagation (workflow ID, security context) replaces ThreadLocal
- **Structured Concurrency**: Parallel work item processing with automatic cancellation
- **Text Blocks**: Multi-line XML, JSON, test data (triple-quote syntax)
- **Flexible Constructors**: Final field initialization before `super()` in validation paths

### Performance Wins
- **Compact Object Headers**: -4-8 bytes/object, 5-10% throughput improvement (enabled via `-XX:+UseCompactObjectHeaders`)
- **Parallel Builds**: `-T 1.5C` = 50% faster compilation and testing
- **Virtual Threads**: Thousands of concurrent cases without thread pool exhaustion
- **Startup Optimization**: AOT method profiling via `-XX:+UseAOTCache`
- **GC Tuning**: Generational ZGC/Shenandoah production-ready for large heaps

### Architecture Patterns (See `.claude/ARCHITECTURE-PATTERNS-JAVA25.md`)
1. **Virtual Thread Per Case** - `YNetRunner.continueIfPossible()` runs on dedicated virtual thread
2. **Structured Concurrency** - Parallel work item processing with `StructuredTaskScope.ShutdownOnFailure`
3. **CQRS for Interface B** - Split `InterfaceBClient` into commands (mutations) and queries (reads)
4. **Sealed Events** - Record-based event hierarchy with exhaustive pattern matching
5. **Module Boundaries** - Resolve `engine` vs `stateless.engine` duplication via Java modules
6. **Scoped Values for Context** - Replace ThreadLocal with immutable `ScopedValue<WorkflowContext>`

### Security Compliance
- ✅ **TLS 1.3 Enforced**: Disable TLS 1.2 in production (set via `jdk.tls.disabledAlgorithms`)
- ✅ **Compact Object Headers**: `-XX:+UseCompactObjectHeaders` (now product flag)
- ✅ **SBOM Generation**: `mvn cyclonedx:makeBom` for supply chain security
- ✅ **No Security Manager**: Removed in JDK 24+; use Spring Security or custom RBAC
- ✅ **Deprecated Crypto**: Avoid MD5, SHA-1, DES; require AES-GCM, RSA-3072+, ECDSA
- See `.claude/SECURITY-CHECKLIST-JAVA25.md` for full requirements

### Migration from Java 21
```bash
# Step 1: Compact object headers (free performance win)
-XX:+UseCompactObjectHeaders

# Step 2: Generational ZGC or Shenandoah (if low-latency critical)
# -XX:+UseZGC -XX:ZGenerational=true  (for ultra-low pause times)
# -XX:+UseShenandoahGC               (for medium heaps 8-64GB)

# Step 3: Virtual thread adoption (GenericPartyAgent discovery loop)
# Replace: Thread discoveryThread = new Thread(...)
# With:    discoveryThread = Thread.ofVirtual().name(...).start(...)

# Step 4: Parallel tests (update .mvn/maven.config with -T 1.5C)
```

## System Specification

**O** = {engine, elements, stateless, integration, schema, test}
**Σ** = Java25 + Maven + JUnit + XML/XSD
**Λ** = compile ≺ test ≺ validate ≺ deploy

## μ(O) → A (Agents)

**μ** = {engineer, validator, architect, integrator, reviewer, tester, prod-val, perf-bench}
Task(prompt, agent) ∈ μ(O)

**Agent Roles**:
- **engineer**: Implement features, write code, create tests
- **validator**: Run builds, verify compilation, check tests
- **architect**: Design patterns, define structure, plan refactoring
- **integrator**: Coordinate subsystems, manage dependencies
- **reviewer**: Code quality, security, standards compliance
- **tester**: Write/run tests, verify behavior, regression check
- **prod-val**: Production validation, performance testing
- **perf-bench**: Benchmark performance, identify bottlenecks

## Π (Skills ⊕-monoid)

**Π** = {/yawl-build, /yawl-test, /yawl-validate, /yawl-deploy, /yawl-review, /yawl-integrate, /yawl-spec, /yawl-pattern}

Invoke skills with `/skill-name` - see `.claude/skills/` for implementations.

## H (Guards) - ENFORCED BY HOOKS

**H** = {TODO, FIXME, mock, stub, fake, empty_return, silent_fallback, lie}
PostToolUse(Write|Edit) → guard(H) → ⊥ if H ∩ content ≠ ∅

**What this means**: After every Write/Edit, `.claude/hooks/hyper-validate.sh` checks for 14 anti-patterns.
If ANY guard is detected, the operation is **blocked** (exit 2) with violation details.

**Forbidden Patterns**:
1. Deferred work markers: TODO, FIXME, XXX, HACK
2. Mock/stub method names: mockFetch(), stubValidation()
3. Mock/stub class names: class MockService
4. Mock mode flags: boolean useMockData = true
5. Empty returns: return "";
6. Null returns with stubs: return null; // stub
7. No-op methods: public void method() { }
8. Placeholder constants: DUMMY_CONFIG, PLACEHOLDER_VALUE
9. Silent fallbacks: catch (e) { return mockData(); }
10. Conditional mocks: if (isTestMode) return mock();
11. Fake defaults: .getOrDefault(key, "test_value")
12. Logic skipping: if (true) return;
13. Log instead of throw: log.warn("not implemented")
14. Mock imports in src/: import org.mockito.*

**Your Options**:
- ✅ Implement REAL version (with real dependencies)
- ✅ Throw UnsupportedOperationException("Clear message")
- ❌ NEVER write mock/stub/placeholder code

## Δ (Build System) - Java 25 Optimized

**Δ_dx** = `bash scripts/dx.sh` (agent inner loop: changed-module-only, incremental, 2C parallel, no overhead)
**Δ_build** = mvn -T 1.5C {clean compile | clean package | clean | clean test}
**Δ_test** = mvn -T 1.5C clean test (with JUnit 5.14.0 LTS, parallel execution at method level)
**Δ_validate** = xmllint --schema schema/YAWL_Schema4.0.xsd spec.xml
**Δ_analyze** = mvn clean verify -P analysis (SpotBugs, PMD, SonarQube)

**Build sequence**: clean → compile → test → validate → deploy
**Agent DX loop**: `bash scripts/dx.sh` (auto-detects changed modules, ~5-15s per cycle)
**Fast verification**: `mvn -T 1.5C clean compile` (~45 seconds, parallel)
**Full verification**: `mvn -T 1.5C clean package` (~90 seconds, was ~180s without parallelization)
**With CI caching**: ~50% additional improvement

**Key optimizations**:
- **Module targeting**: `dx.sh` auto-detects git changes, builds only affected modules + dependents
- **Incremental compilation**: No `clean` by default — only recompiles changed files
- **agent-dx profile**: 2C test parallelism, fail-fast, no JaCoCo/javadoc/analysis overhead
- `-T 1.5C` enables parallel execution (1.5 × CPU cores)
- JUnit 5 method-level parallelization via `@Execution(ExecutionMode.CONCURRENT)`
- Maven BOM (Bill of Materials) for dependency alignment
- Profiles: `fast` (no analysis), `agent-dx` (max speed), `analysis` (static checks), `security` (SBOM + scanning)

## Ψ (Observatory) — Observe ≺ Act

**Ψ** = `docs/v6/latest/{facts/, diagrams/, receipts/}` | Ψ_refresh = `bash scripts/observatory/observatory.sh`
**Ψ_read** = Read(`docs/v6/latest/INDEX.md`) → ∀q ∈ Q_codebase: answer(q) = Read(Ψ.facts[q]) ≻ grep(codebase)

**AXIOM**: Context window is finite. Codebase is not. **Read facts, don't explore.**
- 1 fact file ≈ 50 tokens. Grepping for same answer ≈ 5000 tokens. **Compression: 100×.**
- If >3 files needed to answer a question → **build an instrument** (see `.claude/OBSERVATORY.md`)

**Ψ.facts → Q mapping**:
- `modules.json` → what modules? | `reactor.json` → build order, deps
- `shared-src.json` → who owns which `src/` files? | `tests.json` → tests per module (scoped vs visible)
- `dual-family.json` → stateful↔stateless | `duplicates.json` → duplicate FQCNs
- `gates.json` → quality gates (default_active vs profile-gated) | `deps-conflicts.json` → version conflicts
- `maven-hazards.json` → broken M2 artifacts

**Ψ.verify**: `receipts/observatory.json` → SHA256 hashes for all outputs. Stale? Re-run.
**Ψ.extend**: New question? Add `emit_X()` to `scripts/observatory/lib/emit-facts.sh`. See `.claude/OBSERVATORY.md § Φ`.
**Ψ_∞**: Observatory FMEA's itself → `50-risk-surfaces.mmd`. Wrong output? Fix emit, re-run, verify.

## Γ (Architecture)

**Γ(engine)** = {YEngine, YNetRunner, YWorkItem, YSpecification}
**Γ(interface)** = {A:design, B:client, E:events, X:extended}
**Γ(integration)** = {MCP:zai-mcp, A2A:agent-to-agent}
**Γ(stateless)** = {YStatelessEngine, YCaseMonitor, YCaseImporter/Exporter}

**Key Entry Points**:
- `org.yawlfoundation.yawl.engine.YEngine` - Stateful workflow engine
- `org.yawlfoundation.yawl.stateless.YStatelessEngine` - Stateless engine
- `org.yawlfoundation.yawl.elements.YSpecification` - Workflow definitions
- `org.yawlfoundation.yawl.integration.mcp.YawlMcpServer` - MCP server
- `org.yawlfoundation.yawl.integration.a2a.YawlA2AServer` - A2A server

**Documentation**: All 89 packages have `package-info.java` files. Read these for module understanding.

## Q (Invariants)

**Q** = {real_impl ∨ throw UnsupportedOperationException, no_mock, no_stub, no_fallback, no_lie}

**Non-Negotiable Rules**:
1. **Real Implementation OR Throw**: Every public method either does real work or throws UnsupportedOperationException
2. **No Mocks**: No mock objects, mock services, or mock data in production code
3. **No Stubs**: No empty implementations, placeholders, or TODOs
4. **No Silent Fallbacks**: Exceptions must propagate or be explicitly handled (logged + thrown)
5. **No Lies**: Code does exactly what it claims (method names match behavior)

## τ (Coordination)

Task(a₁,...,aₙ) ∈ single_message ∧ max_agents=8
topology = hierarchical(μ) | mesh(integration)

**Parallel Execution**: Launch multiple agents in single message for independent work.
**Sequential Phases**: Use batching for dependent work (compile → test → commit).
**Context Management**: Keep sessions under 70% context via batching and verification checkpoints.

## Ω (Workflow Before Commit)

**CRITICAL**: Before every commit, you MUST:

1. **Compile + Test**: `bash scripts/dx.sh all` (fast) or `mvn -T 1.5C clean compile && mvn -T 1.5C clean test` (full)
2. **Stage Specific Files**: `git add <files>` (no `git add .`)
3. **Commit with Message**: Include session URL
4. **Push to Feature Branch**: `git push -u origin claude/<desc>-<sessionId>`

**Stop Hook**: Runs `.claude/hooks/validate-no-mocks.sh` after response completion.
**Git Check**: Verifies no uncommitted changes remain.

## Γ (Git Protocol) — Toyota Production System Inspired

**ABSOLUTE ZERO FORCE POLICY**:
- **NEVER use `--force` or `--force-with-lease`** unless explicitly instructed
- **NEVER amend commits** that have been pushed - create a new commit instead
- **ALWAYS preserve history** - think Toyota: build quality in, don't hide defects
- **Respect the remote** - if rejected, understand why and adapt

**Git Rules (Like Andon Cord)**:
1. **Stage properly**: `git add <specific-files>` - be precise
2. **Commit atomicity**: One logical change per commit
3. **Push clean**: No working directory changes before push
4. **Respect merges**: If rejected, `git pull` and integrate properly
5. **No history rewriting**: What's done is done - document mistakes as learning

**Why**: Toyota Production System respects every step as valuable. Force pushing hides problems and prevents learning from mistakes.

## Channels

**emit**: {src/, test/, schema/, .claude/} - Modify freely
**⊗**: root, docs/, *.md - Ask before modifying

## References

- **Observatory**: `.claude/OBSERVATORY.md` (instrument protocol — read FIRST before exploring codebase)
- **Observatory Index**: `docs/v6/latest/INDEX.md` (9 facts, 7 diagrams, FMEA table)
- **Best Practices**: `.claude/BEST-PRACTICES-2026.md` (comprehensive 2026 guide)
- **Quick Start**: `.claude/README-QUICK.md` (2-paragraph summary)
- **Standards**: `.claude/HYPER_STANDARDS.md` (detailed guard examples)
- **Agents**: `.claude/agents/AGENTS_REFERENCE.md` (agent specifications)
- **Java 25 Features**: `.claude/JAVA-25-FEATURES.md` (adoption strategy, feature matrix)
- **Architecture Patterns**: `.claude/ARCHITECTURE-PATTERNS-JAVA25.md` (8 patterns with code examples)
- **Build Performance**: `.claude/BUILD-PERFORMANCE.md` (Maven 4.x, JUnit 5/6, parallelization)
- **Security Checklist**: `.claude/SECURITY-CHECKLIST-JAVA25.md` (compliance requirements)

## Receipt

**A** = μ(O) | O ⊨ Java+BPM+PetriNet | μ∘μ = μ | drift(A) → 0

**Verification**: Session setup via `.claude/hooks/session-start.sh` (Maven + H2 database + Observatory)
**Validation**: PostToolUse guards enforce H automatically
**Quality**: Stop hook ensures clean state before completion

## 📊 Observatory Cache Integration

**Automatic on SessionStart**:
- Observatory runs in `--facts` mode (~13s) to generate cached codebase facts
- Facts stored in `docs/v6/latest/facts/` (24 JSON files)
- Receipt with commit hash for staleness checking
- Available immediately when sessions start

**Reading Cached Data**:
```bash
# Full summary (default)
./scripts/read-observatory.sh

# Show specific data types
./scripts/read-observatory.sh facts      # Facts summary only
./scripts/read-observatory.sh diagrams   # Diagrams summary only
./scripts/read-observatory.sh index      # INDEX.md content

# Access raw facts directly
cat docs/v6/latest/facts/modules.json    # Module inventory
cat docs/v6/latest/facts/reactor.json    # Build order
cat docs/v6/latest/facts/tests.json      # Test coverage
```

**Protocol**: Always read facts first before exploring codebase. 1 fact file ≈ 50 tokens vs. 5000 tokens for grep compression (100× savings).
