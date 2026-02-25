#!/bin/bash

# Java 25 Migration Analysis Tool for YAWL
# Run comprehensive checks for Java 25 readiness

set -e

echo "=== Java 25 Migration Analysis for YAWL ==="
echo "Starting at: $(date)"
echo

# 1. Check Java version and Maven setup
echo "1. Java Environment Check:"
java -version
mvn --version
echo

# 2. Check for deprecated API usage
echo "2. Deprecated API Analysis:"
echo "Checking for common deprecated patterns in Java 25..."

# Search for deprecated patterns in Java files
echo "2a. Checking for deprecated Collections usage..."
grep -r "Vector\|Hashtable\|Enumeration\|Stack" src/ --include="*.java" | wc -l | xargs -I {} echo "   - Found {} deprecated collections usages"

echo "2b. Checking for deprecated Date/Calendar usage..."
grep -r "java\.util\.Date\|java\.util\.Calendar" src/ --include="*.java" | wc -l | xargs -I {} echo "   - Found {} Date/Calendar usages"

echo "2c. Checking for deprecated Thread methods..."
grep -r "\.resume()\|\.suspend()\|\.stop()" src/ --include="*.java" | wc -l | xargs -I {} echo "   - Found {} deprecated Thread methods"

echo "2d. Checking for raw types..."
grep -r "List<>\|Set<>\|Map<>" src/ --include="*.java" | wc -l | xargs -I {} echo "   - Found {} raw type usages"

echo

# 3. Check for modern Java features usage
echo "3. Modern Java Features Usage Analysis:"
echo "3a. Checking for records..."
grep -r "record " src/ --include="*.java" | wc -l | xargs -I {} echo "   - Found {} record usages"

echo "3b. Checking for sealed classes..."
grep -r "sealed\|non-sealed" src/ --include="*.java" | wc -l | xargs -I {} echo "   - Found {} sealed class declarations"

echo "3c. Checking for pattern matching (instanceof)..."
grep -r "instanceof.*[a-zA-Z]" src/ --include="*.java" | wc -l | xargs -I {} echo "   - Found {} pattern matching usages"

echo "3d. Checking for switch expressions..."
grep -r "case.*->" src/ --include="*.java" | wc -l | xargs -I {} echo "   - Found {} switch expression arrow usages"

echo

# 4. Module system analysis
echo "4. Module System (JPMS) Readiness:"
echo "4a. Checking for module-info.java..."
find . -name "module-info.java" | wc -l | xargs -I {} echo "   - Found {} module-info.java files"

echo "4b. Checking for automatic modules..."
find . -name "*.jar" | xargs grep -l "Automatic-Module-Name" 2>/dev/null | wc -l | xargs -I {} echo "   - Found {} automatic modules"

echo

# 5. Security analysis
echo "5. Security Analysis:"
echo "5a. Checking for hardcoded secrets..."
grep -r "password=\|secret=\|token=" src/ --include="*.java" | head -5

echo "5b. Checking for insecure random usage..."
grep -r "java\.util\.Random" src/ --include="*.java" | wc -l | xargs -I {} echo "   - Found {} Random usages (should use SecureRandom)"

echo "5c. Checking for weak crypto algorithms..."
grep -r "MD5\|SHA1\|DES" src/ --include="*.java" | wc -l | xargs -I {} echo "   - Found {} potentially weak crypto usages"

echo

# 6. Performance optimization opportunities
echo "6. Performance Analysis:"
echo "6a. Checking for string concatenation in loops..."
grep -n "+\s+String" src/ --include="*.java" | grep -E "for\|while" | head -3 | xargs -I {} echo "   - Potential string concatenation in loop at {}"

echo "6b. Checking for synchronized blocks..."
grep -r "synchronized" src/ --include="*.java" | wc -l | xargs -I {} echo "   - Found {} synchronized usages"

echo "6c. Checking for unnecessary object creation..."
grep -r "new.*String(" src/ --include="*.java" | wc -l | xargs -I {} echo "   - Found unnecessary String() constructor usages"

echo

# 7. Migration readiness score
echo "7. Java 25 Migration Readiness Score:"

# Calculate score based on modern features usage
modern_features=$(grep -r "record\|sealed" src/ --include="*.java" | wc -l)
deprecated_count=$(grep -r "Vector\|Hashtable\|java\.util\.Date" src/ --include="*.java" | wc -l)
total_files=$(find . -name "*.java" | wc -l)

score=$((modern_features * 10))
max_score=100
if [ $score -gt $max_score ]; then
    score=$max_score
fi

echo "   Modern Features Score: $modern_features/50"
echo "   Deprecated APIs Score: $((50 - deprecated_count/10))/50"
echo "   Overall Readiness Score: $score/100"

if [ $score -ge 80 ]; then
    echo "   Status: ✅ Ready for Java 25"
elif [ $score -ge 60 ]; then
    echo "   Status: ⚠️  Mostly ready, minor updates needed"
else
    echo "   Status: ❌ Significant refactoring required"
fi

echo

# 8. Build performance analysis
echo "8. Build Performance Analysis:"
echo "8a. Maven build time..."
time_start=$(date +%s)
mvn compile -q -Dmaven.test.skip=true
time_end=$(date +%s)
echo "   Maven compile time: $((time_end - time_start)) seconds"

echo "8b. Compilation warnings..."
mvn compile -q -Dmaven.test.skip=true 2>&1 | grep -E "(WARNING|INFO)" | wc -l | xargs -I {} echo "   Found {} compilation warnings"

echo

# 9. Test compatibility
echo "9. Test Compatibility:"
mvn test-compile -q -Dmaven.test.skip=false 2>&1 | grep -E "(FAILED|ERROR|SKIPPED)" | wc -l | xargs -I {} echo "   Found {} test issues"

echo

# 10. Module-level analysis
echo "10. Module-by-Module Analysis:"
for module in yawl-utilities yawl-elements yawl-engine yawl-stateless yawl-integration; do
    if [ -d "$module" ]; then
        echo "   $module:"
        java_files=$(find "$module" -name "*.java" | wc -l)
        echo "     - Java files: $java_files"
        if [ -f "$module/pom.xml" ]; then
            module_deps=$(grep -c "<dependency>" "$module/pom.xml")
            echo "     - Dependencies: $module_deps"
        fi
    fi
done

echo
echo "=== Analysis Complete ==="
echo "Completed at: $(date)"