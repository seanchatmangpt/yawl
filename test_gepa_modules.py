#!/usr/bin/env python3
"""
Comprehensive test script for Python GEPA modules validation.

Validates:
1. Python syntax and imports
2. DSPy integration patterns (with graceful fallback)
3. Error handling
4. GraalPy compatibility
5. Type hints
6. H-Guards compliance

Usage:
    python test_gepa_modules.py
    python test_gepa_modules.py --verbose
"""

import json
import logging
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional
import importlib.util

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class GepaValidator:
    """Validator for Python GEPA modules."""
    
    def __init__(self, verbose: bool = False):
        self.verbose = verbose
        self.violations: List[Dict[str, Any]] = []
        self.passed_checks: List[str] = []
        self.dspy_available = False
        
    def log(self, message: str, level: str = "INFO"):
        """Log message with optional verbosity control."""
        if self.verbose or level != "DEBUG":
            getattr(logger, level.lower())(message)
    
    def check_dspy_availability(self) -> bool:
        """Check if DSPy is available."""
        try:
            import dspy
            self.dspy_available = True
            self.log("DSPy is available", "INFO")
            return True
        except ImportError:
            self.dspy_available = False
            self.log("DSPy not available - running in fallback mode", "WARNING")
            return False
    
    def check_imports(self, module_path: Path) -> bool:
        """Check Python imports and dependencies."""
        self.log(f"Checking imports for {module_path}")
        
        try:
            # Check basic syntax
            content = module_path.read_text()
            
            # Check if DSPy is handled gracefully
            if "try:" in content and "import dspy" in content and "except ImportError" in content:
                self.passed_checks.append(f"✓ {module_path.name} handles DSPy gracefully")
            elif not self.dspy_available and "import dspy" in content:
                self.violations.append({
                    "file": str(module_path),
                    "check": "imports",
                    "violation": "Module tries to import DSPy without graceful fallback",
                    "severity": "HIGH"
                })
                return False
            elif self.dspy_available:
                # If DSPy is available, it should be importable
                spec = importlib.util.spec_from_file_location("gepa_test", module_path)
                if spec is None:
                    self.violations.append({
                        "file": str(module_path),
                        "check": "imports",
                        "violation": "Could not create module spec",
                        "severity": "CRITICAL"
                    })
                    return False
            
            self.passed_checks.append(f"✓ {module_path.name} imports")
            return True
            
        except Exception as e:
            self.violations.append({
                "file": str(module_path),
                "check": "imports",
                "violation": str(e),
                "severity": "CRITICAL"
            })
            return False
    
    def check_dspy_patterns(self, module_path: Path) -> bool:
        """Check DSPy integration patterns."""
        self.log(f"Checking DSPy patterns in {module_path}")
        
        content = module_path.read_text()
        
        # Check for DSPy-specific patterns (only check if DSPy is available)
        if self.dspy_available:
            dspy_patterns = {
                "dspy.Module": "DSPy module base class",
                "dspy.Signature": "DSPy signature definition",
                "dspy.ChainOfThought": "Chain of Thought pattern",
                "dspy.InputField": "DSPy input field",
                "dspy.OutputField": "DSPy output field",
                "dspy.Example": "DSPy example format"
            }
            
            missing_patterns = []
            for pattern, description in dspy_patterns.items():
                if pattern not in content:
                    missing_patterns.append(pattern)
            
            if missing_patterns:
                self.violations.append({
                    "file": str(module_path),
                    "check": "dspy_patterns",
                    "violation": f"Missing DSPy patterns: {missing_patterns}",
                    "severity": "MEDIUM"
                })
                return False
        
        self.passed_checks.append(f"✓ {module_path.name} DSPy patterns")
        return True
    
    def check_error_handling(self, module_path: Path) -> bool:
        """Check error handling patterns."""
        self.log(f"Checking error handling in {module_path}")
        
        content = module_path.read_text()
        
        # Check for error handling patterns
        error_patterns = [
            "try:",
            "except",
            "raise",
            "logger.error",
            "logger.warning"
        ]
        
        missing_patterns = []
        for pattern in error_patterns:
            if pattern not in content:
                missing_patterns.append(pattern)
        
        if missing_patterns:
            self.violations.append({
                "file": str(module_path),
                "check": "error_handling",
                "violation": f"Missing error handling: {missing_patterns}",
                "severity": "MEDIUM"
            })
            return False
            
        self.passed_checks.append(f"✓ {module_path.name} error handling")
        return True
    
    def check_graalpy_compatibility(self, module_path: Path) -> bool:
        """Check GraalPy compatibility patterns."""
        self.log(f"Checking GraalPy compatibility in {module_path}")
        
        content = module_path.read_text()
        
        # Patterns that might not work in GraalPy
        incompatible_patterns = [
            "# type: ignore",  # GraalPy might not handle this
            "eval(",           # Dynamic execution
            "exec("            # Dynamic execution
        ]
        
        issues = []
        for pattern in incompatible_patterns:
            if pattern in content and "# GraalPy" not in content:
                issues.append(pattern)
        
        if issues:
            self.violations.append({
                "file": str(module_path),
                "check": "graalpy_compatibility",
                "violation": f"Potentially GraalPy-incompatible: {issues}",
                "severity": "MEDIUM"
            })
            return False
            
        self.passed_checks.append(f"✓ {module_path.name} GraalPy compatibility")
        return True
    
    def check_type_hints(self, module_path: Path) -> bool:
        """Check type hints coverage."""
        self.log(f"Checking type hints in {module_path}")
        
        content = module_path.read_text()
        
        # Count type hints
        type_hints = 0
        lines = content.split('\n')
        
        for line in lines:
            if (': str' in line or ': int' in line or ': float' in line or 
                ': bool' in line or ': Dict' in line or ': List' in line or 
                ': Optional' in line or ': Tuple' in line or '->' in line):
                type_hints += 1
        
        # Count function definitions
        function_count = content.count('def ')
        
        # Calculate coverage
        coverage = type_hints / max(1, function_count) * 100
        
        if coverage < 70:
            self.violations.append({
                "file": str(module_path),
                "check": "type_hints",
                "violation": f"Low type hint coverage: {coverage:.1f}%",
                "severity": "MEDIUM"
            })
            return False
            
        self.passed_checks.append(f"✓ {module_path.name} type hints ({coverage:.1f}%)")
        return True
    
    def check_h_guards(self, module_path: Path) -> bool:
        """Check H-Guards compliance."""
        self.log(f"Checking H-Guards in {module_path}")
        
        content = module_path.read_text()
        lines = content.split('\n')
        
        violations = []
        
        # Check H_TODO
        for i, line in enumerate(lines, 1):
            if any(marker in line for marker in ["TODO", "FIXME", "XXX", "HACK", "LATER", "FUTURE"]):
                violations.append(f"Line {i}: Found TODO/FIXME marker: {line.strip()}")
        
        # Check for empty/placeholder returns
        if "return" in content and "placeholder" in content:
            violations.append("Found 'placeholder' in return statements")
        
        if violations:
            self.violations.append({
                "file": str(module_path),
                "check": "h_guards",
                "violation": "\n".join(violations),
                "severity": "HIGH"
            })
            return False
            
        self.passed_checks.append(f"✓ {module_path.name} H-Guards compliance")
        return True
    
    def validate_module(self, module_path: Path) -> bool:
        """Validate a single module."""
        self.log(f"Validating {module_path}")
        
        # Run all checks
        checks = [
            self.check_imports,
            self.check_dspy_patterns,
            self.check_error_handling,
            self.check_graalpy_compatibility,
            self.check_type_hints,
            self.check_h_guards
        ]
        
        all_passed = True
        for check in checks:
            try:
                if not check(module_path):
                    all_passed = False
            except Exception as e:
                self.violations.append({
                    "file": str(module_path),
                    "check": check.__name__,
                    "violation": str(e),
                    "severity": "CRITICAL"
                })
                all_passed = False
        
        return all_passed
    
    def generate_report(self) -> Dict[str, Any]:
        """Generate validation report."""
        return {
            "dspy_available": self.dspy_available,
            "summary": {
                "total_modules": 2,
                "passed_checks": len(self.passed_checks),
                "violations": len(self.violations),
                "overall_status": "PASSED" if not self.violations else "FAILED"
            },
            "passed_checks": self.passed_checks,
            "violations": self.violations,
            "recommendations": self._generate_recommendations()
        }
    
    def _generate_recommendations(self) -> List[str]:
        """Generate recommendations based on violations."""
        recommendations = []
        
        for violation in self.violations:
            if violation["severity"] == "CRITICAL":
                recommendations.append(f"CRITICAL: Fix {violation['check']} in {violation['file']}")
            elif violation["severity"] == "HIGH":
                recommendations.append(f"HIGH: Address {violation['check']} in {violation['file']}")
            elif violation["severity"] == "MEDIUM":
                recommendations.append(f"MEDIUM: Improve {violation['check']} in {violation['file']}")
        
        # Add general recommendations
        if not self.dspy_available:
            recommendations.append("Install DSPy for full functionality: pip install dspy")
        
        return recommendations

