# YAWL v5.2 | A = μ(O)

O = {engine, elements, stateless, integration, schema, test}
Σ = Java25 + Maven + JUnit + XML/XSD | Λ = compile ≺ test ≺ validate ≺ deploy
**Yet Another Workflow Language** - Enterprise BPM/Workflow system based on rigorous Petri net semantics.

## Quick Commands

```bash
# Build
mvn clean compile                        # Compile source code
mvn clean package                        # Full build (compile + test + package)
mvn clean                                # Clean build artifacts

# Test & Validate
mvn clean test                           # Run JUnit test suite (required before commit)
xmllint --schema schema/YAWL_Schema4.0.xsd spec.xml  # Validate specifications

# Before Committing
mvn clean compile && mvn clean test      # Build + test (must pass)
```

## System Specification

**O** = {engine, elements, stateless, integration, schema, test}
**Σ** = Java21 + Maven + JUnit + XML/XSD
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

## Δ (Build System)

**Δ_build** = mvn {clean compile | clean package | clean | clean test}
**Δ_test** = mvn clean test
**Δ_validate** = xmllint --schema schema/YAWL_Schema4.0.xsd spec.xml

**Build sequence**: clean → compile → test → validate → deploy
**Fast verification**: `mvn clean compile` (~45 seconds)
**Full verification**: `mvn clean package` (~90 seconds)

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

1. **Compile**: `mvn clean compile` (must succeed)
2. **Test**: `mvn clean test` (must pass 100%)
3. **Stage Specific Files**: `git add <files>` (no `git add .`)
4. **Commit with Message**: Include session URL
5. **Push to Feature Branch**: `git push -u origin claude/<desc>-<sessionId>`

**Stop Hook**: Runs `.claude/hooks/validate-no-mocks.sh` after response completion.
**Git Check**: Verifies no uncommitted changes remain.

## Channels

**emit**: {src/, test/, schema/, .claude/} - Modify freely
**⊗**: root, docs/, *.md - Ask before modifying

## References

- **Best Practices**: `.claude/BEST-PRACTICES-2026.md` (comprehensive 2026 guide)
- **Quick Start**: `.claude/README-QUICK.md` (2-paragraph summary)
- **Standards**: `.claude/HYPER_STANDARDS.md` (detailed guard examples)
- **Agents**: `.claude/agents/definitions.md` (agent specifications)

## Receipt

**A** = μ(O) | O ⊨ Java+BPM+PetriNet | μ∘μ = μ | drift(A) → 0

**Verification**: Session setup via `.claude/hooks/session-start.sh` (Maven + H2 database)
**Validation**: PostToolUse guards enforce H automatically
**Quality**: Stop hook ensures clean state before completion
