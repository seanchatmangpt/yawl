# Configure OpenSage Memory System

**Version**: v6.0.0-GA
**Scope**: Pattern-based memory retention for YAWL workflow execution

---

## OpenSage Architecture Overview

OpenSage is YAWL's pattern-based memory system that retains and reuses successful workflow patterns. It learns from past executions to optimize future performance through pattern recognition and retention.

### Key Components
- **Pattern Extractor**: Identifies recurring workflow patterns
- **Memory Storage**: Hierarchical pattern storage with TTL
- **Query Engine**: Fast pattern matching and retrieval
- **Retention Policy**: Adaptive pattern preservation
- **Monitoring**: Memory usage and effectiveness tracking

### Integration Points
- `YEngine`: Pattern extraction during execution
- `YNetRunner`: Query memory for pattern matching
- `YWorkItem`: Pattern-based task scheduling
- `YMonitor`: Memory metrics collection

---

## Memory Storage Configuration

Configure OpenSage storage backends and policies for optimal pattern retention.

### Storage Backends

```toml
# OpenSage Memory Configuration for YAWL v6.0.0-GA
# Path: .claude/config/opensage.toml

[storage]
# Primary storage configuration
backend = "hybrid"  # Options: "memory", "disk", "hybrid"
cache_size = 10000
max_patterns = 50000
max_memory_usage = "1GB"
disk_quota = "10GB"
compression_enabled = true

# Hybrid configuration
[storage.hybrid]
in_memory_ratio = 0.3  # 30% in memory, 70% on disk
l1_cache_size = 1000
l2_cache_size = 5000
eviction_policy = "lru"  # lru, lfu, fifo, adaptive

# Disk storage
[storage.disk]
path = "./opensage_patterns"
compression = "lz4"
encryption = false
backup_enabled = true
backup_interval = "1h"
backup_retention = "7d"

# Cloud storage
[storage.cloud]
enabled = false
provider = "s3"  # s3, gcs, azure
bucket = "yawl-opensage"
prefix = "patterns"
sync_interval = "5m"
credentials_path = "~/.aws/credentials"
```

### Memory Layout

```toml
[memory_layout]
# Pattern hierarchy
hierarchy_levels = ["workload", "workflow", "task", "resource"]
workload_patterns = 1000
workflow_patterns = 5000
task_patterns = 15000
resource_patterns = 20000

# Pattern indexing
index_type = "hnsw"  # Options: "hnsw", "ivf", "flat"
index_dimension = 128
index_ef_construction = 200
index_ef_search = 50

# Pattern metadata
metadata_enabled = true
metadata_fields = [
    "created_at",
    "last_used",
    "success_rate",
    "avg_latency",
    "resource_usage",
    "context_hash"
]
```

---

## Pattern Retention Policies

Configure how OpenSage retains and prunes patterns based on their value.

### Retention Policy Configuration

```toml
[retention]
# Basic retention policies
max_age = "30d"
min_success_rate = 0.8
min_usage_count = 5
min_benefit_score = 0.7

# Adaptive retention
adaptive_enabled = true
retrain_interval = "24h"
retrain_threshold = 0.1  # Retrain if accuracy drops 10%

# Pattern scoring
[retention.scoring]
success_rate_weight = 0.4
frequency_weight = 0.3
benefit_weight = 0.2
freshness_weight = 0.1

# Scoring thresholds
high_score_threshold = 0.8
medium_score_threshold = 0.6
low_score_threshold = 0.4
```

### Pattern Lifecycle

```toml
[lifecycle]
# Pattern states
states = ["candidate", "active", "dormant", "archived", "deleted"]

# State transitions
transitions = [
    { from = "candidate", to = "active", condition = "min_usage=3 AND min_success=0.7" },
    { from = "active", to = "dormant", condition = "unused_days>7 AND benefit<0.5" },
    { from = "dormant", to = "active", condition = "usage_count>2" },
    { from = "dormant", to = "archived", condition = "unused_days>30" },
    { from = "archived", to = "deleted", condition = "archived_days>90" }
]

# Transition timing
[candidate]
duration = "1h"
evaluation_interval = "5m"

[active]
evaluation_interval = "1h"
prune_if_unused_days = 14

[dormant]
prune_if_unused_days = 60
resurrection_probability = 0.3

[archived]
retention_period = "90d"
compress_immediately = true
```

### Expiration and Pruning

