//! NIF atoms module
//!
//! This module defines all atoms used in the NIF implementation.

// Define all atoms used in the NIF implementation
pub mod atoms {
    rustler::atoms! {
        ok,
        error,
    }
}