use chrono::Utc;
use std::path::Path;

use crate::fetch::{FetchResult, WatermarkStore, Watermark};
use crate::targets::{Target, TargetsConfig};

/// Run the full fetch cycle for all targets.
/// Returns a summary of what changed.
pub struct FetchSummary {
    pub targets_checked: usize,
    pub targets_changed: usize,
    pub targets_skipped: usize,
    pub targets_errored: usize,
    pub intelligence_updated: bool,
}

pub fn fetch_all(
    targets: &TargetsConfig,
    watermarks_path: &Path,
    intelligence_path: &Path,
) -> anyhow::Result<FetchSummary> {
    let mut store = WatermarkStore::load(watermarks_path)?;
    let mut results = Vec::new();

    for target in &targets.target {
        let result = crate::fetch::fetch_target(target, &store);
        results.push((target.clone(), result));
    }

    let targets_checked = results.len();
    let mut targets_changed = 0;
    let mut targets_skipped = 0;
    let mut targets_errored = 0;
    let mut changed_results: Vec<(Target, FetchResult)> = Vec::new();

    for (target, result) in results {
        if result.error.is_some() {
            targets_errored += 1;
            eprintln!(
                "[scout] ERROR fetching {}: {}",
                result.target_name,
                result.error.as_deref().unwrap_or("unknown")
            );
        } else if result.skipped {
            targets_skipped += 1;
        } else if result.changed {
            targets_changed += 1;
            // Update watermark
            store.upsert(Watermark {
                name: result.target_name.clone(),
                url: result.url.clone(),
                fetched_at: Utc::now(),
                content_hash: result.content_hash.clone(),
                ttl_hours: target.watermark_ttl_hours,
            });
            changed_results.push((target, result));
        } else {
            // Fetched but unchanged — update timestamp only
            store.upsert(Watermark {
                name: result.target_name.clone(),
                url: result.url.clone(),
                fetched_at: Utc::now(),
                content_hash: result.content_hash.clone(),
                ttl_hours: target.watermark_ttl_hours,
            });
        }
    }

    // Save updated watermarks
    store.save(watermarks_path)?;

    // Write intelligence file if anything changed
    let intelligence_updated = if !changed_results.is_empty() {
        write_intelligence(intelligence_path, &changed_results)?;
        true
    } else {
        false
    };

    Ok(FetchSummary {
        targets_checked,
        targets_changed,
        targets_skipped,
        targets_errored,
        intelligence_updated,
    })
}

fn write_intelligence(
    intelligence_path: &Path,
    changed: &[(Target, FetchResult)],
) -> anyhow::Result<()> {
    let mut content = String::new();
    let now = Utc::now();

    content.push_str(&format!(
        "# YAWL Live Intelligence\n\n*Last updated*: {}\n\n",
        now.format("%Y-%m-%d %H:%M UTC")
    ));

    for (target, result) in changed {
        content.push_str(&format!("## {}\n\n", target.name));
        content.push_str(&format!("**URL**: {}\n\n", target.url));
        content.push_str(&format!("**Semantic unit**: {}\n\n", target.semantic_unit));

        if let Some(raw_content) = &result.content {
            // Include first 500 chars of the fetched content
            let preview: String = raw_content
                .chars()
                .filter(|c| c.is_ascii() || c.is_alphanumeric())
                .take(500)
                .collect();
            content.push_str("**Preview**:\n\n```\n");
            content.push_str(&preview);
            content.push_str("\n```\n\n");
        }

        content.push_str(&format!("**Content hash**: `{}`\n\n", result.content_hash));
    }

    // Atomic write: write to temp file then rename
    let temp_path = intelligence_path.with_extension("md.tmp");
    if let Some(parent) = intelligence_path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    std::fs::write(&temp_path, &content)?;
    std::fs::rename(&temp_path, intelligence_path)?;

    Ok(())
}
