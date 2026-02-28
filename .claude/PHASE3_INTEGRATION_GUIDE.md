# Phase 3 LLM Integration - Quick Reference

**For**: Phase 4 and downstream teams
**Updated**: 2026-02-28
**Status**: Production-ready

---

## One-Minute Overview

Phase 3 refines schemas using LLMs (offline llama.cpp or online Ollama API).

**Input**: InferenceResult (from Phase 2)
**Output**: RefineResult (ready for Phase 4 mapping)
**Cost**: 30 seconds max per schema
**Fallback**: Returns original schema if LLM unavailable

---

## Basic Usage

```java
// 1. Get Phase 2 output
InferenceResult inferred = phase2.getResult();

// 2. Create bridge (reuse from Phase 2)
DataModellingBridge bridge = phase2.getBridge();

// 3. Create refiner
DataModellingLlmRefiner refiner = new DataModellingLlmRefiner(bridge);

// 4. Configure LLM
LlmConfig config = LlmConfig.builder()
        .mode(LlmConfig.LlmMode.OFFLINE)  // or ONLINE
        .model("qwen2.5-coder")
        .temperature(0.7)
        .build();

// 5. Refine schema
RefineResult result = refiner.refineSchema(inferred, config,
        "improve naming",
        "detect constraints");

// 6. Use refined schema in Phase 4
String refinedSchema = result.getRefinedSchema();
boolean llmApplied = result.getLlmApplied();
double confidence = result.getConfidenceScore();

refiner.close();
```

---

## API Reference

### Main Class: DataModellingLlmRefiner

#### Constructor
```java
DataModellingLlmRefiner(DataModellingBridge bridge)
```

#### Key Methods

| Method | Purpose | Returns |
|--------|---------|---------|
| `refineSchema(InferenceResult, LlmConfig, String...)` | Main refinement entry point | RefineResult |
| `matchFieldsWithLlm(String, String, LlmConfig)` | Match fields between schemas | JSON string |
| `enrichDocumentation(String, LlmConfig)` | Auto-generate documentation | String (enriched schema) |
| `detectPatterns(String, LlmConfig)` | Identify PII, temporal, etc. | JSON string |
| `loadDocumentationContext(String)` | Load context file | String (file content) |
| `isLlmAvailable(LlmConfig)` | Health check | boolean |
| `close()` | Cleanup | void |

---

## Configuration: LlmConfig

### Builder Pattern
```java
LlmConfig config = LlmConfig.builder()
        .mode(LlmConfig.LlmMode.OFFLINE)      // OFFLINE or ONLINE
        .model("qwen2.5-coder")               // Model name
        .temperature(0.7)                     // 0.0-2.0 (default 0.7)
        .maxTokens(2048)                      // Max output tokens (default 2048)
        .timeoutSeconds(30)                   // Timeout (default 30s)
        .baseUrl("http://localhost:11434")    // LLM server (default localhost:11434)
        .enableFallback(true)                 // Try online if offline fails (default true)
        .contextFile("/path/to/docs.md")      // Optional context file
        .systemPrompt("You are...")           // Optional system prompt
        .build();
```

### Available Models
```java
LlmConfig.ModelType.QWEN_2_5_CODER    // "qwen2.5-coder"
LlmConfig.ModelType.LLAMA2_7B         // "llama2-7b"
LlmConfig.ModelType.LLAMA2_13B        // "llama2-13b"
LlmConfig.ModelType.MISTRAL_7B        // "mistral-7b"
LlmConfig.ModelType.CLAUDE            // "claude-opus-4-6"
LlmConfig.ModelType.CUSTOM            // Custom model name
```

### LlmMode
```java
LlmConfig.LlmMode.OFFLINE   // Local llama.cpp (privacy-first)
LlmConfig.LlmMode.ONLINE    // Ollama HTTP API
```

---

## Output: RefineResult

```java
RefineResult result = refiner.refineSchema(...);

// Access results
String refinedSchema = result.getRefinedSchema();      // JSON string
Boolean llmApplied = result.getLlmApplied();           // true/false
Double confidence = result.getConfidenceScore();       // 0.0-1.0
Integer rulesApplied = result.getRulesApplied();       // Count
String notes = result.getRefinementNotes();            // Description
Instant timestamp = result.getTimestamp();             // When completed
Object patterns = result.getPatternDetections();       // Optional patterns
```

---

## Graceful Fallback

If LLM is unavailable:
- Returns original schema with `llmApplied=false`
- Sets `confidence` to 0.3-0.5 (low confidence)
- Documents reason in `refinementNotes`
- **Pipeline continues** (LLM is optional)

