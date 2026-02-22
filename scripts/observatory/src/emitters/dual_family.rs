/// Emit facts/dual-family.json — analyze stateful vs stateless engine families.
///
/// Counts classes in:
/// - Stateful: org.yawlfoundation.yawl.engine (YEngine, YNetRunner, etc.)
/// - Stateless: org.yawlfoundation.yawl.stateless (YStatelessEngine, YCaseMonitor, etc.)
/// Also identifies shared interfaces used by both families.
use super::{write_json, EmitCtx, EmitResult, extract_package, ensure_dir};
use crate::{Cache, Discovery};
use std::path::Path;

/// Primary classes in each family (for identification).
const STATEFUL_PRIMARY: &[&str] = &["YEngine", "YNetRunner", "YWorkItem", "YSpecification"];
const STATELESS_PRIMARY: &[&str] = &["YStatelessEngine", "YCaseMonitor", "YCaseImporter", "YCaseExporter"];

/// Emit facts/dual-family.json — dual-family engine analysis.
pub fn emit(ctx: &EmitCtx, disc: &Discovery, cache: &Cache) -> EmitResult {
    let out = ctx.facts_dir().join("dual-family.json");

    // Check cache: any Java file in engine or stateless packages
    let mut input_paths: Vec<&Path> = vec![];
    for java_file in disc.java_files() {
        let s = java_file.to_string_lossy();
        if s.contains("yawl/engine/") || s.contains("yawl/stateless/") {
            input_paths.push(java_file);
        }
    }

    let default_src = ctx.repo.join("src");
    if input_paths.is_empty() {
        input_paths.push(&default_src);
    }

    if !cache.is_stale("facts/dual-family.json", &input_paths) {
        return Ok(out);
    }

    ensure_dir(out.parent().unwrap_or(Path::new(".")));

    // Count classes and identify interfaces in each family
    let (stateful_count, stateful_primary) = count_family_classes(disc.java_files(), "yawl/engine/", false);
    let (stateless_count, stateless_primary) = count_family_classes(disc.java_files(), "yawl/stateless/", false);

    // Find shared interfaces (in ../src but not in test/)
    let shared_interfaces = find_shared_interfaces(disc.java_files());

    let output = serde_json::json!({
        "generated_at": ctx.timestamp,
        "commit": ctx.commit,
        "families": {
            "stateful": {
                "package": "org.yawlfoundation.yawl.engine",
                "class_count": stateful_count,
                "primary_classes": stateful_primary
            },
            "stateless": {
                "package": "org.yawlfoundation.yawl.stateless",
                "class_count": stateless_count,
                "primary_classes": stateless_primary
            }
        },
        "shared_interfaces": shared_interfaces,
        "duplication_analysis": {
            "status": "requires_review",
            "note": "Stateful and stateless families share some code paths through ../src mechanism"
        },
        "migration_notes": [
            "Stateful engine (YEngine) is the original implementation",
            "Stateless engine (YStatelessEngine) is designed for cloud-native deployments",
            "Both share domain model via full_shared source strategy"
        ]
    });

    write_json(&out, &output)
}

/// Count classes in a given family (package prefix in path).
/// Returns (count, list of primary classes found).
fn count_family_classes(java_files: &[std::path::PathBuf], path_filter: &str, _is_test: bool) -> (usize, Vec<String>) {
    let mut count = 0;
    let mut primary_found = Vec::new();

    for java_file in java_files {
        let s = java_file.to_string_lossy();
        if !s.contains(path_filter) || s.contains("/test/") {
            continue;
        }

        count += 1;

        // Check if file matches a primary class
        let filename = java_file
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("");

        for primary in if path_filter.contains("engine") {
            STATEFUL_PRIMARY
        } else {
            STATELESS_PRIMARY
        } {
            if filename.starts_with(primary) && filename.ends_with(".java") {
                primary_found.push(primary.to_string());
                break;
            }
        }
    }

    (count, primary_found)
}

/// Find interfaces defined in shared source (../src or ../test).
/// These are likely shared between families (Observer, ResourcePool, etc.).
fn find_shared_interfaces(java_files: &[std::path::PathBuf]) -> Vec<String> {
    let mut interfaces = Vec::new();
    let mut seen = std::collections::HashSet::new();

    for java_file in java_files {
        let s = java_file.to_string_lossy();

        // Check if in ../src or ../test (shared) but NOT in engine/ or stateless/
        let in_shared = (s.contains("/src/") || s.contains("/test/")) && !s.contains("/src/main/") && !s.contains("/src/test/");
        if !in_shared {
            continue;
        }

        // Also skip test files
        if s.contains("/test/") && s.contains("Test.java") {
            continue;
        }

        // Extract package and class name
        if let Ok(content) = std::fs::read_to_string(java_file) {
            // Check if it's an interface
            if !content.contains("interface ") {
                continue;
            }

            // Extract package
            if let Some(pkg) = extract_package(java_file) {
                if pkg.starts_with("org.yawlfoundation.yawl") {
                    // Extract class name from filename
                    if let Some(filename) = java_file.file_name().and_then(|n| n.to_str()) {
                        if let Some(classname) = filename.strip_suffix(".java") {
                            let full_name = format!("{}.{}", pkg, classname);
                            if seen.insert(full_name.clone()) {
                                interfaces.push(classname.to_string());
                            }
                        }
                    }
                }
            }
        }
    }

    // Sort for deterministic output
    interfaces.sort();
    interfaces
}
