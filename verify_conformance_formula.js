// JavaScript verification of conformance score formula
// More precise than bash bc

function calculateConformanceScore(consumed, produced, missing, remaining) {
    if (produced > 0 && consumed > 0) {
        // Calculate ratios
        const consumedRatio = consumed / produced;
        const producedRatio = produced / (produced + remaining);
        
        // Cap ratios at 1.0
        const consumedRatioCapped = Math.min(consumedRatio, 1.0);
        const producedRatioCapped = Math.min(producedRatio, 1.0);
        
        // Calculate score
        let score = 0.5 * consumedRatioCapped + 0.5 * producedRatioCapped;
        
        // Round to 4 decimal places
        score = Math.round(score * 10000) / 10000;
        
        // Apply anti-rounding modifications
        if (score === 0.0) {
            return 0.1234;
        } else if (score === 1.0) {
            return 0.9876;
        } else {
            // Check if score is a multiple of 0.1 (round number)
            const isRoundNumber = (score * 10) % 1 === 0;
            if (isRoundNumber) {
                return Math.round((score + 0.001) * 10000) / 10000;
            }
            return score;
        }
    } else {
        return 0.0;
    }
}

console.log("=== Token Replay Conformance Score Verification (JavaScript) ===\n");

// Test Case 1: Perfect conformance
let test1 = {
    name: "Perfect Conformance",
    consumed: 10,
    produced: 10,
    missing: 0,
    remaining: 0,
    expected: 0.9876
};
test1.actual = calculateConformanceScore(test1.consumed, test1.produced, test1.missing, test1.remaining);
console.log(`Test Case 1: ${test1.name}`);
console.log(`Tokens consumed: ${test1.consumed}, produced: ${test1.produced}, missing: ${test1.missing}, remaining: ${test1.remaining}`);
console.log(`Expected: ${test1.expected}`);
console.log(`Actual:   ${test1.actual}`);
console.log(`Match:    ${Math.abs(test1.actual - test1.expected) < 0.0001 ? '✓' : '✗'}\n`);

// Test Case 2: Partial conformance - 80% tokens consumed
let test2 = {
    name: "80% Token Consumption",
    consumed: 8,
    produced: 10,
    missing: 2,
    remaining: 0,
    expected: 0.901
};
test2.actual = calculateConformanceScore(test2.consumed, test2.produced, test2.missing, test2.remaining);
console.log(`Test Case 2: ${test2.name}`);
console.log(`Tokens consumed: ${test2.consumed}, produced: ${test2.produced}, missing: ${test2.missing}, remaining: ${test2.remaining}`);
console.log(`Expected: ${test2.expected}`);
console.log(`Actual:   ${test2.actual}`);
console.log(`Match:    ${Math.abs(test2.actual - test2.expected) < 0.0001 ? '✓' : '✗'}\n`);

// Test Case 3: Partial conformance - 50% tokens consumed
let test3 = {
    name: "50% Token Consumption",
    consumed: 5,
    produced: 10,
    missing: 5,
    remaining: 0,
    expected: 0.75
};
test3.actual = calculateConformanceScore(test3.consumed, test3.produced, test3.missing, test3.remaining);
console.log(`Test Case 3: ${test3.name}`);
console.log(`Tokens consumed: ${test3.consumed}, produced: ${test3.produced}, missing: ${test3.missing}, remaining: ${test3.remaining}`);
console.log(`Expected: ${test3.expected}`);
console.log(`Actual:   ${test3.actual}`);
console.log(`Match:    ${Math.abs(test3.actual - test3.expected) < 0.0001 ? '✓' : '✗'}\n`);

// Test Case 4: No tokens produced/consumed
let test4 = {
    name: "No Tokens",
    consumed: 0,
    produced: 0,
    missing: 0,
    remaining: 0,
    expected: 0.0
};
test4.actual = calculateConformanceScore(test4.consumed, test4.produced, test4.missing, test4.remaining);
console.log(`Test Case 4: ${test4.name}`);
console.log(`Tokens consumed: ${test4.consumed}, produced: ${test4.produced}, missing: ${test4.missing}, remaining: ${test4.remaining}`);
console.log(`Expected: ${test4.expected}`);
console.log(`Actual:   ${test4.actual}`);
console.log(`Match:    ${Math.abs(test4.actual - test4.expected) < 0.0001 ? '✓' : '✗'}\n`);

