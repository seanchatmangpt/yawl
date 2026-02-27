pub mod fetch;
pub mod intelligence;
pub mod targets;

pub use fetch::{fetch_target, FetchResult, Watermark, WatermarkStore};
pub use intelligence::{fetch_all, FetchSummary};
pub use targets::{Target, TargetsConfig};
