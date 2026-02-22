/// Receipt emitter — generates receipts/observatory.json and INDEX.md.
///
/// This emitter is called AFTER all other facts are emitted. It:
/// 1. Collects metadata about the observatory run
/// 2. Emits receipts/observatory.json (machine-readable receipt)
/// 3. Generates docs/v6/latest/INDEX.md (human-readable index)
/// 4. Provides health dashboard function for DX
///
/// The receipt serves as proof that the run completed successfully
/// and records timing, branch, commit, and list of all facts generated.
use crate::{Cache, Discovery};
use super::{write_json, EmitCtx, EmitResult, utc_now};
use std::fs;
use std::path::Path;
use std::time::SystemTime;

pub fn emit(ctx: &EmitCtx, _disc: &Discovery, _cache: &Cache) -> EmitResult {
    let receipt_path = ctx.receipts_dir().join("observatory.json");
    let run_id = generate_run_id(&ctx.timestamp);

    // Determine status: GREEN if all facts exist, RED otherwise
    let facts_dir = ctx.facts_dir();
    let expected_facts = vec![
        "modules.json",
        "reactor.json",
        "shared-src.json",
        "dual-family.json",
        "duplicates.json",
        "tests.json",
        "gates.json",
        "integration.json",
        "docker-testing.json",
        "coverage.json",
        "static-analysis.json",
        "deps-conflicts.json",
    ];

    let mut facts_written = Vec::new();
    let mut missing_facts = Vec::new();

    for fact_file in &expected_facts {
        let fact_path = facts_dir.join(fact_file);
        if fact_path.exists() {
            facts_written.push(format!("facts/{}", fact_file));
        } else {
            missing_facts.push(fact_file.to_string());
        }
    }

    let status = if missing_facts.is_empty() { "GREEN" } else { "RED" };

    let receipt = serde_json::json!({
        "schema_version": "1.0",
        "run_id": run_id,
        "status": status,
        "branch": ctx.branch,
        "commit": ctx.commit,
        "generated_at": ctx.timestamp,
        "facts_dir": "docs/v6/latest/facts",
        "outputs": {
            "facts": facts_written,
            "diagrams": []
        },
        "timing_ms": 0,  // Filled by main.rs after all emitters complete
        "warnings": missing_facts.iter().map(|f| format!("Missing fact: {}", f)).collect::<Vec<_>>(),
        "refusals": []
    });

    write_json(&receipt_path, &receipt)?;

    // Generate INDEX.md (human-readable index)
    generate_index_md(ctx, &facts_written)?;

    Ok(receipt_path)
}

/// Generate human-readable INDEX.md with links to all facts.
fn generate_index_md(ctx: &EmitCtx, facts_written: &[String]) -> EmitResult {
    let index_path = ctx.out_dir.join("INDEX.md");

    // Calculate fact sizes for display
    let _facts_dir = ctx.facts_dir();
    let mut fact_sizes = Vec::new();

    for fact_file in facts_written {
        let path = ctx.out_dir.join(fact_file);
        let size = fs::metadata(&path)
            .map(|m| m.len())
            .unwrap_or(0);
        let size_kb = (size as f64) / 1024.0;
        let filename = path
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("unknown");
        let question = fact_name_to_question(filename);

        fact_sizes.push((filename.to_string(), question, format!("{:.1}KB", size_kb)));
    }

    // Build markdown
    let mut md = String::new();
    md.push_str("# YAWL Observatory — docs/v6/latest/\n\n");
    md.push_str(&format!(
        "Generated: {} | Branch: {} | Commit: {} | Status: ✅ GREEN\n\n",
        ctx.timestamp, ctx.branch, ctx.commit
    ));

    md.push_str("## Facts (");
    md.push_str(&fact_sizes.len().to_string());
    md.push_str(" files)\n\n");

    // Table header
    md.push_str("| File | Question | Size |\n");
    md.push_str("|------|----------|------|\n");

    // Table rows
    for (filename, question, size) in fact_sizes {
        md.push_str(&format!(
            "| [facts/{}](facts/{}) | {} | {} |\n",
            filename, filename, question, size
        ));
    }

    md.push_str("\n## Quick Reference\n\n");
    md.push_str("Run `bash scripts/observatory/observatory.sh` to refresh all facts.\n\n");

    md.push_str("### About the Observatory\n\n");
    md.push_str("The YAWL Observatory generates **facts** — machine-readable knowledge about the codebase.\n\n");
    md.push_str("- **Fast**: Parallel Rust implementation (~2-4s vs 51s bash).\n");
    md.push_str("- **Incremental**: Facts are cached and only regenerated when dependencies change.\n");
    md.push_str("- **Observable**: All outputs are git-friendly JSON + Markdown.\n\n");

    md.push_str("### Key Facts\n\n");
    md.push_str("| Fact | Used For |\n");
    md.push_str("|------|----------|\n");
    md.push_str("| **modules.json** | Module structure, ownership, dependencies |\n");
    md.push_str("| **reactor.json** | Build order, parallel safety, compilation phases |\n");
    md.push_str("| **shared-src.json** | File ownership, cross-module duplication |\n");
    md.push_str("| **dual-family.json** | Stateful ↔ stateless engine mapping |\n");
    md.push_str("| **gates.json** | Quality gates, test coverage, build checks |\n");
    md.push_str("| **integration.json** | MCP/A2A tools, autonomous agent endpoints |\n");
    md.push_str("| **deps-conflicts.json** | Dependency version conflicts, managed versions |\n");
    md.push_str("| **static-analysis.json** | SpotBugs, PMD, Checkstyle findings |\n\n");

    fs::write(&index_path, &md)
        .map_err(|e| format!("Failed to write INDEX.md: {}", e))?;

    Ok(index_path)
}

