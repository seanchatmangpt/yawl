# YAWL ML Bridge

Enterprise-grade machine learning bridge connecting YAWL workflows with state-of-the-art ML frameworks through a fault-tolerant, high-performance pipeline.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Java API Layer                                │
│                                                                 │
│  DSPy.predict()          Pipeline.optimize()                    │
│  Signature.builder()      OptimizationResult                   │
│  Example API              Tpot2Optimizer                        │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Erlang/OTP Layer                              │
│                                                                 │
│  yawl_ml_bridge.erl      dspy_bridge.erl                      │
│  (supervised, fault-tolerant)                                  │
│  tpot2_bridge.erl                                              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Rust NIF Layer (PyO3)                         │
│                                                                 │
│  dspy_nif.rs             tpot2_nif.rs                         │
│  Embedded Python interpreter                                     │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Python Layer                                  │
│                                                                 │
│  dspy==3.1.3             tpot2                                │
│  (LLM optimization)      (genetic programming)                 │
└─────────────────────────────────────────────────────────────────┘
```

## Quick Start Guide

### Prerequisites

- **Java 25** with enabled preview features
- **Erlang/OTP** (25.2 or later)
- **Rust** (1.75+)
- **Python 3.9+** with pip
- **Maven** (3.9+)

### 1. Setup Environment

```bash
# Clone repository
cd /Users/sac/yawl/yawl-ml-bridge

# Set environment variable
export GROQ_API_KEY="your-groq-api-key"
```

### 2. Build the Bridge

```bash
# Build Rust NIF
cd native
bash build.sh

# Build Java components
mvn clean compile
```

### 3. Start Erlang Node

```bash
# Start Erlang node with the ML bridge
erl -name yawl_ml@localhost -pa ebin
```

### 4. Run Quick Demo

```bash
# Run the showcase
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.ml.MlBridgeShowcase"
```

## DSPy API Usage Examples

### Basic Prediction

```java
import org.yawlfoundation.yawl.ml.dspy.*;

// 1. Define the prediction signature
Signature signature = Signature.builder()
    .description("Predict case outcome based on workflow events")
    .input("events", "workflow event sequence", String.class)
    .input("duration_ms", "case duration in milliseconds", Long.class)
    .output("outcome", "predicted outcome", String.class)
    .output("confidence", "confidence score", Double.class)
    .build();

// 2. Create a DSPy program
DspyProgram program = DspyProgram.create(signature)
    .withGroq()  // Use Groq LLM
    .withModel("llama-3.3-70b-versatile")
    .withExample(
        Map.of("events", "StartTask -> Approve -> EndTask", "duration_ms", 5000L),
        Map.of("outcome", "approved", "confidence", 0.95)
    )
    .build();

// 3. Make prediction
Map<String, Object> inputs = Map.of(
    "events", "StartTask -> Review -> Reject -> EndTask",
    "duration_ms", 3000L
);

Map<String, Object> result = program.predict(inputs);
System.out.println("Prediction: " + result);
// Output: {outcome= rejected, confidence=0.87}
```

### Multi-shot Learning

```java
// Add multiple examples
DspyProgram program = DspyProgram.create(signature)
    .withOpenAI()
    .withExample(Map.of("events", "Start -> Approve -> End", "duration_ms", 2000L),
                Map.of("outcome", "approved", "confidence", 0.98))
    .withExample(Map.of("events", "Start -> Review -> Reject -> End", "duration_ms", 4500L),
                Map.of("outcome", "rejected", "confidence", 0.92))
    .withExample(Map.of("events", "Start -> Escalate -> Approve -> End", "duration_ms", 8000L),
                Map.of("outcome", "escalated", "confidence", 0.85))
    .build();

// Predict with confidence
Map<String, Object> result = program.predict(inputs);
Double confidence = (Double) result.get("confidence");
if (confidence > 0.8) {
    System.out.println("High confidence prediction: " + result.get("outcome"));
}
```

### Different LLM Providers

```java
// Groq (fast, cost-effective)
DspyProgram groqProgram = DspyProgram.create(signature)
    .withGroq()
    .withModel("llama-3.3-70b-versatile")
    .build();

// OpenAI (GPT-4)
DspyProgram openaiProgram = DspyProgram.create(signature)
    .withOpenAI()
    .withModel("gpt-4")
    .build();

// Anthropic (Claude)
DspyProgram anthropicProgram = DspyProgram.create(signature)
    .withAnthropic()
    .withModel("claude-3-opus-20240229")
    .build();
