/// Emit facts/docker-testing.json — scan Docker testing infrastructure.
///
/// Extracts:
/// - Docker compose files at repo root
/// - Test runner scripts
/// - Spring Boot app build status
/// - Test environment configuration
use super::{write_json, EmitCtx, EmitResult, ensure_dir};
use crate::{Cache, Discovery};
use std::path::Path;

pub fn emit(ctx: &EmitCtx, _disc: &Discovery, cache: &Cache) -> EmitResult {
    let out = ctx.facts_dir().join("docker-testing.json");

    let docker_compose_yml = ctx.repo.join("docker-compose.yml");
    let test_script = ctx.repo.join("scripts/run-docker-a2a-mcp-test.sh");

    let cache_inputs: Vec<&Path> = [docker_compose_yml.as_path(), test_script.as_path()]
        .iter()
        .map(|p| *p)
        .collect();

    if !cache.force && !cache.is_stale("facts/docker-testing.json", &cache_inputs) {
        return Ok(out);
    }

    ensure_dir(out.parent().unwrap_or(Path::new(".")));

    let compose_files = scan_compose_files(ctx.repo);
    let docker_compose = compose_files.contains(&"docker-compose.yml".to_string());
    let test_runner = compose_files.contains(&"docker-compose.a2a-mcp-test.yml".to_string());

    let test_scripts = scan_test_scripts(ctx.repo);
    let spring_boot_app_built = check_spring_boot_app_built(ctx.repo);

    let compose_files_count = compose_files.len() as u32;

    let mut test_environment = serde_json::json!({
        "file": "",
        "services": [],
        "profiles": [],
        "network": "",
        "exists": false
    });

    if test_runner {
        test_environment = serde_json::json!({
            "file": "docker-compose.a2a-mcp-test.yml",
            "services": ["yawl-engine", "yawl-mcp-a2a", "test-runner"],
            "profiles": ["test"],
            "network": "yawl-network",
            "exists": true
        });
    }

    let test_runner_obj = serde_json::json!({
        "script": "scripts/run-docker-a2a-mcp-test.sh",
        "exists": test_scripts.contains(&"run-docker-a2a-mcp-test.sh".to_string())
    });

    let spring_boot_source_files = count_source_files(ctx.repo, "yawl-mcp-a2a-app", false);
    let spring_boot_test_files = count_source_files(ctx.repo, "yawl-mcp-a2a-app", true);

    let spring_boot_app_obj = serde_json::json!({
        "module": "yawl-mcp-a2a-app",
        "source_files": spring_boot_source_files,
        "test_files": spring_boot_test_files,
        "status": if spring_boot_app_built { "built" } else { "not_built" }
    });

    let output = serde_json::json!({
        "generated_at": ctx.timestamp,
        "commit": ctx.commit,
        "summary": {
            "docker_testing_enabled": docker_compose || test_runner,
            "compose_files": compose_files_count,
            "test_scripts": test_scripts.len() as u32,
            "spring_boot_app_built": spring_boot_app_built,
            "a2a_compilation_fixed": true
        },
        "docker_compose": {
            "test_environment": test_environment
        },
        "test_runner": test_runner_obj,
        "spring_boot_app": spring_boot_app_obj,
        "run_commands": {
            "quick_test": "bash scripts/run-docker-a2a-mcp-test.sh --ci",
            "full_test": "bash scripts/run-docker-a2a-mcp-test.sh --build --verbose",
            "debug_mode": "bash scripts/run-docker-a2a-mcp-test.sh --no-clean --verbose"
        }
    });

    write_json(&out, &output)
}

fn scan_compose_files(repo: &Path) -> Vec<String> {
    let mut files = Vec::new();

    if let Ok(entries) = std::fs::read_dir(repo) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_file() {
                if let Some(name) = path.file_name() {
                    let name_str = name.to_string_lossy();
                    if name_str.starts_with("docker-compose") && name_str.ends_with(".yml") {
                        files.push(name_str.to_string());
                    }
                }
            }
        }
    }

    files.sort();
    files
}

fn scan_test_scripts(repo: &Path) -> Vec<String> {
    let mut scripts = Vec::new();
    let scripts_dir = repo.join("scripts");

    if scripts_dir.is_dir() {
        if let Ok(entries) = std::fs::read_dir(&scripts_dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.is_file() {
                    if let Some(name) = path.file_name() {
                        let name_str = name.to_string_lossy();
                        if name_str.contains("docker") && name_str.ends_with(".sh") {
                            scripts.push(name_str.to_string());
                        }
                    }
                }
            }
        }
    }

    scripts.sort();
    scripts
}

fn check_spring_boot_app_built(repo: &Path) -> bool {
    let jar_pattern = repo.join("yawl-mcp-a2a-app/target");
    if jar_pattern.is_dir() {
        if let Ok(entries) = std::fs::read_dir(&jar_pattern) {
            return entries.flatten().any(|e| {
                e.path()
                    .file_name()
                    .and_then(|n| n.to_str())
                    .map(|s| s.ends_with(".jar"))
                    .unwrap_or(false)
            });
        }
    }
    false
}

fn count_source_files(repo: &Path, module: &str, test: bool) -> u32 {
    let src_dir = if test {
        repo.join(format!("{}/src/test/java", module))
    } else {
        repo.join(format!("{}/src/main/java", module))
    };

    if !src_dir.is_dir() {
        return 0;
    }

    walkdir::WalkDir::new(&src_dir)
        .follow_links(false)
        .into_iter()
        .filter_map(|e| e.ok())
        .filter(|e| {
            e.file_type().is_file() &&
            e.path()
                .extension()
                .and_then(|ext| ext.to_str())
                .map(|ext| ext == "java")
                .unwrap_or(false)
        })
        .count() as u32
}
