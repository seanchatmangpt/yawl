#!/usr/bin/env python3
"""
YAWL Cost Optimization Calculator
Analyzes and optimizes cloud infrastructure costs across AWS, GCP, and Azure.
"""

import json
import sys
from dataclasses import dataclass, asdict
from typing import Dict, List, Optional, Tuple
from datetime import datetime, timedelta
import re


@dataclass
class ComputeInstance:
    """Represents a compute instance with pricing information."""
    name: str
    instance_type: str
    cloud: str  # aws, gcp, azure
    region: str
    vcpus: int
    memory_gb: float
    hourly_cost: float
    monthly_hours: int = 730
    current_usage_percent: float = 100.0

    def monthly_cost(self) -> float:
        """Calculate current monthly cost."""
        return self.hourly_cost * self.monthly_hours

    def on_demand_annual_cost(self) -> float:
        """Calculate annual on-demand cost."""
        return self.monthly_cost() * 12

    def reserved_instance_cost(self, term_months: int = 12, utilization_percent: int = 100) -> float:
        """Calculate reserved instance cost."""
        # RI discounts typically range from 25-70% depending on term and prepayment
        discount_map = {
            12: {100: 0.36},   # 1-year, all upfront
            36: {100: 0.55},   # 3-year, all upfront
            12: {33: 0.30},    # 1-year, no upfront
            36: {33: 0.47},    # 3-year, no upfront
        }

        discount = discount_map.get((term_months, utilization_percent), 0.30)
        annual_cost = self.on_demand_annual_cost()
        years = term_months / 12

        return annual_cost * years * (1 - discount)

    def spot_instance_cost(self) -> float:
        """Calculate spot instance cost (typically 70% discount)."""
        return self.on_demand_annual_cost() * 0.30

    def on_demand_monthly_cost(self) -> float:
        """Calculate monthly on-demand cost at current utilization."""
        return self.monthly_cost() * (self.current_usage_percent / 100)


@dataclass
class StorageVolume:
    """Represents storage volumes and their costs."""
    name: str
    cloud: str
    region: str
    volume_type: str  # gp3, io2, st1, standard, etc.
    size_gb: float
    hourly_cost: float
    monthly_hours: int = 730

    def monthly_cost(self) -> float:
        """Calculate monthly storage cost."""
        return self.hourly_cost * self.monthly_hours

    def optimized_cost(self, target_volume_type: str, cost_reduction: float = 0.25) -> float:
        """Calculate cost after optimization (volume type change)."""
        return self.monthly_cost() * (1 - cost_reduction)


@dataclass
class DatabaseInstance:
    """Represents database instances and their costs."""
    name: str
    cloud: str
    region: str
    engine: str  # mysql, postgres, mongodb, etc.
    instance_class: str
    multi_az: bool
    monthly_cost: float

    def single_az_cost(self) -> float:
        """Calculate cost for single AZ deployment."""
        return self.monthly_cost * 0.5 if self.multi_az else self.monthly_cost

    def read_replica_savings(self, num_replicas: int) -> float:
        """Calculate savings from using read replicas."""
        # Read replicas typically cost 50% of master instance
        return self.monthly_cost * num_replicas * 0.5


