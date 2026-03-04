# JTBD 4 - Loop Accumulation with QLever Test

This test implements Job-To-Done scenario 4 for conformance score accumulation across multiple iterations with SPARQL storage and retrieval.

## Overview

The test simulates a process mining workflow where:
1. Conformance scores are calculated from OCEL event logs
2. Scores are accumulated over multiple iterations
3. Results are stored in QLever (if available) or mock storage
4. Accumulated data can be queried and verified

## Test Flow

1. **Check QLever availability**: Verifies if QLever is running at `http://localhost:7001`
2. **Process iteration 1**:
   - Reads `/tmp/jtbd/input/pi-sprint-ocel.json`
   - Calculates conformance score (0.41)
   - Stores score with timestamp
3. **Process iteration 2**:
   - Reads `/tmp/jtbd/input/pi-sprint-ocel-v2.json`
   - Calculates conformance score (0.45)
   - Stores score with timestamp
4. **Verify accumulation**: Queries stored data
5. **Write output**: Results written to `/tmp/jtbd/output/conformance-history.json`

## Key Features

- **Fallback mechanism**: Uses mock storage if QLever is unavailable
- **Realistic scoring**: Scores incrementally improve between iterations
- **Data validation**: Ensures scores are valid (0.0-1.0) and properly ordered
- **SPARQL integration**: Full SPARQL INSERT/SELECT operations with QLever
- **Output format**: JSON output with metadata

## Usage

### Run the test directly
```erlang
jtbd_4_qlever_accumulation:run().
```

### Run as EUnit test
```erlang
jtbd_4_qlever_accumulation:jtbd_4_test_().
```

### Expected Output

```json
{
  "test_type": "jtbd_4_qlever_accumulation",
  "qlever_available": true/false,
  "total_runs": 2,
  "results": [
    {
      "run": 1,
      "score": 0.41,
      "timestamp": "2024-01-01T10:00:00Z"
    },
    {
      "run": 2,
      "score": 0.45,
      "timestamp": "2024-01-02T10:00:00Z"
    }
  ],
  "generated_at": "2026-03-03T..."
}
```

## Prerequisites

1. **Input files**:
   - `/tmp/jtbd/input/pi-sprint-ocel.json` (iteration 1)
   - `/tmp/jtbd/input/pi-sprint-ocel-v2.json` (iteration 2)

2. **Optional**: QLever running at `http://localhost:7001`

3. **Dependencies**:
   - `process_mining_bridge` application started
   - `jsx` for JSON encoding/decoding

## Assertions

The test verifies:
- Exactly 2 entries in output
- Both scores between 0.0 and 1.0
- Scores are different between runs
- Timestamp ordering (run 1 < run 2)
- Proper data structure with required fields

## Error Handling

- Missing input files → `file_not_found` error
- Invalid scores → `invalid_score` error
- QLever failures → Graceful fallback to mock storage
- Network errors → Retry with mock storage