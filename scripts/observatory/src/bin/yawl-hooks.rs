//! YAWL Hook Handler — Omnipresent Rust binary for all Claude Code hook events.
//!
//! GODSPEED!!! Embodies the attributes of Logos:
//!
//! • OMNISCIENCE   — UserPromptSubmit:   injects Ψ facts before every prompt (65ms warm)
//! • OMNIPOTENCE   — PreToolUse:         warns on shared-src blast radius before any write;
//!                                       guards destructive Bash commands (Ω gate)
//! • PROVIDENCE    — PostToolUse:        async observatory refresh; build result injection
//! • JUSTICE       — Stop:               quality summary + git state before completion
//! • ETERNITY      — PreCompact:         Ψ truth survives context compression
//! • DISCERNMENT   — PermissionRequest:  auto-allow safe operations (reads, project writes)
//! • COMPASSION    — PostToolUseFailure: inject failure context for immediate diagnosis
//!
//! Architecture: Single binary handles 7 hook event types.
//! Routes by `hook_event_name` field in stdin JSON.
//!
//! Hook coverage: 7 / 17 Claude Code event types
//!   Covered: UserPromptSubmit | PreToolUse[Write|Edit|Bash] | PostToolUse[Write|Edit|Bash]
//!            PostToolUseFailure | PermissionRequest | Stop | PreCompact
//!
//! Binary: scripts/observatory/target/release/yawl-hooks
//! Built:  cargo build --release (same workspace as observatory binary)

use serde_json::{json, Value};
use std::io::Read;
use std::path::{Path, PathBuf};
use std::process;
use regex::Regex;
use std::sync::OnceLock;

fn main() {
    let mut input = String::new();
    std::io::stdin().read_to_string(&mut input).unwrap_or_default();

    let hook: Value = serde_json::from_str(&input).unwrap_or_else(|_| json!({}));
    let event = hook["hook_event_name"].as_str().unwrap_or("");

    // Resolve project root: CLAUDE_PROJECT_DIR env var preferred, fallback to cwd
    let project_dir = std::env::var("CLAUDE_PROJECT_DIR")
        .map(PathBuf::from)
        .unwrap_or_else(|_| {
            hook["cwd"]
                .as_str()
                .map(PathBuf::from)
                .unwrap_or_else(|| PathBuf::from("."))
        });

    match event {
        "UserPromptSubmit"    => handle_user_prompt_submit(&project_dir),
        "PreToolUse"          => handle_pre_tool_use(&hook, &project_dir),
        "PostToolUse"         => handle_post_tool_use(&hook, &project_dir),
        "PostToolUseFailure"  => handle_post_tool_use_failure(&hook),
        "PermissionRequest"   => handle_permission_request(&hook, &project_dir),
        "Stop"                => handle_stop(&project_dir),
        "PreCompact"          => handle_pre_compact(&project_dir),
        _                     => process::exit(0),
    }
}

// ─── Java Guard Pattern Definitions ──────────────────────────────────────────

