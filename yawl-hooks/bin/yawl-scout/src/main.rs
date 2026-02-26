use std::path::{Path, PathBuf};

use anyhow::Context;
use clap::{Parser, Subcommand};

use scout::{fetch_all, TargetsConfig};

/// yawl-scout — YAWL Intelligence Layer live intelligence fetcher.
///
/// Fetches external dependencies, specs, and changelogs, respecting watermark TTLs.
/// Writes results to .claude/context/live-intelligence.md atomically.
///
/// Exit codes:
///   0  Success
///   1  Non-blocking error (some targets failed but execution continues)
#[derive(Parser)]
#[command(name = "yawl-scout", version, about)]
struct Cli {
    #[command(subcommand)]
    command: Command,

    /// Repository root (default: find git root from CWD)
    #[arg(long, global = true)]
    repo_root: Option<PathBuf>,
}

#[derive(Subcommand)]
enum Command {
    /// Fetch all targets (respects watermark TTLs)
    Fetch {
        /// Run in async mode: spawn as background process
        #[arg(long)]
        r#async: bool,
        /// Force fetch even if watermark is fresh
        #[arg(long)]
        force: bool,
    },
    /// Show watermark status for all targets
    Status,
}

fn main() {
    let cli = Cli::parse();
    let repo_root = resolve_repo_root(cli.repo_root.as_deref());

    let result = match cli.command {
        Command::Fetch { r#async, .. } => {
            if r#async {
                // Spawn ourselves in the background and return immediately
                spawn_background(&repo_root)
            } else {
                cmd_fetch(&repo_root)
            }
        }
        Command::Status => cmd_status(&repo_root),
    };

    if let Err(e) = result {
        eprintln!("[yawl-scout] ERROR: {:?}", e);
        std::process::exit(1);
    }
}

fn cmd_fetch(repo_root: &Path) -> anyhow::Result<()> {
    let targets_path = repo_root.join(".claude").join("scout").join("targets.toml");
    if !targets_path.exists() {
        eprintln!(
            "[yawl-scout] No targets.toml found at {}. Skipping fetch.",
            targets_path.display()
        );
        return Ok(());
    }

    let targets = TargetsConfig::load(&targets_path)
        .context("Failed to load targets.toml")?;

    let watermarks_path = repo_root.join(".claude").join("context").join("watermarks.json");
    let intelligence_path = repo_root.join(".claude").join("context").join("live-intelligence.md");

    let summary = fetch_all(&targets, &watermarks_path, &intelligence_path)
        .context("Failed to run fetch cycle")?;

    eprintln!(
        "[yawl-scout] Fetch complete: {} checked, {} changed, {} skipped, {} errored",
        summary.targets_checked,
        summary.targets_changed,
        summary.targets_skipped,
        summary.targets_errored
    );

    if summary.intelligence_updated {
        eprintln!(
            "[yawl-scout] live-intelligence.md updated → {}",
            intelligence_path.display()
        );
    }

    if summary.targets_errored > 0 {
        std::process::exit(1);
    }

    Ok(())
}

fn cmd_status(repo_root: &Path) -> anyhow::Result<()> {
    let watermarks_path = repo_root.join(".claude").join("context").join("watermarks.json");
    let store = scout::WatermarkStore::load(&watermarks_path)?;

    let targets_path = repo_root.join(".claude").join("scout").join("targets.toml");
    if !targets_path.exists() {
        eprintln!("[yawl-scout] No targets.toml found.");
        return Ok(());
    }

    let targets = TargetsConfig::load(&targets_path)?;
    let now = chrono::Utc::now();

    println!("{:<30} {:<12} URL", "Target", "Status");
    println!("{}", "-".repeat(80_usize));

    for target in &targets.target {
        if let Some(wm) = store.get(&target.name) {
            let age_hours = (now - wm.fetched_at).num_hours();
            let status = if wm.is_fresh() {
                format!("FRESH ({}h)", age_hours)
            } else {
                format!("STALE ({}h)", age_hours)
            };
            println!("{:<30} {:<12} {}", target.name, status, target.url);
        } else {
            println!("{:<30} {:<12} {}", target.name, "NEVER", target.url);
        }
    }

    Ok(())
}

fn spawn_background(repo_root: &Path) -> anyhow::Result<()> {
    // Spawn a new process running fetch (without --async)
    let exe = std::env::current_exe()
        .context("Failed to get current executable path")?;

    std::process::Command::new(exe)
        .arg("--repo-root")
        .arg(repo_root)
        .arg("fetch")
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .spawn()
        .context("Failed to spawn background fetch process")?;

    eprintln!("[yawl-scout] Background fetch spawned");
    Ok(())
}

fn resolve_repo_root(specified: Option<&Path>) -> PathBuf {
    if let Some(p) = specified {
        return p.to_path_buf();
    }

    let cwd = std::env::current_dir().unwrap_or_else(|_| PathBuf::from("."));
    let mut current = cwd.as_path();
    loop {
        if current.join(".git").exists() {
            return current.to_path_buf();
        }
        match current.parent() {
            Some(p) => current = p,
            None => return cwd,
        }
    }
}
