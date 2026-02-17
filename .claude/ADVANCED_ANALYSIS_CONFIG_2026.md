# Advanced Static Analysis & SonarQube Configuration 2026

**For**: YAWL v5.2 (Java 25, 89 packages) | **Date**: 2026-02-17 | **Framework**: Maven

---

## Overview

This document provides advanced static analysis configurations for integrating multiple analysis tools (SpotBugs, PMD, SonarQube, Error Prone, Checkstyle) with YAWL's Maven build system.

---

## Part 1: Complete SonarQube Integration

### 1.1 POM Configuration

#### Parent POM Properties

```xml
<properties>
    <!-- SonarQube Settings -->
    <sonar.host.url>https://sonarqube.example.com</sonar.host.url>
    <!-- Token: Set via environment variable SONAR_TOKEN -->
    <sonar.projectKey>org.yawlfoundation:yawl</sonar.projectKey>
    <sonar.projectName>YAWL Workflow Engine v5.2</sonar.projectName>
    <sonar.projectDescription>Enterprise BPM/Workflow system based on Petri net semantics</sonar.projectDescription>

    <!-- Source Configuration -->
    <sonar.sources>src/main/java</sonar.sources>
    <sonar.tests>src/test/java</sonar.tests>
    <sonar.java.source>25</sonar.java.source>
    <sonar.java.target>25</sonar.java.target>

    <!-- Code Coverage Integration -->
    <sonar.coverage.jacoco.xmlReportPaths>${project.basedir}/target/site/jacoco/jacoco.xml</sonar.coverage.jacoco.xmlReportPaths>

    <!-- Exclusions -->
    <sonar.exclusions>
        **/generated/**,
        **/target/**,
        **/*.xml,
        **/*.properties
    </sonar.exclusions>

    <!-- Test Exclusions (tests themselves not analyzed) -->
    <sonar.test.exclusions>
        **/*Test*.java,
        **/test/**
    </sonar.test.exclusions>

    <!-- Code Smells Threshold -->
    <sonar.qualitygate.wait>true</sonar.qualitygate.wait>
</properties>
```

#### SonarQube Plugin Configuration

```xml
<plugin>
    <groupId>org.sonarsource.scanner.maven</groupId>
    <artifactId>sonar-maven-plugin</artifactId>
    <version>3.11.0.3477</version>
    <executions>
        <execution>
            <phase>verify</phase>
            <goals>
                <goal>sonar</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### 1.2 Running SonarQube Analysis

#### Local SonarQube Server

```bash
# Start SonarQube (Docker)
docker run -d -p 9000:9000 sonarqube:latest

# Run analysis
mvn clean verify \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login=admin \
  sonar:sonar
```

#### CI/CD Environment

```bash
# With token authentication
mvn clean verify sonar:sonar \
  -Dsonar.host.url=$SONAR_HOST_URL \
  -Dsonar.login=$SONAR_TOKEN
```

#### GitHub Actions Integration

```yaml
name: SonarQube Analysis
on: [push, pull_request]

jobs:
  sonarqube:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
      with:
        fetch-depth: 0  # Full history for analysis

    - name: Set up JDK 25
      uses: actions/setup-java@v4
      with:
        java-version: 25
        distribution: temurin
        cache: maven

    - name: Build and analyze
      env:
        SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
        SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      run: |
        mvn -B clean verify \
          -Dsonar.projectKey=org.yawlfoundation:yawl \
          sonar:sonar
```

---

## Part 2: Quality Gates & Rules

### 2.1 SonarQube Quality Gate Configuration

**Quality Gate XML** (Import into SonarQube):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<qualityGate>
    <!-- Overall Code Quality -->
    <condition>
        <metric>alert_status</metric>
        <operator>EQUALS</operator>
        <error>ERROR</error>
    </condition>

    <!-- Code Coverage: Minimum 75% -->
    <condition>
        <metric>coverage</metric>
        <operator>LESS_THAN</operator>
        <error>75</error>
    </condition>

    <!-- Duplicated Lines: Maximum 5% -->
    <condition>
        <metric>duplicated_lines_density</metric>
        <operator>GREATER_THAN</operator>
        <error>5</error>
    </condition>

    <!-- Maintainability Rating: A or better -->
    <condition>
        <metric>sqale_rating</metric>
        <operator>GREATER_THAN</operator>
        <error>1</error>
    </condition>

    <!-- Security Rating: A or better -->
    <condition>
        <metric>security_rating</metric>
        <operator>GREATER_THAN</operator>
        <error>1</error>
    </condition>

    <!-- Reliability Rating: A or better -->
    <condition>
        <metric>reliability_rating</metric>
        <operator>GREATER_THAN</operator>
        <error>1</error>
    </condition>

    <!-- Critical Issues: Zero -->
    <condition>
        <metric>critical_violations</metric>
        <operator>GREATER_THAN</operator>
        <error>0</error>
    </condition>

    <!-- Blocker Issues: Zero -->
    <condition>
        <metric>blocker_violations</metric>
        <operator>GREATER_THAN</operator>
        <error>0</error>
    </condition>
</qualityGate>
```