/// 14 Java hyper-standards patterns. Checked on PreToolUse Write|Edit.
/// Any match → exit 2 (blocking), stderr → Claude sees violation details.
const GUARD_PATTERNS: &[(&str, &str)] = &[
    ("DEFERRED WORK MARKERS", r"//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|NOTE:.*implement|REVIEW:.*implement|TEMPORARY|@incomplete|@unimplemented|@stub|@mock|@fake|not\s+implemented\s+yet|coming\s+soon|placeholder|for\s+demo|simplified\s+version|basic\s+implementation)"),
    ("MOCK/STUB METHOD NAMES", r"(mock|stub|fake|demo)[A-Z][a-zA-Z]*\s*[=(]"),
    ("MOCK/STUB CLASS NAMES", r"(class|interface)\s+(Mock|Stub|Fake|Demo)[A-Za-z]*\s+(implements|extends|\{)"),
    ("MOCK MODE FLAGS", r"(is|use|enable|allow)(Mock|Fake|Demo|Stub)(Mode|Data|ing)\s*="),
    ("EMPTY STRING RETURNS", r#"return\s+""\s*;"#),
    ("NULL STUB RETURNS", r"(?i)return\s+null\s*;\s*//\s*(stub|todo|placeholder|not\s+implemented|temporary)"),
    ("EMPTY METHOD BODIES", r"public\s+void\s+\w+\([^)]*\)\s*\{\s*\}"),
    ("PLACEHOLDER CONSTANTS", r"(DUMMY|PLACEHOLDER|MOCK|FAKE)_[A-Z_]+\s*="),
    ("SILENT FALLBACK", r"catch\s*\([^)]+\)\s*\{[^}]*(return\s+(new|mock|fake|test)|log\.(warn|error).*not\s+implemented)"),
    ("CONDITIONAL MOCK", r"if\s*\([^)]*\)\s*return\s+(mock|fake|test|sample|demo)[A-Z][a-zA-Z]*\(\)"),
    ("SUSPICIOUS GETORDEFAULT", r#"\.getOrDefault\([^,]+,\s*"(test|mock|fake|default|sample|placeholder)"#),
    ("EARLY RETURN SKIP", r"if\s*\(true\)\s*return\s*;"),
    ("LOG INSTEAD OF THROW", r#"log\.(warn|error)\([^)]*"[^"]*not\s+implemented[^"]*""#),
    ("MOCK FRAMEWORK IMPORTS", r"import\s+(org\.mockito|org\.easymock|org\.jmock|org\.powermock)"),
];

// ─── Bash Guard Pattern Definitions ──────────────────────────────────────────

/// 7 Ω-gate destructive Bash command patterns. Checked on PreToolUse Bash.
/// These align with the explicit prohibitions in CLAUDE.md Ω section.
/// Any match → exit 2 (blocking), stderr → Claude sees violation details.
const BASH_GUARD_PATTERNS: &[(&str, &str)] = &[
    ("FORCE PUSH (Ω: never --force)",
     r"git\s+push\s+[^|&;\n]*--force|git\s+push\s+[^|&;\n]*\s-f\s"),
    ("DESTRUCTIVE RESET (Ω: loses uncommitted work)",
     r"git\s+reset\s+--hard"),
    ("AMEND PUBLISHED COMMIT (Ω: never amend pushed commits)",
     r"git\s+commit\s+[^|&;\n]*--amend"),
    ("BYPASS HOOKS (Ω: never --no-verify)",
     r"git\s+commit\s+[^|&;\n]*--no-verify"),
    ("DIRECT PUSH TO MAIN (Ω: claude/* branch required)",
     r"git\s+push\s+[^|&;\n]*\s+main\s*$"),
    ("DATABASE DESTRUCTION",
     r"(?i)(DROP\s+TABLE|TRUNCATE\s+TABLE)"),
    ("FORCE BRANCH DELETE (Ω: destructive)",
     r"git\s+branch\s+[^|&;\n]*-D\s"),
];

/// Lazy-compiled Java guard regex patterns (OnceLock for thread-safe one-time init).
fn compiled_patterns() -> &'static Vec<(String, Regex)> {
    static PATTERNS: OnceLock<Vec<(String, Regex)>> = OnceLock::new();
    PATTERNS.get_or_init(|| {
        GUARD_PATTERNS
            .iter()
            .map(|(name, pat)| (name.to_string(), Regex::new(pat).expect("valid guard regex")))
            .collect()
    })
}

/// Lazy-compiled Bash guard regex patterns.
fn compiled_bash_patterns() -> &'static Vec<(String, Regex)> {
    static BASH_PATTERNS: OnceLock<Vec<(String, Regex)>> = OnceLock::new();
    BASH_PATTERNS.get_or_init(|| {
        BASH_GUARD_PATTERNS
            .iter()
            .map(|(name, pat)| (name.to_string(), Regex::new(pat).expect("valid bash guard regex")))
            .collect()
    })
}

/// Check a shell command against the Ω-gate Bash guard patterns.
/// Returns violation names for any matches.
fn bash_guard_check(command: &str) -> Vec<String> {
    compiled_bash_patterns()
        .iter()
        .filter(|(_, regex)| regex.is_match(command))
        .map(|(name, _)| name.clone())
        .collect()
}

/// Check a Java file for hyper-standards violations.
///
/// Returns a Vec of (violation_name, vec_of_matching_lines).
/// Only checks .java files in src/ or test/ paths (skips orderfulfillment/).
fn hyper_validate(file_path: &str) -> Vec<(String, Vec<String>)> {
    if !file_path.ends_with(".java") {
        return vec![];
    }
    if !file_path.contains("/src/") && !file_path.contains("/test/") {
        return vec![];
    }
    if file_path.contains("/orderfulfillment/") {
        return vec![];
    }

    let content = match std::fs::read_to_string(file_path) {
        Ok(c) => c,
        Err(_) => return vec![],
    };

    let mut violations = vec![];
    for (pattern_name, regex) in compiled_patterns() {
        let matching_lines: Vec<String> = content
            .lines()
            .filter(|line| regex.is_match(line))
            .map(|line| line.trim().to_string())
            .collect();
        if !matching_lines.is_empty() {
            violations.push((pattern_name.clone(), matching_lines));
        }
    }
    violations
}

// ─── Event Handlers ──────────────────────────────────────────────────────────

/// OMNISCIENCE — Inject fresh Ψ facts into Claude's context before every prompt.
///
/// Fires on every user message. Reads observatory fact files (<5ms) and emits
/// a compact, high-density summary to stdout. Claude Code adds this to Claude's
/// context, so every response starts from truth, not vibes.
///
/// Output: 1 line, ~100 tokens, 8 key dimensions.
/// Exit 0 = allow prompt, stdout injected as context.
fn handle_user_prompt_submit(project_dir: &Path) {
    if let Some(summary) = build_fact_summary(project_dir) {
        println!("{}", summary);
    }
    process::exit(0);
}

/// OMNIPOTENCE — Guard before any write or destructive command.
///
/// For Write|Edit: shared-src blast radius warning + 14 Java guard patterns.
/// For Bash:       7 Ω-gate destructive command patterns.
fn handle_pre_tool_use(hook: &Value, project_dir: &Path) {
    let tool_name = hook["tool_name"].as_str().unwrap_or("");
    match tool_name {
        "Write" | "Edit" => handle_pre_file_tool_use(hook, project_dir),
        "Bash"            => handle_pre_bash_tool_use(hook),
        _                 => process::exit(0),
    }
}

/// PreToolUse for Write/Edit: blast radius warning + 14 Java guard patterns.
fn handle_pre_file_tool_use(hook: &Value, project_dir: &Path) {
    let file_path = hook["tool_input"]["file_path"].as_str().unwrap_or("");
    if file_path.is_empty() {
        process::exit(0);
    }

    // Check for shared-src warning (non-blocking additionalContext)
    if let Some(warning) = check_shared_src(file_path, project_dir) {
        let output = json!({
            "hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "permissionDecision": "allow",
                "additionalContext": warning
            }
        });
        println!("{}", serde_json::to_string(&output).unwrap_or_default());
    }

    // Check for guard violations (blocking: exit 2)
    let violations = hyper_validate(file_path);
    if !violations.is_empty() {
        eprintln!();
        eprintln!("╔═══════════════════════════════════════════════════════════════════╗");
        eprintln!("║  🚨 FORTUNE 5 STANDARDS VIOLATION DETECTED                       ║");
        eprintln!("╚═══════════════════════════════════════════════════════════════════╝");
        eprintln!();
        eprintln!("File: {}", file_path);
        eprintln!();
        for (violation_name, matching_lines) in violations {
            eprintln!("❌ {}", violation_name);
            if let Some(first_line) = matching_lines.first() {
                eprintln!("{}", first_line);
            }
            eprintln!();
        }
        eprintln!("Fix: Implement the REAL version or throw UnsupportedOperationException");
        eprintln!();
        process::exit(2);
    }

    process::exit(0);
}

