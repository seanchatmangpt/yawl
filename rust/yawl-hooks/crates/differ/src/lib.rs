use delta::{Delta, DeltaList, DeclKind, Change};
use std::path::Path;

/// Detect deltas between old and new Java source files
pub fn diff_java(old_content: &str, new_content: &str, _file_path: &str) -> Result<DeltaList, Box<dyn std::error::Error>> {
    let mut deltas = DeltaList::new();

    // Simple declaration detection: look for public/private/protected identifiers
    let old_funcs = extract_declarations(old_content);
    let new_funcs = extract_declarations(new_content);

    // Detect removed functions
    for (name, kind) in &old_funcs {
        if !new_funcs.contains_key(name) {
            deltas.add(Delta::Declaration {
                kind: kind.clone(),
                name: name.clone(),
                change: Change::Removed,
            });
        }
    }

    // Detect added/modified functions
    for (name, kind) in &new_funcs {
        if let Some(old_kind) = old_funcs.get(name) {
            if old_kind != kind {
                deltas.add(Delta::Declaration {
                    kind: kind.clone(),
                    name: name.clone(),
                    change: Change::Modified {
                        from: format!("{:?}", old_kind),
                        to: format!("{:?}", kind),
                    },
                });
            }
        } else {
            deltas.add(Delta::Declaration {
                kind: kind.clone(),
                name: name.clone(),
                change: Change::Added,
            });
        }
    }

    Ok(deltas)
}

/// Detect deltas in TOML files (manifests, config)
pub fn diff_toml(old_content: &str, new_content: &str) -> Result<DeltaList, Box<dyn std::error::Error>> {
    let mut deltas = DeltaList::new();

    let old_table: toml::Table = toml::from_str(old_content).unwrap_or_default();
    let new_table: toml::Table = toml::from_str(new_content).unwrap_or_default();

    // Check for dependency version changes
    if let (Some(old_deps), Some(new_deps)) = (
        old_table.get("dependencies").and_then(|v| v.as_table()),
        new_table.get("dependencies").and_then(|v| v.as_table()),
    ) {
        for (name, new_val) in new_deps {
            if let Some(old_val) = old_deps.get(name) {
                if old_val != new_val {
                    let old_ver = old_val.as_str().unwrap_or("unknown").to_string();
                    let new_ver = new_val.as_str().unwrap_or("unknown").to_string();
                    deltas.add(Delta::Dependency {
                        name: name.clone(),
                        from: old_ver,
                        to: new_ver,
                        breaking: detect_breaking_change(name, old_val.as_str().unwrap_or(""), new_val.as_str().unwrap_or("")),
                    });
                }
            }
        }
    }

    Ok(deltas)
}

/// Detect deltas in CLAUDE.md SPR rules
pub fn diff_spr(old_content: &str, new_content: &str) -> Result<DeltaList, Box<dyn std::error::Error>> {
    let mut deltas = DeltaList::new();

    let old_sections = extract_sections(old_content);
    let new_sections = extract_sections(new_content);

    for (section, new_rules) in &new_sections {
        if let Some(old_rules) = old_sections.get(section) {
            for rule in new_rules {
                if !old_rules.contains(rule) {
                    deltas.add(Delta::Rule {
                        section: section.clone(),
                        predicate: rule.clone(),
                        change: Change::Added,
                    });
                }
            }
        } else {
            for rule in new_rules {
                deltas.add(Delta::Rule {
                    section: section.clone(),
                    predicate: rule.clone(),
                    change: Change::Added,
                });
            }
        }
    }

    Ok(deltas)
}

/// Main dispatcher for diff based on file type
pub fn diff_file(old_content: &str, new_content: &str, file_path: &str) -> Result<DeltaList, Box<dyn std::error::Error>> {
    match Path::new(file_path).extension().and_then(|s| s.to_str()) {
        Some("java") => diff_java(old_content, new_content, file_path),
        Some("toml") => diff_toml(old_content, new_content),
        Some("md") if file_path.contains("CLAUDE.md") => diff_spr(old_content, new_content),
        _ => Ok(DeltaList::new()), // No diff for unknown types
    }
}

// Helper: extract declarations from Java source
fn extract_declarations(content: &str) -> std::collections::HashMap<String, DeclKind> {
    let mut decls = std::collections::HashMap::new();

    for line in content.lines() {
        let trimmed = line.trim();
        if trimmed.starts_with("public ") || trimmed.starts_with("private ") || trimmed.starts_with("protected ") {
            if trimmed.contains("void ") || trimmed.contains("String ") || trimmed.contains("int ") {
                if let Some(name) = extract_function_name(trimmed) {
                    decls.insert(name, DeclKind::Function);
                }
            } else if trimmed.starts_with("public class ") || trimmed.starts_with("public interface ") {
                if let Some(name) = extract_class_name(trimmed) {
                    decls.insert(name, DeclKind::Type);
                }
            }
        }
    }

    decls
}

fn extract_function_name(line: &str) -> Option<String> {
    if let Some(paren_pos) = line.find('(') {
        let before_paren = line[..paren_pos].trim();
        if let Some(name) = before_paren.split_whitespace().last() {
            return Some(name.to_string());
        }
    }
    None
}

fn extract_class_name(line: &str) -> Option<String> {
    let words: Vec<&str> = line.split_whitespace().collect();
    if words.len() > 2 {
        Some(words[2].trim_end_matches('{').to_string())
    } else {
        None
    }
}

// Helper: extract sections from SPR markdown
fn extract_sections(content: &str) -> std::collections::HashMap<String, Vec<String>> {
    let mut sections = std::collections::HashMap::new();
    let mut current_section = String::new();
    let mut rules = Vec::new();

    for line in content.lines() {
        if line.starts_with("## ") {
            if !current_section.is_empty() {
                sections.insert(current_section.clone(), rules.clone());
            }
            current_section = line.trim_start_matches("## ").to_string();
            rules.clear();
        } else if !line.is_empty() && !current_section.is_empty() {
            rules.push(line.to_string());
        }
    }

    if !current_section.is_empty() {
        sections.insert(current_section, rules);
    }

    sections
}

fn detect_breaking_change(_crate_name: &str, old_ver: &str, new_ver: &str) -> bool {
    let old_major = old_ver.split('.').next().unwrap_or("0");
    let new_major = new_ver.split('.').next().unwrap_or("0");
    old_major != new_major
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_java_diff() {
        let old = "public void foo() { }";
        let new = "public void foo() { }\npublic void bar() { }";
        let deltas = diff_java(old, new, "test.java").unwrap();
        assert!(!deltas.deltas.is_empty());
    }

    #[test]
    fn test_toml_diff() {
        let old = "[dependencies]\ntest = \"1.0\"";
        let new = "[dependencies]\ntest = \"2.0\"";
        let deltas = diff_toml(old, new).unwrap();
        assert!(!deltas.deltas.is_empty());
    }
}