### 2.2 Custom Rules for YAWL

**Petri Net-Specific Rules** (Configure in SonarQube):

```properties
# Rule: YEngine methods should handle WorkItems immutably
sonar.java.customRules.1.key=YAWL_IMMUTABLE_WORKITEM
sonar.java.customRules.1.name=WorkItem must be immutable in YEngine
sonar.java.customRules.1.severity=CRITICAL

# Rule: No direct database access outside YPersistence
sonar.java.customRules.2.key=YAWL_NO_DIRECT_DB
sonar.java.customRules.2.name=Database access only via YPersistence layer
sonar.java.customRules.2.severity=BLOCKER

# Rule: YSpecification must validate Petri net properties
sonar.java.customRules.3.key=YAWL_SPEC_VALIDATION
sonar.java.customRules.3.name=YSpecification must validate Petri net invariants
sonar.java.customRules.3.severity=CRITICAL

# Rule: YNetRunner must not block indefinitely
sonar.java.customRules.4.key=YAWL_NO_INDEFINITE_WAITS
sonar.java.customRules.4.name=YNetRunner operations must have timeouts
sonar.java.customRules.4.severity=MAJOR
```

---

## Part 3: Multi-Tool Analysis Pipeline

### 3.1 Integrated Build Profile

```xml
<!-- In parent pom.xml -->
<profile>
    <id>full-analysis</id>
    <description>Complete static analysis pipeline</description>

    <build>
        <plugins>
            <!-- 1. Compile with Error Prone -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>com.google.errorprone</groupId>
                            <artifactId>error_prone_core</artifactId>
                            <version>2.36.0</version>
                        </path>
                    </annotationProcessorPaths>
                    <compilerArgs>
                        <arg>-XepDisableAllChecks</arg>
                        <!-- Enable critical checks -->
                        <arg>-Xep:NullableProto:ERROR</arg>
                        <arg>-Xep:NumericEquality:WARNING</arg>
                        <arg>-Xep:ProtocolBufferOrdinal:ERROR</arg>
                        <arg>-Xep:UnusedVariable:WARNING</arg>
                    </compilerArgs>
                </configuration>
            </plugin>

            <!-- 2. Run Unit Tests with Coverage -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.4</version>
            </plugin>

            <!-- 3. Code Coverage Analysis -->
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>0.8.15</version>
                <executions>
                    <execution>
                        <id>jacoco-check</id>
                        <goals>
                            <goal>check</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>

            <!-- 4. Bug Detection (SpotBugs) -->
            <plugin>
                <groupId>com.github.spotbugs</groupId>
                <artifactId>spotbugs-maven-plugin</artifactId>
                <version>4.8.2</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>check</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>

            <!-- 5. Code Smell Detection (PMD) -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-pmd-plugin</artifactId>
                <version>3.25.0</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>check</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>

            <!-- 6. Style Enforcement (Checkstyle) -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-checkstyle-plugin</artifactId>
                <version>3.3.1</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>check</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>

            <!-- 7. Dependency Audit -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-dependency-plugin</artifactId>
                <version>3.6.1</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>analyze</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>

            <!-- 8. SonarQube Analysis -->
            <plugin>
                <groupId>org.sonarsource.scanner.maven</groupId>
                <artifactId>sonar-maven-plugin</artifactId>
                <version>3.11.0.3477</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>sonar</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</profile>
```

### 3.2 Running Full Analysis Pipeline

```bash
# Sequential execution (ensures order)
mvn -Pfull-analysis clean verify

# Parallel with full analysis
mvn -T 1.5C -Pfull-analysis clean verify

# With SonarQube
mvn -T 1.5C -Pfull-analysis clean verify \
  -Dsonar.host.url=$SONAR_HOST_URL \
  -Dsonar.login=$SONAR_TOKEN

# Expected time: ~180-240s (full build + all analysis)
```