```toml
[expiration]
# Time-based expiration
max_age = "30d"
stale_threshold = "7d"

# Usage-based expiration
min_usage = 5
usage_decay_rate = 0.95

# Performance-based expiration
performance_threshold = 0.6
performance_window = "7d"

# Pruning schedule
[pruning]
enabled = true
schedule = "0 2 * * *"  # Daily at 2 AM
batch_size = 1000
dry_run = false
notify_on_prune = true
```

---

## Query and Retrieval Settings

Configure how OpenSage searches and retrieves patterns for matching.

### Query Configuration

```toml
[query]
# Query strategy
strategy = "hybrid"  # exact, semantic, hybrid
similarity_threshold = 0.8
max_results = 10
timeout = "1s"

# Search configuration
[search]
exact_match_enabled = true
semantic_search_enabled = true
fuzzy_search_enabled = true
fuzzy_threshold = 0.7

# Caching
[cache]
enabled = true
size = 1000
ttl = "5m"
eviction_policy = "lru"

# Query optimization
[optimization]
parallel_search = true
batch_size = 50
pre_filter_enabled = true
pre_filter_fields = ["workflow_type", "resource_type", "complexity"]
```

### Similarity Scoring

```toml
[similarity]
# Similarity components
structural_weight = 0.5
semantic_weight = 0.3
temporal_weight = 0.2

# Structural similarity
[structural]
task_matching = "exact"  # exact, fuzzy, semantic
branch_matching = true
dependency_matching = true
resource_matching = true

# Semantic similarity
[semantic]
embedding_model = "all-MiniLM-L6-v2"
embedding_dimension = 384
semantic_threshold = 0.7

# Temporal similarity
[temporal]
time_window = "7d"
recency_weight = 0.5
seasonality_adjustment = true
```

### Pattern Matching

```toml
[matching]
# Matching modes
modes = ["exact", "partial", "adaptive"]
default_mode = "adaptive"

# Exact matching
[exact]
strict = true
match_all_tasks = true
match_all_resources = true

# Partial matching
[partial]
min_match_ratio = 0.7
max_mismatch_count = 2
allow_resource_substitution = true
allow_task_skipping = true

# Adaptive matching
[adaptive]
learning_rate = 0.1
context_window = "24h"
success_threshold = 0.8
fallback_to_partial = true
```

---

## Memory Monitoring

Configure monitoring and alerting for OpenSage performance and health.

### Metrics Collection

```toml
[monitoring]
# Metrics collection
enabled = true
interval = "30s"
retention = "7d"

# Metrics categories
metrics = [
    "pattern_count",
    "memory_usage",
    "query_latency",
    "match_rate",
    "retention_rate",
    "compression_ratio"
]

# Alert thresholds
[alerts]
enabled = true

[alerts.pattern_count]
warning = 40000
critical = 45000

[alerts.memory_usage]
warning = "800MB"
critical = "900MB"

[alerts.query_latency]
warning = "100ms"
critical = "500ms"

[alerts.match_rate]
warning = 0.7
critical = 0.5

[alerts.retention_rate]
warning = 0.8
critical = 0.6
```

### Performance Monitoring

```toml
[performance]
# Performance tracking
track_queries = true
track_matches = true
track_failures = true
track_latency = true

# Performance targets
[targets]
query_latency_p95 = "50ms"
query_latency_p99 = "200ms"
match_accuracy = 0.95
throughput = "1000qps"

# Performance analysis
[analysis]
correlation_analysis = true
pattern_analysis = true
trend_analysis = true
anomaly_detection = true
```

### Dashboard Configuration

```toml
[dashboard]
# Dashboard setup
enabled = true
port = 8080
host = "localhost"
auth_enabled = false
update_interval = "5s"

# Dashboard views
[dashboard.views.overview]
enabled = true
refresh_interval = "10s"

[dashboard.views.patterns]
enabled = true
max_display = 100

[dashboard.views.queries]
enabled = true
tail_length = 50

[dashboard.views.performance]
enabled = true
time_window = "1h"
```

---

## Backup and Restore

Configure backup policies and restore procedures for OpenSage data.

### Backup Configuration

