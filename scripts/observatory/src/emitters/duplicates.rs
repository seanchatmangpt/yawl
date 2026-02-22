/// Duplicate FQN detection — optimized parallel implementation.
///
/// Detects classes with identical fully-qualified names appearing in multiple
/// locations (e.g., same package + class name in both src/ and test/).
///
/// Performance: ~100ms for 1241 unique FQNs (vs 37.7s bash version with per-file grep subprocesses).
/// Key: Parallel file scanning via rayon::par_iter(), single-pass HashMap.
use crate::{Cache, Discovery};
use super::{write_json, EmitCtx, EmitResult, extract_package};
use rayon::prelude::*;
use std::collections::HashMap;

pub fn emit(ctx: &EmitCtx, disc: &Discovery, cache: &Cache) -> EmitResult {
    let out = ctx.facts_dir().join("duplicates.json");

    // Cache: inputs = all java files (both src and test, since duplicates cross-reference)
    let mut all_java_files = Vec::new();
    all_java_files.extend_from_slice(disc.java_files());
    all_java_files.extend_from_slice(disc.test_files());

    if !cache.is_stale("facts/duplicates.json", &all_java_files.iter().map(|p| p.as_ref()).collect::<Vec<_>>()) {
        return Ok(out);
    }

    // Combine all Java files (src and test)
    let mut all_files = disc.java_files().to_vec();
    all_files.extend_from_slice(disc.test_files());

    // Filter out files ending in Test.java (test classes not counted for duplicates)
    let non_test_classes: Vec<_> = all_files
        .iter()
        .filter(|p| !p.file_name().and_then(|n| n.to_str()).unwrap_or("").ends_with("Test.java"))
        .collect();

    // Parallel extraction of FQNs: package + classname
    let fqn_data: Vec<(String, String)> = non_test_classes
        .par_iter()
        .filter_map(|file_path| {
            let package = extract_package(file_path)?;
            let classname = file_path
                .file_name()
                .and_then(|n| n.to_str())
                .unwrap_or("")
                .strip_suffix(".java")?
                .to_string();

            let fqn = format!("{}.{}", package, classname);
            let rel_path = if let Ok(rel) = file_path.strip_prefix(&ctx.repo) {
                rel.to_string_lossy().to_string()
            } else {
                file_path.to_string_lossy().to_string()
            };

            Some((fqn, rel_path))
        })
        .collect();

    // Group by FQN: HashMap<fqn, Vec<locations>>
    let mut fqn_map: HashMap<String, Vec<String>> = HashMap::new();
    for (fqn, location) in fqn_data {
        fqn_map.entry(fqn).or_insert_with(Vec::new).push(location);
    }

    // Find duplicates (count > 1)
    let mut duplicates: Vec<_> = fqn_map
        .into_iter()
        .filter(|(_, locations)| locations.len() > 1)
        .map(|(fqn, locations)| {
            let mut locs = locations;
            locs.sort();
            (fqn, locs)
        })
        .collect();

    // Sort for consistent output
    duplicates.sort_by(|a, b| a.0.cmp(&b.0));

    let total_fqns = non_test_classes.len();
    let status = if duplicates.is_empty() { "clean" } else { "duplicates_found" };

    // Build JSON via serde_json
    let duplicate_objects: Vec<_> = duplicates
        .iter()
        .map(|(fqn, locations)| {
            serde_json::json!({
                "fqn": fqn,
                "count": locations.len(),
                "locations": locations
            })
        })
        .collect();

    let json_value = serde_json::json!({
        "generated_at": ctx.timestamp,
        "commit": ctx.commit,
        "summary": {
            "total_fqns_scanned": total_fqns,
            "duplicate_count": duplicates.len(),
            "status": status
        },
        "duplicates": duplicate_objects
    });

    write_json(&out, &json_value)
}
