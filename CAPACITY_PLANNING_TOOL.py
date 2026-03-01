#!/usr/bin/env python3
"""
YAWL v6.0.0 Capacity Planning Tool

Calculates deployment sizing (instances, memory, CPU) based on throughput targets.

Usage:
    python3 capacity_planner.py --target-cases 1000000 --profile conservative
    python3 capacity_planner.py --target-cases 5000000 --profile moderate
    python3 capacity_planner.py --target-cases 10000000 --profile aggressive
"""

import argparse
import json
from datetime import datetime

class CapacityPlanner:
    """Calculate YAWL deployment size requirements"""

    THROUGHPUT_BASELINES = {
        "conservative": 50,
        "moderate": 100,
        "aggressive": 150,
    }

    RESOURCE_PROFILES = {
        "conservative": {
            "cpu_requests": "2",
            "cpu_limits": "4",
            "memory_requests": "4Gi",
            "memory_limits": "8Gi",
            "jvm_heap": "4g",
            "jvm_heap_max": "6g",
            "db_connections_per_pod": 20,
        },
        "moderate": {
            "cpu_requests": "4",
            "cpu_limits": "8",
            "memory_requests": "8Gi",
            "memory_limits": "16Gi",
            "jvm_heap": "6g",
            "jvm_heap_max": "12g",
            "db_connections_per_pod": 30,
        },
        "aggressive": {
            "cpu_requests": "8",
            "cpu_limits": "16",
            "memory_requests": "16Gi",
            "memory_limits": "32Gi",
            "jvm_heap": "12g",
            "jvm_heap_max": "24g",
            "db_connections_per_pod": 40,
        },
    }

    DB_SIZING = {
        "conservative": {
            "max_connections": 100,
            "shared_buffers": "4GB",
            "effective_cache_size": "12GB",
            "work_mem": "256MB",
            "storage_per_million_cases": 50,
        },
        "moderate": {
            "max_connections": 200,
            "shared_buffers": "8GB",
            "effective_cache_size": "24GB",
            "work_mem": "512MB",
            "storage_per_million_cases": 50,
        },
        "aggressive": {
            "max_connections": 400,
            "shared_buffers": "16GB",
            "effective_cache_size": "48GB",
            "work_mem": "1GB",
            "storage_per_million_cases": 50,
        },
    }

    def __init__(self, target_cases, profile="moderate"):
        self.target_cases = target_cases
        self.profile = profile.lower()

        if self.profile not in self.THROUGHPUT_BASELINES:
            raise ValueError("Unknown profile")

    def calculate_required_pods(self):
        daily_throughput = self.THROUGHPUT_BASELINES[self.profile]
        effective_throughput = daily_throughput * 0.8
        daily_case_load = self.target_cases / 30
        required_pods = max(1, int(daily_case_load / effective_throughput / 86400) + 1)
        return required_pods

    def calculate_database_sizing(self):
        db_config = self.DB_SIZING[self.profile]
        storage_gb = int((self.target_cases / 1000000) * db_config["storage_per_million_cases"])

        return {
            "max_connections": db_config["max_connections"],
            "shared_buffers": db_config["shared_buffers"],
            "effective_cache_size": db_config["effective_cache_size"],
            "work_mem": db_config["work_mem"],
            "estimated_storage_gb": storage_gb,
            "recommended_instance": self._get_rds_instance(storage_gb),
        }

    def _get_rds_instance(self, storage_gb):
        if storage_gb < 100:
            return "db.t3.large (2 vCPU, 8GB RAM)"
        elif storage_gb < 500:
            return "db.m5.xlarge (4 vCPU, 16GB RAM)"
        elif storage_gb < 1000:
            return "db.m5.2xlarge (8 vCPU, 32GB RAM)"
        else:
            return "db.r5.4xlarge (16 vCPU, 128GB RAM)"

    def calculate_monitoring(self):
        pods = self.calculate_required_pods()
        metrics_per_second = pods * 1000

        return {
            "prometheus_retention_days": 30,
            "prometheus_storage_gb": int((metrics_per_second * 86400 * 30) / (8 * 1024 ** 2)),
            "log_volume_gb_per_day": pods * 10,
            "elastic_shards": max(1, pods // 2),
            "grafana_dashboards": 5,
        }

    def generate_report(self):
        pods = self.calculate_required_pods()
        db = self.calculate_database_sizing()
        monitoring = self.calculate_monitoring()
        profile_config = self.RESOURCE_PROFILES[self.profile]

        report = f"""
╔════════════════════════════════════════════════════════════════╗
║  YAWL v6.0.0 Capacity Planning Report                          ║
╚════════════════════════════════════════════════════════════════╝

Profile:                    {self.profile.upper()}
Target Case Volume:         {self.target_cases:,} cases
Generated:                  {datetime.now().isoformat()}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. COMPUTE REQUIREMENTS (Kubernetes)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Pods Required:              {pods} instances
CPU per pod:                {profile_config['cpu_requests']} request / {profile_config['cpu_limits']} limit
Memory per pod:             {profile_config['memory_requests']} request / {profile_config['memory_limits']} limit
JVM Heap:                   {profile_config['jvm_heap']} min / {profile_config['jvm_heap_max']} max

Total Cluster Requirements:
  - CPU:                    {int(profile_config['cpu_requests']) * pods} cores
  - Memory:                 {pods * int(profile_config['memory_requests'].replace('Gi', ''))}Gi
  - Storage:                50Gi per pod

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. DATABASE REQUIREMENTS (PostgreSQL)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Max Connections:            {db['max_connections']}
DB Connections per Pod:     {profile_config['db_connections_per_pod']}
Shared Buffers:             {db['shared_buffers']}
Cache Size:                 {db['effective_cache_size']}
Work Memory:                {db['work_mem']}

Storage:
  - Estimated:              {db['estimated_storage_gb']}GB
  - Recommended Instance:   {db['recommended_instance']}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. MONITORING & OBSERVABILITY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prometheus:
  - Retention:              {monitoring['prometheus_retention_days']} days
  - Storage:                ~{monitoring['prometheus_storage_gb']}GB

Log Storage:
  - Daily Volume:           ~{monitoring['log_volume_gb_per_day']}GB/day
  - 30-day Retention:       ~{monitoring['log_volume_gb_per_day'] * 30}GB
  - Elasticsearch Shards:   {monitoring['elastic_shards']}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. PERFORMANCE TARGETS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Case Creation Latency:      <500ms (p99)
Total Throughput:           {self.THROUGHPUT_BASELINES[self.profile] * pods:,} cases/sec
GC Pause Time:              <50ms average
Error Rate:                 <0.01%
Availability:               99.9%

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. COST ESTIMATION (AWS)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

EKS (3 nodes m5.xlarge):    ~${pods * 0.192 * 730:.0f}/month
RDS PostgreSQL:             ~$1,500/month
Storage & Backups:          ~$150/month

Total Estimated:            ~${pods * 0.192 * 730 + 1650:.0f}/month

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

"""
        return report

def main():
    parser = argparse.ArgumentParser(description="YAWL Capacity Planning Calculator")
    parser.add_argument("--target-cases", type=int, required=True)
    parser.add_argument("--profile", choices=["conservative", "moderate", "aggressive"], default="moderate")
    args = parser.parse_args()

    planner = CapacityPlanner(args.target_cases, args.profile)
    print(planner.generate_report())

if __name__ == "__main__":
    main()
