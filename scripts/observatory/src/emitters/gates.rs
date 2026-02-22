/// Emit facts/gates.json — scan root pom.xml for profiles and plugins.
///
/// Extracts:
/// - Profile IDs (ci, analysis, prod, security-audit, etc.)
/// - Plugin presence: spotbugs, checkstyle, pmd, jacoco, enforcer, dependency-check
/// - Default active gates for each gating profile
use super::{write_json, EmitCtx, EmitResult, ensure_dir};
use crate::{Cache, Discovery};
use std::path::Path;

/// Emit facts/gates.json — build gates configuration.
pub fn emit(ctx: &EmitCtx, _disc: &Discovery, cache: &Cache) -> EmitResult {
    let out = ctx.facts_dir().join("gates.json");

    let root_pom = ctx.repo.join("pom.xml");
    if !cache.is_stale("facts/gates.json", &[&root_pom]) {
        return Ok(out);
    }

    ensure_dir(out.parent().unwrap_or(Path::new(".")));

    // Parse root pom.xml for profiles and plugins
    let content = match std::fs::read_to_string(&root_pom) {
        Ok(c) => c,
        Err(_) => return Err("Cannot read root pom.xml".into()),
    };

    let profiles = extract_profiles(&content);
    let plugins = detect_plugins(&content);

    let output = serde_json::json!({
        "generated_at": ctx.timestamp,
        "commit": ctx.commit,
        "profiles": profiles,
        "plugins": plugins,
        "default_active_gates": {
            "java25": ["compile", "test"],
            "ci": ["jacoco", "spotbugs", "enforcer"],
            "analysis": ["jacoco", "spotbugs", "checkstyle", "pmd"],
            "prod": ["jacoco", "spotbugs", "dependency-check"]
        },
        "gated_profiles": {
            "ci": {
                "jacoco_halt_on_failure": true,
                "spotbugs_fail_on_error": true,
                "enforcer_fail": true
            },
            "analysis": {
                "jacoco_halt_on_failure": false,
                "spotbugs_fail_on_error": false,
                "checkstyle_fail_on_error": false,
                "pmd_fail_on_error": false
            },
            "prod": {
                "dependency_check_fail_on_cvss": 7,
                "spotbugs_fail_on_error": true
            }
        }
    });

    write_json(&out, &output)
}

/// Extract all profile IDs from pom.xml.
fn extract_profiles(content: &str) -> Vec<String> {
    let mut profiles = Vec::new();

    // Find <profiles> section
    let mut in_profiles = false;
    let mut in_profile = false;

    for line in content.lines() {
        let trimmed = line.trim();

        if trimmed.starts_with("<profiles>") {
            in_profiles = true;
            continue;
        }
        if trimmed.starts_with("</profiles>") {
            break;
        }

        if in_profiles {
            if trimmed.starts_with("<profile>") {
                in_profile = true;
                continue;
            }
            if trimmed.starts_with("</profile>") {
                in_profile = false;
                continue;
            }

            if in_profile && trimmed.starts_with("<id>") && trimmed.ends_with("</id>") {
                if let Some(rest) = trimmed.strip_prefix("<id>") {
                    if let Some(id) = rest.strip_suffix("</id>") {
                        profiles.push(id.trim().to_string());
                    }
                }
            }
        }
    }

    profiles.sort();
    profiles.dedup();
    profiles
}

/// Detect which build plugins are present in pom.xml.
fn detect_plugins(content: &str) -> serde_json::Value {
    let has_spotbugs = content.contains("spotbugs-maven-plugin");
    let has_checkstyle = content.contains("maven-checkstyle-plugin");
    let has_pmd = content.contains("maven-pmd-plugin");
    let has_jacoco = content.contains("jacoco-maven-plugin");
    let has_enforcer = content.contains("maven-enforcer-plugin");
    let has_dependency_check = content.contains("dependency-check-maven");

    // Extract JaCoCo line coverage threshold
    let jacoco_line_coverage = extract_jacoco_threshold(content, "jacoco.line.coverage").unwrap_or(0.65);
    let jacoco_branch_coverage = extract_jacoco_threshold(content, "jacoco.branch.coverage").unwrap_or(0.55);

    serde_json::json!({
        "spotbugs": {
            "enabled": has_spotbugs,
            "phase": "verify",
            "fail_on_error": false
        },
        "checkstyle": {
            "enabled": has_checkstyle,
            "phase": "verify",
            "fail_on_error": false
        },
        "pmd": {
            "enabled": has_pmd,
            "phase": "verify",
            "fail_on_error": false
        },
        "jacoco": {
            "enabled": has_jacoco,
            "line_coverage": jacoco_line_coverage,
            "branch_coverage": jacoco_branch_coverage
        },
        "enforcer": {
            "enabled": has_enforcer,
            "rules": ["requireUpperBoundDeps", "banDuplicatePomDependencyVersions"]
        },
        "dependency-check": {
            "enabled": has_dependency_check,
            "fail_on_cvss": 7
        }
    })
}

/// Extract JaCoCo coverage threshold from property definition.
/// E.g., <jacoco.line.coverage>0.80</jacoco.line.coverage>
fn extract_jacoco_threshold(content: &str, property_name: &str) -> Option<f64> {
    let pattern = format!("<{}>", property_name);
    let close = format!("</{}>", property_name);

    for line in content.lines() {
        let trimmed = line.trim();
        if let Some(rest) = trimmed.strip_prefix(&pattern) {
            if let Some(val) = rest.strip_suffix(&close) {
                return val.trim().parse::<f64>().ok();
            }
        }
    }

    None
}
