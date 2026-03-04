//! Python module entry point for YAWL Process Mining
//!
//! This file defines the Python module structure and registers
//! the submodules and functions with PyO3.

use pyo3::prelude::*;

use crate::python::xes;
use crate::python::discovery;
use crate::python::conformance;

/// YAWL Process Mining Python module
///
/// This module provides high-performance process mining functionality
/// through PyO3 bindings to the Rust implementation.
///
/// Example:
/// ```python
/// import yawl_process_mining as ypm
///
/// # Import XES event log
/// event_log = ypm.import_xes("path/to/log.xes")
///
/// # Discover DFG
/// dfg = ypm.discover_dfg(event_log)
///
/// # Discover Alpha miner
/// pnml = ypm.discover_alpha(event_log)
///
/// # Check conformance
/// conformance = ypm.check_conformance(event_log, pnml)
/// ```
#[pymodule]
fn yawl_process_mining(_py: Python, m: &Bound<'_, PyModule>) -> PyResult<()> {
    // Register submodules
    let xes_mod = PyModule::new_bound(m.py(), "xes")?;
    xes::xes(m.py(), &xes_mod)?;
    m.add_submodule(&xes_mod)?;

    let discovery_mod = PyModule::new_bound(m.py(), "discovery")?;
    discovery::discovery(m.py(), &discovery_mod)?;
    m.add_submodule(&discovery_mod)?;

    let conformance_mod = PyModule::new_bound(m.py(), "conformance")?;
    conformance::conformance(m.py(), &conformance_mod)?;
    m.add_submodule(&conformance_mod)?;

    // Add convenience functions at module level
    m.add_function(wrap_pyfunction!(xes::import_xes, m)?)?;
    m.add_function(wrap_pyfunction!(discovery::discover_dfg, m)?)?;
    m.add_function(wrap_pyfunction!(discovery::discover_alpha, m)?)?;
    m.add_function(wrap_pyfunction!(conformance::check_conformance, m)?)?;

    // Add module docstring
    m.add("__doc__", "YAWL Process Mining Python bindings

This module provides high-performance process mining functionality
through PyO3 bindings to the Rust implementation.

Features:
- XES import/export
- Directly Follows Graph (DFG) discovery
- Alpha miner for Petri Net discovery
- Conformance checking
- Token-based replay

Example usage:
    import yawl_process_mining

    # Import XES event log
    event_log = yawl_process_mining.import_xes(\"path/to/log.xes\")

    # Discover DFG
    dfg = yawl_process_mining.discover_dfg(event_log)

    # Discover Alpha miner
    pnml = yawl_process_mining.discover_alpha(event_log)

    # Check conformance
    conformance = yawl_process_mining.check_conformance(event_log, pnml)
")?;

    Ok(())
}