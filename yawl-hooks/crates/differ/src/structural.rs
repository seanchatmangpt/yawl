use delta::types::{Change, DeclKind, Delta};

/// Tokenize source code into declaration-boundary tokens.
/// Splits on function/class/type/const/import boundaries.
fn tokenize_declarations(source: &str) -> Vec<String> {
    let mut tokens = Vec::new();
    let mut current = String::new();

    for line in source.lines() {
        let trimmed = line.trim();
        // Detect declaration boundaries for Java, Rust, Python
        let is_boundary = trimmed.starts_with("public ")
            || trimmed.starts_with("private ")
            || trimmed.starts_with("protected ")
            || trimmed.starts_with("static ")
            || trimmed.starts_with("fn ")
            || trimmed.starts_with("pub fn ")
            || trimmed.starts_with("pub(crate) fn ")
            || trimmed.starts_with("async fn ")
            || trimmed.starts_with("pub async fn ")
            || trimmed.starts_with("def ")
            || trimmed.starts_with("class ")
            || trimmed.starts_with("struct ")
            || trimmed.starts_with("enum ")
            || trimmed.starts_with("trait ")
            || trimmed.starts_with("interface ")
            || trimmed.starts_with("import ")
            || trimmed.starts_with("use ")
            || trimmed.starts_with("const ")
            || trimmed.starts_with("type ")
            || trimmed.starts_with("@")
            || trimmed.starts_with("record ");

        if is_boundary && !current.trim().is_empty() {
            tokens.push(current.clone());
            current = String::new();
        }
        current.push_str(line);
        current.push('\n');
    }
    if !current.trim().is_empty() {
        tokens.push(current);
    }
    tokens
}

/// Extract the name of a declaration token for use in the Delta.
fn extract_decl_name(token: &str) -> (DeclKind, String) {
    let trimmed = token.trim();
    let line = trimmed.lines().next().unwrap_or("").trim();

    // Java / Rust / Python detection
    if line.contains("class ") || line.contains("struct ") {
        let kind = DeclKind::Class;
        let name = extract_identifier_after(line, &["class ", "struct "]);
        return (kind, name);
    }
    if line.contains("enum ") {
        let name = extract_identifier_after(line, &["enum "]);
        return (DeclKind::Enum, name);
    }
    if line.contains("interface ") {
        let name = extract_identifier_after(line, &["interface "]);
        return (DeclKind::Interface, name);
    }
    if line.contains("trait ") {
        let name = extract_identifier_after(line, &["trait "]);
        return (DeclKind::Trait, name);
    }
    if line.contains("fn ") || (line.contains('(') && line.contains(')')) {
        let name = extract_identifier_after(line, &["fn ", "def ", "public ", "private ", "protected ", "static "]);
        let name = name.split('(').next().unwrap_or(&name).trim().to_string();
        return (DeclKind::Function, name);
    }
    if line.contains("import ") || line.contains("use ") {
        let name = extract_identifier_after(line, &["import ", "use "]);
        return (DeclKind::Import, name);
    }
    if line.contains("const ") {
        let name = extract_identifier_after(line, &["const "]);
        return (DeclKind::Constant, name);
    }
    if line.contains("type ") {
        let name = extract_identifier_after(line, &["type "]);
        return (DeclKind::Type, name);
    }

    (DeclKind::Field, line.chars().take(40).collect())
}

fn extract_identifier_after(line: &str, keywords: &[&str]) -> String {
    for kw in keywords {
        if let Some(pos) = line.find(kw) {
            let after = &line[pos + kw.len()..];
            let name: String = after
                .chars()
                .take_while(|c| c.is_alphanumeric() || *c == '_')
                .collect();
            if !name.is_empty() {
                return name;
            }
        }
    }
    line.chars().take(40).collect()
}