class CostAnalyzer:
    """Main cost analysis engine for YAWL infrastructure."""

    def __init__(self):
        self.instances: List[ComputeInstance] = []
        self.volumes: List[StorageVolume] = []
        self.databases: List[DatabaseInstance] = []

    def add_instance(self, instance: ComputeInstance) -> None:
        """Add a compute instance for analysis."""
        self.instances.append(instance)

    def add_storage(self, volume: StorageVolume) -> None:
        """Add a storage volume for analysis."""
        self.volumes.append(volume)

    def add_database(self, database: DatabaseInstance) -> None:
        """Add a database instance for analysis."""
        self.databases.append(database)

    def total_on_demand_cost(self) -> Tuple[float, float]:
        """Calculate total current on-demand costs (monthly, annual)."""
        compute_monthly = sum(inst.on_demand_monthly_cost() for inst in self.instances)
        storage_monthly = sum(vol.monthly_cost() for vol in self.volumes)
        database_monthly = sum(db.monthly_cost for db in self.databases)

        total_monthly = compute_monthly + storage_monthly + database_monthly
        total_annual = total_monthly * 12

        return total_monthly, total_annual

    def total_optimized_cost(self) -> Tuple[float, float]:
        """Calculate total costs after optimization strategies."""
        # Compute optimization: mix of RIs and spots
        compute_monthly = 0
        for inst in self.instances:
            # Use RIs for baseline load, spots for variable load
            baseline = inst.monthly_cost() * 0.7  # 70% baseline
            variable = inst.monthly_cost() * 0.3  # 30% variable

            ri_cost = baseline * 0.36  # 36% of on-demand for 1-year RI
            spot_cost = variable * 0.30  # 30% of on-demand for spot

            compute_monthly += ri_cost + spot_cost

        # Storage optimization: right-sizing and volume type changes
        storage_monthly = sum(vol.optimized_cost(vol.volume_type) for vol in self.volumes)

        # Database optimization: single AZ + read replicas
        database_monthly = 0
        for db in self.databases:
            # Single AZ + read replicas for scale-out reads
            base = db.single_az_cost()
            replicas = db.read_replica_savings(2)  # 2 read replicas
            database_monthly += base + replicas

        total_monthly = compute_monthly + storage_monthly + database_monthly
        total_annual = total_monthly * 12

        return total_monthly, total_annual

    def get_recommendations(self) -> List[Dict]:
        """Generate cost optimization recommendations."""
        recommendations = []

        # Compute recommendations
        for inst in self.instances:
            if inst.current_usage_percent < 50:
                recommendations.append({
                    "resource": inst.name,
                    "type": "compute",
                    "priority": "high",
                    "recommendation": f"Downsize instance {inst.instance_type} or use auto-scaling",
                    "potential_savings": inst.monthly_cost() * 0.4,
                    "implementation": "Consider smaller instance type or enable auto-scaling"
                })

            # Reserved instance recommendation
            annual_cost = inst.on_demand_annual_cost()
            ri_cost = inst.reserved_instance_cost(12)
            savings = annual_cost - ri_cost

            if savings > 0:
                recommendations.append({
                    "resource": inst.name,
                    "type": "compute",
                    "priority": "medium",
                    "recommendation": f"Purchase 1-year reserved instance",
                    "potential_savings": savings,
                    "implementation": "Buy 1-year all-upfront reserved instance"
                })

        # Storage recommendations
        for vol in self.volumes:
            if vol.volume_type in ["io2", "io1"]:
                optimized = vol.optimized_cost(vol.volume_type, 0.30)
                savings = vol.monthly_cost() - optimized

                recommendations.append({
                    "resource": vol.name,
                    "type": "storage",
                    "priority": "medium",
                    "recommendation": f"Migrate {vol.volume_type} to gp3 for cost savings",
                    "potential_savings": savings * 12,
                    "implementation": "Create snapshot and restore to gp3 volume"
                })

        # Database recommendations
        for db in self.databases:
            if db.multi_az:
                savings = db.monthly_cost * 0.5 * 12
                recommendations.append({
                    "resource": db.name,
                    "type": "database",
                    "priority": "low",
                    "recommendation": f"Consider single-AZ for non-critical databases",
                    "potential_savings": savings,
                    "implementation": "Evaluate HA requirements and downgrade if possible"
                })

        # Sort by potential savings
        recommendations.sort(key=lambda x: x["potential_savings"], reverse=True)
        return recommendations

    def generate_report(self) -> Dict:
        """Generate comprehensive cost analysis report."""
        monthly_on_demand, annual_on_demand = self.total_on_demand_cost()
        monthly_optimized, annual_optimized = self.total_optimized_cost()

        return {
            "timestamp": datetime.now().isoformat(),
            "current_costs": {
                "monthly": round(monthly_on_demand, 2),
                "annual": round(annual_on_demand, 2),
            },
            "optimized_costs": {
                "monthly": round(monthly_optimized, 2),
                "annual": round(annual_optimized, 2),
            },
            "potential_savings": {
                "monthly": round(monthly_on_demand - monthly_optimized, 2),
                "annual": round(annual_on_demand - annual_optimized, 2),
                "percentage": round(((annual_on_demand - annual_optimized) / annual_on_demand * 100), 2)
                    if annual_on_demand > 0 else 0,
            },
            "resources_analyzed": {
                "compute_instances": len(self.instances),
                "storage_volumes": len(self.volumes),
                "databases": len(self.databases),
            },
            "recommendations": self.get_recommendations(),
        }


