/// Emit facts/shared-src.json — detect shared source strategy across modules.
///
/// Categorizes modules by their sourceDirectory strategy:
/// - full_shared: ../src (all modules share one src/ root)
/// - package_scoped: ../src/org/yawlfoundation/yawl/... (scoped to package)
/// - standard: src/main/java (normal Maven layout, no sharing)
use super::{write_json, EmitCtx, EmitResult, extract_xml_value, ensure_dir};
use crate::{Cache, Discovery};
use std::path::Path;

/// Emit facts/shared-src.json — shared source root analysis.
pub fn emit(ctx: &EmitCtx, disc: &Discovery, cache: &Cache) -> EmitResult {
    let out = ctx.facts_dir().join("shared-src.json");

    // Check cache: root pom.xml + all module poms
    let root_pom = ctx.repo.join("pom.xml");
    let mut input_paths: Vec<&Path> = vec![&root_pom];
    for pom in disc.pom_files() {
        input_paths.push(pom);
    }

    if !cache.is_stale("facts/shared-src.json", &input_paths) {
        return Ok(out);
    }

    ensure_dir(out.parent().unwrap_or(Path::new(".")));

    let mut full_shared = Vec::new();
    let mut package_scoped = Vec::new();
    let mut standard = Vec::new();

    // Categorize each module by its strategy
    for module_dir in disc.module_dirs() {
        let pom_path = module_dir.join("pom.xml");
        if !pom_path.exists() {
            continue;
        }

        let module_name = module_dir
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("unknown")
            .to_string();

        let source_dir = extract_xml_value(&pom_path, "sourceDirectory")
            .unwrap_or_else(|| "src/main/java".to_string());

        let _strategy = if source_dir == "../src" {
            full_shared.push(module_name);
            "full_shared"
        } else if source_dir.starts_with("../src/") {
            package_scoped.push(module_name);
            "scoped"
        } else {
            standard.push(module_name);
            "standard"
        };
    }

    let output = serde_json::json!({
        "generated_at": ctx.timestamp,
        "commit": ctx.commit,
        "summary": {
            "full_shared_count": full_shared.len(),
            "package_scoped_count": package_scoped.len(),
            "standard_count": standard.len()
        },
        "full_shared_modules": full_shared,
        "package_scoped_modules": package_scoped,
        "standard_modules": standard,
        "shared_source_root": "../src",
        "architecture_pattern": "shared_monolith_with_package_scoped_integration"
    });

    write_json(&out, &output)
}
