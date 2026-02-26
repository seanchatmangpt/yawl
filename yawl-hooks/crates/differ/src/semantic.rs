use delta::types::Delta;

/// Semantic diff using `dissimilar` for changelog/spec content.
/// Only `Insert` chunks are returned — Claude sees only what was added to the world.
/// Equal and Delete chunks are discarded.
pub fn diff_behavior(component: &str, old: &str, new: &str) -> Vec<Delta> {
    let chunks = dissimilar::diff(old, new);
    let mut deltas = Vec::new();

    let mut new_text = String::new();
    let mut old_text = String::new();

    for chunk in &chunks {
        match chunk {
            dissimilar::Chunk::Insert(s) => new_text.push_str(s),
            dissimilar::Chunk::Delete(s) => old_text.push_str(s),
            dissimilar::Chunk::Equal(_) => {}
        }
    }

    if !new_text.is_empty() || !old_text.is_empty() {
        // Split into logical entries by double-newline or "## " headings
        let old_entries = split_changelog_entries(&old_text);
        let new_entries = split_changelog_entries(&new_text);

        for entry in &new_entries {
            if !entry.trim().is_empty() {
                deltas.push(Delta::Behavior {
                    component: component.to_string(),
                    old: old_entries.first().cloned().unwrap_or_default(),
                    new: entry.clone(),
                    breaking: entry.to_lowercase().contains("breaking")
                        || entry.contains("BREAKING"),
                });
            }
        }
    }

    deltas
}

fn split_changelog_entries(text: &str) -> Vec<String> {
    let mut entries = Vec::new();
    let mut current = String::new();

    for line in text.lines() {
        if (line.starts_with("## ") || line.starts_with("### ") || line.starts_with("- "))
            && !current.trim().is_empty()
        {
            entries.push(current.trim().to_string());
            current = String::new();
        }
        current.push_str(line);
        current.push('\n');
    }
    if !current.trim().is_empty() {
        entries.push(current.trim().to_string());
    }

    entries
}

/// Compute a semantic similarity score (0.0 to 1.0) between two strings.
/// Used to detect high-similarity changes that might be documentation drift.
pub fn similarity_score(a: &str, b: &str) -> f64 {
    if a == b {
        return 1.0;
    }
    if a.is_empty() || b.is_empty() {
        return 0.0;
    }

    let chunks = dissimilar::diff(a, b);
    let total: usize = chunks
        .iter()
        .map(|c| match c {
            dissimilar::Chunk::Equal(s) | dissimilar::Chunk::Insert(s) | dissimilar::Chunk::Delete(s) => s.len(),
        })
        .sum();

    let equal: usize = chunks
        .iter()
        .map(|c| match c {
            dissimilar::Chunk::Equal(s) => s.len(),
            _ => 0,
        })
        .sum();

    if total == 0 {
        1.0
    } else {
        (2 * equal) as f64 / (a.len() + b.len()) as f64
    }
}
