# JTBD 3 Test: OC-DECLARE Constraints Discovery

## Overview
This test validates the discovery of object-centric DECLARE constraints from an OCEL event log using the YAWL Process Mining Bridge.

## Test Purpose
- Verify OC-DECLARE constraint discovery functionality
- Test constraint output format validation
- Handle feature gaps gracefully when not implemented

## Test Flow
1. Import OCEL JSON from `/tmp/jtbd/input/pi-sprint-ocel.json`
2. Optionally apply `slim_link_ocel` optimization if available
3. Discover OC-DECLARE constraints using `discover_oc_declare` or equivalent
4. Write results to `/tmp/jtbd/output/pi-sprint-constraints.json`

## Test Assertions
✅ Output file exists
✅ Output is array with ≥ 1 constraint
✅ Each constraint has "type" (or "constraint_type") key
✅ Each constraint has "activity_a" (or "template") key
✅ Each constraint has "support" or "confidence" key with value 0.0-1.0
✅ At least one constraint mentions activities from input OCEL

## Fallback Behavior
When OC-DECLARE is not implemented:
- Writes placeholder JSON explaining feature gap
- Returns `{error, {not_implemented, discover_oc_declare}}`

## Expected Output Format
```json
{
  "timestamp": 1704112000000,
  "source_file": "/tmp/jtbd/input/pi-sprint-ocel.json",
  "constraint_count": 3,
  "constraints": [
    {
      "type": "Exclusive Choice",
      "activity_a": "Task_A",
      "activity_b": "Task_B",
      "support": 0.75,
      "confidence": 0.90
    }
  ]
}
```

## Feature Placeholder Format
```json
{
  "timestamp": 1704112000000,
  "status": "feature_gap",
  "missing_function": "discover_oc_declare",
  "message": "OC-DECLARE constraint discovery is not implemented",
  "expected_output_format": { ... },
  "implementation_status": "Not Implemented",
  "suggested_alternatives": ["discover_alpha_plus", "discover_dfg"]
}
```

## Running the Test

### Method 1: Via Makefile
```bash
cd /Users/sac/yawl/yawl-erlang-bridge/yawl-erlang-bridge/test
make jtbd
```

### Method 2: Direct Compilation
```bash
erl -pa ebin -pa _build/default/lib/*/ebin \
  -eval 'c(jtbd_runner), jtbd_runner:run_all(), halt(0)'
```

### Method 3: Individual Test
```bash
erl -pa ebin -pa _build/default/lib/*/ebin \
  -eval 'c(jtbd_3_constraints), jtbd_3_constraints:run(), halt(0)'
```

## Dependencies
- `process_mining_bridge` module (NIF library)
- `jsx` library for JSON encoding
- Sample OCEL input file at `/tmp/jtbd/input/pi-sprint-ocel.json`

## Sample OCEL File
If the input file doesn't exist, the test creates a sample OCEL with:
- 3 events (CreateOrder, ProcessOrder, ShipOrder)
- 1 object (Order)
- Standard OCEL JSON format

## Return Value
- `{ok, #{output => OutputPath, constraint_count => Count}}` on success
- `{error, Reason}` on failure

## Error Cases
- Missing input file → Creates sample data
- Missing `discover_oc_declare` function → Writes placeholder
- Invalid constraint format → Returns validation error
- File write failures → Returns I/O error

## Integration
This test is integrated into the JTBD test suite via `jtbd_runner.erl`.