/// PreToolUse for Bash: guard destructive commands per Ω gate (7 patterns).
fn handle_pre_bash_tool_use(hook: &Value) {
    let command = hook["tool_input"]["command"].as_str().unwrap_or("");
    if command.is_empty() {
        process::exit(0);
    }

    let violations = bash_guard_check(command);
    if !violations.is_empty() {
        eprintln!();
        eprintln!("╔═══════════════════════════════════════════════════════════════════╗");
        eprintln!("║  ⚠️  Ω GATE: DESTRUCTIVE COMMAND BLOCKED                          ║");
        eprintln!("╚═══════════════════════════════════════════════════════════════════╝");
        eprintln!();
        eprintln!("Command: {}", command);
        eprintln!();
        for violation in violations {
            eprintln!("❌ {}", violation);
        }
        eprintln!();
        eprintln!("Ω gate prohibits this operation. If explicitly authorized by the user, proceed.");
        eprintln!();
        process::exit(2);
    }

    process::exit(0);
}

/// PROVIDENCE — Async observatory refresh + build result injection.
///
/// For Write|Edit (async: true in settings): run observatory in background.
/// For Bash (synchronous): parse build output → inject one-line summary as additionalContext.
fn handle_post_tool_use(hook: &Value, project_dir: &Path) {
    let tool_name = hook["tool_name"].as_str().unwrap_or("");
    match tool_name {
        "Write" | "Edit" => handle_post_file_tool_use(hook, project_dir),
        "Bash"            => handle_post_bash_tool_use(hook),
        _                 => process::exit(0),
    }
}

