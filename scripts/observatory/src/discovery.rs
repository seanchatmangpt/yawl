/// File discovery with parallel scanning and file-list caching.
///
/// Single pass at startup — results shared across all emitters.
/// Replaces 4 separate `find` subprocess calls in bash observatory.
///
/// Cache strategy: after scan, writes `docs/v6/latest/discovery-cache.json` with
/// the file list. On the next run, if src/, test/, and all module dirs have the
/// same mtime as when the cache was written, load from JSON (~5ms) instead of
/// re-walking the tree (~210ms). Invalidated by adding/removing files or directories.
/// For content-only changes that alter module structure, run with OBSERVATORY_FORCE=1.
use rayon::prelude::*;
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use std::time::SystemTime;
use walkdir::WalkDir;

pub struct Discovery {
    pub java_files: Vec<PathBuf>,   // src/**/*.java (non-test)
    pub test_files: Vec<PathBuf>,   // test/**/*.java + **/src/test/**/*.java
    pub pom_files: Vec<PathBuf>,    // **/pom.xml
    pub module_dirs: Vec<PathBuf>,  // top-level dirs containing pom.xml
}

/// On-disk representation for the discovery cache.
#[derive(Serialize, Deserialize)]
struct DiscoveryCache {
    java_files: Vec<String>,
    test_files: Vec<String>,
    pom_files: Vec<String>,
    module_dirs: Vec<String>,
}

impl Discovery {
    /// Scan with caching: loads from `out_dir/discovery-cache.json` if the
    /// source tree structure hasn't changed. Falls back to full scan otherwise.
    /// `force = true` bypasses the cache and always does a full scan.
    pub fn scan_cached(repo: &Path, out_dir: &Path, force: bool) -> Self {
        let cache_file = out_dir.join("discovery-cache.json");

        if !force {
            if let Some(disc) = try_load_discovery_cache(&cache_file, repo) {
                return disc;
            }
        }

        let disc = Self::scan(repo);
        save_discovery_cache(&cache_file, &disc);
        disc
    }

    /// Parallel scan of the repository. Replaces bash `parallel_discover_all`.
    pub fn scan(repo: &Path) -> Self {
        let repo_str = repo.to_path_buf();

        // Walk directory tree (3 parallel walks via rayon::join)
        let (all_java, (pom_files, module_dirs)) = rayon::join(
            || discover_java_files(&repo_str),
            || rayon::join(
                || discover_pom_files(&repo_str),
                || discover_module_dirs(&repo_str),
            ),
        );

        let (java_files, test_files) = partition_java_files(all_java);

        Discovery {
            java_files,
            test_files,
            pom_files,
            module_dirs,
        }
    }

    /// All non-test Java source files.
    pub fn java_files(&self) -> &[PathBuf] {
        &self.java_files
    }

    /// All test Java files (test/ directory + src/test/java/).
    pub fn test_files(&self) -> &[PathBuf] {
        &self.test_files
    }

    /// All pom.xml files.
    pub fn pom_files(&self) -> &[PathBuf] {
        &self.pom_files
    }

    /// Top-level module directories (those with a pom.xml).
    pub fn module_dirs(&self) -> &[PathBuf] {
        &self.module_dirs
    }
}

fn discover_java_files(repo: &Path) -> Vec<PathBuf> {
    let src = repo.join("src");
    let test = repo.join("test");
    let mut files = Vec::new();

    for root in [&src, &test] {
        if root.is_dir() {
            for entry in WalkDir::new(root)
                .follow_links(false)
                .into_iter()
                .filter_map(|e| e.ok())
                .filter(|e| e.file_type().is_file())
            {
                let p = entry.into_path();
                if p.extension().and_then(|s| s.to_str()) == Some("java") {
                    files.push(p);
                }
            }
        }
    }

    // Also scan module-level src/main/java directories
    if let Ok(entries) = std::fs::read_dir(repo) {
        for entry in entries.flatten() {
            let mod_src = entry.path().join("src").join("main").join("java");
            if mod_src.is_dir() {
                for e in WalkDir::new(&mod_src)
                    .follow_links(false)
                    .into_iter()
                    .filter_map(|e| e.ok())
                    .filter(|e| e.file_type().is_file())
                {
                    let p = e.into_path();
                    if p.extension().and_then(|s| s.to_str()) == Some("java") {
                        files.push(p);
                    }
                }
            }
            // Also scan module-level src/test/java
            let mod_test = entry.path().join("src").join("test").join("java");
            if mod_test.is_dir() {
                for e in WalkDir::new(&mod_test)
                    .follow_links(false)
                    .into_iter()
                    .filter_map(|e| e.ok())
                    .filter(|e| e.file_type().is_file())
                {
                    let p = e.into_path();
                    if p.extension().and_then(|s| s.to_str()) == Some("java") {
                        files.push(p);
                    }
                }
            }
        }
    }

    files.sort_unstable();
    files.dedup();
    files
}