```

## TPOT2 API Usage Examples

### Basic Pipeline Optimization

```java
import org.yawlfoundation.yawl.ml.tpot2.*;
import java.time.Duration;
import java.util.*;

// 1. Prepare training data
List<List<Double>> X_train = List.of(
    List.of(1.0, 2.0, 3.0),
    List.of(4.0, 5.0, 6.0),
    List.of(7.0, 8.0, 9.0),
    // ... more samples
);

List<Object> y_train = List.of(0, 1, 1, 0, 1, 0, 1, 1);

// 2. Configure optimizer
OptimizationResult result = Tpot2Optimizer.create()
    .withGenerations(50)
    .withPopulationSize(100)
    .withTimeout(Duration.ofMinutes(10))
    .withScoring("accuracy")
    .withRandomState(42)
    .optimize(X_train, y_train);

// 3. Get results
double fitness = result.fitnessScore();
String bestPipeline = result.bestPipeline();
Map<String, Object> metrics = result.metrics();

System.out.println("Best fitness: " + fitness);
System.out.println("Best pipeline: " + bestPipeline);
```

### Quick Optimization

```java
// For rapid prototyping
OptimizationResult quickResult = Tpot2Optimizer.quick()
    .optimize(X_train, y_train);

System.out.println("Quick optimization complete!");
System.out.println("Best pipeline: " + quickResult.bestPipeline());
```

### Custom Scoring Metrics

```java
// Use different evaluation metrics
OptimizationResult result = Tpot2Optimizer.create()
    .withGenerations(30)
    .withPopulationSize(50)
    .withScoring("f1")  // or "roc_auc", "precision", "recall"
    .optimize(X_train, y_train);
```

## Erlang API Documentation

### Starting the Bridge

```erlang
% Start the ML bridge supervisor
yawl_ml_bridge:start_link().

% Check status
{ok, Status} = yawl_ml_bridge:status().
% Status: #{python => true, dspy => true, tpot2 => true}
```

### DSPy Operations

```erlang
% Initialize DSPy
Config = #{provider => "groq", model => "llama-3.3-70b-versatile"},
ok = yawl_ml_bridge:dspy_init(Config).

% Make prediction
Signature = #{description => "Predict outcome", inputs => [events], outputs => [outcome]},
Inputs = #{events => "Start -> Approve -> End"},
Examples = [],
{ok, Result} = yawl_ml_bridge:dspy_predict(Signature, Inputs, Examples).

% Load few-shot examples
Examples = [
    #{inputs => #{events => "A -> B"}, outputs => #{outcome => "success"}},
    #{inputs => #{events => "A -> C"}, outputs => #{outcome => "failed"}}
],
ok = yawl_ml_bridge:dspy_load_examples(Examples).
```

### TPOT2 Operations

```erlang
% Initialize TPOT2
Config = #{generations => 50, population_size => 100, timeout_minutes => 10},
ok = yawl_ml_bridge:tpot2_init(Config).

% Run optimization
X = [[1.0,2.0], [3.0,4.0], [5.0,6.0]],
Y = [0, 1, 0],
OptimizationId = "tpot_123",
ok = yawl_ml_bridge:tpot2_optimize(X, Y, OptimizationId).

% Get results
{ok, Pipeline} = yawl_ml_bridge:tpot2_get_best_pipeline(OptimizationId),
{ok, Fitness} = yawl_ml_bridge:tpot2_get_fitness(OptimizationId).
```

## Java API Documentation

### DSPy Components

#### Signature

```java
/**
 * Defines input/output contract for DSPy predictions.
 *
 * @param description Natural language description of the task
 * @param inputs Map of field names to Field definitions
 * @param outputs Map of field names to Field definitions
 */
public final class Signature {
    public static Builder builder()
    public String description()
    public Map<String, Field> inputs()
    public Map<String, Field> outputs()
    public String toJson()
}
```

#### DspyProgram

```java
/**
 * Fluent API for building and running DSPy predictions.
 */
public final class DspyProgram {
    public static Builder create(Signature signature)
    public Map<String, Object> predict(Map<String, Object> inputs)
    public String provider()
    public String model()

    public static final class Builder {
        public Builder withGroq()
        public Builder withOpenAI()
        public Builder withAnthropic()
        public Builder withProvider(String provider)
        public Builder withModel(String model)
        public Builder withExample(Map<String, Object> inputs, Map<String, Object> outputs)
        public DspyProgram build()
    }
}
```

#### Example

```java
/**
 * Represents a few-shot example for DSPy.
 */