/// PostToolUse for Write/Edit: async observatory refresh (called with async: true).
fn handle_post_file_tool_use(hook: &Value, project_dir: &Path) {
    let file_path = hook["tool_input"]["file_path"].as_str().unwrap_or("");
    if !file_path.ends_with(".java") && !file_path.ends_with("pom.xml") {
        process::exit(0);
    }

    // Run observatory synchronously (we are already the background async process)
    let observatory_sh = project_dir
        .join("scripts")
        .join("observatory")
        .join("observatory.sh");
    if observatory_sh.exists() {
        let _ = std::process::Command::new("bash")
            .arg(&observatory_sh)
            .current_dir(project_dir)
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status();
    }

    process::exit(0);
}

/// PostToolUse for Bash: parse build output and inject one-line summary.
///
/// Only injects context for recognized build tools (dx.sh, mvn, cargo).
/// Exits 0 silently for unrecognized commands — no noise for non-build Bash.
fn handle_post_bash_tool_use(hook: &Value) {
    let command = hook["tool_input"]["command"].as_str().unwrap_or("");

    let is_dx    = command.contains("dx.sh");
    let is_mvn   = command.contains("mvn") || command.contains("maven");
    let is_cargo = command.contains("cargo");

    if !is_dx && !is_mvn && !is_cargo {
        process::exit(0);
    }

    // tool_response may be a string or {"output": "..."} depending on Claude Code version
    let output = hook["tool_response"]["output"].as_str()
        .or_else(|| hook["tool_response"].as_str())
        .unwrap_or("");

    if output.is_empty() {
        process::exit(0);
    }

    let tool_label = if is_dx { "dx.sh" } else if is_mvn { "mvn" } else { "cargo" };

    if let Some(summary) = parse_build_output(tool_label, output) {
        let result = json!({"additionalContext": summary});
        println!("{}", serde_json::to_string(&result).unwrap_or_default());
    }

    process::exit(0);
}

/// COMPASSION — Inject structured failure context after tool failures.
///
/// Extracts error-relevant lines from failed Bash output and injects as
/// additionalContext, so Claude immediately sees the root cause without
/// scrolling through verbose output.
fn handle_post_tool_use_failure(hook: &Value) {
    let tool_name = hook["tool_name"].as_str().unwrap_or("");
    if tool_name != "Bash" {
        process::exit(0);
    }

    let command = hook["tool_input"]["command"].as_str().unwrap_or("");

    // tool_response, then error field as fallback
    let output = hook["tool_response"]["output"].as_str()
        .or_else(|| hook["tool_response"].as_str())
        .or_else(|| hook["error"].as_str())
        .unwrap_or("");

    if output.is_empty() {
        process::exit(0);
    }

    // Filter for error-relevant lines first
    let error_lines: Vec<&str> = output
        .lines()
        .filter(|l| {
            let low = l.to_lowercase();
            low.contains("error") || low.contains("failed") ||
            low.contains("exception") || low.contains("cannot find") ||
            low.contains("error[") // Rust compiler error format
        })
        .collect();

    // Fall back to last 10 lines if no error keywords found
    let relevant: Vec<&str> = if !error_lines.is_empty() {
        let n = error_lines.len().min(5);
        error_lines[error_lines.len() - n..].to_vec()
    } else {
        let all: Vec<&str> = output.lines().collect();
        let n = all.len().min(10);
        all[all.len().saturating_sub(n)..].to_vec()
    };

    let context_lines = relevant.join("\n");
    let truncated = if context_lines.len() > 500 {
        format!("...{}", &context_lines[context_lines.len() - 500..])
    } else {
        context_lines
    };

    let short_cmd = if command.len() > 60 { &command[..60] } else { command };
    let context = format!("❌ Bash failed — `{}`: {}", short_cmd, truncated);
    let result = json!({"additionalContext": context});
    println!("{}", serde_json::to_string(&result).unwrap_or_default());

    process::exit(0);
}