fn partition_java_files(all: Vec<PathBuf>) -> (Vec<PathBuf>, Vec<PathBuf>) {
    let (tests, src): (Vec<_>, Vec<_>) = all.into_par_iter().partition(|p| {
        let s = p.to_string_lossy();
        s.contains("/test/") || s.contains("/src/test/")
    });
    (src, tests)
}

fn discover_pom_files(repo: &Path) -> Vec<PathBuf> {
    WalkDir::new(repo)
        .max_depth(3)
        .follow_links(false)
        .into_iter()
        .filter_map(|e| e.ok())
        .filter(|e| e.file_type().is_file() && e.file_name() == "pom.xml")
        .filter(|e| !e.path().to_string_lossy().contains("/target/"))
        .map(|e| e.into_path())
        .collect()
}

fn discover_module_dirs(repo: &Path) -> Vec<PathBuf> {
    let Ok(entries) = std::fs::read_dir(repo) else { return vec![] };
    let mut dirs: Vec<PathBuf> = entries
        .flatten()
        .filter(|e| e.file_type().map(|t| t.is_dir()).unwrap_or(false))
        .map(|e| e.path())
        .filter(|p| {
            let name = p.file_name().and_then(|n| n.to_str()).unwrap_or("");
            name.starts_with("yawl-") && p.join("pom.xml").exists()
        })
        .collect();
    dirs.sort_unstable();
    dirs
}

/// Attempt to load discovery from on-disk cache.
/// Returns None if the cache is missing, corrupt, or any watched directory changed.
fn try_load_discovery_cache(cache_file: &Path, repo: &Path) -> Option<Discovery> {
    let cache_mtime = std::fs::metadata(cache_file).and_then(|m| m.modified()).ok()?;

    // Validate: all watched dirs must have mtime ≤ cache mtime.
    // If any dir is newer, files may have been added/removed → invalidate.
    let src_dir = repo.join("src");
    let test_dir = repo.join("test");
    for dir in [&src_dir, &test_dir] {
        if dir.is_dir() && dir_newer_than(dir, cache_mtime) {
            return None;
        }
    }

    let content = std::fs::read_to_string(cache_file).ok()?;
    let cached: DiscoveryCache = serde_json::from_str(&content).ok()?;

    // Also validate module dirs from cache
    for module_dir_str in &cached.module_dirs {
        let module_dir = Path::new(module_dir_str);
        if module_dir.is_dir() && dir_newer_than(module_dir, cache_mtime) {
            return None;
        }
    }

    Some(Discovery {
        java_files: cached.java_files.into_iter().map(PathBuf::from).collect(),
        test_files: cached.test_files.into_iter().map(PathBuf::from).collect(),
        pom_files: cached.pom_files.into_iter().map(PathBuf::from).collect(),
        module_dirs: cached.module_dirs.into_iter().map(PathBuf::from).collect(),
    })
}

/// Persist the discovery results so the next run can skip the directory walk.
fn save_discovery_cache(cache_file: &Path, disc: &Discovery) {
    let cache = DiscoveryCache {
        java_files: disc.java_files.iter().map(|p| p.to_string_lossy().into_owned()).collect(),
        test_files: disc.test_files.iter().map(|p| p.to_string_lossy().into_owned()).collect(),
        pom_files: disc.pom_files.iter().map(|p| p.to_string_lossy().into_owned()).collect(),
        module_dirs: disc.module_dirs.iter().map(|p| p.to_string_lossy().into_owned()).collect(),
    };
    if let Some(parent) = cache_file.parent() {
        std::fs::create_dir_all(parent).ok();
    }
    if let Ok(json) = serde_json::to_string(&cache) {
        std::fs::write(cache_file, json).ok();
    }
}

/// True if a directory's mtime is newer than `than` (file was added/removed inside it).
fn dir_newer_than(dir: &Path, than: SystemTime) -> bool {
    std::fs::metadata(dir)
        .and_then(|m| m.modified())
        .map(|t| t > than)
        .unwrap_or(false)
}