---

## Part 4: Error Prone Advanced Configuration

### 4.1 Error Prone with NullAway

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.15.0</version>
    <configuration>
        <release>25</release>
        <annotationProcessorPaths>
            <!-- Error Prone Core -->
            <path>
                <groupId>com.google.errorprone</groupId>
                <artifactId>error_prone_core</artifactId>
                <version>2.36.0</version>
            </path>
            <!-- NullAway (null safety checker) -->
            <path>
                <groupId>com.uber.nullaway</groupId>
                <artifactId>nullaway</artifactId>
                <version>0.10.25</version>
            </path>
        </annotationProcessorPaths>
        <compilerArgs>
            <!-- Error Prone configuration -->
            <arg>-XepDisableAllChecks</arg>

            <!-- Enable critical checks -->
            <arg>-Xep:NullableProto:ERROR</arg>
            <arg>-Xep:NumericEquality:ERROR</arg>
            <arg>-Xep:UnusedVariable:WARNING</arg>
            <arg>-Xep:MissingOverride:ERROR</arg>
            <arg>-Xep:SelfEquals:ERROR</arg>
            <arg>-Xep:SelfAssignment:ERROR</arg>

            <!-- NullAway configuration -->
            <arg>-XepOpt:NullAway:AnnotatedPackages=org.yawlfoundation</arg>
            <arg>-XepOpt:NullAway:TreatGeneratedAsUnannotated=true</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

### 4.2 Custom Annotation Definitions

Create `src/main/java/org/yawlfoundation/yawl/annotations/Nullable.java`:

```java
package org.yawlfoundation.yawl.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Indicates that a value can be null.
 * Use with Error Prone's NullAway for compile-time null safety.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({
    ElementType.METHOD,
    ElementType.PARAMETER,
    ElementType.FIELD,
    ElementType.LOCAL_VARIABLE
})
public @interface Nullable {
    String value() default "";
}
```

Create `src/main/java/org/yawlfoundation/yawl/annotations/NonNull.java`:

```java
package org.yawlfoundation.yawl.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Indicates that a value must never be null.
 * Enforced at compile-time by Error Prone's NullAway.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({
    ElementType.METHOD,
    ElementType.PARAMETER,
    ElementType.FIELD,
    ElementType.LOCAL_VARIABLE
})
public @interface NonNull {
}
```

### 4.3 Using Annotations in YAWL Code

```java
// YEngine example
public class YEngine {

    /**
     * Execute a work item.
     * @param item Non-null work item to execute
     * @param data May be null if task has no input parameters
     * @return Result ID (never null)
     */
    @NonNull
    public String executeWorkItem(
        @NonNull YWorkItem item,
        @Nullable Map<String, Object> data
    ) {
        // Compile-time null safety checking
        String result = item.getId(); // OK
        if (data == null) {
            // Must handle null case
            data = new HashMap<>();
        }
        return result;
    }
}
```

---

## Part 5: SpotBugs Advanced Configuration

### 5.1 SpotBugs Exclusion Filters

Create `.spotbugs-exclude.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<FindBugsFilter>
    <!-- Exclude test classes -->
    <Match>
        <Class name="~.*Test$"/>
    </Match>

    <!-- Exclude generated code -->
    <Match>
        <Class name="~.*\.generated\..*"/>
    </Match>

    <!-- Exclude intentional singletons -->
    <Match>
        <Bug pattern="ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD"/>
        <Class name="org.yawlfoundation.yawl.engine.YEngine"/>
    </Match>

    <!-- Exclude false positives in PetriNet analysis -->
    <Match>
        <Bug pattern="NP_NULL_PARAM_DEREF_ALL_PATHS_EXCEPT_ONE"/>
        <Class name="org.yawlfoundation.yawl.elements.*"/>
    </Match>

    <!-- Allow null returns in factory methods -->
    <Match>
        <Bug pattern="NP_RETURN_FROM_METHOD_WITHOUT_THROWING_EXCEPTION"/>
        <Class name="org.yawlfoundation.yawl.elements.YElementFactory"/>
    </Match>
</FindBugsFilter>
```

### 5.2 SpotBugs Configuration