/// DISCERNMENT — Auto-allow safe operations to reduce permission friction.
///
/// Read/Glob/Grep: always safe, always allow.
/// Write/Edit within CLAUDE_PROJECT_DIR: allow.
/// Bash without Ω-gate violations: allow.
/// Unknown/dangerous: exit 0 with no output — let Claude Code prompt the user.
fn handle_permission_request(hook: &Value, project_dir: &Path) {
    let tool_name  = hook["tool_name"].as_str().unwrap_or("");
    let tool_input = &hook["tool_input"];

    let should_allow = match tool_name {
        // Read-only operations: always safe
        "Read" | "Glob" | "Grep" => true,

        // File writes: allow only within the project directory
        "Write" | "Edit" => {
            let file_path = tool_input["file_path"].as_str().unwrap_or("");
            !file_path.is_empty()
                && Path::new(file_path).starts_with(project_dir)
        }

        // Bash: allow only if no Ω-gate patterns match
        "Bash" => {
            let command = tool_input["command"].as_str().unwrap_or("");
            !command.is_empty() && bash_guard_check(command).is_empty()
        }

        // Other known-safe tools
        "TodoWrite" | "WebFetch" | "WebSearch" => true,

        // Unknown tools: let Claude Code handle
        _ => false,
    };

    if should_allow {
        let output = json!({
            "hookSpecificOutput": {
                "hookEventName": "PermissionRequest",
                "permissionDecision": "allow"
            }
        });
        println!("{}", serde_json::to_string(&output).unwrap_or_default());
    }
    // No output if not allowing: Claude Code prompts the user normally

    process::exit(0);
}

/// JUSTICE — Quality summary + git state before Claude stops working.
///
/// Consolidates stop-hook-git-check.sh into a single Rust process.
/// Surfaces: duplicate FQNs, test count, git state, commit reminder.
fn handle_stop(project_dir: &Path) {
    let git_state = check_git_state(project_dir);
    if let Some(quality) = build_quality_summary(project_dir) {
        println!("{}{}", quality, git_state);
    } else {
        println!("Ψ stop{}", git_state);
    }
    process::exit(0);
}

/// ETERNITY — Preserve Ψ facts across context compression.
///
/// PreCompact fires before Claude Code compresses the conversation. This hook
/// emits the fact summary to stdout, which Claude Code uses as custom_instructions
/// for the compacted context — truth persists beyond the context window.
fn handle_pre_compact(project_dir: &Path) {
    if let Some(summary) = build_fact_summary(project_dir) {
        println!("PRESERVED Ψ FACTS (pre-compact snapshot): {}", summary);
    }
    process::exit(0);
}

// ─── Fact Reading ─────────────────────────────────────────────────────────────

fn facts_dir(project_dir: &Path) -> PathBuf {
    project_dir
        .join("docs")
        .join("v6")
        .join("latest")
        .join("facts")
}

fn read_fact(facts: &Path, name: &str) -> Option<Value> {
    let content = std::fs::read_to_string(facts.join(name)).ok()?;
    serde_json::from_str(&content).ok()
}

