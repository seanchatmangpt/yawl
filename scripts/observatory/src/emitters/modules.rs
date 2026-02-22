/// Emit facts/modules.json — scans all module directories and collects metadata.
///
/// For each module with a pom.xml:
/// - Count src Java files and test files
/// - Detect source directory strategy (full_shared, scoped, standard)
/// - Detect Spring and Hibernate configuration
use super::{write_json, EmitCtx, EmitResult, extract_xml_value, ensure_dir};
use crate::{Cache, Discovery};
use std::path::{Path, PathBuf};

/// Emit facts/modules.json — metadata for all yawl-* modules.
pub fn emit(ctx: &EmitCtx, disc: &Discovery, cache: &Cache) -> EmitResult {
    let out = ctx.facts_dir().join("modules.json");

    // Check cache: if any module pom.xml is newer than output, regenerate
    let root_pom = ctx.repo.join("pom.xml");
    let mut input_paths: Vec<&Path> = vec![&root_pom];
    for pom in disc.pom_files() {
        input_paths.push(pom);
    }

    if !cache.is_stale("facts/modules.json", &input_paths) {
        return Ok(out);
    }

    ensure_dir(out.parent().unwrap_or(Path::new(".")));

    let mut modules = Vec::new();

    // Scan each module directory
    for module_dir in disc.module_dirs() {
        let module_name = module_dir
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("unknown")
            .to_string();

        let pom_path = module_dir.join("pom.xml");
        if !pom_path.exists() {
            continue;
        }

        // Count src and test files for this module
        let src_files = count_files_in_module(disc.java_files(), &module_dir, false);
        let test_files = count_files_in_module(disc.test_files(), &module_dir, true);
        let test_resources = count_test_resources(&module_dir);

        // Detect config files
        let spring_config = has_spring_config(&module_dir);
        let hibernate_config = has_hibernate_config(&module_dir);
        let config_files = if spring_config || hibernate_config { 1 } else { 0 };

        // Detect source directory strategy
        let source_dir = extract_xml_value(&pom_path, "sourceDirectory")
            .unwrap_or_else(|| "src/main/java".to_string());
        let strategy = detect_strategy(&source_dir);

        let module_json = serde_json::json!({
            "name": module_name,
            "path": module_dir.file_name().and_then(|n| n.to_str()).unwrap_or("unknown"),
            "has_pom": true,
            "src_files": src_files,
            "test_files": test_files,
            "test_resources": test_resources,
            "config_files": config_files,
            "spring_config": spring_config,
            "hibernate_config": hibernate_config,
            "source_dir": source_dir,
            "strategy": strategy
        });

        modules.push(module_json);
    }

    let output = serde_json::json!({
        "generated_at": ctx.timestamp,
        "commit": ctx.commit,
        "modules": modules
    });

    write_json(&out, &output)
}

/// Count Java files belonging to this module.
fn count_files_in_module(files: &[PathBuf], module_dir: &Path, _is_test: bool) -> usize {
    let module_str = module_dir.to_string_lossy();
    files
        .iter()
        .filter(|f| f.to_string_lossy().starts_with(module_str.as_ref()))
        .count()
}

/// Detect source directory strategy from sourceDirectory tag.
fn detect_strategy(source_dir: &str) -> &'static str {
    if source_dir == "../src" {
        "full_shared"
    } else if source_dir.starts_with("../src/") {
        "scoped"
    } else {
        "standard"
    }
}

/// Check if module has Spring configuration files.
fn has_spring_config(module_dir: &Path) -> bool {
    let resources = module_dir.join("src/main/resources");
    resources.join("application.yml").exists() || resources.join("application.properties").exists()
}

/// Check if module has Hibernate configuration.
fn has_hibernate_config(module_dir: &Path) -> bool {
    let resources = module_dir.join("src/main/resources");
    resources.join("hibernate.properties").exists()
}

/// Count test resource files.
fn count_test_resources(module_dir: &Path) -> usize {
    let test_resources = module_dir.join("src/test/resources");
    if !test_resources.exists() {
        return 0;
    }

    walkdir::WalkDir::new(&test_resources)
        .into_iter()
        .filter_map(|e| e.ok())
        .filter(|e| e.file_type().is_file())
        .count()
}
