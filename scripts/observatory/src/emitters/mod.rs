/// Shared types and context for all fact emitters.
///
/// Each emitter module implements a function:
///   pub fn emit(ctx: &EmitCtx, disc: &Discovery, cache: &Cache) -> EmitResult
///
/// Emitters are independent — no cross-module imports between emitter modules.
use std::path::{Path, PathBuf};

pub mod coverage;
pub mod deps_conflicts;
pub mod docker;
pub mod dual_family;
pub mod duplicates;
pub mod gates;
pub mod integration;
pub mod modules;
pub mod reactor;
pub mod receipt;
pub mod shared_src;
pub mod static_analysis;
pub mod tests;
pub mod workflow_runtime;

/// Return type for all emitters: Ok(path written) or Err(description).
pub type EmitResult = Result<PathBuf, String>;

/// Shared context passed to every emitter.
pub struct EmitCtx<'a> {
    pub repo: &'a Path,
    pub out_dir: &'a Path,
    pub commit: String,
    pub branch: String,
    pub timestamp: String, // ISO-8601 UTC
}

impl<'a> EmitCtx<'a> {
    pub fn new(repo: &'a Path, out_dir: &'a Path) -> Self {
        let commit = git_rev(repo, "HEAD").unwrap_or_else(|| "unknown".into());
        let branch = git_branch(repo).unwrap_or_else(|| "unknown".into());
        let timestamp = utc_now();
        EmitCtx { repo, out_dir, commit, branch, timestamp }
    }

    pub fn facts_dir(&self) -> PathBuf {
        self.out_dir.join("facts")
    }

    pub fn diagrams_dir(&self) -> PathBuf {
        self.out_dir.join("diagrams")
    }

    pub fn receipts_dir(&self) -> PathBuf {
        self.out_dir.join("receipts")
    }
}

// ── Shared helpers used across multiple emitters ──────────────────────────

/// Extract package from already-loaded content (no disk I/O).
///
/// Companion to `extract_package()` for use with pre-read heads from `Discovery`.
/// Stops at the first `package` declaration within 30 lines.
pub fn extract_package_from_str(content: &str) -> Option<&str> {
    for line in content.lines().take(30) {
        let trimmed = line.trim();
        if let Some(rest) = trimmed.strip_prefix("package ") {
            let pkg = rest.trim_end_matches(';').trim();
            if !pkg.is_empty() {
                return Some(pkg);
            }
        }
    }
    None
}

/// Read a file and extract the first `package foo.bar;` declaration.
///
/// Uses BufReader with early exit — stops reading after finding the package
/// or scanning 30 lines. Avoids reading entire file into memory (avg 8KB → ~200 bytes).
pub fn extract_package(path: &Path) -> Option<String> {
    use std::io::{BufRead, BufReader};
    let file = std::fs::File::open(path).ok()?;
    let reader = BufReader::with_capacity(1024, file); // 1KB buffer, stops at package line
    for line in reader.lines().take(30) {
        let line = line.ok()?;
        let trimmed = line.trim();
        if let Some(rest) = trimmed.strip_prefix("package ") {
            let pkg = rest.trim_end_matches(';').trim().to_string();
            if !pkg.is_empty() {
                return Some(pkg);
            }
        }
    }
    None
}

/// Read the first N lines of a file for pattern matching.
///
/// Uses BufReader with early exit — reads only the first N lines, avoiding
/// loading the entire file into memory.
pub fn head_lines(path: &Path, n: usize) -> Vec<String> {
    use std::io::{BufRead, BufReader};
    let Ok(file) = std::fs::File::open(path) else { return vec![] };
    BufReader::new(file).lines().take(n).filter_map(|l| l.ok()).collect()
}

/// Check if a file contains a given string (fast: stops at first match).
///
/// Uses BufReader line-by-line — stops reading once the needle is found,
/// avoiding loading the entire file into memory for early matches.
pub fn file_contains(path: &Path, needle: &str) -> bool {
    use std::io::{BufRead, BufReader};
    let Ok(file) = std::fs::File::open(path) else { return false };
    BufReader::new(file)
        .lines()
        .any(|l| l.ok().map(|l| l.contains(needle)).unwrap_or(false))
}