/// Build a compact Ψ facts summary (1 line, ~100 tokens, 8+ dimensions).
///
/// Example output:
///   Ψ facts: 14 modules (11 full_shared) | 527 tests (398 JUnit5) |
///            1 duplicates (duplicates_found) | stateful=163 stateless=91 | engine=offline
fn build_fact_summary(project_dir: &Path) -> Option<String> {
    let facts = facts_dir(project_dir);
    if !facts.exists() {
        return None;
    }

    let modules    = read_fact(&facts, "modules.json");
    let tests      = read_fact(&facts, "tests.json");
    let duplicates = read_fact(&facts, "duplicates.json");
    let shared     = read_fact(&facts, "shared-src.json");
    let dual       = read_fact(&facts, "dual-family.json");
    let runtime    = read_fact(&facts, "workflow-runtime.json");

    let module_count = modules
        .as_ref()
        .and_then(|v| v["modules"].as_array())
        .map(|a| a.len())
        .unwrap_or(0);

    let full_shared = shared
        .as_ref()
        .and_then(|v| v["summary"]["full_shared_count"].as_u64())
        .unwrap_or(0);

    let test_count = tests
        .as_ref()
        .and_then(|v| v["summary"]["total_test_files"].as_u64())
        .unwrap_or(0);

    let junit5 = tests
        .as_ref()
        .and_then(|v| v["summary"]["junit5_count"].as_u64())
        .unwrap_or(0);

    let dup_count = duplicates
        .as_ref()
        .and_then(|v| v["summary"]["duplicate_count"].as_u64())
        .unwrap_or(0);

    let dup_status = duplicates
        .as_ref()
        .and_then(|v| v["summary"]["status"].as_str())
        .unwrap_or("unknown");

    let stateful = dual
        .as_ref()
        .and_then(|v| v["families"]["stateful"]["class_count"].as_u64())
        .unwrap_or(0);

    let stateless = dual
        .as_ref()
        .and_then(|v| v["families"]["stateless"]["class_count"].as_u64())
        .unwrap_or(0);

    let engine_status = runtime
        .as_ref()
        .and_then(|v| v["engine_status"].as_str())
        .unwrap_or("offline");

    let engine_summary = if engine_status == "online" {
        let cases = runtime
            .as_ref()
            .and_then(|v| v["active_cases"].as_u64())
            .unwrap_or(0);
        let workitems = runtime
            .as_ref()
            .and_then(|v| v["enabled_workitems"].as_u64())
            .unwrap_or(0);
        format!("| engine=online {} cases {} workitems", cases, workitems)
    } else {
        "| engine=offline".to_string()
    };

    Some(format!(
        "Ψ facts: {} modules ({} full_shared) | {} tests ({} JUnit5) | {} duplicates ({}) | stateful={} stateless={} {}",
        module_count, full_shared, test_count, junit5, dup_count, dup_status, stateful, stateless, engine_summary
    ))
}

/// Check if a file is in the shared src/ directory (affects all full_shared modules).
/// Returns a warning string if shared, None if not.
fn check_shared_src(file_path: &str, project_dir: &Path) -> Option<String> {
    let src_dir = project_dir.join("src");
    let file    = Path::new(file_path);

    if !file.starts_with(&src_dir) {
        return None;
    }

    let facts = facts_dir(project_dir);
    let shared = read_fact(&facts, "shared-src.json")?;

    let full_shared_count = shared["summary"]["full_shared_count"]
        .as_u64()
        .unwrap_or(11);

    let modules = shared["full_shared_modules"]
        .as_array()
        .map(|a| {
            a.iter()
                .filter_map(|v| v.as_str())
                .collect::<Vec<_>>()
                .join(", ")
        })
        .unwrap_or_default();

    Some(format!(
        "⚠️  SHARED SOURCE: src/ is shared by {} full_shared modules: {}. \
         Changes affect ALL modules simultaneously — verify intent before proceeding.",
        full_shared_count, modules
    ))
}

/// Check git state and return a suffix string for the Stop quality summary.
///
/// Replaces stop-hook-git-check.sh (47 lines bash → ~25 lines Rust, no subprocess overhead).
fn check_git_state(project_dir: &Path) -> String {
    // git diff --quiet exits 1 when there ARE unstaged changes
    let has_unstaged = std::process::Command::new("git")
        .args(["diff", "--quiet"])
        .current_dir(project_dir)
        .status()
        .map(|s| !s.success())
        .unwrap_or(false);

    // git diff --cached --quiet exits 1 when there ARE staged changes
    let has_staged = std::process::Command::new("git")
        .args(["diff", "--cached", "--quiet"])
        .current_dir(project_dir)
        .status()
        .map(|s| !s.success())
        .unwrap_or(false);

    let untracked = std::process::Command::new("git")
        .args(["ls-files", "--others", "--exclude-standard"])
        .current_dir(project_dir)
        .output()
        .map(|o| !String::from_utf8_lossy(&o.stdout).trim().is_empty())
        .unwrap_or(false);

    if has_staged || has_unstaged || untracked {
        let mut parts: Vec<&str> = vec![];
        if has_staged   { parts.push("staged"); }
        if has_unstaged { parts.push("unstaged"); }
        if untracked    { parts.push("untracked"); }
        format!(" | ⚠️  git: {} changes — commit before closing", parts.join("+"))
    } else {
        " | ✓ git: clean".to_string()
    }
}

