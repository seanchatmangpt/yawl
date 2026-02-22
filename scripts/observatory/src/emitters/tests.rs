/// Test inventory and coverage hints — parallel JUnit detection.
///
/// Scans test files to:
/// - Count total test files and resources
/// - Detect JUnit version (Jupiter 5 vs JUnit 4)
/// - Organize test inventory by module
/// - Report coverage targets
///
/// Performance: ~100ms via parallel grep on test_files (vs 689ms bash version).
use crate::{Cache, Discovery};
use super::{write_json, EmitCtx, EmitResult, file_contains};
use rayon::prelude::*;
use std::collections::HashMap;
use std::path::PathBuf;

pub fn emit(ctx: &EmitCtx, disc: &Discovery, cache: &Cache) -> EmitResult {
    let out = ctx.facts_dir().join("tests.json");

    // Cache inputs: test files + module directories
    let mut cache_inputs = Vec::new();
    cache_inputs.extend_from_slice(disc.test_files());
    cache_inputs.extend_from_slice(disc.module_dirs());

    if !cache.is_stale("facts/tests.json", &cache_inputs.iter().map(|p| p.as_ref()).collect::<Vec<_>>()) {
        return Ok(out);
    }

    let test_files = disc.test_files();

    // Partition test files by location: shared test/ vs module src/test/java/
    let repo_str = ctx.repo.to_string_lossy();
    let shared_test_prefix = format!("{}/test/", repo_str);
    let (shared_tests, module_tests): (Vec<_>, Vec<_>) = test_files
        .iter()
        .partition(|p| p.to_string_lossy().starts_with(&shared_test_prefix));

    // Count JUnit versions in parallel
    let (shared_junit5, shared_junit4) = count_junit_versions(&shared_tests);

    // Organize module-specific tests by module directory
    let mut module_test_map: HashMap<String, Vec<&PathBuf>> = HashMap::new();
    for test_file in &module_tests {
        let file_str = test_file.to_string_lossy();
        // Extract module name from path: "yawl-foo/src/test/java/..." → "yawl-foo"
        if let Some(start) = file_str.find(repo_str.as_ref()).map(|i| i + repo_str.len() + 1) {
            if let Some(slash_pos) = file_str[start..].find('/') {
                let module_name = &file_str[start..start + slash_pos];
                if module_name.starts_with("yawl-") {
                    module_test_map.entry(module_name.to_string()).or_insert_with(Vec::new).push(test_file);
                }
            }
        }
    }

    // Build test inventory with JUnit detection per module
    let mut test_inventory = Vec::new();
    let mut total_tests = 0;
    let mut total_junit5 = shared_junit5;
    let mut total_junit4 = shared_junit4;

    if !shared_tests.is_empty() {
        total_tests += shared_tests.len();
        test_inventory.push(serde_json::json!({
            "module": "shared-root-test",
            "test_count": shared_tests.len(),
            "junit5": shared_junit5,
            "junit4": shared_junit4,
            "source": "test/"
        }));
    }

    // Process each module's tests
    let mut module_entries: Vec<_> = module_test_map.into_iter().collect();
    module_entries.sort_by(|a, b| a.0.cmp(&b.0));

    for (module_name, test_files) in module_entries {
        let (junit5, junit4) = count_junit_versions(&test_files);
        total_tests += test_files.len();
        total_junit5 += junit5;
        total_junit4 += junit4;

        let source = format!("{}/src/test/java/", module_name);
        test_inventory.push(serde_json::json!({
            "module": module_name,
            "test_count": test_files.len(),
            "junit5": junit5,
            "junit4": junit4,
            "source": source
        }));
    }

    // Count test resource files
    let test_resource_count = test_files
        .par_iter()
        .filter(|p| p.to_string_lossy().contains("/test/resources/"))
        .count();

    // Check for JaCoCo exec file
    let jacoco_exec_available = ctx.repo.join("target").join("jacoco.exec").exists();

    let json_value = serde_json::json!({
        "generated_at": ctx.timestamp,
        "commit": ctx.commit,
        "summary": {
            "total_test_files": total_tests,
            "modules_with_tests": test_inventory.len(),
            "test_resource_files": test_resource_count,
            "jacoco_exec_available": jacoco_exec_available,
            "junit5_count": total_junit5,
            "junit4_count": total_junit4
        },
        "test_inventory": test_inventory,
        "coverage_hints": {
            "minimum_line_coverage": 0.65,
            "minimum_branch_coverage": 0.55,
            "profile": "coverage",
            "run_command": "mvn -T 1.5C clean verify -P coverage"
        }
    });

    write_json(&out, &json_value)
}

/// Parallel JUnit version detection for a batch of test files.
/// Returns (junit5_count, junit4_count)
fn count_junit_versions(test_files: &[&PathBuf]) -> (usize, usize) {
    if test_files.is_empty() {
        return (0, 0);
    }

    let (junit5_count, junit4_count) = test_files
        .par_iter()
        .fold(
            || (0, 0),
            |(j5, j4), file| {
                // Check for JUnit 5 (Jupiter)
                let has_j5 = file_contains(file, "org.junit.jupiter");
                // Check for JUnit 4
                let has_j4 = file_contains(file, "org.junit.Test") || file_contains(file, "org.junit.Before");

                let new_j5 = if has_j5 { 1 } else { 0 };
                let new_j4 = if has_j4 { 1 } else { 0 };
                (j5 + new_j5, j4 + new_j4)
            },
        )
        .reduce(|| (0, 0), |(j5a, j4a), (j5b, j4b)| (j5a + j5b, j4a + j4b));

    (junit5_count, junit4_count)
}
