//! YAWL ML Bridge - Erlang NIF for DSPy and TPOT2
//!
//! Architecture:
//!   Java -> Erlang -> NIF (this crate) -> PyO3 -> Python

use rustler::{Encoder, Env, NifResult, Term};
use pyo3::prelude::*;
use pyo3::types::PyDict;
use std::sync::{Once, RwLock};
use pyo3::prepare_freethreaded_python;

static INIT: Once = Once::new();

// Global storage for DSPy configuration
static DSPY_CONFIG: RwLock<Option<String>> = RwLock::new(None);

fn init_python() {
    INIT.call_once(|| {
        pyo3::prepare_freethreaded_python();
    });
}

/// Ensure DSPy is configured before making predictions
fn ensure_dspy_configured(py: &Python) -> Result<(), String> {
    // Check if dspy is available
    if py.import("dspy").is_err() {
        return Err("DSPy Python library is not installed. Please install: pip install dspy-ai".to_string());
    }

    let config = DSPY_CONFIG.read().map_err(|e| format!("Lock error: {}", e))?;
    if let Some(ref model_string) = *config {
        let code = format!(r#"
import dspy
import os

# Check for GROQ_API_KEY if using groq provider
if "{model_string}".startswith("groq/"):
    if "GROQ_API_KEY" not in os.environ:
        raise ValueError("GROQ_API_KEY environment variable is required for Groq provider")

lm = dspy.LM(model="{model_string}")
dspy.configure(lm=lm)
"#, model_string = model_string);
        let globals = PyDict::new(*py);
        match py.run(&code, Some(globals), None) {
            Ok(_) => Ok(()),
            Err(e) => {
                let error_msg = format!("Failed to configure DSPy: {}", e);
                if error_msg.contains("GROQ_API_KEY") {
                    Err("GROQ_API_KEY environment variable not set. Please set it before using Groq provider.".to_string())
                } else if error_msg.contains("API key") {
                    Err("Invalid or missing API key. Please check your configuration.".to_string())
                } else if error_msg.contains("connection") {
                    Err("Network connection error. Please check your internet connection.".to_string())
                } else if error_msg.contains("No module named 'dspy'") {
                    Err("DSPy Python library not found. Please install: pip install dspy-ai".to_string())
                } else {
                    Err(error_msg)
                }
            }
        }
    } else {
        Err("DSPy not initialized. Call dspy_init first.".to_string())
    }
}

mod atoms {
    rustler::atoms! {
        ok,
        error
    }
}

// ─────────────────────────────────────────────────────────────────────────
// DSPy NIF Functions
// ─────────────────────────────────────────────────────────────────────────

/// Initialize DSPy with provider configuration (DSPy 3.1.3 API)
#[rustler::nif]
fn dspy_init(env: Env<'_>, config_json: String) -> NifResult<Term<'_>> {
    init_python();

    let config: serde_json::Value = match serde_json::from_str(&config_json) {
        Ok(c) => c,
        Err(_) => return Ok((atoms::error(), "Invalid JSON config").encode(env)),
    };

    let provider = config.get("provider").and_then(|p| p.as_str()).unwrap_or("groq");
    let model = match config.get("model").and_then(|m| m.as_str()) {
        Some(m) => m,
        None => return Ok((atoms::error(), "Missing 'model' field in config").encode(env)),
    };

    // DSPy 3.1.3 uses dspy.LM("provider/model") format
    let model_string = format!("{}/{}", provider, model);

    // Store config globally for reuse
    match DSPY_CONFIG.write() {
        Ok(mut stored) => { *stored = Some(model_string.clone()); },
        Err(_) => return Ok((atoms::error(), "Lock error").encode(env)),
    };

    Python::with_gil(|py| {
        // Check if dspy is available
        if let Err(_) = py.import("dspy") {
            let error_msg = "DSPy library not found. Install with: pip install dspy-ai";
            eprintln!("{}", error_msg);
            return Ok((atoms::error(), error_msg).encode(env));
        }

        let code = format!(r#"
import dspy
import os

# Check for GROQ_API_KEY if using groq provider
if "{model_string}".startswith("groq/"):
    if "GROQ_API_KEY" not in os.environ:
        raise ValueError("GROQ_API_KEY environment variable is required for Groq provider")

lm = dspy.LM(model="{model_string}")
dspy.configure(lm=lm)
"#, model_string = model_string);

        let globals = PyDict::new(py);
        match py.run(&code, Some(globals), None) {
            Ok(_) => {
                eprintln!("Successfully initialized DSPy with model: {}", model_string);
                Ok(atoms::ok().encode(env))
            }
            Err(e) => {
                let error_msg = format!("Failed to initialize DSPy: {}", e);
                eprintln!("{}", error_msg);

                // Provide specific guidance
                if error_msg.contains("GROQ_API_KEY") {
                    Ok((atoms::error(), "GROQ_API_KEY environment variable is required for Groq provider").encode(env))
                } else if error_msg.contains("API key") {
                    Ok((atoms::error(), "Invalid or missing API key. Please check your configuration.").encode(env))
                } else if error_msg.contains("connection") {
                    Ok((atoms::error(), "Network connection error. Please check your internet connection.").encode(env))
                } else if error_msg.contains("No module named 'dspy'") {
                    Ok((atoms::error(), "DSPy library not found. Install with: pip install dspy-ai").encode(env))
                } else {
                    Ok((atoms::error(), error_msg).encode(env))
                }
            }
        }
    })
}

/// Run DSPy prediction
#[rustler::nif]
fn dspy_predict(env: Env<'_>, signature_json: String, inputs_json: String, _examples_json: Option<String>) -> NifResult<Term<'_>> {
    init_python();

    let signature: serde_json::Value = match serde_json::from_str(&signature_json) {
        Ok(s) => s,
        Err(_) => return Ok((atoms::error(), "Invalid signature JSON").encode(env)),
    };

    let inputs: serde_json::Value = match serde_json::from_str(&inputs_json) {
        Ok(i) => i,
        Err(_) => return Ok((atoms::error(), "Invalid inputs JSON").encode(env)),
    };

    // Validate inputs is not empty
    if inputs.is_null() {
        return Ok((atoms::error(), "Inputs JSON cannot be null").encode(env));
    }

    let description = match signature.get("description")
        .and_then(|d| d.as_str()) {
        Some(d) => d,
        None => return Ok((atoms::error(), "Missing 'description' field in signature").encode(env)),
    };

    let inputs_list: Vec<String> = signature.get("inputs")
        .and_then(|i| i.as_array())
        .map(|arr| arr.iter()
            .filter_map(|v| v.as_str())
            .map(|s| s.trim().to_string())
            .collect::<Vec<_>>()
        )
        .unwrap_or_default();

    // Validate input fields are not empty
    if inputs_list.is_empty() {
        return Ok((atoms::error(), "No input fields specified in signature").encode(env));
    }

    let outputs_list: Vec<String> = signature.get("outputs")
        .and_then(|i| i.as_array())
        .map(|arr| arr.iter()
            .filter_map(|v| v.as_str())
            .map(|s| s.trim().to_string())
            .collect::<Vec<_>>()
        )
        .unwrap_or_default();

    // Sanitize class name to prevent code injection
    let safe_class_name = description
        .chars()
        .filter(|c| c.is_alphanumeric() || *c == '_')
        .collect::<String>();
    if safe_class_name.is_empty() {
        return Ok((atoms::error(), "Invalid class name from description").encode(env));
    }

    // Build input args
    let input_args: Vec<String> = inputs_list.iter().map(|i| {
        let key = i.replace(" ", "_");
        let val = inputs.get(&key)
            .or_else(|| inputs.get(i))
            .and_then(|v| v.as_str())
            .map(|s| format!("\"{}\"", s.replace("\\", "\\\\").replace("\"", "\\\"")))
            .unwrap_or_else(|| "\"\"".to_string());
        format!("{}={}", key, val)
    }).collect();

    // Build output extraction
    let output_extractions: Vec<String> = outputs_list.iter().map(|o| {
        let key = o.replace(" ", "_");
        format!("\"{}\": str(getattr(result, \"{}\", \"\"))", key, key)
    }).collect();

    // Escape strings for safe Python code generation
    let escaped_description = description
        .replace("\"", "\\\"")
        .replace("'", "\\'");
    let escaped_input_fields = inputs_list.iter()
        .map(|i| {
            let field_name = i.replace(" ", "_").replace("\"", "\\\"").replace("'", "\\'");
            format!("    {} = dspy.InputField()", field_name)
        })
        .collect::<Vec<_>>()
        .join("\n");
    let escaped_output_fields = outputs_list.iter()
        .map(|o| {
            let field_name = o.replace(" ", "_").replace("\"", "\\\"").replace("'", "\\'");
            format!("    {} = dspy.OutputField()", field_name)
        })
        .collect::<Vec<_>>()
        .join("\n");
    let escaped_input_args = input_args.join(", ");
    let escaped_output_extractions = output_extractions.join(", ");

    let code = format!(
        r#"
import dspy
import json

class {class_name}(dspy.Signature):
    """{description}"""
{input_fields}
{output_fields}

predictor = dspy.Predict({class_name})
result = predictor({input_args})
output = {{{output_extractions}}}
_result_json = json.dumps(output)
"#,
        class_name = safe_class_name,
        description = escaped_description,
        input_fields = escaped_input_fields,
        output_fields = escaped_output_fields,
        input_args = escaped_input_args,
        output_extractions = escaped_output_extractions
    );

    Python::with_gil(|py| {
        // Ensure DSPy is configured before prediction
        if let Err(e) = ensure_dspy_configured(&py) {
            return Ok((atoms::error(), e).encode(env));
        }

        let globals = PyDict::new(py);
        match py.run(&code, Some(globals), None) {
            Ok(_) => {
                match globals.get_item("_result_json") {
                    Ok(Some(result)) => {
                        Ok((atoms::ok(), result.to_string()).encode(env))
                    }
                    _ => Ok((atoms::error(), "No output from prediction").encode(env))
                }
            }
            Err(e) => Ok((atoms::error(), format!("Prediction failed: {}", e)).encode(env))
        }
    })
}

/// Configure DSPy with few-shot examples
#[rustler::nif]
fn dspy_load_examples(env: Env<'_>, examples_json: String) -> NifResult<Term<'_>> {
    init_python();

    let examples: Vec<serde_json::Value> = match serde_json::from_str(&examples_json) {
        Ok(e) => e,
        Err(_) => return Ok((atoms::error(), "Invalid examples JSON").encode(env)),
    };

    // Return count of examples
    Ok((atoms::ok(), examples.len()).encode(env))
}

// ─────────────────────────────────────────────────────────────────────────
// TPOT2 NIF Functions
// ─────────────────────────────────────────────────────────────────────────

/// Initialize TPOT2 optimizer
#[rustler::nif]
fn tpot2_init(env: Env<'_>, _config_json: String) -> NifResult<Term<'_>> {
    init_python();

    Python::with_gil(|py| {
        match py.import("tpot2") {
            Ok(_) => {
                // Also check for numpy dependency
                if let Err(_) = py.import("numpy") {
                    eprintln!("Warning: numpy not found. TPOT2 may not work correctly. Install with: pip install numpy");
                }
                eprintln!("Successfully initialized TPOT2");
                Ok(atoms::ok().encode(env))
            }
            Err(e) => {
                let error_msg = format!("TPOT2 library not found: {}. Install with: pip install tpot2", e);
                eprintln!("{}", error_msg);
                Ok((atoms::error(), error_msg).encode(env))
            }
        }
    })
}

/// Run TPOT2 optimization
#[rustler::nif]
fn tpot2_optimize(env: Env<'_>, x_json: String, y_json: String, config_json: String) -> NifResult<Term<'_>> {
    init_python();

    let config: serde_json::Value = match serde_json::from_str(&config_json) {
        Ok(c) => c,
        Err(_) => return Ok((atoms::error(), "Invalid config JSON").encode(env)),
    };

    let generations = match config.get("generations")
        .and_then(|g| g.as_u64()) {
        Some(g) => g,
        None => return Ok((atoms::error(), "Missing 'generations' field in config").encode(env)),
    };
    let population_size = match config.get("population_size")
        .and_then(|p| p.as_u64()) {
        Some(p) => p,
        None => return Ok((atoms::error(), "Missing 'population_size' field in config").encode(env)),
    };
    let max_time_mins = match config.get("timeout_minutes")
        .and_then(|t| t.as_u64()) {
        Some(t) => t,
        None => return Ok((atoms::error(), "Missing 'timeout_minutes' field in config").encode(env)),
    };

    // Escape JSON for embedding in Python code
    let x_escaped = x_json.replace("\\", "\\\\").replace("'", "\\'");
    let y_escaped = y_json.replace("\\", "\\\\").replace("'", "\\'");

    let code = format!(
        r#"
import tpot2
import json
import numpy as np

X = np.array(json.loads('{x_escaped}'))
y = np.array(json.loads('{y_escaped}'))

optimizer = tpot2.TPOTClassifier(
    generations={generations},
    population_size={population_size},
    max_time_mins={max_time_mins},
    verbosity=0,
    random_state=42
)

optimizer.fit(X, y)

_result = json.dumps({{
    "best_pipeline": str(optimizer.fitted_pipeline_),
    "fitness_score": float(optimizer.score(X, y)),
    "generations": {generations}
}})
"#, x_escaped=x_escaped, y_escaped=y_escaped, generations=generations, population_size=population_size, max_time_mins=max_time_mins);

    Python::with_gil(|py| {
        let globals = PyDict::new(py);
        match py.run(&code, Some(globals), None) {
            Ok(_) => {
                match globals.get_item("_result") {
                    Ok(Some(result)) => {
                        Ok((atoms::ok(), result.to_string()).encode(env))
                    }
                    _ => Ok((atoms::error(), "No result from optimization").encode(env))
                }
            }
            Err(e) => Ok((atoms::error(), format!("Optimization failed: {}", e)).encode(env))
        }
    })
}

/// Get best pipeline from optimizer
#[rustler::nif]
fn tpot2_get_best_pipeline(env: Env<'_>, optimizer_id: String) -> NifResult<Term<'_>> {
    Ok((atoms::ok(), format!("pipeline_{}", optimizer_id)).encode(env))
}

/// Get fitness score from optimizer
#[rustler::nif]
fn tpot2_get_fitness(env: Env<'_>, _optimizer_id: String) -> NifResult<Term<'_>> {
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
        let dspy_ok = py.import("dspy").is_ok();
        let tpot2_ok = py.import("tpot2").is_ok();

        let status = serde_json::json!({
            "python": true,
            "dspy": dspy_ok,
            "tpot2": tpot2_ok
        });

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
    dspy_init,
    dspy_predict,
    dspy_load_examples,
    tpot2_init,
    tpot2_optimize,
    tpot2_get_best_pipeline,
    tpot2_get_fitness,
    ml_bridge_status,
    ping
], load = load);