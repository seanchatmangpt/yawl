# Design Decisions

## Why GraalPy Instead of Jython?

We chose GraalPy over Jython for several reasons:

| Factor | GraalPy | Jython |
|--------|---------|--------|
| Python Version | 3.11+ | 2.7 |
| DSPy Compatibility | ✅ Full | ❌ None |
| Performance | Excellent | Good |
| Maintenance | Active | Minimal |
| Type System | Modern | Legacy |

## Why GEPA Instead of BootstrapFewShot?

GEPA provides advantages over standard DSPy optimization:

### Behavioral Correctness First

GEPA prioritizes behavioral footprint agreement:
- Ensures generated workflows match reference behavior
- Prevents optimization from breaking correctness
- Validates against perfect workflow patterns

### Configurable Optimization Targets

```java
enum OptimizationTarget {
    BEHAVIORAL,    // 100% footprint agreement required
    PERFORMANCE,   // Optimize execution time
    BALANCED       // Weighted combination
}
```

### Multi-Objective Optimization

GEPA balances multiple objectives:
1. **Behavioral correctness** - Footprint agreement score
2. **Performance efficiency** - Execution time, throughput
3. **Resource utilization** - Memory, CPU usage

## Why Sealed Interfaces for OptimizationTarget?

We use Java sealed interfaces for type-safe pattern matching:

```java
public sealed interface OptimizationTarget
    permits BehavioralTarget, PerformanceTarget, BalancedTarget {
    String name();
}
```

Benefits:
- **Exhaustive matching** - Compiler ensures all cases handled
- **Type safety** - Cannot create invalid targets
- **Pattern matching** - Clean switch expressions

## Why Records Instead of Classes?

Records provide:
- **Immutability** - Thread-safe by design
- **Auto-generated methods** - equals, hashCode, toString
- **Compact syntax** - Less boilerplate

```java
public record GepaOptimizationResult(
    String target,
    double score,
    Map<String, Object> behavioralFootprint,
    // ...
) {}
```

## Why MCP and A2A Together?

Both protocols serve different purposes:

| Protocol | Use Case | Consumer |
|----------|----------|----------|
| MCP | LLM tool calling | Claude, GPT-4 |
| A2A | Autonomous agents | Multi-agent systems |

Having both enables:
- **MCP**: Direct LLM integration for workflow decisions
- **A2A**: Agent orchestration for complex multi-step optimization

## Why Behavioral Footprints?

Footprints capture workflow behavior patterns:

### Three Core Relations

1. **Direct Succession (→)**: A always precedes B
2. **Concurrency (∥)**: A and B can execute in parallel
3. **Exclusivity (×)**: A and B never both execute

### Perfect Agreement

```
Generated Footprint  ==  Reference Footprint
```

When footprints match exactly, the generated workflow preserves all behavioral properties.

## Why Hot Reload?

DSPy programs can be optimized without restarting YAWL:

```java
registry.reload("worklet_selector");
```

Benefits:
- **Zero downtime** - Continuous operation
- **Fast iteration** - Quick optimization cycles
- **Production safe** - No service interruption

## Why GraalPy Context Pooling?

Creating GraalPy contexts is expensive. We pool them:

```java
private final GraalPyContextPool contextPool;

DspyProgram program = contextPool.execute(context -> {
    return loadProgram(context, name, path, className);
});
```

Benefits:
- **Reduced latency** - Reuse existing contexts
- **Resource efficiency** - Limited concurrent contexts
- **Thread safety** - Proper context isolation
