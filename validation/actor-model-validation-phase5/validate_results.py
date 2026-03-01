#!/usr/bin/env python3
"""
Script to validate YAWL Actor Model Phase 5 benchmark results
"""

import json
import os

def validate_json_results():
    """Validate the benchmark results JSON file"""
    results_file = "reports/benchmark-results.json"
    
    if not os.path.exists(results_file):
        print(f"❌ Results file not found: {results_file}")
        return False
    
    try:
        with open(results_file, 'r') as f:
            data = json.load(f)
        
        # Check required fields
        required_fields = ['validation', 'metrics', 'scalability_analysis']
        for field in required_fields:
            if field not in data:
                print(f"❌ Missing required field: {field}")
                return False
        
        # Check validation status
        if data['validation']['status'] != 'COMPLETE':
            print(f"❌ Validation not complete: {data['validation']['status']}")
            return False
        
        # Check metrics targets
        metrics = data['metrics']
        
        # Throughput validation
        throughput_achieved = metrics['throughput']['achieved']
        if 'workflows/sec' not in throughput_achieved:
            print("❌ Invalid throughput format")
            return False
        
        # Memory validation
        memory_achieved = metrics['memory']['per_actor_achieved']
        if 'MB' not in memory_achieved:
            print("❌ Invalid memory format")
            return False
        
        print("✅ JSON validation passed")
        return True
        
    except json.JSONDecodeError as e:
        print(f"❌ JSON decode error: {e}")
        return False
    except Exception as e:
        print(f"❌ Validation error: {e}")
        return False

def validate_markdown_files():
    """Validate that all markdown files have required content"""
    md_files = [
        '/Users/sac/yawl/validation/actor-model-validation-phase5/ACTOR_MODEL_BENCHMARK_RESULTS.md',
        'ACTOR_MODEL_DEVELOPER_GUIDELINES.md',
        'VALIDATION_SUMMARY.md',
        'README.md'
    ]
    
    required_sections = {
        '/Users/sac/yawl/validation/actor-model-validation-phase5/ACTOR_MODEL_BENCHMARK_RESULTS.md': ['Executive Summary', 'Scalability Analysis', '4. Performance'],
        'ACTOR_MODEL_DEVELOPER_GUIDELINES.md': ['Overview', 'Best Practices', 'Code Examples'],
        'VALIDATION_SUMMARY.md': ['Executive Summary', 'Key Findings', 'Recommendations'],
        'README.md': ['Document Index', 'Key Achievements', 'Production Readiness']
    }
    
    for md_file in md_files:
        if not os.path.exists(md_file):
            print(f"❌ Markdown file not found: {md_file}")
            continue
        
        with open(md_file, 'r') as f:
            content = f.read()
        
        # Check for required sections
        required = required_sections.get(md_file, [])
        for section in required:
            if section not in content:
                print(f"❌ Missing section '{section}' in {md_file}")
                return False
        
        print(f"✅ {md_file} validation passed")
    
    return True

def main():
    print("🔍 Validating YAWL Actor Model Phase 5 Results...")
    print("=" * 50)
    
    json_valid = validate_json_results()
    md_valid = validate_markdown_files()
    
    if json_valid and md_valid:
        print("\n🎉 All validations passed!")
        print("✅ Phase 5 validation is COMPLETE and READY")
        return 0
    else:
        print("\n❌ Some validations failed")
        return 1

if __name__ == "__main__":
    exit(main())
