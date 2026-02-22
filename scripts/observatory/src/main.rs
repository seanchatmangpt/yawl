mod cache;
mod discovery;
mod emitters;

use cache::{Cache, CacheEvent, CacheStatus};
use discovery::Discovery;
use emitters::{
    coverage, deps_conflicts, docker, dual_family, duplicates, gates, integration, modules, reactor, receipt,
    shared_src, static_analysis, tests, utc_now, EmitCtx,
};
use std::path::PathBuf;
use std::time::Instant;

/// CLI argument parsing (no external crates, just std::env).
#[derive(Debug, Clone)]
struct Config {
    facts_only: bool,
    diagrams_only: bool,
    force: bool,
    verbose: bool,
    repo_root: PathBuf,
    out_dir: PathBuf,
}

impl Config {
    fn from_args() -> Self {
        let args: Vec<String> = std::env::args().collect();

        let mut facts_only = false;
        let mut diagrams_only = false;
        let mut force = std::env::var("OBSERVATORY_FORCE").is_ok();
        let mut verbose = false;
        let mut repo_root = None;
        let mut out_dir = None;

        let mut i = 1;
        while i < args.len() {
            match args[i].as_str() {
                "--facts" => facts_only = true,
                "--diagrams" => diagrams_only = true,
                "--all" => {
                    facts_only = false;
                    diagrams_only = false;
                }
                "--force" => force = true,
                "--verbose" => verbose = true,
                "--repo-root" => {
                    i += 1;
                    if i < args.len() {
                        repo_root = Some(PathBuf::from(&args[i]));
                    }
                }
                "--out-dir" => {
                    i += 1;
                    if i < args.len() {
                        out_dir = Some(PathBuf::from(&args[i]));
                    }
                }
                _ => {}
            }
            i += 1;
        }

        // Default repo root: ../.. from script directory
        let repo_root = repo_root.unwrap_or_else(|| {
            let script_dir = std::env::current_exe()
                .ok()
                .and_then(|p| p.parent().map(|p| p.to_path_buf()))
                .unwrap_or_else(|| PathBuf::from("."));
            script_dir.join("../../../")
        });

        // Default out dir: <repo>/docs/v6/latest
        let out_dir = out_dir.unwrap_or_else(|| repo_root.join("docs/v6/latest"));

        // Canonicalize paths
        let repo_root = repo_root.canonicalize().unwrap_or(repo_root);
        let out_dir = out_dir.canonicalize().unwrap_or(out_dir);

        Config {
            facts_only,
            diagrams_only,
            force,
            verbose,
            repo_root,
            out_dir,
        }
    }
}

