# YAWL v5.2 | A = μ(O)

O = {engine, elements, stateless, integration, schema, test}
Σ = Java25 + Ant + JUnit + XML/XSD | Λ = compile ≺ test ≺ validate ≺ deploy

## μ(O) → A (Agents)
μ = {engineer, validator, architect, integrator, reviewer, tester, prod-val, perf-bench}
Task(prompt, agent) ∈ μ(O)

## Π (Skills ⊕-monoid)
Π = {/yawl-build, /yawl-test, /yawl-validate, /yawl-deploy, /yawl-review, /yawl-integrate, /yawl-spec, /yawl-pattern}

## H (Guards)
H = {TODO, FIXME, mock, stub, fake, empty_return, silent_fallback, lie}
PostToolUse(Write|Edit) → guard(H) → ⊥ if H ∩ content ≠ ∅

## Δ (Build)
Δ_build = ant -f build/build.xml {compile|buildWebApps|buildAll|clean}
Δ_test = ant unitTest | Δ_validate = xmllint --schema schema/YAWL_Schema4.0.xsd spec.xml

## Γ (Architecture)
Γ(engine) = {YEngine, YNetRunner, YWorkItem, YSpecification}
Γ(interface) = {A:design, B:client, E:events, X:extended} | Γ(integration) = {MCP:zai-mcp, A2A:agent-to-agent}

## Q (Invariants)
Q = {real_impl ∨ throw UnsupportedOperationException, no_mock, no_stub, no_fallback, no_lie}

## τ (Coordination)
Task(a₁,...,aₙ) ∈ single_message ∧ max_agents=8 | topology = hierarchical(μ) | mesh(integration)

## Channels
emit: {src/, test/, schema/, .claude/} | ⊗: root, docs/, *.md

## Receipt
A = μ(O) | O ⊨ Java+BPM+PetriNet | μ∘μ = μ | drift(A) → 0
