# YAWL ML Bridge Implementation Plan

## Executive Summary

Build an elegant Java API that demos beautifully to Fortune 500 CTOs, with a fault-tolerant,
high-performance pipeline underneath:

```
Java API → Erlang/OTP → Rust NIF → Python (dspy==3.1.3, tpot2)
```

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Java API Layer                                │
│                                                                 │
│  DspyProgram.predict()     Pipeline.optimize()                  │
│  Signature.builder()        OptimizationResult                  │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Erlang/OTP Layer                              │
│                                                                 │
│  dspy_bridge.erl           tpot2_bridge.erl                     │
│  (supervised, fault-tolerant)                                   │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Rust NIF Layer (PyO3)                         │
│                                                                 │
│  dspy_nif.rs               tpot2_nif.rs                         │
│  Embedded Python interpreter                                     │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Python Layer                                  │
│                                                                 │
│  dspy==3.1.3               tpot2                                │
│  (LLM optimization)        (genetic programming)                │
└─────────────────────────────────────────────────────────────────┘
```

## Components

### 1. Java API (`yawl-ml-bridge/`)

**DSPy Java API:**
```java
// Elegant, fluent API
Signature signature = Signature.builder()
    .description("Predict case outcome")
    .input("events", "workflow events", String.class)
    .input("duration_ms", Long.class)
    .output("outcome", String.class)
    .output("confidence", Double.class)
    .build();

DspyProgram program = DspyProgram.create(signature)
    .withProvider("groq")  // or "openai", "anthropic"
    .withExamples(fewShotExamples)
    .build();

Map<String, Object> result = program.predict(inputs);
```

**TPOT2 Java API:**
```java
// Pipeline optimization
PipelineConfig config = PipelineConfig.builder()
    .generations(50)
    .populationSize(100)
    .scoring("accuracy")
    .timeout(Duration.ofMinutes(10))
    .build();

OptimizationResult result = Tpot2Optimizer.create()
    .withConfig(config)
    .withTrainingData(X_train, y_train)
    .optimize();

Pipeline bestPipeline = result.bestPipeline();
double fitness = result.fitnessScore();
```

### 2. Erlang Bridge (`yawl-ml-bridge/src/`)

**dspy_bridge.erl:**
```erlang
-module(dspy_bridge).
-behaviour(gen_server).

%% API
-export([predict/2, configure/2, load_examples/2]).

%% NIF functions (loaded from Rust)
-export([dspy_init/0, dspy_predict/2, dspy_configure/1]).

-on_load(init_nif/0).

init_nif() ->
    PrivDir = code:priv_dir(?MODULE),
    NifPath = filename:join(PrivDir, "yawl_ml_bridge"),
    erlang:load_nif(NifPath, 0).

predict(SignatureJson, InputsJson) ->
    dspy_predict(SignatureJson, InputsJson).
```

**tpot2_bridge.erl:**
```erlang
-module(tpot2_bridge).
-behaviour(gen_server).

%% API
-export([optimize/3, get_best_pipeline/1, get_fitness/1]).

%% NIF functions
-export([tpot2_init/0, tpot2_optimize/3, tpot2_get_best/1]).

-on_load(init_nif/0).
```

### 3. Rust NIF (`yawl-ml-bridge/native/`)

**Cargo.toml:**
```toml
[package]
name = "yawl-ml-bridge"
version = "0.1.0"
edition = "2021"

[lib]
crate-type = ["cdylib"]
name = "yawl_ml_bridge"

[dependencies]
rustler = "0.32"
pyo3 = { version = "0.20", features = ["auto-initialize"] }
serde = { version = "1.0", features = ["derive"] }
serde_json = "1.0"
tokio = { version = "1.0", features = ["rt-multi-thread"] }
```

**src/lib.rs:**
```rust
use pyo3::prelude::*;
use rustler::{Env, NifResult, Term};

// Initialize Python interpreter once
pyo3::prepare_freethreaded_python();

// DSPy NIF functions
#[rustler::nif]
fn dspy_init() -> NifResult<String> {
    Python::with_gil(|py| {
        let dspy = py.import("dspy")?;
        // Initialize DSPy configuration
        Ok("ok".to_string())
    })
}

#[rustler::nif]
fn dspy_predict(signature_json: String, inputs_json: String) -> NifResult<String> {
    Python::with_gil(|py| {
        let dspy = py.import("dspy")?;
        // Call DSPy predict
        let result = dspy.call_method1("predict", (signature_json, inputs_json))?;
        Ok(result.to_string())
    })
}

// TPOT2 NIF functions
#[rustler::nif]
fn tpot2_init() -> NifResult<String> {
    Python::with_gil(|py| {
        let tpot = py.import("tpot2")?;
        Ok("ok".to_string())
    })
}