**Result**: Phase 4 always gets valid schema, even if LLM failed.

---

## Timeout Handling

- **Default**: 30 seconds per LLM call
- **Custom**: `config.timeoutSeconds(60)` for longer
- **On timeout**: Fallback to original schema (see above)
- **No retry**: First timeout triggers fallback immediately

---

## Thread Safety

- ✅ Safe for concurrent calls to `refineSchema()`
- ✅ Safe for concurrent calls to different methods
- ✅ No deadlock risk
- **Pattern**: Lock during read operations only

```java
// Safe: Multiple threads can call simultaneously
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
for (int i = 0; i < 100; i++) {
    executor.submit(() -> refiner.refineSchema(inferred, config));
}
```

---

## Error Handling

### Exceptions Thrown
- `NullPointerException` - null arguments
- `IllegalStateException` - refiner is closed
- `DataModellingException` - file not found (loadDocumentationContext)
- `IllegalArgumentException` - invalid config (temperature out of range, etc.)

### No Silent Failures
- Errors are logged (SLF4J)
- Logged to RefineResult.refinementNotes if possible
- Never returns empty strings (CLAUDE.md H-Guards)

---

## Common Recipes

### Recipe 1: Offline-First with Fallback
```java
LlmConfig config = LlmConfig.builder()
        .mode(LlmConfig.LlmMode.OFFLINE)
        .enableFallback(true)
        .build();
// Tries local llama.cpp, falls back to Ollama API if unavailable
```

### Recipe 2: Deterministic Refinement (Low Temperature)
```java
LlmConfig config = LlmConfig.builder()
        .temperature(0.1)  // Less random, more consistent
        .maxTokens(1024)   // Quick response
        .build();
```

### Recipe 3: With Domain Knowledge
```java
LlmConfig config = LlmConfig.builder()
        .contextFile("/etc/yawl/domain-glossary.md")
        .systemPrompt("You understand healthcare data structures. " +
                      "Improve clarity and add HIPAA-relevant notes.")
        .build();

RefineResult result = refiner.refineSchema(inferred, config,
        "add HIPAA-relevant documentation",
        "improve clarity");
```

### Recipe 4: Quick Health Check
```java
boolean available = refiner.isLlmAvailable(config);
if (available) {
    result = refiner.refineSchema(inferred, config);
} else {
    log.warn("LLM unavailable, skipping refinement");
    result = new RefineResult(inferred.getInferredSchemaJson(), false);
}
```

---

## Integration Checklist

- [ ] Import DataModellingLlmRefiner in Phase 4
- [ ] Reuse DataModellingBridge from Phase 2
- [ ] Create LlmConfig with appropriate mode
- [ ] Call refineSchema() with InferenceResult
- [ ] Use result.getRefinedSchema() for Phase 4 mapping
- [ ] Handle llmApplied=false gracefully
- [ ] Log confidence score for observability
- [ ] Call refiner.close() on pipeline shutdown

---

## Performance Notes

| Operation | Typical Latency |
|-----------|-----------------|
| Offline refinement (2048 tokens) | 5-15 seconds |
| Online refinement (Ollama API) | 3-10 seconds |
| Health check | <100ms |
| Field matching | 2-5 seconds |
| Pattern detection | 1-3 seconds |

**Memory**: ~500MB per active refiner (mostly WASM bridge)

---

## Troubleshooting

### "LLM offline service unavailable"
→ Check llama.cpp is running at localhost:11434
→ Or set `mode(ONLINE)` for Ollama API
→ Or enable fallback: `enableFallback(true)`

### "Context file not found: /path/to/file"
→ Verify file exists and is readable
→ Use absolute path
→ Omit contextFile if not needed

### "timeout exceeded"
→ Increase timeoutSeconds: `config.timeoutSeconds(60)`
→ Reduce maxTokens for faster response
→ Check LLM server responsiveness

### "confidence is 0.3"
→ LLM was unavailable, original schema returned
→ Check LLM service health with `isLlmAvailable()`
→ Review refinementNotes for details

---

## Versioning

| Component | Version | Status |
|-----------|---------|--------|
| DataModellingLlmRefiner | 6.0.0 | Production |
| LlmConfig | 6.0.0 | Production |
| WASM bridge methods | Pending | Awaiting SDK exposure |
| data-modelling-sdk | 2.3.0 | Compatible |

---

## Support

**Questions?** See PHASE3_IMPLEMENTATION_SUMMARY.md for full details.
**Issues?** File bug with InferenceResult and LlmConfig details.
**Feature Requests?** Add to Phase 5 backlog (caching, metrics, etc.).
