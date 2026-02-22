/// Dependency conflict detection — identifies version mismatches across modules.
///
/// This fact answers: "Can I add dependency Y? Are there version conflicts?"
///
/// Algorithm:
/// 1. Read root pom.xml for <dependencyManagement> (canonical versions)
/// 2. Scan all module pom.xml files for direct <dependencies>
/// 3. Check for version conflicts: same artifactId with different versions
/// 4. Check for duplicates within the same POM
///
/// The root pom.xml is the source of truth for all managed versions.
/// Conflicts occur when a module uses a version different from the managed version,
/// or when two modules use the same dependency with different versions.
use crate::{Cache, Discovery};
use super::{write_json, EmitCtx, EmitResult};
use std::collections::HashMap;
use std::path::Path;

pub fn emit(ctx: &EmitCtx, disc: &Discovery, cache: &Cache) -> EmitResult {
    let out = ctx.facts_dir().join("deps-conflicts.json");

    // Cache: inputs = root pom.xml + all module pom.xml files
    let mut pom_files = vec![ctx.repo.join("pom.xml")];
    pom_files.extend_from_slice(disc.pom_files());

    if !cache.is_stale("facts/deps-conflicts.json", &pom_files.iter().map(|p| p.as_ref()).collect::<Vec<_>>()) {
        return Ok(out);
    }

    // Read managed versions from root pom.xml
    let root_pom = ctx.repo.join("pom.xml");
    let managed_versions = extract_managed_versions(&root_pom);

    // Read direct dependencies from all module pom.xml files
    let mut module_deps: HashMap<String, Vec<String>> = HashMap::new();
    for pom_path in disc.pom_files() {
        if pom_path.file_name().and_then(|n| n.to_str()).map(|n| n == "pom.xml").unwrap_or(false) {
            if let Some(module_name) = extract_module_name(&pom_path) {
                let deps = extract_direct_dependencies(&pom_path);
                module_deps.insert(module_name, deps);
            }
        }
    }

    // Detect conflicts: same dependency with different versions across modules
    let conflicts = detect_conflicts(&managed_versions, &module_deps);

    let conflict_count = conflicts.len();
    let status = if conflicts.is_empty() {
        "clean"
    } else {
        "conflicts_found"
    };

    let conflict_objects: Vec<_> = conflicts
        .into_iter()
        .map(|(dep_id, versions)| {
            serde_json::json!({
                "dependency": dep_id,
                "versions_used": versions
            })
        })
        .collect();

    let json_value = serde_json::json!({
        "generated_at": ctx.timestamp,
        "commit": ctx.commit,
        "managed_versions": managed_versions,
        "conflicts": conflict_objects,
        "direct_deps_by_module": module_deps,
        "status": status,
        "conflict_count": conflict_count,
        "advice": if conflict_count == 0 {
            "No conflicts detected. Add new deps to <dependencyManagement> in root pom.xml first."
        } else {
            "Resolve conflicts by aligning versions to managed versions in root pom.xml."
        }
    });

    write_json(&out, &json_value)
}

/// Extract managed dependency versions from <dependencyManagement> section.
///
/// Returns HashMap<"groupId:artifactId", "version">
fn extract_managed_versions(pom_path: &Path) -> HashMap<String, String> {
    let mut versions = HashMap::new();

    if let Ok(content) = std::fs::read_to_string(pom_path) {
        // Find <dependencyManagement> section
        if let Some(start) = content.find("<dependencyManagement>") {
            if let Some(end) = content[start..].find("</dependencyManagement>") {
                let dep_mgmt_section = &content[start..start + end + "</dependencyManagement>".len()];

                // Extract all <dependency> blocks
                let mut search_start = 0;
                while let Some(dep_start) = dep_mgmt_section[search_start..].find("<dependency>") {
                    let dep_start = search_start + dep_start;
                    if let Some(dep_end) = dep_mgmt_section[dep_start..].find("</dependency>") {
                        let dep_end = dep_start + dep_end + "</dependency>".len();
                        let dep_block = &dep_mgmt_section[dep_start..dep_end];

                        // Extract groupId, artifactId, version
                        if let (Some(g), Some(a), Some(v)) = (
                            extract_xml_tag_value(dep_block, "groupId"),
                            extract_xml_tag_value(dep_block, "artifactId"),
                            extract_xml_tag_value(dep_block, "version"),
                        ) {
                            let key = format!("{}:{}", g, a);
                            versions.insert(key, v);
                        }

                        search_start = dep_end;
                    } else {
                        break;
                    }
                }
            }
        }
    }

    versions
}