public final class Example {
    public Example(Map<String, Object> inputs, Map<String, Object> outputs)
    public Map<String, Object> inputs()
    public Map<String, Object> outputs()
}
```

### TPOT2 Components

#### Tpot2Optimizer

```java
/**
 * Genetic programming for ML pipeline optimization.
 */
public final class Tpot2Optimizer {
    public static Builder create()
    public static Builder quick()
    public OptimizationResult optimize(List<List<Double>> X, List<Object> y)
    public OptimizationResult optimizeWithValidation(
        List<List<Double>> XTrain, List<Object> yTrain,
        List<List<Double>> XVal, List<Object> yVal)

    public static final class Builder {
        public Builder withGenerations(int generations)
        public Builder withPopulationSize(int populationSize)
        public Builder withTimeout(Duration timeout)
        public Builder withScoring(String scoring)
        public Builder withRandomState(int randomState)
        public Tpot2Optimizer build()
    }
}
```

#### OptimizationResult

```java
/**
 * Result from TPOT2 optimization.
 */
public final class OptimizationResult {
    public double fitnessScore()
    public String bestPipeline()
    public Map<String, Object> metrics()
    public List<String> pipelineSteps()
}
```

## Build Instructions

### Prerequisites

- Java 25 (with preview features enabled)
- Erlang/OTP 25.2+
- Rust 1.75+
- Python 3.9+
- Maven 3.9+

### Building from Source

```bash
# 1. Clone repository
git clone <repository-url>
cd yawl-ml-bridge

# 2. Build Rust NIF
cd native
bash build.sh

# 3. Build Java components
mvn clean compile

# 4. Run tests
mvn test

# 5. Package
mvn package
```

### Building with Docker

```bash
# Build the complete system
docker build -t yawl-ml-bridge .

# Run tests
docker run --rm yawl-ml-bridge mvn test
```

### Native Build Options

```bash
# Debug build
cd native
bash build.sh dev

# Clean build artifacts
bash build.sh clean

# Build and test
bash build.sh test
```

## Environment Variables

### Required Variables

```bash
# Groq API Key for LLM access
export GROQ_API_KEY="your-groq-api-key-here"

# Optional: OpenAI API Key
export OPENAI_API_KEY="your-openai-api-key"

# Optional: Anthropic API Key
export ANTHROPIC_API_KEY="your-anthropic-api-key"
```

### Configuration Variables

```bash
# Erlang node configuration
export ERLANG_COOKIE="your-erlang-cookie"
export ERLANG_NODE="yawl_ml@localhost"

# Logging level
export LOG_LEVEL="INFO"

# Python interpreter path (default: python3)
export PYTHON_CMD="python3.9"
```

## Testing Instructions

### Unit Tests

```bash
# Run all tests
mvn test

# Run specific test classes
mvn test -Dtest=DspyEndToEndTest
mvn test -Dtest=Tpot2EndToEndTest

# Run with coverage
mvn test jacoco:report
```

### Integration Tests

```bash
# Start Erlang node first
erl -name yawl_ml@localhost -pa ebin

# Run integration tests
mvn test -Dtest="*IntegrationTest"

# End-to-end test with Groq
mvn test -Dtest="DspyEndToEndTest" -Dgroq.key="${GROQ_API_KEY}"
```

### Test Data

```bash
# Generate test datasets
python3 -c "
import numpy as np
from sklearn.datasets import make_classification

X, y = make_classification(n_samples=100, n_features=5, n_classes=2)
np.savez('test_data.npz', X=X, y=y)
print('Test data generated: test_data.npz')
"
```

### Performance Tests

```bash
# Run benchmark suite
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.ml.Benchmark"

# Measure prediction throughput
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.ml.ThroughputTest"
```

## Advanced Configuration

### Custom Python Environments

```bash
# Use virtual environment
python3 -m venv ml-bridge-env
source ml-bridge-env/bin/activate
pip install -r requirements.txt

# Build with specific Python path
export PYTHON_PATH="/path/to/venv/bin/python3"
bash build.sh
```

### Multiple LLM Providers

```java
// Switch providers at runtime
DspyProgram program = DspyProgram.create(signature)
    .withProvider("openai")
    .withModel("gpt-4-turbo-preview")
    .build();

// Later, switch to Groq
program.withGroq().build();
```

### Fault Tolerance

```java
// Enable retries
MlBridgeClient client = new MlBridgeClient()
    .withMaxRetries(3)
    .withTimeout(Duration.ofSeconds(30));