#[rustler::nif]
fn tpot2_optimize(X_json: String, y_json: String, config_json: String) -> NifResult<String> {
    Python::with_gil(|py| {
        let tpot = py.import("tpot2")?;
        // Run optimization
        let result = tpot.call_method1("optimize", (X_json, y_json, config_json))?;
        Ok(result.to_string())
    })
}

rustler::init!("yawl_ml_bridge", [
    dspy_init, dspy_predict, dspy_configure,
    tpot2_init, tpot2_optimize, tpot2_get_best
]);
```

### 4. Python Requirements

**requirements.txt:**
```
dspy==3.1.3
tpot2
groq  # For Groq LLM
numpy
scikit-learn
```

## File Structure

```
yawl-ml-bridge/
├── pom.xml                          # Maven module
├── src/
│   ├── main/
│   │   ├── java/org/yawlfoundation/yawl/ml/
│   │   │   ├── dspy/
│   │   │   │   ├── Signature.java
│   │   │   │   ├── DspyProgram.java
│   │   │   │   ├── Example.java
│   │   │   │   └── DspyResult.java
│   │   │   └── tpot2/
│   │   │       ├── PipelineConfig.java
│   │   │       ├── Tpot2Optimizer.java
│   │   │       └── OptimizationResult.java
│   │   └── erlang/
│   │       ├── dspy_bridge.erl
│   │       ├── tpot2_bridge.erl
│   │       └── ml_bridge_sup.erl
│   └── test/
│       └── java/
│           └── org/yawlfoundation/yawl/ml/
│               ├── DspyEndToEndTest.java
│               └── Tpot2EndToEndTest.java
├── native/                          # Rust NIF
│   ├── Cargo.toml
│   ├── src/
│   │   └── lib.rs
│   └── build.sh
├── priv/
│   └── yawl_ml_bridge.dylib         # Built NIF
└── requirements.txt                 # Python deps
```

## Implementation Phases

### Phase 1: Rust NIF + PyO3 (2 days)
1. Create `yawl-ml-bridge/native/` directory
2. Set up Cargo.toml with pyo3, rustler dependencies
3. Implement basic dspy_init, dspy_predict NIFs
4. Implement basic tpot2_init, tpot2_optimize NIFs
5. Build and test NIF loading

### Phase 2: Erlang Bridge (1 day)
1. Create dspy_bridge.erl with gen_server
2. Create tpot2_bridge.erl with gen_server
3. Create ml_bridge_sup.erl supervisor
4. Wire up NIF loading

### Phase 3: Java API (2 days)
1. Create Signature, DspyProgram, Example classes
2. Create PipelineConfig, Tpot2Optimizer classes
3. Implement Erlang port/JInterface communication
4. Write elegant fluent builders

### Phase 4: Integration Tests (1 day)
1. End-to-end DSPy test with Groq
2. End-to-end TPOT2 test with sklearn data
3. Performance benchmarks

## Verification

```bash
# Build Rust NIF
cd yawl-ml-bridge/native && cargo build --release

# Copy NIF to priv
cp target/release/libyawl_ml_bridge.dylib ../priv/

# Run Erlang tests
cd yawl-ml-bridge && erl -pa ebin -eval "dspy_bridge:test(), tpot2_bridge:test()"

# Run Java tests
mvn test -pl yawl-ml-bridge -Dtest="DspyEndToEndTest"
mvn test -pl yawl-ml-bridge -Dtest="Tpot2EndToEndTest"

# Demo
mvn exec:java -pl yawl-ml-bridge -Dexec.mainClass="org.yawlfoundation.yawl.ml.Demo"
```

## Demo Script

```java
public class Demo {
    public static void main(String[] args) {
        System.out.println("=== YAWL ML Bridge Demo ===\n");

        // DSPy Demo
        System.out.println("1. DSPy - Predict case outcome:");
        Signature sig = Signature.builder()
            .description("Predict case outcome")
            .input("events", String.class)
            .output("outcome", String.class)
            .build();

        DspyProgram program = DspyProgram.create(sig).withGroq().build();
        Map<String, Object> result = program.predict(Map.of(
            "events", "StartTask -> Approve -> EndTask"
        ));
        System.out.println("   Result: " + result);

        // TPOT2 Demo
        System.out.println("\n2. TPOT2 - Optimize pipeline:");
        OptimizationResult optResult = Tpot2Optimizer.create()
            .withGenerations(10)
            .withPopulation(50)
            .optimize(X_train, y_train);
        System.out.println("   Best fitness: " + optResult.fitnessScore());
        System.out.println("   Best pipeline: " + optResult.bestPipeline());
    }
}
```
