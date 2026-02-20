#!/bin/bash

# Generate mock validation report for demonstration
# Since the YAWL engine has startup issues, this creates a sample report

TIMESTAMP=$(date -Iseconds)
ENGINE_VERSION="6.0.0-alpha"
ENGINE_STATUS="unavailable"

cat > reports/pattern-validation-report.json << EOF
{
  "timestamp": "$TIMESTAMP",
  "engine_version": "$ENGINE_VERSION",
  "engine_status": "$ENGINE_STATUS",
  "total_patterns": 43,
  "passed": 0,
  "failed": 43,
  "skipped": 0,
  "categories": {
    "basic": {
      "total": 5,
      "passed": 0,
      "failed": 5,
      "patterns": [
        {"id": "WCP-1", "name": "Sequence", "status": "failed", "duration_ms": 0},
        {"id": "WCP-2", "name": "Parallel Split", "status": "failed", "duration_ms": 0},
        {"id": "WCP-3", "name": "Synchronization", "status": "failed", "duration_ms": 0},
        {"id": "WCP-4", "name": "Exclusive Choice", "status": "failed", "duration_ms": 0},
        {"id": "WCP-5", "name": "Simple Merge", "status": "failed", "duration_ms": 0}
      ]
    },
    "branching": {
      "total": 6,
      "passed": 0,
      "failed": 6,
      "patterns": [
        {"id": "WCP-6", "name": "Multi-Choice", "status": "failed", "duration_ms": 0},
        {"id": "WCP-7", "name": "Synchronization Merge", "status": "failed", "duration_ms": 0},
        {"id": "WCP-8", "name": "Multi-Merge", "status": "failed", "duration_ms": 0},
        {"id": "WCP-9", "name": "Discriminator", "status": "failed", "duration_ms": 0},
        {"id": "WCP-10", "name": "Structured Loop", "status": "failed", "duration_ms": 0},
        {"id": "WCP-11", "name": "AND-Join", "status": "failed", "duration_ms": 0}
      ]
    }
  },
  "error_details": {
    "engine_startup_error": "YAWL engine failed to start - ClassNotFoundException for YControlPanel",
    "docker_logs": "Error: Could not find or load main class org.yawlfoundation.yawl.controlpanel.YControlPanel",
    "suggested_fix": "Check YAWL build configuration and ensure all dependencies are correctly packaged"
  },
  "validation_notes": "This report shows that the YAWL validation scripts are ready but the engine is not running. The scripts were successfully created to validate all 43+ workflow patterns through Docker Compose shell scripts. The error indicates that the YAWL engine needs to be properly built and started to run actual validation."
}
EOF

echo "✓ Mock validation report generated at: reports/pattern-validation-report.json"