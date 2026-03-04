# Plugin Research Template

**Use this template for each plugin you discover**

---

## Plugin: [NAME]

**Status**: ☐ Discovered ☐ Analyzed ☐ Classified ☐ Adapted

**Date Found**: YYYY-MM-DD
**Researcher**: [Your team/agent name]

---

## 1. Identification

### File Locations
- **Main class**: `src/main/java/...`
- **Tests**: `src/test/java/...` or `test/...`
- **Config**: `config/`, `pom.xml`, `.toml`, `.properties`
- **Docs**: `doc/`, `README.md`, `.md`

### Entry Points
```
- Interface: org.yawl.somepackage.SomePluginInterface
- Implementation: org.yawl.somepackage.SomePluginImpl
- Factory: org.yawl.somepackage.SomePluginFactory
- Registration point: where is it registered?
```

### Type Classification
- [ ] MCP Server (Anthropic Model Context Protocol)
- [ ] A2A Endpoint (Agent-to-Agent)
- [ ] Hook (pre/post processing)
- [ ] Validator (H or Q phase)
- [ ] Agent Delegate
- [ ] Integration (external systems)
- [ ] Other: ____________

---

## 2. Purpose & Responsibility

### What Problem Does It Solve?
**One sentence**: [Describe primary purpose]

**Details**:
- Input: [What data/events trigger it?]
- Processing: [What does it do?]
- Output: [What does it produce?]

### Current Status
- [ ] Actively used
- [ ] Legacy/deprecated
- [ ] Experimental
- [ ] Core functionality
- [ ] Optional/plugin

### Dependencies
```
Depends on:
- Library: version
- Plugin: name
- Artifact: org.yawl:artifact-id

Used by:
- Other plugins: names
- Core services: names
```

---

## 3. Current Implementation

### Configuration Format
```
Current config:  [ XML | TOML | YAML | Properties | Java ]
Example config file:
[paste config here]
```

### Input/Output Format
```
Input format:  [ JSON | XML | Java objects | Raw bytes ]
Output format: [ JSON | XML | Java objects | Logging | Files ]

Example input:
[paste example here]

Example output:
[paste example here]
```

### Error Handling
- [ ] Throws exceptions (what types?)
- [ ] Returns error codes (what values?)
- [ ] Logs errors (where?)
- [ ] Produces error receipts (what format?)
- [ ] Other: ____________

### Testing
```bash
# How is it tested today?
Test class: org.yawl.test.SomePluginTest
Test command: mvn test -Dtest=SomePluginTest
```

---

## 4. Autonomous Integration Assessment

### Can It Be Error-Driven?

**Question**: Can this plugin be triggered by an error receipt?
- [ ] Yes (error type: __________)
- [ ] Partially (some error types)
- [ ] No (why? __________)

**If yes**: Which error types should trigger it?
- [ ] Compilation errors
- [ ] Test failures
- [ ] Guard violations (H phase)
- [ ] Invariant violations (Q phase)
- [ ] Custom: __________

### Can It Be Routed by Decision Engine?

**Question**: Can `decision-engine.sh` make routing decisions for this plugin?
- [ ] Yes, simple routing (1 agent type)
- [ ] Yes, complex routing (multiple agent types)
- [ ] Partially (some scenarios)
- [ ] No (why? __________)

**If yes**: What agent types should handle it?
- [ ] yawl-engineer
- [ ] yawl-validator
- [ ] yawl-reviewer
- [ ] yawl-architect
- [ ] yawl-integrator
- [ ] Custom: __________

### Can It Produce JSON Receipts?

**Question**: Can this plugin emit results in JSON format?
- [ ] Already outputs JSON
- [ ] Can be adapted (how? __________)
- [ ] No JSON support (why? __________)

**If yes/adapted**: Receipt schema:
```json
{
  "phase": "plugin-<name>",
  "timestamp": "ISO-8601",
  "status": "GREEN|YELLOW|RED",
  "violations": [],
  "summary": {}
}
```

