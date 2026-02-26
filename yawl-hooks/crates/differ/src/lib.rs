pub mod manifest;
pub mod semantic;
pub mod structural;

use delta::types::{Delta, SemanticUnit};

/// Dispatch to the correct diff algorithm based on the artifact's semantic unit.
/// This is the primary entry point for the differ crate.
pub fn compute_deltas(path: &str, old: &str, new: &str) -> anyhow::Result<Vec<Delta>> {
    let unit = SemanticUnit::from_path(path);

    match unit {
        SemanticUnit::Declaration => {
            Ok(structural::diff_declarations(old, new))
        }
        SemanticUnit::Rule => {
            Ok(structural::diff_rules(old, new))
        }
        SemanticUnit::Quad => {
            Ok(structural::diff_quads(old, new))
        }
        SemanticUnit::DependencyConstraint => {
            // For Cargo.toml and similar manifests
            if path.contains("tickets/") {
                // Extract ticket ID from path
                let ticket_id = std::path::Path::new(path)
                    .file_stem()
                    .and_then(|s| s.to_str())
                    .unwrap_or("UNKNOWN")
                    .to_string();
                manifest::diff_ticket_toml(&ticket_id, old, new)
            } else {
                manifest::diff_cargo_toml(old, new)
            }
        }
        SemanticUnit::Criterion => {
            let ticket_id = std::path::Path::new(path)
                .file_stem()
                .and_then(|s| s.to_str())
                .unwrap_or("UNKNOWN")
                .to_string();
            manifest::diff_ticket_toml(&ticket_id, old, new)
        }
        SemanticUnit::Behavior => {
            let component = std::path::Path::new(path)
                .file_name()
                .and_then(|s| s.to_str())
                .unwrap_or("unknown")
                .to_string();
            Ok(semantic::diff_behavior(&component, old, new))
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_declaration_diff_detects_added_function() {
        let old = "pub fn existing() -> bool { true }";
        let new = "pub fn existing() -> bool { true }\npub fn added() -> String { String::new() }";
        let deltas = compute_deltas("src/lib.rs", old, new).unwrap();
        assert!(
            deltas.iter().any(|d| matches!(d, Delta::Declaration { name, change: delta::types::Change::Added, .. } if name == "added")),
            "Expected 'added' function to appear as Added delta, got: {:?}", deltas
        );
    }

    #[test]
    fn test_cargo_diff_detects_version_change() {
        let old = r#"[dependencies]
serde = "1.0"
"#;
        let new = r#"[dependencies]
serde = "2.0"
"#;
        let deltas = compute_deltas("Cargo.toml", old, new).unwrap();
        assert!(
            deltas.iter().any(|d| matches!(d, Delta::Dependency { name, from, to, .. } if name == "serde" && from == "1.0" && to == "2.0")),
            "Expected serde version delta, got: {:?}", deltas
        );
    }
}
