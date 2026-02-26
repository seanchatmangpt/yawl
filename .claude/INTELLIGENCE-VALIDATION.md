# Intelligence Layer (ι) Validation Guide

**Status**: Implementation complete and pushed to `claude/yawl-intelligence-layer-R9nML`

**What was implemented**:
- Scout: Real HTTP fetch with watermark-based deduplication (curl subprocess)
- Jira: Auto-satisfies acceptance criteria when deltas match declaration names/rule predicates
- Differ: Produces typed Delta objects (Declaration, Rule, Dependency, Behavior, Quad)
- Hooks: Shell integration with proper binary paths and JSON output

---

## Validation Checklist for Next Session

Run these tests to verify the Intelligence Layer is production-ready.

### 1. Build & Unit Tests
```bash
cd /home/user/yawl/rust/yawl-hooks
cargo test --all          # Expected: 8 passed; 0 failed
cargo clippy --all        # Expected: zero warnings
```

**Expected output**:
- Delta tests: serialization, hashing, receipt generation
- Differ tests: Java declarations, TOML dependencies
- Jira tests: ticket markdown generation
- Scout tests: watermark staleness, intelligence MD generation

### 2. Binary Execution

#### Test yawl-jira inject (loads ticket context)
```bash
cd /home/user/yawl
./rust/yawl-hooks/target/release/yawl-jira inject --session
```

**Expected output**:
```json
{
  "additionalContext": "## YAWL Intelligence\n## Active Ticket: YAWL-ι-001\n**Title**: YAWL Intelligence Layer v1.0\n**Priority**: P0 | **Quantum**: MCP/A2A + Engine-semantic\n\n### Acceptance Criteria\n- [✓] All 8 unit tests pass\n- [✓] Both binaries (yawl-jira, yawl-scout) execute correctly\n..."
}
```

#### Test yawl-jira post-write (delta detection)
```bash
./rust/yawl-hooks/target/release/yawl-jira post-write "test.java" "session-test" \
  "public void foo() {}" \
  "public void foo() {} public void bar() {}"
```

**Expected output**:
```json
{
  "timestamp_ns": 1772070440837496265,
  "session_id": "session-test",
  "file_path": "test.java",
  "input_hash": "5c1ba6451750d276a3172fb36acc890fed4b0b92c7977e4831b60e41ac806575",
  "output_hash": "cf4e1a054f9a90ed03910de4f422ee95f1e0a54c16bba41eca0f237f3186c7e2",
  "delta_hash": "d53d18c23212ea7b6300594bb89bce60218f6eff2b9d628b8cc42d3e79bbd5ab",
  "injected": false,
  "delta_count": 1
}
```

Verify: `delta_count >= 1` means diff detected a change (new method `bar()`)

### 3. Hook Integration

#### Test session-start hook
```bash
bash .claude/hooks/intelligence-session-start.sh 2>&1
```

**Expected output**:
- `[✓ Intelligence] Session initialized with ticket context` (if ticket exists)
- `[✓ Intelligence] Scout spawned (async fetch)` (if scout binary found)

#### Test post-edit hook
```bash
bash .claude/hooks/intelligence-post-edit.sh test.java 2>&1
```

**Expected state**:
- Directory created: `.claude/context/deltas/default/`
- Hook exits with code 0 (non-blocking)

### 4. Scout HTTP Fetch

#### Create test targets
```bash
mkdir -p .claude/scout
cat > .claude/scout/targets.toml <<'EOF'
[[target]]
name = "httpbin-json"
url = "https://httpbin.org/json"
semantic_unit = "BehaviorDelta"
watermark_ttl_hours = 1
EOF
```

#### Run scout fetch
```bash
./rust/yawl-hooks/target/release/yawl-scout fetch
```

**Expected output**:
```json
{"status": "success", "fetched": 1, "updated": 1}
```

#### Verify outputs
```bash
# Check live intelligence document was created
ls -la .claude/context/live-intelligence.md
wc -l .claude/context/live-intelligence.md  # Should be > 5 lines

# Check watermark was saved
cat .claude/context/watermarks.json
# Should contain entry for https://httpbin.org/json with:
#   - url
#   - fetched_at_ns (large number, ns since epoch)
#   - content_hash (64-char blake3 hex)
#   - ttl_hours: 1
```

### 5. Watermark Deduplication (prevents thrashing)

#### Run scout fetch again immediately
```bash
./rust/yawl-hooks/target/release/yawl-scout fetch
```

**Expected output**:
```json
{"status": "success", "fetched": 1, "updated": 0}
```

**Key**: `updated: 0` means watermark was fresh (content hash unchanged), so fetch was skipped.

#### Verify via validate command
```bash
./rust/yawl-hooks/target/release/yawl-scout validate
```

**Expected output**:
```json
{"status": "valid", "total_watermarks": 1, "stale": 0}
```

### 6. Ticket Criteria Satisfaction

#### Check ticket state after post-write
```bash
cat .claude/jira/tickets/active.toml | grep -A 10 "acceptance_criteria"
```

**Expected**: Criteria marked as `true` when deltas satisfy them

#### Verify delta receipts saved
```bash
ls -la .claude/context/deltas/session-test/
# Should contain: latest-delta.json (if hooks called it)
```

---

## Success Criteria

All of the following must pass:

✅ `cargo test --all` passes with 8/8 tests
✅ `cargo clippy --all` shows 0 warnings
✅ `yawl-jira inject --session` outputs valid JSON with ticket markdown
✅ `yawl-jira post-write` detects deltas and outputs receipt
✅ `yawl-scout fetch` fetches URL and updates watermark
✅ `yawl-scout fetch` (2nd run) skips fetch (watermark fresh)
✅ `.claude/context/watermarks.json` contains entries
✅ `.claude/context/live-intelligence.md` contains fetched content
✅ `.claude/context/deltas/*/` directories are created
✅ Ticket state persists (acceptance criteria updated)

---

## Failure Recovery

If validation fails:

1. **Build fails**: Check `cargo build --release` output for errors
2. **Tests fail**: Run `cargo test -- --nocapture` to see debug output
3. **Binary crashes**: Check curl availability (`which curl`)
4. **HTTP fetch fails**: Verify network access to httpbin.org
5. **Watermark not created**: Check `.claude/context/` directory permissions

---

## Next Steps After Validation

Once validation passes:

1. **Integration**: Hook binaries into Claude Code web session startup
2. **Production**: Deploy to live environment with real ticket targets
3. **Monitoring**: Track delta receipts and auto-satisfaction metrics
4. **Extension**: Add more semantic patterns (imports, fields, etc.)

---

**Reference**: Implementation details at `rust/yawl-hooks/README.md` (if created)
