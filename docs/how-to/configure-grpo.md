# Configure GRPO Engine

**Version**: v6.0.0-GA
**Scope**: Reinforcement Learning optimization for YAWL workflow scheduling

---

## GRPO Configuration Overview

Gradient Reward Policy Optimization (GRPO) is YAWL's built-in reinforcement learning engine for dynamic workflow scheduling optimization. GRPO continuously learns optimal scheduling policies by evaluating execution patterns and reward signals.

### Key Features
- **Adaptive scheduling**: Learns optimal task scheduling strategies
- **Reward-driven optimization**: Maximize throughput, minimize latency
- **Multi-objective training**: Balance performance metrics
- **Curriculum learning**: Progressive complexity adaptation

### Integration Points
- `YNetRunner`: Primary GRPO integration point
- `YWorkItem`: Task state and reward calculation
- `YEngine`: Decision-making authority delegation

---

## rl-config.toml File Format

The GRPO configuration uses TOML format for human-readable configuration with type safety.

### Basic Structure

```toml
# Reinforcement Learning configuration for YAWL v6.0.0-GA
# Path: .claude/config/rl-config.toml

[engine]
# Engine configuration
enabled = true
max_iterations = 1000
learning_rate = 0.001
gamma = 0.95  # Discount factor
epsilon = 0.1  # Exploration rate
epsilon_decay = 0.995
min_epsilon = 0.01

[curriculum]
# Curriculum learning stages
stages = [
    {
        name = "exploration",
        duration = 100,
        k_value = 5,
        temperature = 1.0,
        exploration_rate = 0.3
    },
    {
        name = "learning",
        duration = 300,
        k_value = 10,
        temperature = 0.8,
        exploration_rate = 0.1
    },
    {
        name = "optimization",
        duration = 600,
        k_value = 20,
        temperature = 0.5,
        exploration_rate = 0.05
    }
]

[k_values]
# K-nearest neighbors configuration
default = 10
max_neighbors = 50
similarity_threshold = 0.7
cache_size = 1000

[rewards]
# Reward function weights
throughput_weight = 0.4
latency_weight = 0.3
resource_weight = 0.2
fairness_weight = 0.1

# Individual reward functions
[rewards.throughput]
type = "exponential"
target = 0.95
scale = 2.0

[rewards.latency]
type = "inverse"
target = 100.0  # ms
scale = 1.0

[rewards.resource]
type = "quadratic"
target = 0.8
scale = 1.5

[rewards.fairness]
type = "stddev"
target = 0.1
scale = 2.0

[sampling]
# Sampling configuration
temperature = 0.8
top_k = 5
sample_size = 100
clamp_rewards = true
reward_clamp_min = -1.0
reward_clamp_max = 1.0

[timeout]
# Training timeout configuration
max_training_time = 3600  # seconds
batch_timeout = 300
eval_timeout = 60

[monitoring]
# Monitoring and logging
log_level = "INFO"
save_checkpoints = true
checkpoint_interval = 100
tensorboard_enabled = true
tensorboard_dir = "./tensorboard"

[optimization]
# Optimization settings
gradient_clipping = 1.0
optimizer = "adam"
weight_decay = 0.01
batch_size = 32
buffer_size = 10000

[performance]
# Performance tuning
num_workers = 4
pin_memory = true
prefetch_factor = 2
persistent_workers = true
```

---

## K Value Selection Guide

The K parameter controls the number of nearest neighbors used in policy evaluation.

### K Value Guidelines

| K Value | Use Case | Pros | Cons |
|---------|----------|------|------|
| **K = 5** | Small workflows (< 10 tasks) | Fast, low memory | Less accurate |
| **K = 10** | Medium workflows (10-50 tasks) | Balanced | Slight overhead |
| **K = 20** | Large workflows (50-100 tasks) | High accuracy | Higher memory |
| **K = 50** | Complex workflows (> 100 tasks) | Maximum accuracy | Performance impact |

### Selection Rules of Thumb

```python
# Algorithmic selection guide
def select_k(workflow_complexity):
    if workflow_complexity < 10:
        return 5
    elif workflow_complexity < 50:
        return 10
    elif workflow_complexity < 100:
        return 20
    else:
        return 50

# Complexity factors:
# - Number of tasks
# - Branching factor
# - Resource dependencies
# - Time constraints
```

