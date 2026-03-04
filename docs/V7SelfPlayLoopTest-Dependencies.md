# V7SelfPlayLoopTest Dependencies and Structure

## Test Overview

The V7SelfPlayLoopTest is a Chicago TDD integration test that verifies the complete YAWL v7 self-play design loop. The test includes **inner loop verification** that confirms composition count increases after gap closure actions.

## Test Structure

### Core Components

1. **V7SelfPlayLoopTest.java** - Main test class with all test methods
2. **GapAnalysisEngine.java** - Discovers and prioritizes capability gaps using WSJF methodology
3. **GapClosureService.java** - Executes gap closure actions and validates results

### Test Methods Added for Inner Loop Verification

#### 1. `testInnerLoopIncreasesCompositionCount()`
- **Purpose**: Verify that gap closure increases composition count
- **Flow**:
  1. Get initial composition count
  2. Discover gaps via `gapAnalysisEngine.discoverGaps()`
  3. Prioritize gaps via `gapAnalysisEngine.prioritizeGaps()`
  4. Get top gap via `gapAnalysisEngine.getTopGaps(1)`
  5. Close gap via `gapClosureService.closeGap()`
  6. Verify composition count increased
- **Assertion**: `assertTrue(after > before)`

#### 2. `testMultipleGapClosuresIncreaseCompositionCount()`
- **Purpose**: Verify multiple gap closures monotonically increase composition count
- **Flow**: Repeats gap closure 3 times, verifying count increases each time
- **Assertion**: Final count >= initial count + 3

#### 3. `testGapClosureRecordsAreStoredAndRetrievable()`
- **Purpose**: Verify audit trail of gap closures is properly stored
- **Validation**: Checks record storage, retrieval, and statistics
- **Assertions**: Record count, retrieval by gap, and statistics correctness

## Dependencies

### Real Implementations (No Mocks/Stubs)

#### GapAnalysisEngine Methods
- `discoverGaps()` - Returns List<CapabilityGap>
- `prioritizeGaps(gaps)` - Returns List<GapPriority>
- `getTopGaps(n)` - Returns List<GapPriority> (top N gaps)
- `initialize()` - Initializes QLever engine
- `shutdown()` - Cleans up resources

#### GapClosureService Methods
- `closeGap(priority)` - Returns GapClosureRecord
- `getCompositionCount()` - Returns int (number of valid compositions)
- `getClosureRecords()` - Returns List<GapClosureRecord>
- `getClosureStats()` - Returns Map<String, Object>
- `initialize()` - Initializes QLever engine
- `shutdown()` - Cleans up resources

### Data Models

#### V7Gap (enum)
- Defines the 7 known v7 gaps:
  - ASYNC_A2A_GOSSIP
  - MCP_SERVERS_SLACK_GITHUB_OBS
  - DETERMINISTIC_REPLAY_BLAKE3
  - THREADLOCAL_YENGINE_PARALLELIZATION
  - SHACL_COMPLIANCE_SHAPES
  - BYZANTINE_CONSENSUS
  - BURIED_ENGINES_MCP_A2A_WIRING

#### GapAnalysisEngine Records
- `CapabilityGap(String gapId, String requiredType, double demandScore, double complexity, String description)`
- `GapPriority(String priorityId, CapabilityGap gap, double wsjfScore, int rank, ...)`

#### GapClosureService Records
- `GapClosureRecord(String closureId, V7Gap gap, String actionTaken, boolean success, int compositionBefore, int compositionAfter, ...)`

### QLever Integration

#### Required SPARQL Queries
1. **queries/valid-compositions.sparql** - Already exists, finds valid 2-hop pipelines
2. **queries/composition-count.sparql** - Created, counts valid pipelines
3. **queries/validate-gap-closure.sparql** - Created, verifies gap closure success

#### QLever Engine
- `QLeverEmbeddedSparqlEngine` - Real SPARQL engine integration
- `QLeverFfiException` - Exception handling for QLever operations
- `QLeverResult` - Query result wrapper

## Key Integration Points

### 1. Composition Count Measurement
- Uses `gapClosureService.getCompositionCount()`
- Queries QLever for `sim:OptimalPipeline` instances
- Returns integer count of valid capability pipelines

### 2. Gap Closure Workflow
```java
// Discovery
List<CapabilityGap> gaps = gapAnalysisEngine.discoverGaps();

// Prioritization
List<GapPriority> priorities = gapAnalysisEngine.prioritizeGaps(gaps);

// Selection
GapPriority top = gapAnalysisEngine.getTopGaps(1).get(0);

// Closure
GapClosureRecord closure = gapClosureService.closeGap(top);
```

### 3. Validation Chain
- Gap closure action applied to QLever knowledge graph
- Composition count measured before/after closure
- Success/failure recorded in audit trail
- Statistics updated and retrievable

## Test Environment Requirements

### Mandatory Dependencies
- YAWL Engine runtime
- QLever SPARQL engine with YAWL data loaded
- Real ZAIOrchestrator for agent communication
- All V7GapProposalService implementations

### Configuration
- QLever data files must contain YAWL workflow definitions
- SPARQL queries must be accessible in `queries/` directory
- Z.AI API key must be set for agent communication

## Chicago TDD Compliance

### No Mocks or Stubs
- All implementations use real objects
- GapAnalysisEngine uses actual QLever queries
- GapClosureService applies real enhancements
- YAWL runtime and Z.AI framework fully exercised

### Error Handling
- All methods throw `QLeverFfiException` on failure
- Test expects and handles these exceptions
- No silent fallbacks or empty returns

### Completeness Verification
- Tests verify 3 key properties of inner loop:
  1. Single gap closure increases composition count
  2. Multiple closures monotonically increase count
  3. Audit trail properly records all actions

## Running the Tests

```bash
# Run all V7SelfPlayLoopTest methods
mvn test -Dtest=V7SelfPlayLoopTest

# Run specific inner loop test
mvn test -Dtest=V7SelfPlayLoopTest#testInnerLoopIncreasesCompositionCount
```

## Troubleshooting

### Common Issues
1. **QLever not initialized**: Ensure QLever engine is started and accessible
2. **SPARQL query errors**: Check query syntax and data availability
3. **Composition count not increasing**: Verify capability enhancements are being applied
4. **Test timeout**: Check Z.AI API connectivity and response times

### Debug Commands
```bash
# Check QLever status
curl -X POST http://localhost<port>/status

# Test SPARQL query directly
curl -X POST -d "query=SELECT * WHERE { ?s ?p ?o }" http://localhost<port>/sparql

# Check YAWL engine status
java -cp yawl-engine.jar org.yawlfoundation.yawl.engine.YEngine --status
```

## Future Enhancements

1. **Performance Metrics**: Add timing and throughput measurements
2. **Regression Testing**: Add automated regression for composition count
3. **Load Testing**: Verify behavior under multiple simultaneous closures
4. **Failure Scenarios**: Test partial closure and recovery mechanisms