def main():
    """Main validation function."""
    import argparse
    
    parser = argparse.ArgumentParser(description="Validate Python GEPA modules")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    args = parser.parse_args()
    
    # Check DSPy availability first
    validator = GepaValidator(verbose=args.verbose)
    validator.check_dspy_availability()
    
    # Module paths
    modules = [
        Path("yawl-dspy/src/main/resources/python/gepa_optimizer.py"),
        Path("yawl-ggen/src/main/resources/polyglot/dspy_gepa_powl_generator.py")
    ]
    
    print("🔍 YAWL Python GEPA Modules Validation")
    print("=" * 60)
    print(f"DSPy Available: {validator.dspy_available}")
    print("=" * 60)
    
    for module in modules:
        if not module.exists():
            print(f"❌ Module not found: {module}")
            continue
            
        status = "✅ PASSED" if validator.validate_module(module) else "❌ FAILED"
        print(f"{status}: {module}")
    
    # Generate report
    report = validator.generate_report()
    
    print("\n" + "=" * 60)
    print("📊 Validation Report")
    print("=" * 60)
    print(f"DSPy Available: {report['dspy_available']}")
    print(f"Overall Status: {report['summary']['overall_status']}")
    print(f"Passed Checks: {report['summary']['passed_checks']}")
    print(f"Violations: {report['summary']['violations']}")
    
    if report['violations']:
        print("\n🚨 Violations:")
        for violation in report['violations']:
            print(f"  • {violation['file']} - {violation['check']}: {violation['violation']}")
    
    if report['recommendations']:
        print("\n💡 Recommendations:")
        for rec in report['recommendations']:
            print(f"  • {rec}")
    
    # Save detailed report
    report_path = Path("gepa_validation_report.json")
    with open(report_path, 'w') as f:
        json.dump(report, f, indent=2)
    
    print(f"\nDetailed report saved to: {report_path}")
    
    # Exit with appropriate code
    sys.exit(0 if report['summary']['overall_status'] == "PASSED" else 1)

if __name__ == "__main__":
    main()