### Environment Override

```bash
# Override K value at runtime
export YAWL_GRPO_K_VALUE=15
export YAWL_GRPO_K_AUTO=true  # Enable auto-scaling
```

---

## Curriculum Stage Configuration

Curriculum learning enables progressive skill development by breaking training into stages.

### Stage Configuration Template

```toml
[curriculum.stages]
[[curriculum.stages]]
name = "foundational"
duration = 200
k_value = 8
temperature = 1.2
learning_rate = 0.003
epsilon = 0.4
reward_scale = 1.0
exploration_rate = 0.4
entropy_bonus = 0.1

[[curriculum.stages]]
name = "adaptive"
duration = 400
k_value = 15
temperature = 0.9
learning_rate = 0.001
epsilon = 0.2
reward_scale = 1.5
exploration_rate = 0.2
entropy_bonus = 0.05

[[curriculum.stages]]
name = "expert"
duration = 800
k_value = 25
temperature = 0.6
learning_rate = 0.0005
epsilon = 0.05
reward_scale = 2.0
exploration_rate = 0.1
entropy_bonus = 0.02
```

### Stage Transition Logic

```python
# Automatic stage progression
def should_transition_to_next_stage(current_stage, metrics):
    # Check if current stage objectives met
    if current_stage.duration > 0:
        if metrics.episodes % current_stage.duration == 0:
            return True

    # Performance-based advancement
    if current_stage.name == "foundational":
        if metrics.avg_reward > 0.6 and metrics.std_reward < 0.2:
            return True

    return False
```

---

## Temperature and Sampling Settings

Temperature controls exploration vs exploitation in policy sampling.

### Temperature Configuration

```toml
[sampling]
# Temperature schedule
temperature_schedule = [
    { episodes = 0, temperature = 1.5 },
    { episodes = 200, temperature = 1.0 },
    { episodes = 500, temperature = 0.7 },
    { episodes = 1000, temperature = 0.5 },
    { episodes = 2000, temperature = 0.3 }
]

# Adaptive temperature adjustment
adaptive_temperature = true
temperature_adjustment_rate = 0.01
min_temperature = 0.1
max_temperature = 2.0
```

### Sampling Methods

```toml
[sampling]
# Sampling strategy
method = "top_k"  # Options: "top_k", "temperature", "epsilon_greedy", "boltzmann"

# Top-K sampling
top_k = 10
top_k_temperature = 0.8

# Temperature sampling
temperature = 0.8
clamp_negative = true

# Epsilon-greedy
epsilon = 0.1
epsilon_decay = 0.995
min_epsilon = 0.01
```

---

## Timeout Configuration

Proper timeout configuration prevents training hangs and ensures responsive operation.

### Timeout Hierarchy

```toml
[timeout]
# Global timeout
max_training_time = 7200  # 2 hours

# Episode timeouts
episode_timeout = 300     # 5 minutes per episode
step_timeout = 30        # 30 seconds per step

# Batch processing
batch_timeout = 600       # 10 minutes for batch updates
eval_timeout = 120       # 2 minutes for evaluation

# Network timeouts
network_timeout = 60      # 60 seconds for remote operations
checkpoint_timeout = 300  # 5 minutes for checkpoint save
```

### Dynamic Timeout Adjustment

```python
# Adaptive timeout based on performance
def adjust_timeouts(metrics):
    if metrics.training_time > metrics.target_time * 1.5:
        # Increase timeouts for slow convergence
        return {
            'episode_timeout': min(600, current_episode_timeout * 1.2),
            'batch_timeout': min(1200, current_batch_timeout * 1.2)
        }
    elif metrics.convergence_rate > 0.1:
        # Decrease timeouts for fast convergence
        return {
            'episode_timeout': max(60, current_episode_timeout * 0.8),
            'batch_timeout': max(180, current_batch_timeout * 0.8)
        }
    return {}
```

---

## Performance Tuning

Optimize GRPO performance for different hardware and workload scenarios.

### GPU Acceleration

```toml
[performance]
# GPU configuration
gpu_enabled = true
gpu_device = 0
mixed_precision = true
gpu_memory_fraction = 0.8
gpu_growth = false

# CUDA optimization
cudnn_benchmark = true
cudnn_deterministic = false
apex_enabled = true
```

