/// Emit facts/reactor.json — build reactor order and inter-module dependencies.
///
/// Reads root pom.xml for <modules> order, then parses each module's
/// pom.xml for <dependencies> to detect inter-module deps (yawl-* → yawl-*).
use super::{write_json, EmitCtx, EmitResult, ensure_dir};
use crate::{Cache, Discovery};
use std::path::Path;

/// Emit facts/reactor.json — reactor build order and module dependency graph.
pub fn emit(ctx: &EmitCtx, disc: &Discovery, cache: &Cache) -> EmitResult {
    let out = ctx.facts_dir().join("reactor.json");

    // Check cache: root pom.xml + all module poms
    let root_pom = ctx.repo.join("pom.xml");
    let mut input_paths: Vec<&Path> = vec![&root_pom];
    for pom in disc.pom_files() {
        input_paths.push(pom);
    }

    if !cache.is_stale("facts/reactor.json", &input_paths) {
        return Ok(out);
    }

    ensure_dir(out.parent().unwrap_or(Path::new(".")));

    // Extract reactor order from root pom.xml <modules> section
    let reactor_order = extract_reactor_order(&root_pom);

    // Extract inter-module dependencies
    let module_deps = extract_module_deps(disc.module_dirs(), &ctx.repo);

    let output = serde_json::json!({
        "generated_at": ctx.timestamp,
        "commit": ctx.commit,
        "reactor_order": reactor_order,
        "module_deps": module_deps
    });

    write_json(&out, &output)
}

/// Extract <modules> section from pom.xml — list of module names in build order.
fn extract_reactor_order(pom_path: &Path) -> Vec<String> {
    let content = match std::fs::read_to_string(pom_path) {
        Ok(c) => c,
        Err(_) => return vec![],
    };

    let mut modules = Vec::new();
    let mut in_modules = false;

    for line in content.lines() {
        let trimmed = line.trim();

        if trimmed.starts_with("<modules>") {
            in_modules = true;
            continue;
        }
        if trimmed.starts_with("</modules>") {
            break;
        }

        if in_modules && trimmed.starts_with("<module>") {
            if let Some(rest) = trimmed.strip_prefix("<module>") {
                if let Some(module) = rest.strip_suffix("</module>") {
                    modules.push(module.trim().to_string());
                }
            }
        }
    }

    modules
}

/// Extract inter-module dependencies (yawl-* → yawl-*).
fn extract_module_deps(module_dirs: &[std::path::PathBuf], _repo: &Path) -> Vec<serde_json::Value> {
    let mut deps = Vec::new();
    let mut seen = std::collections::HashSet::new();

    for module_dir in module_dirs {
        let pom_path = module_dir.join("pom.xml");
        if !pom_path.exists() {
            continue;
        }

        let from = module_dir
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("")
            .to_string();

        if from.is_empty() {
            continue;
        }

        // Extract all dependencies from pom.xml
        if let Ok(content) = std::fs::read_to_string(&pom_path) {
            for line in content.lines() {
                let trimmed = line.trim();

                // Look for <artifactId>yawl-*</artifactId>
                if trimmed.starts_with("<artifactId>yawl-") && trimmed.ends_with("</artifactId>") {
                    if let Some(rest) = trimmed.strip_prefix("<artifactId>") {
                        if let Some(to) = rest.strip_suffix("</artifactId>") {
                            let to_str = to.to_string();

                            // Only add if it's a different module and not yet recorded
                            if to_str != from {
                                let dep_key = format!("{}→{}", from, to_str);
                                if seen.insert(dep_key) {
                                    deps.push(serde_json::json!({
                                        "from": from.clone(),
                                        "to": to_str
                                    }));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deps
}
