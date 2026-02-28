# MCP Tools Reference

Complete reference for YAWL DSPy MCP tools.

## dspy_execute_program

Execute a saved DSPy program with inputs.

### Input Schema

```json
{
  "type": "object",
  "required": ["program", "inputs"],
  "properties": {
    "program": {
      "type": "string",
      "description": "Program name: worklet_selector, resource_router, anomaly_forensics, runtime_adaptation"
    },
    "inputs": {
      "type": "object",
      "description": "Program-specific inputs matching the DSPy signature"
    }
  }
}
```

### Output Schema

```json
{
  "type": "object",
  "properties": {
    "[output_fields]": {
      "description": "Program-specific output fields from DSPy signature"
    },
    "_metadata": {
      "type": "object",
      "properties": {
        "execution_time_ms": { "type": "number" },
        "total_time_ms": { "type": "number" },
        "cache_hit": { "type": "boolean" },
        "program": { "type": "string" }
      }
    }
  }
}
```

### Example

**Request:**
```json
{
  "program": "worklet_selector",
  "inputs": {
    "workflow_description": "Process loan application",
    "case_context": "Amount: $50000, Credit score: 720"
  }
}
```

**Response:**
```json
{
  "worklet_id": "loan_standard",
  "rationale": "Standard loan process for amounts under $100k with good credit",
  "confidence": 0.94,
  "_metadata": {
    "execution_time_ms": 312,
    "total_time_ms": 318,
    "cache_hit": false,
    "program": "worklet_selector"
  }
}
```

### Error Responses

**Program Not Found:**
```json
{
  "error": true,
  "message": "Program not found: unknown_program. Available programs: [worklet_selector, resource_router]"
}
```

**Invalid Inputs:**
```json
{
  "error": true,
  "message": "Parameter 'inputs' is required and must not be empty"
}
```

---

## dspy_list_programs

List all available DSPy programs.

### Input Schema

```json
{
  "type": "object",
  "properties": {}
}
```

### Output Schema

```json
{
  "type": "object",
  "properties": {
    "programs": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name": { "type": "string" },
          "version": { "type": "string" },
          "predictor_count": { "type": "integer" },
          "optimizer": { "type": "string" },
          "validation_score": { "type": "number" },
          "serialized_at": { "type": "string" }
        }
      }
    },
    "total_count": { "type": "integer" }
  }
}
```

### Example

**Request:**
```json
{}
```

**Response:**
```json
{
  "programs": [
    {
      "name": "worklet_selector",
      "version": "1.0.0",
      "predictor_count": 3,
      "optimizer": "GEPA",
      "validation_score": 0.95,
      "serialized_at": "2026-02-28T00:00:00Z"
    },
    {
      "name": "resource_router",
      "version": "1.2.0",
      "predictor_count": 2,
      "optimizer": "BootstrapFewShot",
      "validation_score": 0.88,
      "serialized_at": "2026-02-27T12:00:00Z"
    }
  ],
  "total_count": 2
}
```

---

## dspy_get_program_info

Get detailed information about a specific DSPy program.

### Input Schema

```json
{
  "type": "object",
  "required": ["program"],
  "properties": {
    "program": {
      "type": "string",
      "description": "Program name to get info for"
    }
  }
}
```

### Output Schema

```json
{
  "type": "object",
  "properties": {
    "name": { "type": "string" },
    "version": { "type": "string" },
    "dspy_version": { "type": "string" },
    "source_hash": { "type": "string" },
    "optimizer": { "type": "string" },
    "validation_score": { "type": "number" },
    "serialized_at": { "type": "string" },
    "loaded_at": { "type": "string" },
    "predictors": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name": { "type": "string" },
          "input_fields": { "type": "array", "items": { "type": "string" } },
          "output_fields": { "type": "array", "items": { "type": "string" } },
          "few_shot_example_count": { "type": "integer" }
        }
      }
    }
  }
}
```

---

## dspy_reload_program

Hot-reload a DSPy program from disk.

### Input Schema

```json
{
  "type": "object",
  "required": ["program"],
  "properties": {
    "program": {
      "type": "string",
      "description": "Program name to reload"
    }
  }
}
```

### Output Schema

```json
{
  "type": "object",
  "properties": {
    "status": { "type": "string", "enum": ["reloaded"] },
    "name": { "type": "string" },
    "source_hash": { "type": "string" },
    "predictor_count": { "type": "integer" },
    "loaded_at": { "type": "string" }
  }
}
```

---

## GEPA Tools

### gepa_optimize_workflow

Generate a perfectly optimized workflow using GEPA.

**Input Schema:**
```json
{
  "type": "object",
  "required": ["workflow_spec", "optimization_target"],
  "properties": {
    "workflow_spec": {
      "type": "object",
      "description": "Workflow specification in YAWL format"
    },
    "optimization_target": {
      "type": "string",
      "enum": ["performance", "behavioral", "balanced"],
      "description": "Primary optimization objective"
    },
    "constraints": {
      "type": "object",
      "description": "Optional constraints"
    },
    "reference_patterns": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Reference patterns: sequential, parallel, choice, loop"
    }
  }
}
```

### gepa_validate_footprint

Validate behavioral footprint agreement.

**Input Schema:**
```json
{
  "type": "object",
  "required": ["original_workflow", "optimized_workflow"],
  "properties": {
    "original_workflow": { "type": "object" },
    "optimized_workflow": { "type": "object" },
    "validation_mode": {
      "type": "string",
      "enum": ["strict", "balanced", "lenient"]
    }
  }
}
```

### gepa_score_workflow

Score workflow against reference patterns.

**Input Schema:**
```json
{
  "type": "object",
  "required": ["workflow", "reference_patterns"],
  "properties": {
    "workflow": { "type": "object" },
    "reference_patterns": {
      "type": "array",
      "items": { "type": "string" }
    },
    "scoring_weights": {
      "type": "object",
      "properties": {
        "performance": { "type": "number", "minimum": 0, "maximum": 1 },
        "maintainability": { "type": "number", "minimum": 0, "maximum": 1 },
        "compliance": { "type": "number", "minimum": 0, "maximum": 1 }
      }
    }
  }
}
```

## Error Codes

| Code | Description |
|------|-------------|
| `PROGRAM_NOT_FOUND` | Requested program does not exist |
| `INVALID_INPUTS` | Input validation failed |
| `EXECUTION_ERROR` | Python execution failed |
| `TIMEOUT_ERROR` | Execution exceeded timeout |
| `CACHE_ERROR` | Cache operation failed |
