# OCEL Test Data Sets

This directory contains realistic OCEL (Object-Centric Event Logs) test data sets for testing process mining and conformance checking applications.

## Overview

All files follow the OCEL 2.0 standard format and contain realistic scenarios with edge cases, failures, and variations to ensure robust testing.

## Files

### 1. `ocel-with-failures.json`
**Focus**: Failed activities and cancelled events
- **Events**: 8 events with realistic failure scenarios
- **Key Features**:
  - Payment processing failures (insufficient funds, timeouts)
  - Order cancellations due to fraud detection
  - Document rejections
  - Failed shipping due to address validation
  - Multiple payment attempts with retry logic
- **Objects**: 9 objects (orders, customers, documents)
- **Use Case**: Testing failure handling, exception management, and recovery processes

### 2. `ocel-concurrent-events.json`
**Focus**: Concurrent processing and race conditions
- **Events**: 15 events with overlapping timestamps
- **Key Features**:
  - Multiple system triggers at the same millisecond
  - Concurrent price updates and inventory checks
  - Race conditions between cart operations
  - Simultaneous user actions on same products
  - Overlapping notifications and processing
- **Objects**: 8 objects (products, users, carts, orders)
- **Use Case**: Testing concurrent processing, race condition handling, and system synchronization

### 3. `ocel-edge-cases.json`
**Focus**: Edge cases and malformed data
- **Events**: 12 events with various edge cases
- **Key Features**:
  - NULL values in critical attributes
  - Empty strings and arrays
  - Missing optional fields
  - Undefined values (converted to null)
  - Zero values and empty collections
  - Invalid timestamps (0000-00-00T00:00:00Z)
  - Malformed numeric values
- **Objects**: 17 objects with incomplete/missing data
- **Use Case**: Testing data validation, null handling, and edge case resilience

### 4. `ocel-variable-timing.json`
**Focus**: Realistic timing patterns and variations
- **Events**: 15 events with irregular timing
- **Key Features**:
  - Non-uniform timestamp intervals
  - Business hours and weekend gaps
  - Lunch hour and evening surge patterns
  - Night maintenance windows
  - Morning rush hour patterns
  - Variable session durations
  - Realistic user behavior timing
- **Objects**: 15 objects representing various system components
- **Use Case**: Testing time-based analytics, performance monitoring, and business logic timing

### 5. `ocel-stress-test.json`
**Focus**: High-volume processing scenarios
- **Events**: 20 events with extensive data
- **Key Features**:
  - Large object hierarchies (deep nesting)
  - Complex attribute structures with metadata
  - High user and product diversity
  - Extensive session tracking
  - Rich user profile data
  - Complex product catalogs
  - Multi-dimensional analytics data
- **Objects**: 14 objects with comprehensive attributes
- **Use Case**: Testing performance, memory usage, and large-scale data processing

## OCEL 2.0 Format

All files follow the standard OCEL 2.0 structure:
```json
{
  "ocel": {
    "version": "2.0",
    "global-id": "unique-identifier",
    "events": [...],
    "objects": [...],
    "objectTypes": {...}
  }
}
```

## Usage

### Loading Data
```python
import json

# Load OCEL data
with open('ocel-with-failures.json', 'r') as f:
    ocel_data = json.load(f)

# Access events
events = ocel_data['ocel']['events']

# Access objects
objects = ocel_data['ocel']['objects']

# Access object types
object_types = ocel_data['ocel']['objectTypes']
```

### Processing Events
```python
for event in events:
    print(f"Event {event['id']}: {event['activity']} at {event['timestamp']}")
    for obj_id in event['objects']:
        obj = next(o for o in objects if o['id'] == obj_id)
        print(f"  Related to {obj['type']}: {obj['id']}")
```

## Validation

All files have been validated as JSON compliant:
```bash
python3 -c "import json; json.load(open('filename.json'))"
```

## Quality Assurance

- **Not Perfect Data**: Contains realistic imperfections, failures, and edge cases
- **Realistic Scenarios**: Based on actual business processes
- **Diverse Workloads**: Different timing patterns, failure rates, and data quality
- **Test Coverage**: Designed to stress-test various aspects of OCEL processing

## Contributing

When adding new test files:
1. Follow OCEL 2.0 standard format
2. Include realistic scenarios with edge cases
3. Validate JSON structure before committing
4. Document the specific use case for the test data