// Use in program
DspyProgram program = DspyProgram.create(signature)
    .withClient(client)
    .build();
```

## Error Handling

The YAWL ML Bridge includes robust error handling for missing dependencies and configuration issues. When errors occur, detailed messages are returned to help diagnose and resolve issues.

### Python Library Errors

When required Python libraries are missing, the system provides clear error messages:

**DSPy Library Missing:**
```java
// Error message returned
"Error: DSPy Python library is not installed. Please install: pip install dspy-ai"
```

**TPOT2 Library Missing:**
```java
// Error message returned
"Error: TPOT2 library not found. Install with: pip install tpot2"
```

**NumPy Library Missing:**
```java
// Error message returned
"Error: numpy library not found. Install with: pip install numpy"
```

### GROQ API Key Configuration

The system validates the GROQ API key before making API calls:

**Missing API Key:**
```java
// When using Groq provider without setting the key
"Error: GROQ_API_KEY environment variable is required for Groq provider"
```

### Configuration Validation

The bridge validates configuration before processing:

**Invalid JSON Configuration:**
```java
"Error: Invalid JSON config"
```

**Missing Required Fields:**
```java
"Error: Missing 'model' field in config"
"Error: Missing 'generations' field in config"
```

### Optimization Errors

TPOT2 optimization includes comprehensive error handling:

**Timeout Errors:**
```java
"Error: Optimization timed out. Try increasing max_time_mins or reduce problem complexity"
```

**Memory Issues:**
```java
"Error: Insufficient memory for optimization. Try reducing population_size or generations"
```

**Data Format Errors:**
```java
"Error: Invalid data format. Ensure X and y are properly formatted arrays"
```

### Status Monitoring

Check the system status to verify all components are working:

```java
// Java API
MlBridgeStatus status = client.getStatus();
System.out.println(status.getLibraries());
// Output: {dspy="available: 2.1.0", tpot2="available: 0.22.0", numpy="available: 1.24.0"}

// Erlang API
{ok, Status} = yawl_ml_bridge:status().
% Status contains library availability and recommendations

// Check system health
{ok, Health} = yawl_ml_bridge:ping().
% Returns "pong" if healthy
```

### Environment Setup

Ensure proper environment setup:

```bash
# Required environment variables
export GROQ_API_KEY="your-groq-api-key-here"

# Verify Python installation
python3 --version
# Expected: Python 3.9+

# Verify Python libraries
python3 -c "import dspy, tpot2, numpy; print('All dependencies OK')"

# Check NIF library
ls -la priv/libyawl_ml_bridge.*
```

## Troubleshooting

### Common Issues

1. **NIF Loading Fails**
   ```bash
   # Check library path
   ls -la priv/

   # Rebuild NIF
   cd native && bash build.sh clean && bash build.sh
   ```

2. **Erlang Node Connection Issues**
   ```bash
   # Check cookie consistency
   erl -name test@localhost -eval "io:format('Cookie: ~p~n', [erlang:get_cookie()]), halt()."
   ```

3. **Python Import Errors**
   ```bash
   # Verify Python dependencies
   python3 -c "import dspy, tpot2; print('All dependencies OK')"

   # Install missing packages
   pip install dspy==3.1.3 tpot2
   ```

4. **Memory Issues**
   ```bash
   # Increase Java heap size
   export MAVEN_OPTS="-Xmx2g -Xms1g"
   mvn compile
   ```

### Debug Mode

```bash
# Enable debug logging
export LOG_LEVEL="DEBUG"

# Run with verbose output
mvn compile -X
```

## Performance Tuning

### Optimization Settings

```java
// For fast prototyping
Tpot2Optimizer.quick()
    .withGenerations(10)
    .withPopulationSize(50)
    .optimize(X_train, y_train);

// For production
Tpot2Optimizer.create()
    .withGenerations(100)
    .withPopulationSize(200)
    .withTimeout(Duration.ofMinutes(30))
    .optimize(X_train, y_train);
```

### Concurrency

```java
// Parallel predictions
List<Map<String, Object>> inputs = List.of(input1, input2, input3);
List<Map<String, Object>> results = inputs.parallelStream()
    .map(program::predict)
    .collect(Collectors.toList());
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## License

This project is part of the YAWL Foundation. See the LICENSE file for details.

## Support

For issues and questions:
- GitHub Issues: [Repository Issues]
- Documentation: [YAWL Documentation]
- Community: [YAWL Community Forum]