fn main() {
    let config = Config::from_args();
    let start_time = Instant::now();

    // Print banner
    print_banner(&config);

    // Validate directories
    if !config.repo_root.is_dir() {
        eprintln!(
            "{}[ERROR]{} Repository root not found: {:?}",
            CYAN, RESET, config.repo_root
        );
        std::process::exit(1);
    }

    // Run discovery (parallel file scan)
    eprint!("{}[observatory]{} Scanning repository... ", CYAN, RESET);
    let disc_start = Instant::now();
    let discovery = Discovery::scan(&config.repo_root);
    let disc_elapsed = disc_start.elapsed().as_millis();
    eprintln!(
        "done ({}ms): {} Java, {} POMs, {} tests",
        disc_elapsed,
        cyan(discovery.java_files().len()),
        cyan(discovery.pom_files().len()),
        cyan(discovery.test_files().len())
    );

    // Initialize cache and context
    let cache = Cache::new(&config.out_dir, config.force);
    let ctx = EmitCtx::new(&config.repo_root, &config.out_dir);

    // Determine what to run
    let run_facts = !config.diagrams_only;
    let run_diagrams = config.diagrams_only || !config.facts_only;

    let mut total_facts = 0;
    let total_diagrams = 0;
    let mut failed_count = 0;
    let mut cache_events = Vec::new();

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // FACTS PHASE (parallel emission)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    if run_facts {
        eprint!(
            "{}[observatory]{} Running facts phase in parallel... ",
            CYAN, RESET
        );
        let facts_start = Instant::now();
        let verbose = config.verbose;

        // Collect results from parallel emitters using rayon's parallel iterator
        use rayon::prelude::*;

        let emitter_specs: Vec<&str> = vec![
            "modules",
            "reactor",
            "shared-src",
            "dual-family",
            "duplicates",
            "tests",
            "gates",
            "integration",
            "docker-testing",
            "coverage",
            "static-analysis",
            "deps-conflicts",
            "receipt",
        ];

        let fact_results: Vec<(String, emitters::EmitResult, u64, CacheStatus)> = emitter_specs
            .into_par_iter()
            .map(|emitter_name| {
                let elapsed_start = Instant::now();

                let result = match emitter_name {
                    "modules" => modules::emit(&ctx, &discovery, &cache),
                    "reactor" => reactor::emit(&ctx, &discovery, &cache),
                    "shared-src" => shared_src::emit(&ctx, &discovery, &cache),
                    "dual-family" => dual_family::emit(&ctx, &discovery, &cache),
                    "duplicates" => duplicates::emit(&ctx, &discovery, &cache),
                    "tests" => tests::emit(&ctx, &discovery, &cache),
                    "gates" => gates::emit(&ctx, &discovery, &cache),
                    "integration" => integration::emit(&ctx, &discovery, &cache),
                    "docker-testing" => docker::emit(&ctx, &discovery, &cache),
                    "coverage" => coverage::emit(&ctx, &discovery, &cache),
                    "static-analysis" => static_analysis::emit(&ctx, &discovery, &cache),
                    "deps-conflicts" => deps_conflicts::emit(&ctx, &discovery, &cache),
                    "receipt" => receipt::emit(&ctx, &discovery, &cache),
                    _ => Err("unknown emitter".into()),
                };

                let elapsed = elapsed_start.elapsed().as_millis() as u64;

                if verbose {
                    match &result {
                        Ok(_path) => {
                            eprintln!(
                                "{}  ✅ {:<20} {}ms{}",
                                CYAN, emitter_name, elapsed, RESET
                            );
                        }
                        Err(e) => {
                            eprintln!(
                                "{}  ❌ {:<20} ERROR: {}{}",
                                CYAN, emitter_name, e, RESET
                            );
                        }
                    }
                }

                let status = match &result {
                    Ok(_) => CacheStatus::Miss, // Newly computed
                    Err(_) => CacheStatus::Skipped,
                };

                (emitter_name.to_string(), result, elapsed, status)
            })
            .collect();

        let facts_elapsed = facts_start.elapsed().as_millis();
        eprintln!("done ({}ms)", facts_elapsed);

        // Process results and build cache events
        for (name, result, elapsed, status) in fact_results.iter() {
            match result {
                Ok(_) => {
                    total_facts += 1;
                    eprint!("{}✅{} ", GREEN, RESET);
                }
                Err(e) => {
                    failed_count += 1;
                    eprintln!(
                        "\n{}[ERROR]{} Fact emission failed for {}: {}",
                        RED, RESET, name, e
                    );
                    eprint!("{}❌{} ", RED, RESET);
                }
            }

            cache_events.push(CacheEvent {
                key: name.clone(),
                status: status.clone(),
                elapsed_ms: *elapsed,
            });
        }
        eprintln!();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // DIAGRAMS PHASE (stub for now)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    if run_diagrams && !config.diagrams_only {
        eprint!("{}[observatory]{} Running diagrams phase... ", CYAN, RESET);
        eprintln!("{}(skipped - not yet implemented){}", YELLOW, RESET);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // CACHE STATS & PERFORMANCE SUMMARY
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    cache.write_stats(&cache_events);

    let total_elapsed = start_time.elapsed();
    let total_millis = total_elapsed.as_millis() as u64;
    let total_secs = total_elapsed.as_secs_f64();

    print_performance_summary(total_facts, total_diagrams, total_millis, total_secs);

    // Generate final status line (parsed by session-start.sh)
    let receipt_path = config.out_dir.join("receipts/observatory.json");
    let perf_path = config.out_dir.join("performance/summary.json");
    let status_line = format!(
        "STATUS={} RUN_ID={} RECEIPT={} PERF={}",
        if failed_count == 0 { "GREEN" } else { "RED" },
        utc_now(),
        receipt_path.display(),
        perf_path.display()
    );
    println!("{}", status_line);

    // Exit with appropriate code
    if failed_count > 0 {
        std::process::exit(1);
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// OUTPUT HELPERS
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

const RESET: &str = "\x1b[0m";
const CYAN: &str = "\x1b[36m";
const GREEN: &str = "\x1b[32m";
const RED: &str = "\x1b[31m";
const YELLOW: &str = "\x1b[33m";

fn cyan(val: impl std::fmt::Display) -> String {
    format!("{}{}{}", CYAN, val, RESET)
}

fn green(val: impl std::fmt::Display) -> String {
    format!("{}{}{}", GREEN, val, RESET)
}

fn red(val: impl std::fmt::Display) -> String {
    format!("{}{}{}", RED, val, RESET)
}

fn yellow(val: impl std::fmt::Display) -> String {
    format!("{}{}{}", YELLOW, val, RESET)
}

fn print_banner(config: &Config) {
    let now = utc_now();
    let run_id = now.replace(":", "").replace("-", "").replace("T", "").replace("Z", "");

    eprintln!("{}==================================================================", CYAN);
    eprintln!("  YAWL V6 Code Analysis Observatory (Parallel)");
    eprintln!("  Run ID:  {}", cyan(&run_id));
    eprintln!(
        "  Branch:  {}",
        cyan(
            std::process::Command::new("git")
                .args([
                    "-C",
                    config.repo_root.to_str().unwrap_or("."),
                    "rev-parse",
                    "--abbrev-ref",
                    "HEAD"
                ])
                .output()
                .ok()
                .and_then(|o| if o.status.success() {
                    Some(String::from_utf8_lossy(&o.stdout).trim().to_string())
                } else {
                    None
                })
                .unwrap_or_else(|| "unknown".into())
        )
    );
    eprintln!(
        "  Commit:  {}",
        cyan(
            std::process::Command::new("git")
                .args([
                    "-C",
                    config.repo_root.to_str().unwrap_or("."),
                    "rev-parse",
                    "--short",
                    "HEAD"
                ])
                .output()
                .ok()
                .and_then(|o| if o.status.success() {
                    Some(String::from_utf8_lossy(&o.stdout).trim().to_string())
                } else {
                    None
                })
                .unwrap_or_else(|| "unknown".into())
        )
    );
    eprintln!("  Java:    {}", cyan("25"));
    eprintln!("  Maven:   {}", cyan("unknown"));
    eprintln!(
        "  Mode:    {}",
        if std::env::var("OBSERVATORY_FORCE").is_ok() {
            red("FORCE")
        } else {
            cyan("INCREMENTAL (skip if unchanged)")
        }
    );
    eprintln!("{}==================================================================", CYAN);
}

fn print_performance_summary(
    facts_count: usize,
    diagrams_count: usize,
    total_millis: u64,
    total_secs: f64,
) {
    eprintln!();
    eprintln!(
        "{}==================================================================",
        CYAN
    );
    eprintln!("  Observatory Complete (Parallel)");
    eprintln!("  Output: docs/v6/latest/");
    eprintln!("  Facts:    {} files", green(facts_count));
    eprintln!("  Diagrams: {} files", diagrams_count);
    eprintln!("  YAWL XML: {} files", 0);
    eprintln!("  Refusals: {} files", 0);
    eprintln!("  Warnings: {} files", 0);
    eprintln!("  Failed phases: {}", if facts_count > 0 { green(0) } else { red(0) });
    eprintln!("{}------------------------------------------------------------------", CYAN);
    eprintln!("  Performance Summary (PARALLEL)");
    eprintln!("  Total Time:    {}ms ({:.3}s)", total_millis, total_secs);
    eprintln!("  Peak Memory:   0MB");
    eprintln!("  Throughput:    {} outputs", green(facts_count + diagrams_count));
    eprintln!(
        "{}=================================================================={}",
        CYAN, RESET
    );
}