### Can It Support Team Work?

**Question**: Can multiple agents (team) work on this plugin?
- [ ] Stateless (1 agent per task)
- [ ] Partially (some coordination needed)
- [ ] Stateful (needs team coordination)
- [ ] Not applicable

**If team-capable**: Coordination needed:
- [ ] Mailbox messaging
- [ ] Shared state file
- [ ] Message queue
- [ ] Other: __________

---

## 5. Adaptation Opportunity Score

**Calculate score** (0-10):

| Criterion | Score | Reasoning |
|-----------|-------|-----------|
| **Error-driven capable** (0-2) | | |
| **Routable by decision-engine** (0-2) | | |
| **JSON output capable** (0-2) | | |
| **Team-coordination ready** (0-2) | | |
| **Low adaptation effort** (0-2) | | |
| **TOTAL** | **/10** | |

### Tier Classification

Based on score:
- **9-10**: Tier 1 (Priority) — Adapt ASAP
- **6-8**: Tier 2 (Valuable) — Adapt soon
- **3-5**: Tier 3 (Optional) — Adapt if time permits
- **0-2**: Not Suitable — Skip for now

---

## 6. Adaptation Plan

### What Needs to Change?

- [ ] Add JSON receipt generation
- [ ] Add configuration for autonomous triggering
- [ ] Add error-type mapping
- [ ] Add agent selection logic to decision-engine
- [ ] Add team coordination support
- [ ] Add mailbox message handling
- [ ] Add state persistence
- [ ] Other: __________

### Estimated Effort

- **Configuration changes**: [hours]
- **Code changes**: [hours]
- **Testing**: [hours]
- **Documentation**: [hours]
- **Total**: [hours]

### Blockers

```
Any blockers to adaptation?
- Blocker 1: ______ (severity: high|medium|low)
- Blocker 2: ______ (severity: high|medium|low)
```

---

## 7. Code Locations

### Key Classes
```
- Plugin interface: [full path]
- Main implementation: [full path]
- Factory/registry: [full path]
- Tests: [full path]
- Config handler: [full path]
```

### Code Snippets

#### Entry Point Registration
```java
// Where/how is this plugin registered?
[paste code here]
```

#### Input Handling
```java
// How does it receive input?
[paste code here]
```

#### Error Handling
```java
// How does it handle errors?
[paste code here]
```

#### Output Generation
```java
// What does it output?
[paste code here]
```

---

## 8. Research Notes

### Key Findings
- Finding 1: [detail]
- Finding 2: [detail]
- Finding 3: [detail]

### Questions for Next Session
- Question 1?
- Question 2?
- Question 3?

### Links & References
- [Link 1](url) - Description
- [Link 2](url) - Description
- [Link 3](url) - Description

---

## 9. Adaptation Status

### Proof of Concept
- [ ] Not started
- [ ] In progress
- [ ] Complete (adapter: `.claude/adapters/plugin-<name>-adapter.sh`)
- [ ] Tested
- [ ] Documented

### Files Created
```
.claude/adapters/
├── plugin-<name>-adapter.sh          [wrapper script]
├── plugin-<name>-adapter.test        [test script]
└── plugin-<name>-integration.md      [usage guide]
```

### Test Results
```
Test command: bash .claude/adapters/plugin-<name>-adapter.test
Result: [PASS | FAIL]
Output: [paste test output]
```

---

## 10. Sign-Off

**Researcher**: _______________
**Date Completed**: YYYY-MM-DD
**Ready for Next Phase**: [ ] Yes [ ] No

**Summary** (1-2 sentences):
[Describe plugin, adaptation tier, and key recommendation]

---

## Copy This Template

```bash
# For each new plugin, copy this template:
cp .claude/prompts/PLUGIN-RESEARCH-TEMPLATE.md \
   .claude/audit/plugin-<name>.md

# Then fill it out
nano .claude/audit/plugin-<name>.md
```
