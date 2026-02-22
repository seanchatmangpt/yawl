/// Emit facts/coverage.json — scan JaCoCo coverage reports.
///
/// Extracts:
/// - Overall line and branch coverage percentages
/// - Per-module coverage status
/// - Comparison against targets (line: 65%, branch: 55%)
use super::{write_json, EmitCtx, EmitResult, ensure_dir};
use crate::{Cache, Discovery};
use std::path::Path;

pub fn emit(ctx: &EmitCtx, disc: &Discovery, cache: &Cache) -> EmitResult {
    let out = ctx.facts_dir().join("coverage.json");

    let jacoco_files: Vec<_> = disc.pom_files()
        .iter()
        .map(|p| p.parent().unwrap_or(Path::new(".")).join("target/site/jacoco/jacoco.csv"))
        .collect();

    let jacoco_input: Vec<&Path> = jacoco_files.iter().map(|p| p.as_path()).collect();

    if !cache.force && !cache.is_stale("facts/coverage.json", &jacoco_input) {
        return Ok(out);
    }

    ensure_dir(out.parent().unwrap_or(Path::new(".")));

    let mut aggregate = serde_json::json!({
        "line_pct": 0,
        "branch_pct": 0,
        "lines_covered": 0,
        "lines_missed": 0,
        "target_line_pct": 65,
        "target_branch_pct": 55,
        "meets_line_target": false,
        "meets_branch_target": false,
        "note": "Run mvn test then bash scripts/observatory/observatory.sh --facts to update"
    });

    let mut modules = Vec::new();

    for module_dir in disc.module_dirs() {
        let jacoco_csv = module_dir.join("target/site/jacoco/jacoco.csv");
        let module_name = module_dir
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("unknown")
            .to_string();

        let module_entry = if jacoco_csv.exists() {
            match parse_jacoco_csv(&jacoco_csv) {
                Some((line_pct, branch_pct, lines_covered, lines_missed, instruction_pct)) => {
                    if aggregate.get("line_pct").and_then(|v| v.as_f64()) == Some(0.0) {
                        aggregate["line_pct"] = serde_json::json!(line_pct);
                        aggregate["branch_pct"] = serde_json::json!(branch_pct);
                        aggregate["lines_covered"] = serde_json::json!(lines_covered);
                        aggregate["lines_missed"] = serde_json::json!(lines_missed);
                    }
                    serde_json::json!({
                        "module": module_name,
                        "status": "available",
                        "line_pct": line_pct,
                        "branch_pct": branch_pct,
                        "instruction_pct": instruction_pct,
                        "lines_covered": lines_covered,
                        "lines_missed": lines_missed
                    })
                }
                None => {
                    serde_json::json!({
                        "module": module_name,
                        "status": "parse_error",
                        "line_pct": 0,
                        "branch_pct": 0,
                        "instruction_pct": 0,
                        "lines_covered": 0,
                        "lines_missed": 0
                    })
                }
            }
        } else {
            serde_json::json!({
                "module": module_name,
                "status": "no_report",
                "line_pct": 0,
                "branch_pct": 0,
                "instruction_pct": 0,
                "lines_covered": 0,
                "lines_missed": 0
            })
        };

        modules.push(module_entry);
    }

    if let Some(line_pct) = aggregate.get("line_pct").and_then(|v| v.as_f64()) {
        aggregate["meets_line_target"] = serde_json::json!(line_pct >= 65.0);
    }
    if let Some(branch_pct) = aggregate.get("branch_pct").and_then(|v| v.as_f64()) {
        aggregate["meets_branch_target"] = serde_json::json!(branch_pct >= 55.0);
    }

    let output = serde_json::json!({
        "generated_at": ctx.timestamp,
        "aggregate": aggregate,
        "modules": modules
    });

    write_json(&out, &output)
}

fn parse_jacoco_csv(path: &Path) -> Option<(f64, f64, u64, u64, f64)> {
    let content = std::fs::read_to_string(path).ok()?;
    let lines: Vec<&str> = content.lines().collect();

    if lines.len() < 2 {
        return None;
    }

    let header = lines[0];
    let data_line = lines[1];

    let find_col = |name: &str| -> Option<usize> {
        header.split(',').position(|col| col.contains(name))
    };

    let instruction_missed_idx = find_col("INSTRUCTION_MISSED")?;
    let instruction_covered_idx = find_col("INSTRUCTION_COVERED")?;
    let line_missed_idx = find_col("LINE_MISSED")?;
    let line_covered_idx = find_col("LINE_COVERED")?;
    let branch_missed_idx = find_col("BRANCH_MISSED")?;
    let branch_covered_idx = find_col("BRANCH_COVERED")?;

    let fields: Vec<&str> = data_line.split(',').collect();

    let instruction_missed = fields.get(instruction_missed_idx).and_then(|s| s.parse::<u64>().ok()).unwrap_or(0);
    let instruction_covered = fields.get(instruction_covered_idx).and_then(|s| s.parse::<u64>().ok()).unwrap_or(0);
    let line_missed = fields.get(line_missed_idx).and_then(|s| s.parse::<u64>().ok()).unwrap_or(0);
    let line_covered = fields.get(line_covered_idx).and_then(|s| s.parse::<u64>().ok()).unwrap_or(0);
    let branch_missed = fields.get(branch_missed_idx).and_then(|s| s.parse::<u64>().ok()).unwrap_or(0);
    let branch_covered = fields.get(branch_covered_idx).and_then(|s| s.parse::<u64>().ok()).unwrap_or(0);

    let line_total = line_covered + line_missed;
    let branch_total = branch_covered + branch_missed;
    let instruction_total = instruction_covered + instruction_missed;

    let line_pct = if line_total > 0 {
        (line_covered as f64 / line_total as f64) * 100.0
    } else {
        0.0
    };

    let branch_pct = if branch_total > 0 {
        (branch_covered as f64 / branch_total as f64) * 100.0
    } else {
        0.0
    };

    let instruction_pct = if instruction_total > 0 {
        (instruction_covered as f64 / instruction_total as f64) * 100.0
    } else {
        0.0
    };

    Some((line_pct, branch_pct, line_covered, line_missed, instruction_pct))
}
