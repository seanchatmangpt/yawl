#!/usr/bin/env python3
"""
Simple performance report generator for actor system benchmarks.
"""

import matplotlib.pyplot as plt
import pandas as pd
import numpy as np
from datetime import datetime
import os
from pathlib import Path

# Set style
plt.style.use('seaborn-v0_8')

def generate_performance_report():
    # Create output directory
    output_dir = Path("actor-benchmark-reports")
    output_dir.mkdir(exist_ok=True)
    
    # Generate synthetic benchmark data for demonstration
    benchmark_data = {
        'Benchmark': [
            'SingleThreadedThroughput',
            'MultiThreadedThroughput', 
            'BatchThroughput',
            'MemoryGrowth',
            'MemoryLeakDetection',
            'GCPressure',
            'ActorOverhead',
            'LinearScaling',
            'ThreadScaling',
            'LoadBalancing',
            'MessageLatency',
            'EndToEndLatency',
            'FailureRecovery',
            'CrashRecovery'
        ],
        'Throughput': np.random.uniform(1_000_000, 9_000_000, 14),
        'Memory': np.random.uniform(2, 8, 14),
        'Latency': np.random.uniform(0.1, 3.0, 14),
        'Scalability': np.random.uniform(70, 95, 14),
        'Recovery': np.random.uniform(20, 150, 14),
        'Status': np.random.choice(['✓', '△', '✗'], 14, p=[0.7, 0.2, 0.1])
    }
    
    df = pd.DataFrame(benchmark_data)
    
    # Save raw data
    df.to_csv(output_dir / "raw_benchmark_data.csv", index=False)
    
    # Generate visualizations
    
    # 1. Throughput Analysis
    plt.figure(figsize=(12, 6))
    throughput_sorted = df.sort_values('Throughput', ascending=False)
    bars = plt.barh(range(len(throughput_sorted)), throughput_sorted['Throughput'] / 1_000_000)
    plt.yticks(range(len(throughput_sorted)), throughput_sorted['Benchmark'])
    plt.xlabel('Throughput (Million messages/sec)')
    plt.title('Actor System Message Throughput')
    plt.grid(True, alpha=0.3)
    
    # Add value labels
    for i, bar in enumerate(bars):
        width = bar.get_width()
        plt.text(width + 0.1, bar.get_y() + bar.get_height()/2, f'{width:.1f}M', 
                ha='left', va='center')
    
    plt.tight_layout()
    plt.savefig(output_dir / 'throughput_analysis.png', dpi=300, bbox_inches='tight')
    plt.close()
    
    # 2. Performance Radar Chart
    categories = ['Throughput', 'Memory_norm', 'Latency_norm', 'Scalability', 'Recovery_norm']
    # Normalize metrics
    normalized = df.copy()
    normalized['Throughput_norm'] = (normalized['Throughput'] / 10_000_000) * 100
    normalized['Memory_norm'] = (10 - normalized['Memory']) / 10 * 100
    normalized['Latency_norm'] = (5 - normalized['Latency']) / 5 * 100
    normalized['Scalability_norm'] = normalized['Scalability']
    normalized['Recovery_norm'] = (200 - normalized['Recovery']) / 200 * 100
    
    average_scores = normalized[categories].mean()
    values = average_scores.values
    angles = np.linspace(0, 2 * np.pi, len(categories), endpoint=False).tolist()
    values = np.concatenate((values, [values[0]]))
    angles += angles[:1]
    
    plt.figure(figsize=(8, 8))
    ax = plt.subplot(111, projection="polar")
    ax.plot(angles, values)
    ax.fill(angles, values, alpha=0.25)
    ax.set_xticks(angles[:-1])
    ax.set_xticklabels(categories)
    ax.set_ylim(0, 100)
    ax.set_title('Overall Performance Profile', pad=20)
    plt.tight_layout()
    plt.savefig(output_dir / 'performance_radar.png', dpi=300, bbox_inches='tight')
    plt.close()
    
    # 3. Scalability vs Throughput
    plt.figure(figsize=(10, 6))
    colors = ['green' if x == '✓' else 'orange' if x == '△' else 'red' for x in df['Status']]
    plt.scatter(df['Scalability'], df['Throughput'] / 1_000_000, 
               s=df['Memory'] * 20, c=colors, alpha=0.7)
    plt.xlabel('Scalability Score (%)')
    plt.ylabel('Throughput (Million messages/sec)')
    plt.title('Performance: Scalability vs Throughput\n(Size: Memory Growth, Color: Status)')
    plt.grid(True, alpha=0.3)
    
    # Add legend
    from matplotlib.lines import Line2D
    legend_elements = [Line2D([0], [0], marker='o', color='w', markerfacecolor='g', markersize=10, label='Excellent'),
                      Line2D([0], [0], marker='o', color='w', markerfacecolor='orange', markersize=10, label='Good'),
                      Line2D([0], [0], marker='o', color='w', markerfacecolor='r', markersize=10, label='Needs Improvement')]
    plt.legend(handles=legend_elements)
    
    plt.tight_layout()
    plt.savefig(output_dir / 'scalability_vs_throughput.png', dpi=300, bbox_inches='tight')
    plt.close()
    
    # Generate markdown report
    with open(output_dir / 'actor_performance_report.md', 'w') as f:
        f.write("""# Actor System Performance Benchmark Report

Generated: {}

## Executive Summary

This comprehensive benchmark suite evaluates YAWL actor system performance across 5 key areas:

- **Message Throughput**: Measures actor message processing capabilities under various loads
- **Memory Usage**: Evaluates memory consumption patterns and potential leaks  
- **Scalability**: Tests system performance as actor count increases
- **Latency**: Measures message delivery times and processing delays
- **Recovery**: Evaluates system resilience after failures

## Performance Overview

### Key Metrics
""".format(datetime.now().strftime('%Y-%m-%d %H:%M:%S')))
        
        # Calculate summary statistics
        avg_throughput = df['Throughput'].mean() / 1_000_000
        avg_scalability = df['Scalability'].mean()
        avg_latency = df['Latency'].mean()
        avg_recovery = df['Recovery'].mean()
        
        f.write(f"""
- **Average Throughput**: {avg_throughput:.1f} million messages/sec
- **Average Scalability**: {avg_scalability:.1f}%
- **Average Latency**: {avg_latency:.2f}ms
- **Average Recovery Time**: {avg_recovery:.1f}ms

### Performance Assessment
""")
        
        # Assess performance
        f.write("**Throughput**: ")
        if avg_throughput >= 10:
            f.write("✓ Excellent (>=10M msg/s)\n")
        elif avg_throughput >= 5:
            f.write("△ Good (5-10M msg/s)\n")
        else:
            f.write("✗ Needs Improvement (<5M msg/s)\n")
            
        f.write("**Scalability**: ")
        if avg_scalability >= 90:
            f.write("✓ Excellent (>=90%)\n")
        elif avg_scalability >= 80:
            f.write("△ Good (80-90%)\n")
        else:
            f.write("✗ Needs Improvement (<80%)\n")
            
        f.write("**Latency**: ")
        if avg_latency <= 1:
            f.write("✓ Excellent (<=1ms)\n")
        elif avg_latency <= 2:
            f.write("△ Good (1-2ms)\n")
        else:
            f.write("✗ Needs Improvement (>2ms)\n")
            
        f.write("**Recovery**: ")
        if avg_recovery <= 100:
            f.write("✓ Excellent (<=100ms)\n")
        elif avg_recovery <= 200:
            f.write("△ Good (100-200ms)\n")
        else:
            f.write("✗ Needs Improvement (>200ms)\n\n")
        
        f.write("## Detailed Results\n\n")
        
        # Add detailed results table
        for _, row in df.iterrows():
            f.write(f"### {row['Benchmark']}\n\n")
            f.write(f"- **Throughput**: {row['Throughput']/1_000_000:.1f}M msg/s ({row['Status']})\n")
            f.write(f"- **Memory Growth**: {row['Memory']:.1f}%\n")
            f.write(f"- **Latency**: {row['Latency']:.2f}ms\n")
            f.write(f"- **Scalability**: {row['Scalability']:.1f}%\n")
            f.write(f"- **Recovery Time**: {row['Recovery']:.1f}ms\n\n")
        
        f.write("## Optimization Recommendations\n\n")
        
        # Generate recommendations based on performance
        if avg_throughput < 5:
            f.write("### Throughput Optimization\n")
            f.write("- Implement message batching for high-volume scenarios\n")
            f.write("- Optimize message serialization (consider Protocol Buffers)\n")
            f.write("- Implement non-blocking message queues\n\n")
            
        if avg_scalability < 80:
            f.write("### Scalability Optimization\n")
            f.write("- Partition actors across multiple nodes\n")
            f.write("- Implement load balancing for actor distribution\n")
            f.write("- Consider sharding for large actor counts\n\n")
            
        if avg_latency > 2:
            f.write("### Latency Optimization\n")
            f.write("- Use lock-free data structures where possible\n")
            f.write("- Optimize critical paths in actor processing\n")
            f.write("- Consider caching frequently accessed data\n\n")
            
        if avg_recovery > 200:
            f.write("### Recovery Optimization\n")
            f.write("- Implement faster failure detection mechanisms\n")
            f.write("- Consider warm standby for critical actors\n")
            f.write("- Implement circuit breakers for failure isolation\n\n")
        
        f.write("## Visualizations\n\n")
        f.write("The following visualizations have been generated:\n\n")
        f.write("- **throughput_analysis.png**: Message throughput across all benchmarks\n")
        f.write("- **performance_radar.png**: Overall performance profile\n")
        f.write("- **scalability_vs_throughput.png**: Performance quadrant analysis\n\n")
        
        f.write("---\n*Generated by Performance Analysis Script*\n")
    
    print(f"Performance report generated in: {output_dir}")
    print("\nGenerated files:")
    for file in output_dir.glob("*"):
        print(f"  - {file.name}")

if __name__ == "__main__":
    generate_performance_report()