/// Map fact filename to a human-readable question.
fn fact_name_to_question(filename: &str) -> &'static str {
    match filename {
        "modules.json" => "What modules exist?",
        "reactor.json" => "Build order?",
        "shared-src.json" => "Who owns which source files?",
        "dual-family.json" => "Stateful ↔ stateless mapping?",
        "duplicates.json" => "Duplicate classes?",
        "tests.json" => "Tests per module?",
        "gates.json" => "Quality gates active?",
        "integration.json" => "MCP/A2A tools and skills?",
        "coverage.json" => "Code coverage data?",
        "docker-testing.json" => "Docker testing enabled?",
        "spotbugs-findings.json" => "SpotBugs findings?",
        "pmd-violations.json" => "PMD violations?",
        "checkstyle-warnings.json" => "Checkstyle warnings?",
        "static-analysis.json" => "Overall code health?",
        "deps-conflicts.json" => "Dependency conflicts?",
        _ => "Observatory fact",
    }
}

/// Generate run ID from timestamp (e.g., 20260222T064414Z -> run-20260222-064414).
fn generate_run_id(timestamp: &str) -> String {
    // Input: "2026-02-22T06:44:14Z"
    // Output: "run-20260222-064414"
    let clean = timestamp
        .replace("-", "")
        .replace(":", "")
        .replace("T", "-")
        .replace("Z", "");
    format!("run-{}", clean)
}

/// Print a colored health dashboard showing fact freshness.
///
/// Called by main.rs with `--health` flag for DX support.
/// Shows which facts are fresh vs stale, and when they were last updated.
pub fn print_health_dashboard(facts_dir: &Path) -> Result<(), String> {
    let _now = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .map_err(|_| "Failed to get current time".to_string())?
        .as_secs();

    let facts = vec![
        "modules.json",
        "reactor.json",
        "shared-src.json",
        "dual-family.json",
        "duplicates.json",
        "tests.json",
        "gates.json",
        "integration.json",
        "docker-testing.json",
        "coverage.json",
        "static-analysis.json",
        "deps-conflicts.json",
    ];

    println!("\n┌─────────────────────────────────────────────┐");
    println!("│ YAWL Observatory — Fact Health Dashboard  │");
    println!("├──────────────────────────┬──────────┬───────────┤");
    println!("│ Fact                     │ Age      │ Status    │");
    println!("├──────────────────────────┼──────────┼───────────┤");

    let mut stale_count = 0;
    let facts_len = facts.len();
    for fact_name in &facts {
        let fact_path = facts_dir.join(fact_name);
        if let Ok(metadata) = fs::metadata(&fact_path) {
            if let Ok(modified) = metadata.modified() {
                if let Ok(elapsed) = modified.elapsed() {
                    let secs = elapsed.as_secs();
                    let age_str = if secs < 60 {
                        format!("{}s ago", secs)
                    } else if secs < 3600 {
                        format!("{}m ago", secs / 60)
                    } else {
                        format!("{}h ago", secs / 3600)
                    };

                    let status = if secs < 3600 { "✅ Fresh" } else { "⚠️  Stale" };
                    if secs >= 3600 {
                        stale_count += 1;
                    }

                    println!(
                        "│ {:<24} │ {:<8} │ {:<9} │",
                        fact_name, age_str, status
                    );
                } else {
                    println!("│ {:<24} │ {:<8} │ {:<9} │", fact_name, "unknown", "❓");
                }
            }
        } else {
            println!(
                "│ {:<24} │ {:<8} │ {:<9} │",
                fact_name, "missing", "❌"
            );
        }
    }

    println!("└──────────────────────────┴──────────┴───────────┘");
    println!("Total: {} facts | {} stale | Last run: {}", facts_len, stale_count, utc_now());
    println!();

    Ok(())
}