/// Count lines matching a pattern in a file.
pub fn count_matching_lines(path: &Path, pattern: &str) -> usize {
    std::fs::read_to_string(path)
        .map(|s| s.lines().filter(|l| l.contains(pattern)).count())
        .unwrap_or(0)
}

/// Extract first matching line content after a prefix.
pub fn extract_xml_value(path: &Path, tag: &str) -> Option<String> {
    let open = format!("<{}>", tag);
    let close = format!("</{}>", tag);
    let content = std::fs::read_to_string(path).ok()?;
    for line in content.lines() {
        let t = line.trim();
        if let Some(rest) = t.strip_prefix(&open) {
            if let Some(val) = rest.strip_suffix(&close) {
                return Some(val.trim().to_string());
            }
        }
    }
    None
}

/// Get short git commit hash.
pub fn git_rev(repo: &Path, r#ref: &str) -> Option<String> {
    std::process::Command::new("git")
        .args(["-C", repo.to_str().unwrap_or("."), "rev-parse", "--short", r#ref])
        .output()
        .ok()
        .filter(|o| o.status.success())
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .filter(|s| !s.is_empty())
}

/// Get current git branch name.
pub fn git_branch(repo: &Path) -> Option<String> {
    std::process::Command::new("git")
        .args(["-C", repo.to_str().unwrap_or("."), "rev-parse", "--abbrev-ref", "HEAD"])
        .output()
        .ok()
        .filter(|o| o.status.success())
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .filter(|s| !s.is_empty())
}

/// UTC timestamp in ISO-8601 format.
pub fn utc_now() -> String {
    use std::time::{SystemTime, UNIX_EPOCH};
    let secs = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs();
    // Format as YYYY-MM-DDTHH:MM:SSZ without external crates
    let s = secs;
    let (y, mo, d, h, mi, sec) = epoch_to_ymd_hms(s);
    format!("{:04}-{:02}-{:02}T{:02}:{:02}:{:02}Z", y, mo, d, h, mi, sec)
}

fn epoch_to_ymd_hms(epoch: u64) -> (u64, u64, u64, u64, u64, u64) {
    let sec = epoch % 60;
    let min = (epoch / 60) % 60;
    let hour = (epoch / 3600) % 24;
    let days = epoch / 86400;
    let (y, mo, d) = civil_date(days);
    (y, mo, d, hour, min, sec)
}

/// Convert Unix days (since 1970-01-01) to Gregorian (year, month, day).
/// Uses Howard Hinnant's civil calendar algorithm — branchless, no loops.
fn civil_date(z: u64) -> (u64, u64, u64) {
    let z = z + 719468; // shift to civil epoch (0000-03-01)
    let era = z / 146097;
    let doe = z - era * 146097; // day of era [0, 146096]
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365; // year of era [0, 399]
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100); // day of year from Mar 1 [0, 365]
    let mp = (5 * doy + 2) / 153; // civil month [0=Mar, 11=Feb]
    let d = doy - (153 * mp + 2) / 5 + 1; // day [1, 31]
    let m = if mp < 10 { mp + 3 } else { mp - 9 }; // Gregorian month [1, 12]
    let y = if m <= 2 { y + 1 } else { y }; // year correction for Jan/Feb
    (y, m, d)
}

/// Ensure directory exists.
pub fn ensure_dir(path: &Path) {
    std::fs::create_dir_all(path).ok();
}

/// Write JSON to a file, creating parent directories as needed.
pub fn write_json(path: &Path, value: &serde_json::Value) -> EmitResult {
    ensure_dir(path.parent().unwrap_or(path));
    let json = serde_json::to_string_pretty(value)
        .map_err(|e| format!("JSON serialization failed for {:?}: {}", path, e))?;
    std::fs::write(path, json)
        .map_err(|e| format!("Write failed for {:?}: {}", path, e))?;
    Ok(path.to_path_buf())
}
