use chrono::{DateTime, Duration, Utc};
use serde::{Deserialize, Serialize};
use std::path::Path;

use crate::targets::Target;
use delta::hash::blake3_str;

/// Watermark entry for a single URL.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Watermark {
    pub name: String,
    pub url: String,
    pub fetched_at: DateTime<Utc>,
    pub content_hash: String,
    pub ttl_hours: u64,
}

impl Watermark {
    /// Returns true if this watermark is still fresh (within TTL).
    pub fn is_fresh(&self) -> bool {
        let ttl = Duration::hours(self.ttl_hours as i64);
        Utc::now() < self.fetched_at + ttl
    }
}

/// All watermarks for the session.
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct WatermarkStore {
    version: String,
    watermarks: Vec<Watermark>,
}

impl WatermarkStore {
    pub fn new() -> Self {
        Self {
            version: "1".to_string(),
            watermarks: Vec::new(),
        }
    }

    pub fn load(path: &Path) -> anyhow::Result<Self> {
        if !path.exists() {
            return Ok(Self::new());
        }
        let content = std::fs::read_to_string(path)?;
        let store: WatermarkStore = serde_json::from_str(&content)
            .map_err(|e| anyhow::anyhow!("watermarks.json parse error: {}", e))?;
        Ok(store)
    }

    pub fn save(&self, path: &Path) -> anyhow::Result<()> {
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let content = serde_json::to_string_pretty(self)?;
        std::fs::write(path, content)?;
        Ok(())
    }

    pub fn get(&self, name: &str) -> Option<&Watermark> {
        self.watermarks.iter().find(|w| w.name == name)
    }

    pub fn upsert(&mut self, watermark: Watermark) {
        if let Some(pos) = self.watermarks.iter().position(|w| w.name == watermark.name) {
            self.watermarks[pos] = watermark;
        } else {
            self.watermarks.push(watermark);
        }
    }
}

/// Result of fetching a single target.
pub struct FetchResult {
    pub target_name: String,
    pub url: String,
    pub content: Option<String>,
    pub content_hash: String,
    pub changed: bool,
    pub skipped: bool,
    pub error: Option<String>,
}

/// Fetch a single target, respecting the watermark TTL.
pub fn fetch_target(
    target: &Target,
    store: &WatermarkStore,
) -> FetchResult {
    // Check watermark freshness
    if let Some(wm) = store.get(&target.name) {
        if wm.is_fresh() {
            return FetchResult {
                target_name: target.name.clone(),
                url: target.url.clone(),
                content: None,
                content_hash: wm.content_hash.clone(),
                changed: false,
                skipped: true,
                error: None,
            };
        }
    }

    // Perform HTTP fetch
    match do_fetch(&target.url) {
        Ok(content) => {
            let content_hash = blake3_str(&content);
            let changed = store
                .get(&target.name)
                .map(|wm| wm.content_hash != content_hash)
                .unwrap_or(true);

            FetchResult {
                target_name: target.name.clone(),
                url: target.url.clone(),
                content: Some(content),
                content_hash,
                changed,
                skipped: false,
                error: None,
            }
        }
        Err(e) => FetchResult {
            target_name: target.name.clone(),
            url: target.url.clone(),
            content: None,
            content_hash: String::new(),
            changed: false,
            skipped: false,
            error: Some(e.to_string()),
        },
    }
}

fn do_fetch(url: &str) -> anyhow::Result<String> {
    let response = ureq::get(url)
        .set("User-Agent", "yawl-scout/0.1.0 (YAWL Intelligence Layer)")
        .set("Accept", "text/html,application/json,text/plain")
        .timeout(std::time::Duration::from_secs(30))
        .call()
        .map_err(|e| anyhow::anyhow!("HTTP fetch failed for {}: {}", url, e))?;

    let content = response
        .into_string()
        .map_err(|e| anyhow::anyhow!("Failed to read response body: {}", e))?;

    Ok(content)
}