### Memory Optimization

```toml
[performance]
# Memory management
buffer_size = 50000
buffer_prune_ratio = 0.95
gradient_accumulation_steps = 4
memory_efficient_attention = true
mixed_precision = true
offload_optimizer = true
```

### Parallel Processing

```toml
[performance]
# Parallel training
num_workers = 8
pin_memory = true
prefetch_factor = 4
persistent_workers = true
worker_init_fn = "worker_init"
worker_seeds = [42, 43, 44, 45, 46, 47, 48, 49]

# Distributed training
distributed = true
backend = "nccl"
find_unused_parameters = false
```

---

## Troubleshooting

Common GRPO configuration issues and solutions.

### Training Issues

#### Problem: Convergence too slow
**Solution**:
```toml
# Adjust learning rate
[engine]
learning_rate = 0.01
gamma = 0.99
epsilon_decay = 0.99

# Increase exploration
[curriculum.stages]
[[curriculum.stages]]
name = "exploration"
duration = 500
temperature = 1.5
exploration_rate = 0.5
```

#### Problem: High variance in rewards
**Solution**:
```toml
# Add reward smoothing
[sampling]
reward_smoothing = true
smooth_window = 10
clamp_rewards = true
reward_clamp_min = -0.5
reward_clamp_max = 0.5

# Increase buffer size
[performance]
buffer_size = 100000
```

#### Problem: Memory exhaustion
**Solution**:
```toml
# Reduce memory usage
[performance]
buffer_size = 20000
gradient_accumulation_steps = 8
mixed_precision = true
offload_optimizer = true

# Prune less important experiences
[sampling]
prune_threshold = 0.3
importance_sampling = true
```

### Performance Issues

#### Problem: High CPU usage
**Solution**:
```toml
# Reduce parallelism
[performance]
num_workers = 2
persistent_workers = false

# Use simpler models
[engine]
model_complexity = "simple"
hidden_size = 128
num_layers = 2
```

#### Problem: Slow checkpointing
**Solution**:
```toml
# Optimize checkpointing
[monitoring]
save_checkpoints = true
checkpoint_interval = 50
checkpoint_compression = true
checkpoint_format = "safetensors"

# Asynchronous saving
async_checkpointing = true
```

### Configuration Validation

Validate your configuration before starting training:

```bash
# Validate rl-config.toml
yawl validate-rl-config .claude/config/rl-config.toml

# Test configuration
yawl test-rl-engine .claude/config/rl-config.toml --epochs 10

# Profile configuration
yawl profile-rl-engine .claude/config/rl-config.toml --duration 300
```

---

## Environment Variable Overrides

Override configuration at runtime using environment variables.

```bash
# Override specific settings
export YAWL_GRPO_ENABLED=true
export YAWL_GRPO_K_VALUE=20
export YAWL_GRPO_LEARNING_RATE=0.005
export YAWL_GRPO_EPSILON=0.2
export YAWL_GRPO_TEMPERATURE=0.7

# Performance tuning
export YAWL_GRPO_NUM_WORKERS=8
export YAWL_GRPO_GPU_DEVICE=0
export YAWL_GRPO_MIXED_PRECISION=true

# Monitoring
export YAWL_GRPO_LOG_LEVEL=DEBUG
export YAWL_GRPO_TENSORBOARD_DIR=/tmp/tensorboard
export YAWL_GRPO_SAVE_CHECKPOINTS=true

# Timeout adjustments
export YAWL_GRPO_EPISODE_TIMEOUT=600
export YAWL_GRPO_BATCH_TIMEOUT=1200
export YAWL_GRPO_MAX_TRAINING_TIME=10800
```

### Configuration Priority

1. **Environment variables** (highest priority)
2. **Runtime API** (`YNetRunner.configure()`)
3. **Configuration file** (`rl-config.toml`)
4. **Default values** (lowest priority)

---

## Related Documentation

- [YAWL Reinforcement Learning Overview](../concepts/rl-overview.md)
- [GRPO Algorithm Implementation](../reference/grpo-algorithm.md)
- [Performance Benchmarking](../guides/performance-benchmarks.md)
- [Monitoring and Observability](../guides/monitoring.md)
- [Advanced RL Configuration](../advanced/rl-advanced.md)

---