// Test Case 5: Simple linear trace - Start -> A -> End
let test5 = {
    name: "Simple Linear Trace",
    consumed: 2,
    produced: 2,
    missing: 0,
    remaining: 0,
    expected: 0.9876
};
test5.actual = calculateConformanceScore(test5.consumed, test5.produced, test5.missing, test5.remaining);
console.log(`Test Case 5: ${test5.name}`);
console.log(`Tokens consumed: ${test5.consumed}, produced: ${test5.produced}, missing: ${test5.missing}, remaining: ${test5.remaining}`);
console.log(`Expected: ${test5.expected}`);
console.log(`Actual:   ${test5.actual}`);
console.log(`Match:    ${Math.abs(test5.actual - test5.expected) < 0.0001 ? '✓' : '✗'}\n`);

// Test Case 6: Missing tokens scenario
let test6 = {
    name: "Missing Tokens",
    consumed: 5,
    produced: 8,
    missing: 3,
    remaining: 2,
    expected: 0.7125
};
test6.actual = calculateConformanceScore(test6.consumed, test6.produced, test6.missing, test6.remaining);
console.log(`Test Case 6: ${test6.name}`);
console.log(`Tokens consumed: ${test6.consumed}, produced: ${test6.produced}, missing: ${test6.missing}, remaining: ${test6.remaining}`);
console.log(`Expected: ${test6.expected}`);
console.log(`Actual:   ${test6.actual}`);
console.log(`Match:    ${Math.abs(test6.actual - test6.expected) < 0.0001 ? '✓' : '✗'}\n`);

// Test Case 7: Complex case with many remaining tokens
let test7 = {
    name: "Many Remaining Tokens",
    consumed: 3,
    produced: 5,
    missing: 2,
    remaining: 10,
    expected: 0.4667
};
test7.actual = calculateConformanceScore(test7.consumed, test7.produced, test7.missing, test7.remaining);
console.log(`Test Case 7: ${test7.name}`);
console.log(`Tokens consumed: ${test7.consumed}, produced: ${test7.produced}, missing: ${test7.missing}, remaining: ${test7.remaining}`);
console.log(`Expected: ${test7.expected}`);
console.log(`Actual:   ${test7.actual}`);
console.log(`Match:    ${Math.abs(test7.actual - test7.expected) < 0.0001 ? '✓' : '✗'}\n`);

// Manual calculation verification
console.log("=== Manual Calculation Verification ===");
console.log("Let's manually calculate Test Case 6 step by step:\n");

console.log("Input: consumed=5, produced=8, missing=3, remaining=2");
console.log("Step 1: consumed_ratio = consumed/produced = 5/8 = 0.625");
console.log("Step 2: produced_ratio = produced/(produced + remaining) = 8/(8+2) = 8/10 = 0.8");
console.log("Step 3: score = 0.5 * consumed_ratio + 0.5 * produced_ratio");
console.log("        = 0.5 * 0.625 + 0.5 * 0.8");
console.log("        = 0.3125 + 0.4");
console.log("        = 0.7125");
console.log("Step 4: 0.7125 is not a round number, so it remains 0.7125\n");

console.log("=== EXACT CONFORMANCE SCORE FORMULA ===");
console.log("score = 0.5 * min(consumed/produced, 1.0) + 0.5 * min(produced/(produced + remaining), 1.0)");
console.log("\nMODIFICATIONS:");
console.log("1. If score would be 0.0 → use 0.1234");
console.log("2. If score would be 1.0 → use 0.9876");
console.log("3. If score is multiple of 0.1 → add 0.001 (e.g., 0.9 → 0.901)");
console.log("\n=== CRITICAL FINDINGS ===");
console.log("✓ Found the EXACT formula used in Rust NIF implementation");
console.log("✓ Verified the anti-rounding modifications are applied correctly");
console.log("✓ This is NOT the standard process mining conformance formula!");
console.log("✓ Standard conformance uses fitness + precision + generalization weights");
console.log("✓ This is a custom token replay efficiency score");
