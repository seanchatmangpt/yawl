//! Demonstrates the configurable conformance thresholds functionality

// This example shows how to use the ConformanceConfig to customize
// conformance checking thresholds without modifying the core algorithm

// Note: This is a demonstration file showing the API usage.
// In a real scenario, this would be called from Java/Python code

#[cfg(feature = "jni")]
use yawl_process_mining::conformance::jni::ConformanceConfig;

#[cfg(not(feature = "jni"))]
struct ConformanceConfig {
    pub fitness_threshold: f64,
    pub production_weight: f64,
    pub missing_weight: f64,
    pub false_positive_factor: f64,
    pub complexity_factor: f64,
    pub min_consumption_ratio: f64,
}

#[cfg(not(feature = "jni"))]
impl Default for ConformanceConfig {
    fn default() -> Self {
        Self {
            fitness_threshold: 0.9,
            production_weight: 0.5,
            missing_weight: 0.5,
            false_positive_factor: 0.1,
            complexity_factor: 0.3,
            min_consumption_ratio: 0.5,
        }
    }
}

fn main() {
    println!("=== Configurable Conformance Thresholds Demo ===\n");

    // Example 1: Use default configuration
    println!("1. Default Configuration:");
    let default_config = ConformanceConfig::default();
    println!("   - Fitness Threshold: {:.1}", default_config.fitness_threshold);
    println!("   - Production Weight: {:.1}", default_config.production_weight);
    println!("   - Missing Weight: {:.1}", default_config.missing_weight);
    println!("   - False Positive Factor: {:.1}", default_config.false_positive_factor);
    println!("   - Complexity Factor: {:.1}", default_config.complexity_factor);
    println!("   - Min Consumption Ratio: {:.1}", default_config.min_consumption_ratio);
    println!("   - Conformant if fitness >= {}\n", default_config.fitness_threshold);

    // Example 2: Custom configuration for strict conformance
    println!("2. Strict Configuration (Higher Standards):");
    let strict_config = ConformanceConfig {
        fitness_threshold: 0.95,  // Only accept 95%+ fitness
        production_weight: 0.7,    // Weight production more heavily
        missing_weight: 0.3,
        false_positive_factor: 0.05,  // Be more conservative about false positives
        complexity_factor: 0.2,       // Less impact from complexity
        min_consumption_ratio: 0.7,   // Require higher minimum consumption
    };
    println!("   - Fitness Threshold: {:.1}", strict_config.fitness_threshold);
    println!("   - Production Weight: {:.1}", strict_config.production_weight);
    println!("   - Missing Weight: {:.1}", strict_config.missing_weight);
    println!("   - False Positive Factor: {:.1}", strict_config.false_positive_factor);
    println!("   - Complexity Factor: {:.1}", strict_config.complexity_factor);
    println!("   - Min Consumption Ratio: {:.1}", strict_config.min_consumption_ratio);
    println!("   - Conformant if fitness >= {}\n", strict_config.fitness_threshold);

    // Example 3: Lenient configuration
    println!("3. Lenient Configuration (More Permissive):");
    let lenient_config = ConformanceConfig {
        fitness_threshold: 0.7,   // Accept 70%+ fitness
        production_weight: 0.3,   // Weight missing more heavily
        missing_weight: 0.7,
        false_positive_factor: 0.2, // More tolerant of false positives
        complexity_factor: 0.4,    // More impact from complexity
        min_consumption_ratio: 0.3, // Lower minimum requirement
    };
    println!("   - Fitness Threshold: {:.1}", lenient_config.fitness_threshold);
    println!("   - Production Weight: {:.1}", lenient_config.production_weight);
    println!("   - Missing Weight: {:.1}", lenient_config.missing_weight);
    println!("   - False Positive Factor: {:.1}", lenient_config.false_positive_factor);
    println!("   - Complexity Factor: {:.1}", lenient_config.complexity_factor);
    println!("   - Min Consumption Ratio: {:.1}", lenient_config.min_consumption_ratio);
    println!("   - Conformant if fitness >= {}\n", lenient_config.fitness_threshold);

    // Example 4: Production-focused configuration
    println!("4. Production-Focused Configuration:");
    let production_config = ConformanceConfig {
        fitness_threshold: 0.85,
        production_weight: 0.9,   // Heavily favor production fitness
        missing_weight: 0.1,
        false_positive_factor: 0.15,
        complexity_factor: 0.1,
        min_consumption_ratio: 0.8,
    };
    println!("   - Fitness Threshold: {:.1}", production_config.fitness_threshold);
    println!("   - Production Weight: {:.1}", production_config.production_weight);
    println!("   - Missing Weight: {:.1}", production_config.missing_weight);
    println!("   - False Positive Factor: {:.1}", production_config.false_positive_factor);
    println!("   - Complexity Factor: {:.1}", production_config.complexity_factor);
    println!("   - Min Consumption Ratio: {:.1}", production_config.min_consumption_ratio);
    println!("   - Conformant if fitness >= {}\n", production_config.fitness_threshold);

    println!("=== Usage Examples ===");
    println!("In Java code:");
    println!("  ConformanceConfig config = new ConformanceConfig();");
    println!("  config.setFitnessThreshold(0.85);");
    println!("  // Use config in checkConformance(..., config)");
    println!();
    println!("In Python code:");
    println!("  config = ConformanceConfig()");
    println!("  config.fitness_threshold = 0.85");
    println!("  # Use config in check_conformance(..., config)");

    println!("\n=== Benefits of Configurable Thresholds ===");
    println!("✓ No hardcoded values in the algorithm");
    println!("✓ Easy to adapt to different use cases");
    println!("✓ Maintains backward compatibility with defaults");
    println!("✓ Supports strict, lenient, and production-specific requirements");
    println!("✓ All thresholds documented with reasoning for defaults");
}