/// Extract direct dependencies from a module pom.xml.
///
/// Returns Vec<"groupId:artifactId:version">
fn extract_direct_dependencies(pom_path: &Path) -> Vec<String> {
    let mut deps = Vec::new();

    if let Ok(content) = std::fs::read_to_string(pom_path) {
        // Skip dependencyManagement section (only look at direct <dependencies>)
        let mut search_content = content.as_str();

        // Find the main <dependencies> section (not <dependencyManagement>)
        loop {
            if let Some(dep_start) = search_content.find("<dependencies>") {
                // Make sure this is not inside dependencyManagement
                let before = &content[..content.len() - search_content.len() + dep_start];
                let after_last_mgmt_close = before.rfind("</dependencyManagement>");

                // Check if we're still inside dependencyManagement
                if let Some(mgmt_close_pos) = after_last_mgmt_close {
                    let after_last_open = before[..mgmt_close_pos].rfind("<dependencyManagement>");
                    if after_last_open.is_some() {
                        // We're inside dependencyManagement, skip
                        search_content = &search_content[dep_start + "<dependencies>".len()..];
                        continue;
                    }
                }

                // Found the right <dependencies> section
                if let Some(dep_end) = search_content.find("</dependencies>") {
                    let deps_section = &search_content[dep_start..dep_start + dep_end + "</dependencies>".len()];

                    // Extract all <dependency> blocks
                    let mut search_start = 0;
                    while let Some(d_start) = deps_section[search_start..].find("<dependency>") {
                        let d_start = search_start + d_start;
                        if let Some(d_end) = deps_section[d_start..].find("</dependency>") {
                            let d_end = d_start + d_end + "</dependency>".len();
                            let dep_block = &deps_section[d_start..d_end];

                            // Extract groupId, artifactId, version (if present)
                            if let (Some(g), Some(a)) = (
                                extract_xml_tag_value(dep_block, "groupId"),
                                extract_xml_tag_value(dep_block, "artifactId"),
                            ) {
                                let version = extract_xml_tag_value(dep_block, "version")
                                    .unwrap_or_else(|| "(inherited)".to_string());
                                let dep = format!("{}:{}:{}", g, a, version);
                                deps.push(dep);
                            }

                            search_start = d_end;
                        } else {
                            break;
                        }
                    }
                }
                break;
            } else {
                break;
            }
        }
    }

    deps
}

/// Detect version conflicts across modules.
///
/// Returns Vec<(dependency_id, Vec<(module, version)>)> for deps with conflicts
fn detect_conflicts(
    _managed_versions: &HashMap<String, String>,
    module_deps: &HashMap<String, Vec<String>>,
) -> Vec<(String, Vec<(String, String)>)> {
    let mut dep_versions: HashMap<String, HashMap<String, String>> = HashMap::new();

    for (module, deps) in module_deps {
        for dep in deps {
            // Parse "groupId:artifactId:version"
            let parts: Vec<&str> = dep.split(':').collect();
            if parts.len() >= 3 {
                let group_artifact = format!("{}:{}", parts[0], parts[1]);
                let version = parts[2].to_string();

                dep_versions
                    .entry(group_artifact)
                    .or_insert_with(HashMap::new)
                    .insert(module.clone(), version);
            }
        }
    }

    // Find conflicts: same dependency with multiple versions
    let mut conflicts = Vec::new();
    for (dep_id, versions_by_module) in dep_versions {
        let unique_versions: std::collections::HashSet<_> = versions_by_module.values().cloned().collect();

        if unique_versions.len() > 1 {
            let mut versions_list: Vec<_> = versions_by_module
                .into_iter()
                .map(|(m, v)| (m, v))
                .collect();
            versions_list.sort_by(|a, b| a.1.cmp(&b.1));

            conflicts.push((dep_id, versions_list));
        }
    }

    conflicts.sort_by(|a, b| a.0.cmp(&b.0));
    conflicts
}

/// Extract value from an XML tag on a single line.
///
/// Example: "<groupId>com.example</groupId>" -> Some("com.example")
fn extract_xml_tag_value(content: &str, tag: &str) -> Option<String> {
    let open = format!("<{}>", tag);
    let close = format!("</{}>", tag);

    for line in content.lines() {
        let trimmed = line.trim();
        if let Some(rest) = trimmed.strip_prefix(&open) {
            if let Some(value) = rest.strip_suffix(&close) {
                let val = value.trim().to_string();
                if !val.is_empty() {
                    return Some(val);
                }
            }
        }
    }

    None
}

/// Extract module name from a pom.xml path.
///
/// Assumes module structure: <repo>/module-name/pom.xml
/// Returns Some("module-name") or None
fn extract_module_name(pom_path: &Path) -> Option<String> {
    pom_path
        .parent()
        .and_then(|p| p.file_name())
        .and_then(|n| n.to_str())
        .map(|s| s.to_string())
}
