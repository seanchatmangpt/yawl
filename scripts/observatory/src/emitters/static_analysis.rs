/// Emit 4 static analysis JSON fact files:
/// - facts/spotbugs-findings.json
/// - facts/pmd-violations.json
/// - facts/checkstyle-warnings.json
/// - facts/static-analysis.json (aggregate summary)
///
/// Scans for and parses XML reports from SpotBugs, PMD, and Checkstyle.
/// Health score = 100 - (total issues), min 0, max 100.
/// Status: GREEN (>=80), YELLOW (50-79), RED (<50).
use super::{utc_now, write_json, EmitCtx, EmitResult, ensure_dir};
use crate::{Cache, Discovery};
use std::path::Path;

pub fn emit(ctx: &EmitCtx, disc: &Discovery, cache: &Cache) -> EmitResult {
    let facts_dir = ctx.facts_dir();
    ensure_dir(&facts_dir);

    let spotbugs_xml_files: Vec<_> = disc.pom_files()
        .iter()
        .map(|p| p.parent().unwrap_or(Path::new(".")).join("target/spotbugsXml.xml"))
        .filter(|p| p.exists())
        .collect();

    let pmd_xml_files: Vec<_> = disc.pom_files()
        .iter()
        .map(|p| p.parent().unwrap_or(Path::new(".")).join("target/pmd.xml"))
        .filter(|p| p.exists())
        .collect();

    let checkstyle_xml_files: Vec<_> = disc.pom_files()
        .iter()
        .map(|p| p.parent().unwrap_or(Path::new(".")).join("target/checkstyle-result.xml"))
        .filter(|p| p.exists())
        .collect();

    let xml_input: Vec<&Path> = spotbugs_xml_files.iter().chain(pmd_xml_files.iter()).chain(checkstyle_xml_files.iter()).map(|p| p.as_path()).collect();

    if !cache.force && !cache.is_stale("facts/static-analysis.json", &xml_input) {
        return Ok(facts_dir.join("static-analysis.json"));
    }

    let (spotbugs_findings, spotbugs_count) = scan_spotbugs(&spotbugs_xml_files);
    let (pmd_violations, pmd_count) = scan_pmd(&pmd_xml_files);
    let (checkstyle_warnings, checkstyle_count) = scan_checkstyle(&checkstyle_xml_files);

    let total_issues = spotbugs_count + pmd_count + checkstyle_count;
    let health_score = std::cmp::max(0, 100 - total_issues as i32) as u32;
    let health_status = if health_score >= 80 {
        "GREEN"
    } else if health_score >= 50 {
        "YELLOW"
    } else {
        "RED"
    };

    let mut recommendations = Vec::new();
    if spotbugs_count > 0 {
        recommendations.push(format!("SpotBugs: {} findings detected. Review and fix high-priority issues.", spotbugs_count));
    }
    if pmd_count > 0 {
        recommendations.push(format!("PMD: {} violations detected. Check code style and complexity.", pmd_count));
    }
    if checkstyle_count > 0 {
        recommendations.push(format!("Checkstyle: {} warnings detected. Fix style violations.", checkstyle_count));
    }

    write_json(&facts_dir.join("spotbugs-findings.json"), &spotbugs_findings)?;
    write_json(&facts_dir.join("pmd-violations.json"), &pmd_violations)?;
    write_json(&facts_dir.join("checkstyle-warnings.json"), &checkstyle_warnings)?;

    let summary = serde_json::json!({
        "generated_at": ctx.timestamp,
        "commit": ctx.commit,
        "branch": ctx.branch,
        "health_score": health_score,
        "health_status": health_status,
        "total_issues": total_issues,
        "tools": {
            "spotbugs": {
                "status": if spotbugs_xml_files.is_empty() { "unavailable" } else { "available" },
                "total_findings": spotbugs_count
            },
            "pmd": {
                "status": if pmd_xml_files.is_empty() { "unavailable" } else { "available" },
                "total_violations": pmd_count
            },
            "checkstyle": {
                "status": if checkstyle_xml_files.is_empty() { "unavailable" } else { "available" },
                "total_warnings": checkstyle_count
            }
        },
        "recommendations": recommendations
    });

    write_json(&facts_dir.join("static-analysis.json"), &summary)
}

