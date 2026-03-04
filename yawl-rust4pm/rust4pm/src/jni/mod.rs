//! JNI bindings for YAWL Process Mining Rust integration
//!
//! This module provides Java Native Interface (JNI) bindings for the
//! process_mining crate, following the pattern from the paper:
//! "High-Performance Process Mining with YAWL and Rust"
//!
//! The bindings expose three main functionalities:
//! 1. XES event log import (xes_import.rs)
//! 2. Alpha miner discovery (alpha_miner.rs)
//! 3. Conformance checking (conformance.rs)

pub mod xes_import;
pub mod alpha_miner;
pub mod conformance;