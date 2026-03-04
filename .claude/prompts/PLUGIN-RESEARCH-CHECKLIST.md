# Plugin Research Session — Execution Checklist

**Session Goal**: Discover all YAWL plugins, identify top 5 for adaptation, build 1 working proof-of-concept.

**Session Owner**: [Next Claude Code session]
**Start Time**: [will be filled in]
**Target End Time**: [+6 hours from start]

---

## Pre-Work (5 minutes)

- [ ] Read `PLUGIN-RESEARCH-QUICKSTART.md` (understand scope)
- [ ] Read `NEXT-SESSION-PLUGIN-RESEARCH.md` (understand phases)
- [ ] Verify codebase is ready: `bash scripts/dx.sh compile` → GREEN
- [ ] Create working directory: `mkdir -p .claude/audit .claude/adapters .claude/doc`
- [ ] Create session log: `.claude/audit/SESSION-LOG.md`

---

## Phase 1: Discovery (Target: 1 hour)

### 1.1 Find All Plugins

- [ ] Run plugin discovery commands (save output to `.claude/audit/discovery-raw-output.txt`):
  ```bash
  find . -name "*.java" | xargs grep -l "implements.*Plugin\|@Plugin\|class.*Plugin"
  find . -name "*plugin*" -type f | grep -E "\.(xml|toml|properties)"
  grep -r "registerPlugin\|addPlugin\|MCP\|A2A" --include="*.java" | head -20
  ```

- [ ] Count total plugins found: _____ plugins

### 1.2 Create Raw Inventory