```toml
[backup]
# Backup settings
enabled = true
schedule = "0 3 * * *"  # Daily at 3 AM
retention = "30d"
compression = true
encryption = false
verify_after_backup = true

# Backup destinations
destinations = [
    { type = "local", path = "./backups" },
    { type = "s3", bucket = "yawl-backups", prefix = "opensage" }
]

# Backup content
[backup.content]
include_metadata = true
include_patterns = true
include_index = true
include_stats = true
include_logs = true

# Backup verification
[backup.verification]
checksum_verification = true
restore_test = true
report_errors = true
```

### Restore Configuration

```toml
[restore]
# Restore settings
verify_integrity = true
validate_patterns = true
test_queries = true
dry_run = false

# Restore modes
modes = ["full", "incremental", "point-in-time"]
default_mode = "full"

# Restore validation
[validation]
pattern_count_check = true
query_response_check = true
performance_benchmark = true
consistency_check = true
```

### Disaster Recovery

```toml
[disaster_recovery]
# Recovery strategy
strategy = "multi-region"  # single-region, multi-region, cloud-only

# Failover configuration
[failover]
enabled = true
detection_timeout = "30s"
automatic_failover = true
health_check_interval = "10s"

# Multi-region setup
[regions.primary]
endpoint = "primary-region.example.com"
weight = 0.7

[regions.secondary]
endpoint = "secondary-region.example.com"
weight = 0.3

[regions.backup]
endpoint = "backup-region.example.com"
weight = 0.0  # Standby
```

---

## Configuration Examples

### Production Configuration

```toml
# Production OpenSage Configuration
[storage]
backend = "hybrid"
cache_size = 50000
max_patterns = 200000
max_memory_usage = "4GB"
disk_quota = "50GB"
compression_enabled = true

[storage.hybrid]
in_memory_ratio = 0.2
l1_cache_size = 5000
l2_cache_size = 20000
eviction_policy = "adaptive"

[retention]
max_age = "90d"
min_success_rate = 0.85
min_usage_count = 10
adaptive_enabled = true

[monitoring]
enabled = true
interval = "30s"
retention = "30d"
alerts.enabled = true

[backup]
enabled = true
schedule = "0 2 * * *"
retention = "60d"
encryption = true
verify_after_backup = true
```

### Development Configuration

```toml
# Development OpenSage Configuration
[storage]
backend = "memory"
cache_size = 1000
max_patterns = 5000
max_memory_usage = "512MB"
compression_enabled = false

[retention]
max_age = "7d"
min_success_rate = 0.6
min_usage_count = 3
adaptive_enabled = false

[monitoring]
enabled = false

[backup]
enabled = false
```

---

## Environment Variable Overrides

```bash
# Storage configuration
export YAWL_OPENSAGE_BACKEND=hybrid
export YAWL_OPENSAGE_CACHE_SIZE=10000
export YAWL_OPENSAGE_MAX_PATTERNS=50000
export YAWL_OPENSAGE_MEMORY_LIMIT=1GB

# Retention policies
export YAWL_OPENSAGE_MAX_AGE=30d
export YAWL_OPENSAGE_MIN_SUCCESS_RATE=0.8
export YAWL_OPENSAGE_MIN_USAGE_COUNT=5
export YAWL_OPENSAGE_ADAPTIVE_ENABLED=true

# Query settings
export YAWL_OPENSAGE_QUERY_STRATEGY=hybrid
export YAWL_OPENSAGE_SIMILARITY_THRESHOLD=0.8
export YAWL_OPENSAGE_MAX_RESULTS=10
export YAWL_OPENSAGE_QUERY_TIMEOUT=1s

# Monitoring
export YAWL_OPENSAGE_MONITORING_ENABLED=true
export YAWL_OPENSAGE_MONITORING_INTERVAL=30s
export YAWL_OPENSAGE_DASHBOARD_PORT=8080
export YAWL_OPENSAGE_ALERTS_ENABLED=true

# Backup
export YAWL_OPENSAGE_BACKUP_ENABLED=true
export YAWL_OPENSAGE_BACKUP_SCHEDULE="0 3 * * *"
export YAWL_OPENSAGE_BACKUP_RETENTION=30d
```

---

## Related Documentation

- [OpenSage Architecture](../concepts/opensage-architecture.md)
- [Pattern Matching Algorithms](../reference/pattern-matching.md)
- [Memory Performance Tuning](../guides/memory-performance.md)
- [Backup and Recovery Procedures](../operations/backup-recovery.md)
- [OpenSage API Reference](../api/opensage.md)
- [Monitoring and Observability](../guides/monitoring.md)