use delta::types::{Change, Delta};
use std::collections::HashMap;

/// Structured diff of Cargo.toml dependency version constraints.
/// No diff algorithm — parse both versions, compare fields directly.
pub fn diff_cargo_toml(old: &str, new: &str) -> anyhow::Result<Vec<Delta>> {
    let old_deps = parse_cargo_deps(old)?;
    let new_deps = parse_cargo_deps(new)?;

    let mut deltas = Vec::new();

    // Added dependencies
    for (name, new_version) in &new_deps {
        if !old_deps.contains_key(name) {
            deltas.push(Delta::Dependency {
                name: name.clone(),
                from: String::new(),
                to: new_version.clone(),
                breaking: false,
            });
        }
    }

    // Removed dependencies
    for (name, old_version) in &old_deps {
        if !new_deps.contains_key(name) {
            deltas.push(Delta::Dependency {
                name: name.clone(),
                from: old_version.clone(),
                to: String::new(),
                breaking: true, // Removal is always breaking
            });
        }
    }

    // Version changes
    for (name, new_version) in &new_deps {
        if let Some(old_version) = old_deps.get(name) {
            if old_version != new_version {
                let breaking = is_breaking_version_change(old_version, new_version);
                deltas.push(Delta::Dependency {
                    name: name.clone(),
                    from: old_version.clone(),
                    to: new_version.clone(),
                    breaking,
                });
            }
        }
    }

    Ok(deltas)
}

fn parse_cargo_deps(content: &str) -> anyhow::Result<HashMap<String, String>> {
    let value: toml::Value = content.parse().map_err(|e| anyhow::anyhow!("TOML parse error: {}", e))?;
    let mut deps = HashMap::new();

    let table = value.as_table().ok_or_else(|| anyhow::anyhow!("Not a TOML table"))?;

    // Parse [dependencies], [dev-dependencies], [build-dependencies]
    for section in &["dependencies", "dev-dependencies", "build-dependencies", "workspace.dependencies"] {
        if let Some(dep_table) = table.get(*section).and_then(|v| v.as_table()) {
            for (name, val) in dep_table {
                let version = match val {
                    toml::Value::String(s) => s.clone(),
                    toml::Value::Table(t) => {
                        t.get("version")
                            .and_then(|v| v.as_str())
                            .unwrap_or("*")
                            .to_string()
                    }
                    _ => val.to_string(),
                };
                deps.insert(name.clone(), version);
            }
        }
    }

    Ok(deps)
}

fn is_breaking_version_change(old: &str, new: &str) -> bool {
    // A major version bump is breaking (semver: major > 0)
    let old_major = parse_major(old);
    let new_major = parse_major(new);
    new_major < old_major || (old_major > 0 && new_major > old_major)
}

fn parse_major(version: &str) -> u64 {
    let v = version.trim_start_matches('^').trim_start_matches('~').trim_start_matches('=');
    v.split('.').next().and_then(|s| s.parse().ok()).unwrap_or(0)
}

/// Structured diff of JIRA ticket TOML files.
/// Field-level comparison → Criterion deltas.
pub fn diff_ticket_toml(ticket_id: &str, old: &str, new: &str) -> anyhow::Result<Vec<Delta>> {
    let old_criteria = parse_ticket_criteria(old)?;
    let new_criteria = parse_ticket_criteria(new)?;

    let mut deltas = Vec::new();

    for (ac, new_val) in &new_criteria {
        match old_criteria.get(ac) {
            None => {
                deltas.push(Delta::Criterion {
                    ticket_id: ticket_id.to_string(),
                    ac: ac.clone(),
                    change: Change::Added,
                });
            }
            Some(old_val) if old_val != new_val => {
                deltas.push(Delta::Criterion {
                    ticket_id: ticket_id.to_string(),
                    ac: ac.clone(),
                    change: Change::Modified {
                        from: old_val.to_string(),
                        to: new_val.to_string(),
                    },
                });
            }
            _ => {}
        }
    }

    for ac in old_criteria.keys() {
        if !new_criteria.contains_key(ac) {
            deltas.push(Delta::Criterion {
                ticket_id: ticket_id.to_string(),
                ac: ac.clone(),
                change: Change::Removed,
            });
        }
    }

    Ok(deltas)
}

fn parse_ticket_criteria(content: &str) -> anyhow::Result<HashMap<String, String>> {
    let value: toml::Value = content.parse().map_err(|e| anyhow::anyhow!("TOML parse error: {}", e))?;
    let mut criteria = HashMap::new();

    if let Some(ac_table) = value
        .get("ticket")
        .and_then(|t| t.get("acceptance_criteria"))
        .and_then(|v| v.as_table())
    {
        for (key, val) in ac_table {
            criteria.insert(key.clone(), val.to_string());
        }
    }

    Ok(criteria)
}
