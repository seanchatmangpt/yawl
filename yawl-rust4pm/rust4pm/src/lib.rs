//! YAWL Process Mining Bridge
//!
//! This crate provides bindings to the `process_mining` crate (v0.5.2)
//! from RWTH Aachen for YAWL's process mining capabilities.
//!
//! See: https://docs.rs/process_mining/0.5.2/process_mining/

// Re-export process_mining for convenience
pub use process_mining::*;

// Conditionally include language bindings based on features
#[cfg(feature = "jni")]
pub mod jni;      // Java JNI bindings


#[cfg(feature = "nif")]
pub mod nif;
