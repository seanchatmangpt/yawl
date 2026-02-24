//! YAWL Hook Handler — Omnipresent Rust binary for all Claude Code hook events.
//!
//! GODSPEED!!! Embodies the attributes of Logos:
//!
//! • OMNISCIENCE   — UserPromptSubmit: injects Ψ facts before every prompt (65ms warm)
//! • OMNIPOTENCE   — PreToolUse:       warns on shared-src blast radius before any write
//! • PROVIDENCE    — PostToolUse:      async observatory refresh — facts never stale
//! • JUSTICE       — Stop:             quality summary before completion
//! • ETERNITY      — PreCompact:       Ψ truth survives context compression
//!
//! Architecture: Single binary handles all 5 high-value hook events.
//! Routes by `hook_event_name` field in stdin JSON.
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
        "UserPromptSubmit" => handle_user_prompt_submit(&project_dir),
        "PreToolUse" => handle_pre_tool_use(&hook, &project_dir),
        "PostToolUse" => handle_post_tool_use(&hook, &project_dir),
        "Stop" => handle_stop(&project_dir),
        "PreCompact" => handle_pre_compact(&project_dir),
        _ => process::exit(0),
    }
}

// ─── Guard Pattern Definitions ──────────────────────────────────────────────

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

/// Lazy-compiled regex patterns (OnceLock for thread-safe one-time initialization).
fn compiled_patterns() -> &'static Vec<(String, Regex)> {
    static PATTERNS: OnceLock<Vec<(String, Regex)>> = OnceLock::new();
    PATTERNS.get_or_init(|| {
        GUARD_PATTERNS
            .iter()
            .map(|(name, pat)| {
                (
                    name.to_string(),
                    Regex::new(pat).expect("valid guard regex"),
                )
            })
            .collect()
    })
}

/// Check a Java file for hyper-standards violations.
///
/// Returns a Vec of (violation_name, vec_of_matching_lines).
/// Only checks .java files in src/ or test/ paths (skips orderfulfillment/).
fn hyper_validate(file_path: &str) -> Vec<(String, Vec<String>)> {
    // Filter: only .java files in src/ or test/
    if !file_path.ends_with(".java") {
        return vec![];
    }

    if !file_path.contains("/src/") && !file_path.contains("/test/") {
        return vec![];
    }

    // Skip orderfulfillment directory
    if file_path.contains("/orderfulfillment/") {
        return vec![];
    }

    // Read file contents
    let content = match std::fs::read_to_string(file_path) {
        Ok(c) => c,
        Err(_) => return vec![],
    };

    let patterns = compiled_patterns();
    let mut violations = vec![];

    for (pattern_name, regex) in patterns {
        let mut matching_lines = vec![];

        for line in content.lines() {
            if regex.is_match(line) {
                matching_lines.push(line.trim().to_string());
            }
        }

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

/// OMNIPOTENCE — Warn on shared-src blast radius before any write.
/// Also check for hyper-standards violations (guard patterns).
///
/// The YAWL src/ directory is shared by 11 full_shared modules simultaneously.
/// Editing any file there affects ALL 11 modules. This hook injects that warning
/// into Claude's context as additionalContext before the write proceeds.
///
/// Additionally, validates the file against 14 guard patterns (TODO, mock, stub, etc.).
/// If violations found: write formatted output to stderr and exit(2).
///
/// Exit 0 normally (warn, never block), or exit(2) if guard violations detected.
fn handle_pre_tool_use(hook: &Value, project_dir: &Path) {
    let tool_name = hook["tool_name"].as_str().unwrap_or("");
    if tool_name != "Write" && tool_name != "Edit" {
        process::exit(0);
    }

    let file_path = hook["tool_input"]["file_path"].as_str().unwrap_or("");
    if file_path.is_empty() {
        process::exit(0);
    }

    // Check for shared-src warning (non-blocking)
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

    // Check for guard violations (blocking)
    let violations = hyper_validate(file_path);
    if !violations.is_empty() {
        // Format and emit error to stderr
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

/// PROVIDENCE — Async observatory refresh after every Java/pom.xml change.
///
/// Hook runs with async: true in settings — Claude continues immediately.
/// Observatory runs synchronously in the background process (65ms warm).
/// Result: facts are always current by the time Claude reads them next.
fn handle_post_tool_use(hook: &Value, project_dir: &Path) {
    let tool_name = hook["tool_name"].as_str().unwrap_or("");
    if tool_name != "Write" && tool_name != "Edit" {
        process::exit(0);
    }

    let file_path = hook["tool_input"]["file_path"].as_str().unwrap_or("");
    if !file_path.ends_with(".java") && !file_path.ends_with("pom.xml") {
        process::exit(0);
    }

    // Run observatory synchronously (we're already in a background process via async: true)
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

/// JUSTICE — Emit quality summary to stdout before Claude stops working.
///
/// Stop hook stdout is shown to Claude. This surfaces duplicate FQNs, test
/// coverage summary, and a reminder to run dx.sh all before committing.
/// Exit 0 (non-blocking) — the stop-hook-git-check.sh handles blocking logic.
fn handle_stop(project_dir: &Path) {
    if let Some(summary) = build_quality_summary(project_dir) {
        println!("{}", summary);
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
///   Ψ facts: 13 modules (11 full_shared) | 439 tests (325 JUnit5) |
///            1 duplicates (duplicates_found) | stateful=287 stateless=12 | engine=online 2 cases
fn build_fact_summary(project_dir: &Path) -> Option<String> {
    let facts = facts_dir(project_dir);
    if !facts.exists() {
        return None;
    }

    let modules = read_fact(&facts, "modules.json");
    let tests = read_fact(&facts, "tests.json");
    let duplicates = read_fact(&facts, "duplicates.json");
    let shared = read_fact(&facts, "shared-src.json");
    let dual = read_fact(&facts, "dual-family.json");
    let runtime = read_fact(&facts, "workflow-runtime.json");

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

    // Engine status from runtime facts
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
    let file = Path::new(file_path);

    // Check if file is inside the shared src/ directory
    if !file.starts_with(&src_dir) {
        return None;
    }

    // Read shared-src facts for accurate module list
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

/// Build a quality summary for the Stop hook (brief, informational).
fn build_quality_summary(project_dir: &Path) -> Option<String> {
    let facts = facts_dir(project_dir);
    if !facts.exists() {
        return None;
    }

    let duplicates = read_fact(&facts, "duplicates.json");
    let tests = read_fact(&facts, "tests.json");

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
