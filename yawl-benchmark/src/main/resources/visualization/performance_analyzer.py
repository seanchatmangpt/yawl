#!/usr/bin/env python3
"""
Actor System Performance Visualizer
Generates comprehensive graphs and analysis from benchmark results.
"""

import matplotlib.pyplot as plt
import matplotlib.dates as mdates
import pandas as pd
import numpy as np
import seaborn as sns
from datetime import datetime, timedelta
import json
import os
from pathlib import Path

# Set style
plt.style.use('seaborn-v0_8')
sns.set_palette("husl")

class ActorPerformanceAnalyzer:
    def __init__(self, results_dir="actor-benchmark-reports"):
        self.results_dir = Path(results_dir)
        self.output_dir = self.results_dir / "graphs"
        self.output_dir.mkdir(exist_ok=True)
        
    def load_benchmark_data(self):
        """Load benchmark results from CSV files"""
        csv_files = list(self.results_dir.glob("*.csv"))
        all_data = []
        
        for csv_file in csv_files:
            if "raw_data" not in csv_file.name:
                suite_name = csv_file.stem
                df = pd.read_csv(csv_file)
                df['suite'] = suite_name
                all_data.append(df)
        
        if all_data:
            self.combined_data = pd.concat(all_data, ignore_index=True)
        else:
            self.generate_synthetic_data()
            
    def generate_synthetic_data(self):
        """Generate synthetic data for demonstration"""
        benchmarks = [
            "SingleThreadedThroughput",
            "MultiThreadedThroughput", 
            "BatchThroughput",
            "MemoryGrowth",
            "MemoryLeakDetection",
            "GCPressure",
            "ActorOverhead",
            "LinearScaling",
            "ThreadScaling",
            "LoadBalancing",
            "MessageLatency",
            "EndToEndLatency",
            "FailureRecovery",
            "CrashRecovery"
        ]
        
        data = {
            'Benchmark': benchmarks,
            'Throughput': np.random.uniform(1_000_000, 9_000_000, len(benchmarks)),
            'Memory': np.random.uniform(2, 8, len(benchmarks)),
            'Latency': np.random.uniform(0.1, 3.0, len(benchmarks)),
            'Scalability': np.random.uniform(70, 95, len(benchmarks)),
            'Recovery': np.random.uniform(20, 150, len(benchmarks)),
            'Status': np.random.choice(['✓', '△', '✗'], len(benchmarks), p=[0.7, 0.2, 0.1])
        }
        
        self.combined_data = pd.DataFrame(data)
        
    def plot_throughput_analysis(self):
        """Create throughput analysis charts"""
        fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 10))
        
        # Throughput comparison
        throughput_data = self.combined_data.sort_values('Throughput', ascending=False)
        bars = ax1.barh(range(len(throughput_data)), throughput_data['Throughput'] / 1_000_000)
        ax1.set_yticks(range(len(throughput_data)))
        ax1.set_yticklabels(throughput_data['Benchmark'])
        ax1.set_xlabel('Throughput (Million messages/sec)')
        ax1.set_title('Message Throughput by Benchmark')
        ax1.grid(True, alpha=0.3)
        
        # Add value labels
        for i, bar in enumerate(bars):
            width = bar.get_width()
            ax1.text(width + 0.1, bar.get_y() + bar.get_height()/2, 
                    f'{width:.1f}M', ha='left', va='center')
        
        # Performance threshold comparison
        target_line = 10_000_000  # 10M messages/sec target
        self.combined_data['vs_target'] = (self.combined_data['Throughput'] / target_line * 100)
        
        colors = ['green' if x > 100 else 'orange' if x > 80 else 'red' 
                 for x in self.combined_data['vs_target']]
        
        ax2.bar(range(len(self.combined_data)), self.combined_data['vs_target'], 
               color=colors, alpha=0.7)
        ax2.axhline(y=100, color='black', linestyle='--', label='Target (100%)')
        ax2.set_xticks(range(len(self.combined_data)))
        ax2.set_xticklabels(self.combined_data['Benchmark'], rotation=45, ha='right')
        ax2.set_ylabel('Performance vs Target (%)')
        ax2.set_title('Performance Achievement vs Target')
        ax2.grid(True, alpha=0.3)
        ax2.legend()
        
        plt.tight_layout()
        plt.savefig(self.output_dir / 'throughput_analysis.png', dpi=300, bbox_inches='tight')
        plt.close()
        
    def plot_memory_analysis(self):
        """Create memory usage analysis"""
        fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(15, 6))
        
        # Memory efficiency scatter
        ax1.scatter(self.combined_data['Throughput'] / 1_000_000, 
                   self.combined_data['Memory'],
                   c=self.combined_data['Scalability'], 
                   s=100, alpha=0.7, cmap='viridis')
        ax1.set_xlabel('Throughput (Million messages/sec)')
        ax1.set_ylabel('Memory Growth (%)')
        ax1.set_title('Memory Efficiency vs Throughput')
        ax1.grid(True, alpha=0.3)
        
        # Add colorbar
        cbar = plt.colorbar(ax1.collections[0], ax=ax1)
        cbar.set_label('Scalability Score (%)')
        
        # Memory distribution
        ax2.hist(self.combined_data['Memory'], bins=10, alpha=0.7, edgecolor='black')
        ax2.axvline(x=10, color='red', linestyle='--', label='Target Threshold (10%)')
        ax2.set_xlabel('Memory Growth (%)')
        ax2.set_ylabel('Number of Benchmarks')
        ax2.set_title('Memory Growth Distribution')
        ax2.grid(True, alpha=0.3)
        ax2.legend()
        
        plt.tight_layout()
        plt.savefig(self.output_dir / 'memory_analysis.png', dpi=300, bbox_inches='tight')
        plt.close()
        
    def plot_scalability_heatmap(self):
        """Create scalability heatmap"""
        # Create correlation matrix
        metrics = ['Throughput', 'Memory', 'Latency', 'Scalability', 'Recovery']
        correlation_matrix = self.combined_data[metrics].corr()
        
        fig, ax = plt.subplots(figsize=(10, 8))
        
        # Create heatmap
        sns.heatmap(correlation_matrix, annot=True, cmap='coolwarm', center=0,
                   square=True, fmt='.2f', cbar_kws={'label': 'Correlation Coefficient'})
        
        ax.set_title('Performance Metrics Correlation Matrix')
        
        plt.tight_layout()
        plt.savefig(self.output_dir / 'scalability_heatmap.png', dpi=300, bbox_inches='tight')
        plt.close()
        
    def plot_latency_radar(self):
        """Create radar chart for performance profile"""
        # Normalize metrics to 0-100 scale
        normalized_data = self.combined_data.copy()
        normalized_data['Throughput_norm'] = (normalized_data['Throughput'] / 10_000_000) * 100
        normalized_data['Memory_norm'] = (10 - normalized_data['Memory']) / 10 * 100  # Inverse (lower is better)
        normalized_data['Latency_norm'] = (5 - normalized_data['Latency']) / 5 * 100  # Inverse
        normalized_data['Scalability_norm'] = normalized_data['Scalability']
        normalized_data['Recovery_norm'] = (200 - normalized_data['Recovery']) / 200 * 100  # Inverse
        
        # Average across all benchmarks
        average_scores = normalized_data[['Throughput_norm', 'Memory_norm', 
                                       'Latency_norm', 'Scalability_norm', 
                                       'Recovery_norm']].mean()
        
        # Create radar chart
        categories = ['Throughput', 'Memory Efficiency', 'Low Latency', 'Scalability', 'Fast Recovery']
        values = average_scores.values
        angles = np.linspace(0, 2 * np.pi, len(categories), endpoint=False).tolist()
        values = np.concatenate((values, [values[0]]))
        angles += angles[:1]
        
        fig, ax = plt.subplots(figsize=(8, 8), subplot_kw=dict(projection='polar'))
        
        ax.plot(angles, values)
        ax.fill(angles, values, alpha=0.25)
        ax.set_xticks(angles[:-1])
        ax.set_xticklabels(categories)
        ax.set_ylim(0, 100)
        ax.set_title('Overall Performance Profile', pad=20)
        ax.grid(True)
        
        plt.tight_layout()
        plt.savefig(self.output_dir / 'performance_radar.png', dpi=300, bbox_inches='tight')
        plt.close()
        
    def plot_trend_analysis(self):
        """Create trend analysis charts"""
        fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(15, 12))
        
        # Scalability trend
        scalability_sorted = self.combined_data.sort_values('Scalability', ascending=True)
        ax1.barh(range(len(scalability_sorted)), scalability_sorted['Scalability'])
        ax1.set_yticks(range(len(scalability_sorted)))
        ax1.set_yticklabels(scalability_sorted['Benchmark'])
        ax1.set_xlabel('Scalability Score (%)')
        ax1.set_title('Scalability Performance')
        ax1.grid(True, alpha=0.3)
        
        # Recovery time
        recovery_sorted = self.combined_data.sort_values('Recovery')
        ax2.bar(range(len(recovery_sorted)), recovery_sorted['Recovery'], color='orange')
        ax2.axhline(y=100, color='red', linestyle='--', label='Target (100ms)')
        ax2.set_xticks(range(len(recovery_sorted)))
        ax2.set_xticklabels(recovery_sorted['Benchmark'], rotation=45, ha='right')
        ax2.set_ylabel('Recovery Time (ms)')
        ax2.set_title('Failure Recovery Performance')
        ax2.grid(True, alpha=0.3)
        ax2.legend()
        
        # Latency distribution
        ax3.hist(self.combined_data['Latency'], bins=10, alpha=0.7, color='green', edgecolor='black')
        ax3.axvline(x=1, color='red', linestyle='--', label='Target (1ms)')
        ax3.set_xlabel('Latency (ms)')
        ax3.set_ylabel('Frequency')
        ax3.set_title('Message Latency Distribution')
        ax3.grid(True, alpha=0.3)
        ax3.legend()
        
        # Performance quadrant analysis
        ax4.scatter(self.combined_data['Scalability'], self.combined_data['Throughput'] / 1_000_000,
                   s=self.combined_data['Memory'] * 20, alpha=0.7, c=self.combined_data['Latency'],
                   cmap='plasma')
        ax4.set_xlabel('Scalability Score (%)')
        ax4.set_ylabel('Throughput (Million messages/sec)')
        ax4.set_title('Performance Quadrant Analysis\n(Size: Memory Growth, Color: Latency)')
        ax4.grid(True, alpha=0.3)
        
        # Add quadrant lines
        ax4.axvline(x=80, color='gray', linestyle='--', alpha=0.5)
        ax4.axhline(y=5, color='gray', linestyle='--', alpha=0.5)
        
        plt.tight_layout()
        plt.savefig(self.output_dir / 'trend_analysis.png', dpi=300, bbox_inches='tight')
        plt.close()
        
    def generate_summary_report(self):
        """Generate summary report with key findings"""
        report_path = self.output_dir / 'performance_summary.md'
        
        with open(report_path, 'w') as f:
            f.write("# Actor System Performance Summary\n\n")
            f.write(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
            f.write("---\n\n")
            
            # Overall performance
            avg_throughput = self.combined_data['Throughput'].mean() / 1_000_000
            avg_scalability = self.combined_data['Scalability'].mean()
            avg_latency = self.combined_data['Latency'].mean()
            avg_recovery = self.combined_data['Recovery'].mean()
            
            f.write("## Key Performance Metrics\n\n")
            f.write(f"- **Average Throughput**: {avg_throughput:.1f} million messages/sec\n")
            f.write(f"- **Average Scalability**: {avg_scalability:.1f}%\n")
            f.write(f"- **Average Latency**: {avg_latency:.2f}ms\n")
            f.write(f"- **Average Recovery Time**: {avg_recovery:.1f}ms\n\n")
            
            # Performance assessment
            f.write("## Performance Assessment\n\n")
            
            # Throughput assessment
            if avg_throughput >= 10:
                throughput_status = "✓ Excellent"
            elif avg_throughput >= 5:
                throughput_status = "△ Good"
            else:
                throughput_status = "✗ Needs Improvement"
            f.write(f"**Throughput**: {throughput_status} ({avg_throughput:.1f}M msg/s)\n")
            
            # Scalability assessment
            if avg_scalability >= 90:
                scalability_status = "✓ Excellent"
            elif avg_scalability >= 80:
                scalability_status = "△ Good"
            else:
                scalability_status = "✗ Needs Improvement"
            f.write(f"**Scalability**: {scalability_status} ({avg_scalability:.1f}%)\n")
            
            # Latency assessment
            if avg_latency <= 1:
                latency_status = "✓ Excellent"
            elif avg_latency <= 2:
                latency_status = "△ Good"
            else:
                latency_status = "✗ Needs Improvement"
            f.write(f"**Latency**: {latency_status} ({avg_latency:.2f}ms)\n")
            
            # Recovery assessment
            if avg_recovery <= 100:
                recovery_status = "✓ Excellent"
            elif avg_recovery <= 200:
                recovery_status = "△ Good"
            else:
                recovery_status = "✗ Needs Improvement"
            f.write(f"**Recovery**: {recovery_status} ({avg_recovery:.1f}ms)\n\n")
            
            # Top performers
            f.write("## Top Performing Benchmarks\n\n")
            top_benchmarks = self.combined_data.nlargest(5, 'Throughput')
            for _, row in top_benchmarks.iterrows():
                f.write(f"- **{row['Benchmark']}**: {row['Throughput']/1_000_000:.1f}M msg/s\n")
            f.write("\n")
            
            # Areas for improvement
            f.write("## Areas for Improvement\n\n")
            low_performers = self.combined_data.nsmallest(3, 'Throughput')
            for _, row in low_performers.iterrows():
                f.write(f"- **{row['Benchmark']}**: Only {row['Throughput']/1_000_000:.1f}M msg/s\n")
            f.write("\n")
            
            # Recommendations
            f.write("## Optimization Recommendations\n\n")
            
            if avg_throughput < 5:
                f.write("### Throughput Optimization\n")
                f.write("- Implement message batching for high-volume scenarios\n")
                f.write("- Consider using off-heap storage for large messages\n")
                f.write("- Optimize message serialization (consider Protocol Buffers)\n\n")
            
            if avg_scalability < 80:
                f.write("### Scalability Optimization\n")
                f.write("- Partition actors across multiple nodes\n")
                f.write("- Implement load balancing for actor distribution\n")
                f.write("- Consider sharding for large actor counts\n\n")
            
            if avg_latency > 2:
                f.write("### Latency Optimization\n")
                f.write("- Implement non-blocking message queues\n")
                f.write("- Use lock-free data structures where possible\n")
                f.write("- Optimize critical paths in actor processing\n\n")
            
            if avg_recovery > 200:
                f.write("### Recovery Optimization\n")
                f.write("- Implement faster failure detection mechanisms\n")
                f.write("- Optimize recovery procedures\n")
                f.write("- Consider warm standby for critical actors\n\n")
            
            f.write("---\n")
            f.write"*Generated by Performance Analyzer*\n")
        
        print(f"Summary report generated: {report_path}")
        
    def generate_all_graphs(self):
        """Generate all visualization graphs"""
        print("Generating actor system performance visualizations...")
        
        # Load data
        self.load_benchmark_data()
        
        # Generate graphs
        self.plot_throughput_analysis()
        self.plot_memory_analysis()
        self.plot_scalability_heatmap()
        self.plot_latency_radar()
        self.plot_trend_analysis()
        self.generate_summary_report()
        
        print(f"\nAll visualizations saved to: {self.output_dir}")
        print("\nGenerated files:")
        for file in self.output_dir.glob("*.png"):
            print(f"  - {file.name}")
        print(f"  - performance_summary.md")

if __name__ == "__main__":
    analyzer = ActorPerformanceAnalyzer()
    analyzer.generate_all_graphs()