/// Build a quality summary for the Stop hook (brief, informational).
fn build_quality_summary(project_dir: &Path) -> Option<String> {
    let facts = facts_dir(project_dir);
    if !facts.exists() {
        return None;
    }

    let duplicates = read_fact(&facts, "duplicates.json");
    let tests      = read_fact(&facts, "tests.json");

    let dup_status = duplicates
        .as_ref()
        .and_then(|v| v["summary"]["status"].as_str())
        .unwrap_or("unknown");

    let dup_count = duplicates
        .as_ref()
        .and_then(|v| v["summary"]["duplicate_count"].as_u64())
        .unwrap_or(0);

    let test_count = tests
        .as_ref()
        .and_then(|v| v["summary"]["total_test_files"].as_u64())
        .unwrap_or(0);

    let dup_indicator = if dup_status == "clean" { "✓" } else { "⚠" };

    Some(format!(
        "Ψ quality: {} duplicates {} | {} tests | run dx.sh all before committing",
        dup_count, dup_indicator, test_count
    ))
}

// ─── Build Output Parsing ─────────────────────────────────────────────────────

/// Parse build command output and return a one-line summary for additionalContext.
///
/// Returns None if the output doesn't contain recognizable build result markers
/// (i.e., not a build command or output is inconclusive). Callers exit 0 silently.
fn parse_build_output(tool_label: &str, output: &str) -> Option<String> {
    let is_success = output.contains("BUILD SUCCESS")
        || output.contains("BUILD SUCCESSFUL")   // Gradle
        || output.contains("Finished release [optimized]"); // Cargo release

    let is_failure = output.contains("BUILD FAILURE")
        || output.contains("BUILD FAILED")
        || output.contains("error[E"); // Rust compile errors

    let has_compile_error = output.contains("COMPILATION ERROR")
        || output.contains("[ERROR]")
        || output.contains("error[E");

    if is_success {
        let test_info = extract_maven_test_summary(output)
            .or_else(|| extract_cargo_test_summary(output))
            .map(|s| format!(" | {}", s))
            .unwrap_or_default();
        Some(format!("✅ {}: BUILD SUCCESS{}", tool_label, test_info))
    } else if is_failure {
        let error_hint = if has_compile_error { " | COMPILATION ERROR" } else { "" };
        Some(format!("❌ {}: BUILD FAILURE{}", tool_label, error_hint))
    } else {
        None
    }
}

/// Extract Maven/Surefire aggregate test summary (last matching line wins).
///
/// Looks for: "Tests run: N, Failures: F, Errors: E, Skipped: S"
fn extract_maven_test_summary(output: &str) -> Option<String> {
    for line in output.lines().rev() {
        if line.contains("Tests run:") && line.contains("Failures:") {
            let tests    = extract_number_after(line, "Tests run:").unwrap_or(0);
            let failures = extract_number_after(line, "Failures:").unwrap_or(0);
            let errors   = extract_number_after(line, "Errors:").unwrap_or(0);
            return Some(format!("{} tests, {} failures, {} errors", tests, failures, errors));
        }
    }
    None
}

/// Extract Cargo test result line ("test result: ok. N passed").
fn extract_cargo_test_summary(output: &str) -> Option<String> {
    for line in output.lines().rev() {
        if line.trim_start().starts_with("test result:") {
            return Some(line.trim().to_string());
        }
    }
    None
}

/// Extract a u64 that follows a label (e.g. "Tests run: 527" → Some(527)).
fn extract_number_after(s: &str, label: &str) -> Option<u64> {
    let pos  = s.find(label)?;
    let rest = s[pos + label.len()..].trim_start();
    let end  = rest.find(|c: char| !c.is_ascii_digit()).unwrap_or(rest.len());
    rest[..end].parse().ok()
}