def load_sample_infrastructure() -> CostAnalyzer:
    """Load sample YAWL infrastructure for analysis."""
    analyzer = CostAnalyzer()

    # Compute instances (typical YAWL deployment)
    analyzer.add_instance(ComputeInstance(
        name="yawl-api-server-1",
        instance_type="t3.xlarge",
        cloud="aws",
        region="us-east-1",
        vcpus=4,
        memory_gb=16,
        hourly_cost=0.1664,
        current_usage_percent=45
    ))

    analyzer.add_instance(ComputeInstance(
        name="yawl-worker-1",
        instance_type="c5.2xlarge",
        cloud="aws",
        region="us-east-1",
        vcpus=8,
        memory_gb=16,
        hourly_cost=0.34,
        current_usage_percent=65
    ))

    analyzer.add_instance(ComputeInstance(
        name="yawl-db-replica",
        instance_type="r5.xlarge",
        cloud="aws",
        region="us-east-1",
        vcpus=4,
        memory_gb=32,
        hourly_cost=0.252,
        current_usage_percent=30
    ))

    # Storage volumes
    analyzer.add_storage(StorageVolume(
        name="yawl-data-io2",
        cloud="aws",
        region="us-east-1",
        volume_type="io2",
        size_gb=500,
        hourly_cost=0.0325  # $0.0325/GB/month for io2
    ))

    analyzer.add_storage(StorageVolume(
        name="yawl-backup-standard",
        cloud="aws",
        region="us-east-1",
        volume_type="standard",
        size_gb=1000,
        hourly_cost=0.05
    ))

    # Databases
    analyzer.add_database(DatabaseInstance(
        name="yawl-postgres-master",
        cloud="aws",
        region="us-east-1",
        engine="postgres",
        instance_class="db.r5.large",
        multi_az=True,
        monthly_cost=800
    ))

    return analyzer


def main():
    """Main entry point for cost calculator."""
    analyzer = load_sample_infrastructure()

    # Generate and display report
    report = analyzer.generate_report()

    print("=" * 70)
    print("YAWL COST OPTIMIZATION REPORT")
    print("=" * 70)
    print(f"\nGenerated: {report['timestamp']}")
    print(f"\nResources Analyzed:")
    print(f"  - Compute Instances: {report['resources_analyzed']['compute_instances']}")
    print(f"  - Storage Volumes: {report['resources_analyzed']['storage_volumes']}")
    print(f"  - Databases: {report['resources_analyzed']['databases']}")

    print(f"\nCurrent On-Demand Costs:")
    print(f"  - Monthly: ${report['current_costs']['monthly']:,.2f}")
    print(f"  - Annual: ${report['current_costs']['annual']:,.2f}")

    print(f"\nOptimized Costs (with RI + Spot + Right-sizing):")
    print(f"  - Monthly: ${report['optimized_costs']['monthly']:,.2f}")
    print(f"  - Annual: ${report['optimized_costs']['annual']:,.2f}")

    print(f"\nPotential Savings:")
    print(f"  - Monthly: ${report['potential_savings']['monthly']:,.2f}")
    print(f"  - Annual: ${report['potential_savings']['annual']:,.2f}")
    print(f"  - Percentage: {report['potential_savings']['percentage']:.1f}%")

    print(f"\nTop Recommendations:")
    for i, rec in enumerate(report['recommendations'][:5], 1):
        print(f"\n{i}. [{rec['priority'].upper()}] {rec['recommendation']}")
        print(f"   Resource: {rec['resource']}")
        print(f"   Potential Annual Savings: ${rec.get('potential_savings', 0):,.2f}")
        print(f"   Implementation: {rec['implementation']}")

    print("\n" + "=" * 70)

    # Output full report as JSON to stdout for piping
    if len(sys.argv) > 1 and sys.argv[1] == "--json":
        print(json.dumps(report, indent=2))

    return 0


if __name__ == "__main__":
    sys.exit(main())
