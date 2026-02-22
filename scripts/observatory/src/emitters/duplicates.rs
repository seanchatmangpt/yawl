/// Duplicate FQN detection — fast implementation using BufReader.
///
/// Detects classes with identical fully-qualified names appearing in multiple
/// locations (e.g., same package + class name in both src/ and test/).
///
/// Performance: Uses coarse directory cache check (20 dirs vs 1423 files).
/// For package-declaration-only changes, run with OBSERVATORY_FORCE=1.
use crate::{Cache, Discovery};
use super::{write_json, EmitCtx, EmitResult, extract_package};
use ahash::AHashMap;
use rayon::prelude::*;

pub fn emit(ctx: &EmitCtx, disc: &Discovery, cache: &Cache) -> EmitResult {
    let out = ctx.facts_dir().join("duplicates.json");

    // Fast cache check: module dirs + src/test dirs (20 stat() vs 1423).
    // Directory mtime changes when files are added/removed.
    // For package-declaration-only changes, run with OBSERVATORY_FORCE=1.
    let mut dir_inputs: Vec<std::path::PathBuf> = disc.module_dirs().iter().cloned().collect();
    dir_inputs.push(ctx.repo.join("src"));
    dir_inputs.push(ctx.repo.join("test"));
    if !cache.is_stale("facts/duplicates.json", &dir_inputs.iter().map(|p| p.as_path()).collect::<Vec<_>>()) {
        return Ok(out);
    }

    // Combine all Java files (src and test), filter out Test.java classes
    let mut all_files = disc.java_files().to_vec();
    all_files.extend_from_slice(disc.test_files());
    let non_test_classes: Vec<_> = all_files
        .iter()
        .filter(|p| !p.file_name().and_then(|n| n.to_str()).unwrap_or("").ends_with("Test.java"))
        .collect();

    // Parallel extraction of FQNs via BufReader (first 1KB only — stops at package line)
    let fqn_data: Vec<(String, String)> = non_test_classes
        .par_iter()
        .filter_map(|file_path| {
            let package = extract_package(file_path)?;
            let classname = file_path
                .file_name()
                .and_then(|n| n.to_str())?
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

    let total_fqns = fqn_data.len();

    // Group by FQN using AHashMap (3-10x faster than std HashMap for string keys)
    let mut fqn_map: AHashMap<String, Vec<String>> = AHashMap::with_capacity(total_fqns);
    for (fqn, location) in fqn_data {
        fqn_map.entry(fqn).or_insert_with(Vec::new).push(location);
    }

    // Find duplicates (count > 1)
    let mut duplicates: Vec<(String, Vec<String>)> = fqn_map
        .into_iter()
        .filter(|(_, locations)| locations.len() > 1)
        .map(|(fqn, mut locs)| { locs.sort(); (fqn, locs) })
        .collect();
    duplicates.sort_by(|a, b| a.0.cmp(&b.0));

    let status = if duplicates.is_empty() { "clean" } else { "duplicates_found" };
    let duplicate_objects: Vec<_> = duplicates.iter().map(|(fqn, locations)| {
        serde_json::json!({ "fqn": fqn, "count": locations.len(), "locations": locations })
    }).collect();

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