/// Compute a structural diff between two source files.
/// Returns typed Declaration deltas — no line numbers.
pub fn diff_declarations(old: &str, new: &str) -> Vec<Delta> {
    let old_tokens = tokenize_declarations(old);
    let new_tokens = tokenize_declarations(new);

    let mut deltas = Vec::new();

    // For declarations: compare old vs new token sets
    // Tokens in new but not old → Added; tokens in old but not new → Removed
    // Tokens present in both but with content change → Modified
    let old_set: std::collections::HashMap<String, &str> = old_tokens
        .iter()
        .map(|t| {
            let (_, name) = extract_decl_name(t);
            (name, t.as_str())
        })
        .collect();

    let new_set: std::collections::HashMap<String, &str> = new_tokens
        .iter()
        .map(|t| {
            let (_, name) = extract_decl_name(t);
            (name, t.as_str())
        })
        .collect();

    // Added declarations
    for (name, content) in &new_set {
        if !old_set.contains_key(name) {
            let (decl_kind, _) = extract_decl_name(content);
            deltas.push(Delta::Declaration {
                decl_kind,
                name: name.clone(),
                change: Change::Added,
            });
        }
    }

    // Removed declarations
    for (name, content) in &old_set {
        if !new_set.contains_key(name) {
            let (decl_kind, _) = extract_decl_name(content);
            deltas.push(Delta::Declaration {
                decl_kind,
                name: name.clone(),
                change: Change::Removed,
            });
        }
    }

    // Modified declarations (same name, different content)
    for (name, new_content) in &new_set {
        if let Some(old_content) = old_set.get(name) {
            if old_content != new_content {
                let (decl_kind, _) = extract_decl_name(new_content);
                deltas.push(Delta::Declaration {
                    decl_kind,
                    name: name.clone(),
                    change: Change::Modified {
                        from: old_content.chars().take(200).collect(),
                        to: new_content.chars().take(200).collect(),
                    },
                });
            }
        }
    }

    deltas
}

/// Diff SPR/CLAUDE.md rule predicates.
/// Each section + predicate pair is a semantic unit.
pub fn diff_rules(old: &str, new: &str) -> Vec<Delta> {
    let old_rules = parse_spr_rules(old);
    let new_rules = parse_spr_rules(new);

    let mut deltas = Vec::new();

    for (section, predicates) in &new_rules {
        let old_predicates = old_rules.get(section.as_str()).cloned().unwrap_or_default();
        for pred in predicates {
            if !old_predicates.contains(pred) {
                deltas.push(Delta::Rule {
                    section: section.clone(),
                    predicate: pred.clone(),
                    change: Change::Added,
                });
            }
        }
    }

    for (section, predicates) in &old_rules {
        let new_predicates = new_rules.get(section.as_str()).cloned().unwrap_or_default();
        for pred in predicates {
            if !new_predicates.contains(pred) {
                deltas.push(Delta::Rule {
                    section: section.clone(),
                    predicate: pred.clone(),
                    change: Change::Removed,
                });
            }
        }
    }

    deltas
}

fn parse_spr_rules(content: &str) -> std::collections::HashMap<String, Vec<String>> {
    let mut result: std::collections::HashMap<String, Vec<String>> = std::collections::HashMap::new();
    let mut current_section = "root".to_string();

    for line in content.lines() {
        if line.starts_with("## ") {
            current_section = line.trim_start_matches('#').trim().to_string();
        } else if !line.trim().is_empty() && !line.starts_with('#') {
            result
                .entry(current_section.clone())
                .or_default()
                .push(line.trim().to_string());
        }
    }

    result
}

/// Diff two N-Quad RDF files, returning quad-level deltas.
pub fn diff_quads(old: &str, new: &str) -> Vec<Delta> {
    let old_quads: std::collections::BTreeSet<String> = old
        .lines()
        .map(|l| l.trim().to_string())
        .filter(|l| !l.is_empty() && !l.starts_with('#'))
        .collect();

    let new_quads: std::collections::BTreeSet<String> = new
        .lines()
        .map(|l| l.trim().to_string())
        .filter(|l| !l.is_empty() && !l.starts_with('#'))
        .collect();

    let adds: Vec<delta::types::RdfQuad> = new_quads
        .difference(&old_quads)
        .map(|q| parse_nquad(q))
        .collect();

    let removes: Vec<delta::types::RdfQuad> = old_quads
        .difference(&new_quads)
        .map(|q| parse_nquad(q))
        .collect();

    if adds.is_empty() && removes.is_empty() {
        vec![]
    } else {
        vec![Delta::Quad { adds, removes }]
    }
}

fn parse_nquad(line: &str) -> delta::types::RdfQuad {
    let parts: Vec<&str> = line.splitn(5, ' ').collect();
    delta::types::RdfQuad {
        subject: parts.first().copied().unwrap_or("").to_string(),
        predicate: parts.get(1).copied().unwrap_or("").to_string(),
        object: parts.get(2).copied().unwrap_or("").to_string(),
        graph: parts.get(3).copied().map(|s| s.trim_end_matches('.').to_string()).filter(|s| !s.is_empty()),
    }
}
