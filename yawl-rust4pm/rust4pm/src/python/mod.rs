//! Python bindings for YAWL Process Mining Rust integration
//!
//! This module provides PyO3 bindings for the process_mining crate,
//! following the pattern from the paper:
//! "High-Performance Process Mining with YAWL and Rust"
//!
//! The bindings expose three main functionalities:
//! 1. XES event log import (xes.rs)
//! 2. Process model discovery (discovery.rs)
//! 3. Conformance checking (conformance.rs)

pub mod xes;
pub mod discovery;
pub mod conformance;