fn scan_spotbugs(xml_files: &[std::path::PathBuf]) -> (serde_json::Value, usize) {
    let mut findings = Vec::new();
    let mut by_category = std::collections::HashMap::new();

    for xml_file in xml_files {
        if let Ok(content) = std::fs::read_to_string(xml_file) {
            let lines: Vec<&str> = content.lines().collect();
            for line in lines {
                if line.contains("<BugInstance") {
                    if let Some(bug) = parse_spotbugs_line(line) {
                        let category = bug.get("category").cloned().unwrap_or_default();
                        *by_category.entry(category).or_insert(0) += 1;
                        findings.push(bug);
                    }
                }
            }
        }
    }

    let count = findings.len();
    let json = serde_json::json!({
        "generated_at": utc_now(),
        "commit": "unknown",
        "total_findings": count,
        "by_category": by_category,
        "findings": findings
    });

    (json, count)
}

fn scan_pmd(xml_files: &[std::path::PathBuf]) -> (serde_json::Value, usize) {
    let mut violations = Vec::new();
    let mut by_rule = std::collections::HashMap::new();

    for xml_file in xml_files {
        if let Ok(content) = std::fs::read_to_string(xml_file) {
            for line in content.lines() {
                if line.contains("<violation") {
                    if let Some(viol) = parse_pmd_line(line) {
                        let rule = viol.get("rule").cloned().unwrap_or_default();
                        *by_rule.entry(rule).or_insert(0) += 1;
                        violations.push(viol);
                    }
                }
            }
        }
    }

    let count = violations.len();
    let json = serde_json::json!({
        "generated_at": utc_now(),
        "commit": "unknown",
        "total_violations": count,
        "by_rule": by_rule,
        "violations": violations
    });

    (json, count)
}

fn scan_checkstyle(xml_files: &[std::path::PathBuf]) -> (serde_json::Value, usize) {
    let mut warnings = Vec::new();
    let mut by_severity = std::collections::HashMap::new();
    by_severity.insert("error", 0);
    by_severity.insert("warning", 0);
    by_severity.insert("info", 0);

    for xml_file in xml_files {
        if let Ok(content) = std::fs::read_to_string(xml_file) {
            for line in content.lines() {
                if line.contains("<error") {
                    if let Some(warn) = parse_checkstyle_line(line) {
                        if let Some(sev) = warn.get("severity").and_then(|s| s.as_str()) {
                            if let Some(count) = by_severity.get_mut(sev) {
                                *count += 1;
                            }
                        }
                        warnings.push(warn);
                    }
                }
            }
        }
    }

    let count = warnings.len();
    let json = serde_json::json!({
        "generated_at": utc_now(),
        "commit": "unknown",
        "total_warnings": count,
        "by_severity": by_severity,
        "warnings": warnings
    });

    (json, count)
}

fn parse_spotbugs_line(line: &str) -> Option<serde_json::Value> {
    let type_val = extract_xml_attr(line, "type").unwrap_or_default();
    let priority = extract_xml_attr(line, "priority").unwrap_or_default();
    let category = extract_xml_attr(line, "category").unwrap_or_default();
    let classname = extract_xml_attr(line, "classname").unwrap_or_default();

    Some(serde_json::json!({
        "type": type_val,
        "priority": priority,
        "category": category,
        "classname": classname
    }))
}

fn parse_pmd_line(line: &str) -> Option<serde_json::Value> {
    let rule = extract_xml_attr(line, "rule").unwrap_or_default();
    let priority = extract_xml_attr(line, "priority").unwrap_or_default();
    let line_num = extract_xml_attr(line, "line").unwrap_or_default();

    Some(serde_json::json!({
        "rule": rule,
        "priority": priority,
        "line": line_num
    }))
}

fn parse_checkstyle_line(line: &str) -> Option<serde_json::Value> {
    let line_num = extract_xml_attr(line, "line").unwrap_or_default();
    let column = extract_xml_attr(line, "column").unwrap_or_default();
    let severity = extract_xml_attr(line, "severity").unwrap_or_default();
    let message = extract_xml_attr(line, "message").unwrap_or_default();
    let source = extract_xml_attr(line, "source").unwrap_or_default();

    Some(serde_json::json!({
        "line": line_num,
        "column": column,
        "severity": severity,
        "message": message,
        "source": source
    }))
}

fn extract_xml_attr(line: &str, attr: &str) -> Option<String> {
    let pattern = format!("{}=\"", attr);
    let start = line.find(&pattern)? + pattern.len();
    let rest = &line[start..];
    let end = rest.find("\"")?;
    Some(rest[..end].to_string())
}