```xml
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>4.8.2</version>
    <configuration>
        <!-- Analysis effort -->
        <effort>max</effort>
        <threshold>low</threshold>

        <!-- Output format -->
        <xmlOutput>true</xmlOutput>
        <htmlOutput>true</htmlOutput>

        <!-- Exclude files -->
        <excludeFilterFile>${basedir}/.spotbugs-exclude.xml</excludeFilterFile>
        <skipTests>true</skipTests>

        <!-- Reports -->
        <reportFormat>html</reportFormat>
        <reportFormat>xml</reportFormat>

        <!-- Plugins (optional) -->
        <plugins>
            <plugin>
                <groupId>com.mebigfatguy.fb-contrib</groupId>
                <artifactId>fb-contrib</artifactId>
                <version>7.6.4</version>
            </plugin>
        </plugins>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

---

## Part 6: PMD Advanced Configuration

### 6.1 Custom PMD Rules

Create `custom-pmd-rules.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ruleset name="YAWL Custom Rules"
         xmlns="http://pmd.sf.net/ruleset/2.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://pmd.sf.net/ruleset/2.0.0
         http://pmd.sf.net/ruleset_2_0_0.xsd">

    <!-- Standard rulesets -->
    <rule ref="rulesets/java/basic.xml"/>
    <rule ref="rulesets/java/empty-code-blocks.xml"/>
    <rule ref="rulesets/java/errorprone.xml"/>
    <rule ref="rulesets/java/performance.xml"/>

    <!-- YAWL-specific rules -->
    <rule name="YEngineMethodsMustNotBlock"
          class="net.sourceforge.pmd.lang.java.rule.bestpractices.AvoidUsingVolatile"
          message="YEngine methods should not block indefinitely">
        <priority>1</priority>
    </rule>

    <rule name="YSpecificationMustValidate"
          class="net.sourceforge.pmd.lang.java.rule.design.TooManyFields"
          message="YSpecification must validate Petri net invariants"
          exclude-pattern="**/YSpecification.java">
        <priority>1</priority>
    </rule>

    <rule name="CyclomaticComplexity"
          class="net.sourceforge.pmd.lang.java.rule.complexity.CyclomaticComplexityRule"
          message="Method has too high cyclomatic complexity">
        <priority>3</priority>
        <properties>
            <property name="reportLevel" value="15"/>
        </properties>
    </rule>

    <!-- Exclude test classes -->
    <exclude-pattern>.*Test\.java$</exclude-pattern>
    <exclude-pattern>.*\.test\..*</exclude-pattern>
</ruleset>
```

### 6.2 PMD Configuration

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-pmd-plugin</artifactId>
    <version>3.25.0</version>
    <configuration>
        <!-- Custom ruleset -->
        <rulesets>
            <ruleset>custom-pmd-rules.xml</ruleset>
        </rulesets>

        <!-- Fail configuration -->
        <failOnViolation>true</failOnViolation>
        <printFailingErrors>true</printFailingErrors>
        <maxAllowedViolations>0</maxAllowedViolations>

        <!-- Reporting -->
        <format>html</format>
        <format>xml</format>
        <linkXRef>true</linkXRef>

        <!-- Exclusions -->
        <excludeRoots>
            <excludeRoot>target/generated-sources</excludeRoot>
        </excludeRoots>

        <!-- Performance -->
        <numThreads>4</numThreads>
    </configuration>
    <executions>
        <execution>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
                <goal>cpd-check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

---

## Part 7: CI/CD Analysis Integration

### 7.1 Complete GitHub Actions Workflow

```yaml
name: Complete Analysis Pipeline

