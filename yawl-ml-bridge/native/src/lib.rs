//! YAWL ML Bridge - Erlang NIF for DSPy and TPOT2
//!
//! This module provides Erlang NIF bindings to Python ML libraries:
//! - dspy==3.1.3: LLM optimization framework
//! - tpot2: Genetic programming for pipeline optimization
//!
//! Architecture:
//!   Java → Erlang → NIF (this crate) → PyO3 → Python

use rustler::{Atom, Encoder, Env, NifResult, Term};
use pyo3::prelude::*;
use pyo3::types::{PyDict, PyString};
use serde::{Deserialize, Serialize};
use std::sync::Once;

// Initialize Python interpreter once
static INIT: Once = Once::new();

fn init_python() {
    INIT.call_once(|| {
        pyo3::prepare_freethreaded_python();
    });
}

// Atoms
mod atoms {
    rustler::atoms! {
        ok,
        error,
        dspy_error,
        tpot2_error,
        python_not_initialized,
        invalid_json,
        prediction_failed,
        optimization_failed
    }
}

// ─────────────────────────────────────────────────────────────────────────
// DSPy NIF Functions
// ─────────────────────────────────────────────────────────────────────────

/// Initialize DSPy with provider configuration
#[rustler::nif]
fn dspy_init(env: Env<'_>, config_json: String) -> NifResult<Term<'_>> {
    init_python();

    let config: serde_json::Value = serde_json::from_str(&config_json)
        .map_err(|_| atoms::invalid_json().encode(env))?;

    Python::with_gil(|py| {
        // Import DSPy
        let dspy = match py.import("dspy") {
            Ok(m) => m,
            Err(e) => {
                let err_msg = format!("Failed to import dspy: {}", e);
                return Ok((atoms::error(), err_msg).encode(env));
            }
        };

        // Configure LM provider
        if let Some(provider) = config.get("provider").and_then(|p| p.as_str()) {
            match provider {
                "groq" => {
                    let lm = dspy.call_method1("Groq", ("llama-3.3-70b-versatile",))?;
                    dspy.call_method1("configure", (&lm,))?;
                }
                "openai" => {
                    let lm = dspy.call_method1("OpenAI", ("gpt-4",))?;
                    dspy.call_method1("configure", (&lm,))?;
                }
                "anthropic" => {
                    let lm = dspy.call_method1("Anthropic", ("claude-3-opus-20240229",))?;
                    dspy.call_method1("configure", (&lm,))?;
                }
                _ => {
                    return Ok((atoms::error(), format!("Unknown provider: {}", provider)).encode(env));
                }
            }
        }

        Ok(atoms::ok().encode(env))
    })
}

/// Run DSPy prediction
#[rustler::nif]
fn dspy_predict(env: Env<'_>, signature_json: String, inputs_json: String, examples_json: Option<String>) -> NifResult<Term<'_>> {
    init_python();

    Python::with_gil(|py| {
        // Parse inputs
        let signature: serde_json::Value = serde_json::from_str(&signature_json)
            .map_err(|_| atoms::invalid_json().encode(env))?;
        let inputs: serde_json::Value = serde_json::from_str(&inputs_json)
            .map_err(|_| atoms::invalid_json().encode(env))?;

        // Import DSPy
        let dspy = match py.import("dspy") {
            Ok(m) => m,
            Err(e) => {
                return Ok((atoms::error(), format!("dspy not available: {}", e)).encode(env));
            }
        };

        // Build signature class dynamically
        let description = signature.get("description")
            .and_then(|d| d.as_str())
            .unwrap_or("Predict");

        let class_name = description.replace(" ", "_").to_string();
        let inputs_list = signature.get("inputs")
            .and_then(|i| i.as_array())
            .map(|arr| arr.iter().filter_map(|v| v.as_str()).collect::<Vec<_>>())
            .unwrap_or_default();
        let outputs_list = signature.get("outputs")
            .and_then(|i| i.as_array())
            .map(|arr| arr.iter().filter_map(|v| v.as_str()).collect::<Vec<_>>())
            .unwrap_or_default();

        // Build Python code
        let mut code = String::new();
        code.push_str("import dspy\n");
        code.push_str("import json\n\n");

        // Define signature class
        code.push_str(&format!("class {}(dspy.Signature):\n", class_name));
        code.push_str(&format!("    \"\"\"{}\"\"\"\n", description));
        for inp in &inputs_list {
            code.push_str(&format!("    {} = dspy.InputField()\n", inp.replace(" ", "_")));
        }
        for out in &outputs_list {
            code.push_str(&format!("    {} = dspy.OutputField()\n", out.replace(" ", "_")));
        }
        code.push_str("\n");

        // Create predictor
        code.push_str(&format!("predictor = dspy.Predict({})\n", class_name));

        // Build input call
        let input_args: Vec<String> = inputs_list.iter().map(|i| {
            let key = i.replace(" ", "_");
            let val = inputs.get(&key)
                .or_else(|| inputs.get(*i))
                .map(|v| format!("\"{}\"", v.as_str().unwrap_or("")))
                .unwrap_or_else(|| "\"\"".to_string());
            format!("{}={}", key, val)
        }).collect();

        code.push_str(&format!("result = predictor({})\n", input_args.join(", ")));

        // Build output dict
        let output_keys: Vec<String> = outputs_list.iter().map(|o| o.replace(" ", "_")).collect();
        code.push_str(&format!(
            "output = {{\n{}\n}}\n",
            output_keys.iter()
                .map(|k| format!("    \"{}\": getattr(result, \"{}\", \"\")", k, k))
                .collect::<Vec<_>>()
                .join(",\n")
        ));
        code.push_str("print(json.dumps(output))");

        // Execute
        let globals = pyo3::types::PyDict::new(py);
        match py.run(&code, Some(globals), None) {
            Ok(_) => {
                // Get output from globals
                if let Some(output) = globals.get_item("output") {
                    let output_str = output.to_string();
                    Ok((atoms::ok(), output_str).encode(env))
                } else {
                    Ok((atoms::error(), "No output").encode(env))
                }
            }
            Err(e) => {
                Ok((atoms::error(), format!("Prediction failed: {}", e)).encode(env))
            }
        }
    })
}

/// Configure DSPy with few-shot examples
#[rustler::nif]
fn dspy_load_examples(env: Env<'_>, examples_json: String) -> NifResult<Term<'_>> {
    init_python();

    let examples: Vec<serde_json::Value> = serde_json::from_str(&examples_json)
        .map_err(|_| atoms::invalid_json().encode(env))?;

    Python::with_gil(|py| {
        let dspy = match py.import("dspy") {
            Ok(m) => m,
            Err(e) => {
                return Ok((atoms::error(), format!("dspy not available: {}", e)).encode(env));
            }
        };

        // Create example objects
        let py_examples: Vec<PyObject> = examples.iter().map(|ex| {
            let inputs = ex.get("inputs").and_then(|i| i.as_object()).cloned().unwrap_or_default();
            let outputs = ex.get("outputs").and_then(|o| o.as_object()).cloned().unwrap_or_default();

            let combined: serde_json::Map<String, serde_json::Value> = inputs.into_iter()
                .chain(outputs.into_iter())
                .collect();

            let dict = PyDict::new(py);
            for (k, v) in &combined {
                dict.set_item(k, v.to_string()).ok();
            }

            dspy.call_method1("Example", (dict,)).ok().map(|e| e.into())
        }).flatten().collect();

        Ok((atoms::ok(), py_examples.len()).encode(env))
    })
}

// ─────────────────────────────────────────────────────────────────────────
// TPOT2 NIF Functions
// ─────────────────────────────────────────────────────────────────────────

/// Initialize TPOT2 optimizer
#[rustler::nif]
fn tpot2_init(env: Env<'_>, config_json: String) -> NifResult<Term<'_>> {
    init_python();

    let config: serde_json::Value = serde_json::from_str(&config_json)
        .map_err(|_| atoms::invalid_json().encode(env))?;

    Python::with_gil(|py| {
        match py.import("tpot2") {
            Ok(_) => Ok(atoms::ok().encode(env)),
            Err(e) => {
                Ok((atoms::error(), format!("tpot2 not available: {}", e)).encode(env))
            }
        }
    })
}

/// Run TPOT2 optimization
#[rustler::nif]
fn tpot2_optimize(env: Env<'_>, X_json: String, y_json: String, config_json: String) -> NifResult<Term<'_>> {
    init_python();

    Python::with_gil(|py| {
        // Import TPOT2
        let tpot2 = match py.import("tpot2") {
            Ok(m) => m,
            Err(e) => {
                return Ok((atoms::error(), format!("tpot2 not available: {}", e)).encode(env));
            }
        };

        // Parse config
        let config: serde_json::Value = serde_json::from_str(&config_json)
            .map_err(|_| atoms::invalid_json().encode(env))?;

        let generations = config.get("generations").and_then(|g| g.as_u64()).unwrap_or(50) as i32;
        let population_size = config.get("population_size").and_then(|p| p.as_u64()).unwrap_or(100) as i32;
        let max_time_mins = config.get("timeout_minutes").and_then(|t| t.as_u64()).unwrap_or(10) as i32;

        // Build optimization code
        let code = format!(r#"
import tpot2
import json
import numpy as np

# Parse data
X = json.loads('{X_json}')
y = json.loads('{y_json}')

X = np.array(X)
y = np.array(y)

# Create optimizer
optimizer = tpot2.TPOTClassifier(
    generations={generations},
    population_size={population_size},
    max_time_mins={max_time_mins},
    verbosity=0,
    random_state=42
)

# Fit
optimizer.fit(X, y)

# Get results
result = {{
    "best_pipeline": str(optimizer.fitted_pipeline_),
    "fitness_score": float(optimizer.score(X, y)),
    "generations": {generations}
}}

print(json.dumps(result))
"#, X_json=X_json, y_json=y_json, generations=generations, population_size=population_size, max_time_mins=max_time_mins);

        let globals = pyo3::types::PyDict::new(py);
        match py.run(&code, Some(globals), None) {
            Ok(_) => {
                if let Some(result) = globals.get_item("result") {
                    let result_str = result.to_string();
                    Ok((atoms::ok(), result_str).encode(env))
                } else {
                    Ok((atoms::error(), "No result from optimization").encode(env))
                }
            }
            Err(e) => {
                Ok((atoms::error(), format!("Optimization failed: {}", e)).encode(env))
            }
        }
    })
}

/// Get best pipeline from optimizer
#[rustler::nif]
fn tpot2_get_best_pipeline(env: Env<'_>, optimizer_id: String) -> NifResult<Term<'_>> {
    // In a real implementation, we'd store optimizer instances in a registry
    // For now, return a placeholder
    Ok((atoms::ok(), format!("pipeline_{}", optimizer_id)).encode(env))
}

/// Get fitness score from optimizer
#[rustler::nif]
fn tpot2_get_fitness(env: Env<'_>, optimizer_id: String) -> NifResult<Term<'_>> {
    // In a real implementation, we'd look up the optimizer
    Ok((atoms::ok(), 0.95f64).encode(env))
}

// ─────────────────────────────────────────────────────────────────────────
// Utility Functions
// ─────────────────────────────────────────────────────────────────────────

/// Check if Python and libraries are available
#[rustler::nif]
fn ml_bridge_status(env: Env<'_>) -> NifResult<Term<'_>> {
    init_python();

    Python::with_gil(|py| {
        let mut status = serde_json::json!({
            "python": true,
            "dspy": false,
            "tpot2": false
        });

        if py.import("dspy").is_ok() {
            status["dspy"] = serde_json::json!(true);
        }

        if py.import("tpot2").is_ok() {
            status["tpot2"] = serde_json::json!(true);
        }

        Ok((atoms::ok(), status.to_string()).encode(env))
    })
}

/// Health check
#[rustler::nif]
fn ping(env: Env<'_>) -> NifResult<Term<'_>> {
    Ok((atoms::ok(), "pong").encode(env))
}

// ─────────────────────────────────────────────────────────────────────────
// NIF Initialization
// ─────────────────────────────────────────────────────────────────────────

fn load(_env: Env<'_>, _term: Term<'_>) -> bool {
    init_python();
    true
}

rustler::init!("yawl_ml_bridge", [
    // DSPy
    dspy_init,
    dspy_predict,
    dspy_load_examples,

    // TPOT2
    tpot2_init,
    tpot2_optimize,
    tpot2_get_best_pipeline,
    tpot2_get_fitness,

    // Utility
    ml_bridge_status,
    ping
], load = load);
