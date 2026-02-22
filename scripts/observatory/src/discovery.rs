/// File discovery with parallel scanning.
///
/// Single pass at startup — results shared via Arc across all rayon workers.
/// Replaces 4 separate `find` subprocess calls in bash observatory.
use rayon::prelude::*;
use std::path::{Path, PathBuf};
use walkdir::WalkDir;

pub struct Discovery {
    pub java_files: Vec<PathBuf>,  // src/**/*.java (non-test)
    pub test_files: Vec<PathBuf>,  // test/**/*.java + **/src/test/**/*.java
    pub pom_files: Vec<PathBuf>,   // **/pom.xml
    pub module_dirs: Vec<PathBuf>, // top-level dirs containing pom.xml
}

impl Discovery {
    /// Parallel scan of the repository. Replaces bash `parallel_discover_all`.
    pub fn scan(repo: &Path) -> Self {
        // Run three independent walks in parallel via rayon::join
        let repo_str = repo.to_path_buf();
        let (all_java, (pom_files, module_dirs)) = rayon::join(
            || discover_java_files(&repo_str),
            || rayon::join(
                || discover_pom_files(&repo_str),
                || discover_module_dirs(&repo_str),
            ),
        );

        let (java_files, test_files) = partition_java_files(all_java);

        Discovery { java_files, test_files, pom_files, module_dirs }
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