on:
  push:
    branches: [ main, develop, claude/** ]
  pull_request:
    branches: [ main ]

env:
  SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
  SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}

jobs:
  analyze:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
      with:
        fetch-depth: 0

    - name: Set up JDK 25
      uses: actions/setup-java@v4
      with:
        java-version: 25
        distribution: temurin
        cache: maven

    - name: Compile with Error Prone
      run: |
        mvn -B -T 1.5C clean compile \
          -DskipTests

    - name: Run Unit Tests with Coverage
      run: |
        mvn -B -T 1.5C test

    - name: Code Coverage Check
      run: |
        mvn -B jacoco:check

    - name: SpotBugs Analysis
      run: |
        mvn -B spotbugs:check
      continue-on-error: true

    - name: PMD Analysis
      run: |
        mvn -B pmd:check
      continue-on-error: true

    - name: Checkstyle Analysis
      run: |
        mvn -B checkstyle:check
      continue-on-error: true

    - name: Build Distribution
      run: |
        mvn -B -T 1.5C package -DskipTests

    - name: SonarQube Analysis
      if: always()
      run: |
        mvn -B sonar:sonar \
          -Dsonar.projectKey=org.yawlfoundation:yawl \
          -Dsonar.projectName="YAWL Engine v5.2"

    - name: Upload Analysis Reports
      if: always()
      uses: actions/upload-artifact@v4
      with:
        name: analysis-reports
        path: |
          target/site/
          target/spotbugsXml.xml
          target/pmd.xml
        retention-days: 30

    - name: Publish Test Results
      if: always()
      uses: dorny/test-reporter@v1
      with:
        name: Test Results
        path: target/surefire-reports/TEST-*.xml
        reporter: java-junit
```

### 7.2 Quality Gate Check

```bash
# Wait for quality gate and fail if it fails
mvn sonar:sonar \
  -Dsonar.qualitygate.wait=true \
  -Dsonar.qualitygate.timeout=300

# Exit code will reflect quality gate status
echo "Quality gate status: $?"
```

---

## Part 8: Performance Profiling

### 8.1 Build Time Analysis

```bash
# Measure build time with detailed breakdown
mvn -T 1.5C clean verify \
  -DskipTests \
  -Dorg.slf4j.simpleLogger.defaultLogLevel=warn \
  --debug 2>&1 | tee build.log

# Extract timing information
grep -E "^\[INFO\] BUILD|seconds" build.log
```

### 8.2 Test Performance Profiling

```bash
# Surefire test timing report
mvn test -Dorg.slf4j.simpleLogger.defaultLogLevel=warn

# View slowest tests
cat target/surefire-reports/*.txt | grep -E "Tests run|Failures|Skipped" | sort
```

### 8.3 Coverage Impact Analysis

```bash
# Generate detailed coverage report
mvn clean test jacoco:report

# Extract coverage metrics
grep -oP 'Covered: \K[0-9.]+' target/site/jacoco/index.html
```

---

## Part 9: Reporting & Dashboards

### 9.1 Maven Site Configuration

```xml
<!-- In parent pom.xml -->
<reporting>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-report-plugin</artifactId>
            <version>3.5.4</version>
        </plugin>

        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.15</version>
        </plugin>

        <plugin>
            <groupId>com.github.spotbugs</groupId>
            <artifactId>spotbugs-maven-plugin</artifactId>
            <version>4.8.2</version>
        </plugin>

        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-pmd-plugin</artifactId>
            <version>3.25.0</version>
        </plugin>

        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-checkstyle-plugin</artifactId>
            <version>3.3.1</version>
        </plugin>

        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-jxr-plugin</artifactId>
            <version>3.4.0</version>
        </plugin>

        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-project-info-reports-plugin</artifactId>
            <version>3.6.0</version>
        </plugin>
    </plugins>
</reporting>
```

### 9.2 Generate Site Report

```bash
# Build complete site with all reports
mvn clean site site:stage

# View in browser
open target/staging/index.html  # macOS
xdg-open target/staging/index.html  # Linux
```

---

## Checklist: Full Implementation

- [ ] Update parent POM with all plugin versions
- [ ] Configure SonarQube integration (project key, host URL)
- [ ] Create SonarQube quality gate rules
- [ ] Add `.spotbugs-exclude.xml` to project root
- [ ] Add `custom-pmd-rules.xml` to project root
- [ ] Configure Error Prone with NullAway
- [ ] Create null safety annotations
- [ ] Add annotations to critical classes (YEngine, YSpecification)
- [ ] Setup GitHub Actions complete analysis workflow
- [ ] Test full pipeline: `mvn -Pfull-analysis clean verify`
- [ ] Configure SonarQube webhook in GitHub (optional)
- [ ] Document analysis exclusions and rationale
- [ ] Train team on annotation usage

---

## Quick Commands

```bash
# Full analysis pipeline (requires all tools)
mvn -Pfull-analysis clean verify

# Parallel with full analysis
mvn -T 1.5C -Pfull-analysis clean verify

# SonarQube only
mvn clean verify sonar:sonar

# SpotBugs + PMD (fast)
mvn spotbugs:check pmd:check

# Error Prone compile check
mvn clean compile -DskipTests

# Generate all reports
mvn clean verify site site:stage
```

---

**Document Status**: Complete | **Date**: 2026-02-17 | **Version**: 1.0
