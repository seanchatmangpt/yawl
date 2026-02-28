# A2A Skills Reference

## Overview

Agent-to-Agent (A2A) skills enable autonomous agents to interact with GEPA optimization.

## GepaA2ASkill

### Constructor

```java
public GepaA2ASkill(
    String skillId,
    DspyProgramRegistry registry,
    String optimizationTarget
)
```

| Parameter | Type | Description |
|------------|------|-------------|
| `skillId` | String | Unique skill identifier |
| `registry` | DspyProgramRegistry | DSPy program registry |
| `optimizationTarget` | String | Target: `behavioral`, `performance`, or `balanced` |

### Methods

#### execute

```java
public SkillResult execute(SkillRequest request)
```

Executes the GEPA optimization skill.

**Parameters:**
- `request` - The skill request containing program name and inputs

**Returns:**
- `SkillResult` containing optimization results or error

**Example:**
```java
SkillRequest request = new SkillRequest(
    "gepa_behavioral_optimizer",
    Map.of(
        "program", "worklet_selector",
        "inputs", Map.of("context", "high priority case"),
        "optimization_target", "behavioral"
    )
);

SkillResult result = skill.execute(request);
```

## Available Skills

| Skill ID | Target | Description |
|----------|--------|-------------|
| `gepa_behavioral_optimizer` | behavioral | Optimizes for behavioral correctness |
| `gepa_performance_optimizer` | performance | Optimizes for execution speed |
| `gepa_balanced_optimizer` | balanced | Balanced optimization |

## Skill Result Schema

### Success Response

```json
{
  "status": "success",
  "data": {
    "optimization_target": "behavioral",
    "program": "worklet_selector",
    "score": 0.0,
    "footprint_agreement": 1.0,
    "behavioral_footprint": {
      "direct_succession": ["A", "B", "C"],
      "concurrency": [["B", "C"]],
      "exclusivity": []
    },
    "performance_metrics": {
      "execution_time_ms": 150,
      "throughput": 8.5
    },
    "optimization_history": [
      {
        "iteration": 1,
        "score": 0.85,
        "timestamp": "2026-02-28T12:00:00Z"
      }
    ]
  }
}
```

### Error Response

```json
{
  "status": "error",
  "error": {
    "code": "PROGRAM_NOT_FOUND",
    "message": "DSPy program 'unknown_program' not found in registry"
  }
}
```

## Error Codes

| Code | Description | Resolution |
|------|-------------|------------|
| `PROGRAM_NOT_FOUND` | Program not in registry | Load program first |
| `INVALID_TARGET` | Unknown optimization target | Use behavioral/performance/balanced |
| `OPTIMIZATION_FAILED` | GEPA optimization failed | Check inputs and training data |
| `EXECUTION_ERROR` | Python execution error | Check program syntax |