- [ ] Create `.claude/audit/plugin-inventory-raw.md`:
  - Format: Simple list, 1 plugin per line
  - Include: File path + brief description
  - Target: All plugins listed (don't worry about detail yet)

### 1.3 Categorize by Type

- [ ] Group plugins by type:
  - MCP servers: _____ plugins
  - A2A endpoints: _____ plugins
  - Validators: _____ plugins
  - Hooks: _____ plugins
  - Agents: _____ plugins
  - Other: _____ plugins

**Checkpoint**: Have you found >15 plugins?
- [ ] Yes → Continue
- [ ] No → Re-run discovery, search more carefully

---

## Phase 2: Analysis (Target: 2 hours)

### 2.1 Deep Dive on Top 10 Plugins

For each of the 10 largest/most important plugins:

- [ ] Create analysis file: `.claude/audit/plugin-<name>.md`
- [ ] Copy template: `cp .claude/prompts/PLUGIN-RESEARCH-TEMPLATE.md .claude/audit/plugin-<name>.md`
- [ ] Fill out sections 1-5 of template:
  - [ ] Section 1: Identification (file locations, types)
  - [ ] Section 2: Purpose (what problem it solves)
  - [ ] Section 3: Implementation (config, I/O, errors, tests)
  - [ ] Section 4: Assessment (autonomous integration potential)
  - [ ] Section 5: Opportunity score (0-10)

### 2.2 Analyze Each Plugin

For each plugin, answer these questions:
- [ ] What does it do? (1 sentence)
- [ ] Where is the main code? (file path)
- [ ] What format is input/output? (JSON, XML, etc.)
- [ ] How does it handle errors?
- [ ] Can it be tested in isolation?

### 2.3 Document Findings

- [ ] Create summary: `.claude/audit/plugin-analysis-summary.md`
  - Table with: Name | Purpose | Type | Location | Opportunity Score
  - Sort by opportunity score (highest first)

**Checkpoint**: Have you analyzed 10 plugins?
- [ ] Yes → Continue
- [ ] No → Complete remaining analysis

---

## Phase 3: Classification (Target: 1 hour)

### 3.1 Rank by Adaptation Priority

- [ ] Create `.claude/audit/tier-classification.md`:
  - **Tier 1** (opportunity score 9-10): _____ plugins
    - Names: ________________
  - **Tier 2** (opportunity score 6-8): _____ plugins
    - Names: ________________
  - **Tier 3** (opportunity score 3-5): _____ plugins
    - Names: ________________

### 3.2 Choose Adaptation Target

- [ ] Pick **easiest Tier 1 plugin** for proof-of-concept
  - **Chosen plugin**: ________________
  - **Why easiest?**: [reason]
  - **Estimated effort**: [hours]

### 3.3 Document Strategy

- [ ] Create `.claude/audit/adaptation-strategy.md`:
  - Tier 1 plugins: what order to adapt them
  - Tier 2 plugins: which ones next
  - Tier 3 plugins: nice-to-have
  - Common blockers: what we need to overcome
  - Timeline estimate: how long to adapt all plugins

**Checkpoint**: Have you identified top plugin to adapt?
- [ ] Yes → Continue
- [ ] No → Review tier classification

---

## Phase 4: Proof of Concept (Target: 1.5 hours)

### 4.1 Create Adapter Wrapper

- [ ] Create `.claude/adapters/plugin-<CHOSEN>-adapter.sh`:
  - Takes error receipt as input (`.claude/receipts/error-analysis-receipt.json`)
  - Invokes the actual plugin
  - Generates JSON output receipt
  - Returns proper exit codes (0=success, 2=violation)

- [ ] Copy template:
  ```bash
  cat > .claude/adapters/plugin-<CHOSEN>-adapter.sh <<'ADAPTER_EOF'
  #!/bin/bash
  # Adapter for [PLUGIN_NAME]
  # Bridges analyze-errors.sh → [plugin] → remediate-violations.sh

  set -euo pipefail

  RECEIPT_INPUT="${1:-.claude/receipts/error-analysis-receipt.json}"
  RECEIPT_OUTPUT=".claude/receipts/plugin-<CHOSEN>-receipt.json"

  # TODO: Call actual plugin here
  # TODO: Parse plugin output
  # TODO: Generate JSON receipt

  exit $?
  ADAPTER_EOF
  chmod +x .claude/adapters/plugin-<CHOSEN>-adapter.sh
  ```

### 4.2 Test the Adapter

- [ ] Create `.claude/adapters/plugin-<CHOSEN>-adapter.test`:
  - Test 1: Run with mock error receipt
  - Test 2: Verify output receipt is valid JSON
  - Test 3: Verify exit code is correct
  - Test 4: Verify plugin was actually invoked

- [ ] Run tests:
  ```bash
  bash .claude/adapters/plugin-<CHOSEN>-adapter.test
  ```

- [ ] **Results**: [ ] PASS [ ] FAIL
  - [ ] If PASS: Move to 4.3
  - [ ] If FAIL: Debug and re-run

### 4.3 Verify Integration

- [ ] Does adapter work with `decision-engine.sh`?
  ```bash
  bash .claude/scripts/decision-engine.sh \
    --rule-set agent_selection \
    --input "{\"task\":\"run plugin-<CHOSEN> on violations\"}"
  ```

- [ ] [ ] Yes, produces valid decision
- [ ] [ ] No, needs modification

### 4.4 Document Adapter Usage

- [ ] Create `.claude/adapters/plugin-<CHOSEN>-README.md`:
  - What the adapter does
  - How to use it
  - Example invocation
  - Expected input/output

**Checkpoint**: Have you got 1 working adapter?
- [ ] Yes → Continue
- [ ] No → Debug and retry

---

## Phase 5: Documentation (Target: 0.5 hours)

### 5.1 Create Replication Pattern

- [ ] Create `.claude/doc/PLUGIN-ADAPTATION-GUIDE.md`:
  - Step-by-step: how to adapt a plugin
  - Code examples (from your working adapter)
  - Common pitfalls and how to avoid them
  - Checklist: what every adapter needs

### 5.2 Create Integration Guide

- [ ] Create `.claude/doc/PLUGIN-INTEGRATION-ROADMAP.md`:
  - How to integrate Tier 1 plugins (in order)
  - How to integrate Tier 2 plugins
  - Estimated timeline
  - Resource allocation (how many agents per plugin)

### 5.3 Commit Your Work

- [ ] Stage all files:
  ```bash
  git add .claude/audit/ .claude/adapters/ .claude/doc/
  ```

- [ ] Create comprehensive commit:
  ```bash
  git commit -m "Plugin architecture research & adaptation roadmap

  - Inventoried X plugins (categories, entry points documented)
  - Analyzed Y plugins (deep dive on top 10)
  - Classified by adaptation tier (Tier 1/2/3)
  - Adapted plugin-<CHOSEN> as proof-of-concept
  - Documented replication pattern for future plugins
  - Created integration roadmap for autonomous system

  Deliverables:
  - .claude/audit/plugin-inventory.md (all plugins)
  - .claude/audit/plugin-analysis-summary.md (detail)
  - .claude/audit/tier-classification.md (priority ranking)
  - .claude/adapters/plugin-<CHOSEN>-adapter.sh (working adapter)
  - .claude/doc/PLUGIN-ADAPTATION-GUIDE.md (replication pattern)"
  ```

- [ ] Push to branch:
  ```bash
  git push -u origin claude/rewrite-claude-config-VN59M
  ```

**Checkpoint**: All work committed?
- [ ] Yes → Session complete!
- [ ] No → Complete commits

---

## Post-Work (Self-Assessment)

### Can You Answer These Questions?

1. **How many plugins exist in YAWL?**
   - Answer: _____ plugins
   - Confidence: [ ] High [ ] Medium [ ] Low

2. **Which 5 should be adapted first (Tier 1)?**
   - Answer: __________, __________, __________, __________, __________
   - Confidence: [ ] High [ ] Medium [ ] Low

3. **Which plugin did you adapt?**
   - Answer: __________
   - Does it work? [ ] Yes [ ] No
   - Confidence: [ ] High [ ] Medium [ ] Low

4. **How would you adapt the next plugin?**
   - Answer: [steps from PLUGIN-ADAPTATION-GUIDE.md]
   - Confidence: [ ] High [ ] Medium [ ] Low

### Session Quality Metrics

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| Plugins discovered | 20+ | ___ | ✅/⚠️ |
| Plugins analyzed | 10+ | ___ | ✅/⚠️ |
| Tier 1 classified | 3-5 | ___ | ✅/⚠️ |
| Adapter working | 1 | ___ | ✅/⚠️ |
| Tests passing | 1 suite | ___ | ✅/⚠️ |
| Docs complete | 2 files | ___ | ✅/⚠️ |

### Session Success Criteria

- [ ] **MUST HAVE**: Discovered 20+ plugins
- [ ] **MUST HAVE**: Analyzed top 10 plugins
- [ ] **MUST HAVE**: Created tier classification (Tier 1/2/3)
- [ ] **MUST HAVE**: Built 1 working adapter with tests
- [ ] **MUST HAVE**: Documented replication pattern
- [ ] **NICE TO HAVE**: Integrated adapter with decision-engine
- [ ] **NICE TO HAVE**: Created integration roadmap

**Session Status**: [ ] SUCCESS [ ] PARTIAL [ ] INCOMPLETE

---

## Troubleshooting

### Problem: Can't Find Enough Plugins

**Solution**:
1. Check `.erlmcp/` for Erlang plugins
2. Search for `@Plugin` annotations
3. Look in `yawl/integration/` directory
4. Check MCP servers in `mcp-*.java`
5. Review `pom.xml` for plugin-related dependencies

### Problem: Plugin Analysis Takes Too Long

**Solution**:
1. Use grep + quick code review (don't read entire file)
2. Look at unit tests (they show input/output)
3. Check README or doc comments
4. Don't get perfect, aim for "good enough"

### Problem: Can't Figure Out How to Adapt a Plugin

**Solution**:
1. Start with simplest plugin (smallest file)
2. Wrap it in a bash script (non-intrusive)
3. Read template file and copy structure
4. Ask yourself: "What input does plugin need? What output does it give?"
5. Bridge those 2 points with a script

### Problem: Tests Won't Pass

**Solution**:
1. Run plugin manually to understand behavior
2. Use mock data with known expected output
3. Check exit codes (plugin might return non-zero)
4. Verify JSON syntax with `jq . receipt.json`

---

## Appendix: File Template Reminders

**For each plugin analyzed**, use template:
- `.claude/prompts/PLUGIN-RESEARCH-TEMPLATE.md`

**After analysis**, create:
- `.claude/audit/plugin-<name>.md`

**For adapter**, use structure:
- Input: error-analysis-receipt.json
- Process: invoke plugin
- Output: plugin-<name>-receipt.json
- Exit code: 0 (success), 2 (failure)

---

**Good luck! Remember: Focus on discovery first, depth second. 🚀**
